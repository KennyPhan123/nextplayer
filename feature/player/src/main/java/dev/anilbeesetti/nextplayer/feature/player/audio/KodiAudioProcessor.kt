package dev.anilbeesetti.nextplayer.feature.player.audio

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Ports Kodi's ActiveAE audio processing pipeline (`CAELimiter` + `CActiveAEResampleFFMPEG`
 * multichannel downmixing) to NextPlayer:
 *
 * 1. **32-bit Float Internal Pipeline**:
 *    Reads decoded 32-bit float PCM (`C.ENCODING_PCM_FLOAT`, including unclipped > 0 dBFS peaks
 *    from FFmpeg/MediaCodec decoders) as well as 16/24/32-bit integer PCM, performing all
 *    mixing, dialogue enhancement, amplification, and dynamic peak limiting in 32-bit float.
 *
 * 2. **Device-Aware Smart Stereo Downmixer (5.1 / 7.1 -> 2.0)**:
 *    - **Smart mode ON (default)**: Uses unnormalized weights so Center-channel dialogue remains
 *      loud and clear even at `<= 100%` volume (`FC = 0.85f`), adapts LFE sub-bass by output route
 *      (`0.0f` on built-in phone speaker to prevent rattle/headroom waste; `0.35f` on stereo
 *      headphones for warm bass), and relies on the Kodi peak limiter to catch transient explosions.
 *    - **Smart mode OFF**: Uses standard ITU-R BS.775 normalized downmix matrices (`FC = 0.7071f`,
 *      `Surround = 0.7071f`, `LFE = 0.0f`, scaled so total weight per channel <= 1.0f).
 *    - **Spatial Audio / HDMI Multichannel (`MULTICHANNEL_OR_SPATIAL`)**: Preserves all native
 *      channels without stereo downmixing while running the limiter across all channels per frame.
 *
 * 3. **Kodi `CAELimiter` (`xbmc/cores/AudioEngine/Utils/AELimiter.cpp`)**:
 *    Replaces Android's hardware `LoudnessEnhancer` with Kodi's look-ahead/instant-attack,
 *    25 ms hold, 100 ms exponential release peak limiter so boosting volume up to 200% (+20 dB)
 *    never clips or distorts.
 */
@UnstableApi
class KodiAudioProcessor {

    @Volatile
    var smartAudioEnabled: Boolean = true

    @Volatile
    var routeMode: AudioOutputRouteMode = AudioOutputRouteMode.SPEAKER

    @Volatile
    var gainMillibels: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, MAX_GAIN_MILLIBELS)
            field = clamped
            amplify = if (clamped == 0) {
                1.0f
            } else {
                10.0f.pow(clamped / 2000.0f).coerceIn(1.0f, 1000.0f)
            }
        }

    @Volatile
    var amplify: Float = 1.0f
        private set

    var sampleRate: Int = 48000
        private set

    var inputChannelCount: Int = 2
        private set

    @C.PcmEncoding
    var inputEncoding: Int = C.ENCODING_PCM_FLOAT
        private set

    var configuredOutputChannelCount: Int = 2
        private set

    // Kodi CAELimiter state (mirrors CAELimiter in xbmc/cores/AudioEngine/Utils/AELimiter.cpp)
    private var attenuation: Float = 1.0f
    private var holdCounter: Int = 0
    private var increase: Float = 0.0f

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inFrameScratch = FloatArray(MAX_CHANNELS)
    private var outFrameScratch = FloatArray(MAX_CHANNELS)

    fun resolveConfiguredOutputChannels(upstreamChannelCount: Int): Int {
        if (upstreamChannelCount <= 2) return upstreamChannelCount
        return if (routeMode == AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL) {
            upstreamChannelCount
        } else {
            2
        }
    }

    fun configure(
        sampleRate: Int,
        inputChannelCount: Int,
        @C.PcmEncoding inputEncoding: Int,
    ): Int {
        this.sampleRate = sampleRate.coerceAtLeast(8000)
        this.inputChannelCount = inputChannelCount.coerceAtLeast(1)
        this.inputEncoding = inputEncoding
        this.configuredOutputChannelCount = resolveConfiguredOutputChannels(this.inputChannelCount)
        if (inFrameScratch.size < this.inputChannelCount) {
            inFrameScratch = FloatArray(this.inputChannelCount)
        }
        if (outFrameScratch.size < this.configuredOutputChannelCount) {
            outFrameScratch = FloatArray(this.configuredOutputChannelCount)
        }
        resetLimiterState()
        return this.configuredOutputChannelCount
    }

    fun flush() {
        resetLimiterState()
        outputBuffer = EMPTY_BUFFER
    }

    fun reset() {
        flush()
        sampleRate = 48000
        inputChannelCount = 2
        configuredOutputChannelCount = 2
        inputEncoding = C.ENCODING_PCM_FLOAT
    }

    private fun resetLimiterState() {
        attenuation = 1.0f
        holdCounter = 0
        increase = 0.0f
    }

    /**
     * Processes [input] without mutating its position (caller advances position once downstream
     * sink consumes the returned [ByteBuffer]) and returns a `C.ENCODING_PCM_16BIT` [ByteBuffer]
     * with [configuredOutputChannelCount] channels.
     */
    fun process(input: ByteBuffer): ByteBuffer {
        val bytesPerInputSample = getBytesPerSample(inputEncoding)
        val inputFrameSize = bytesPerInputSample * inputChannelCount
        val remainingBytes = input.remaining()
        if (inputFrameSize <= 0 || remainingBytes < inputFrameSize) {
            return EMPTY_BUFFER
        }

        val numFrames = remainingBytes / inputFrameSize
        val outputFrameSize = 2 * configuredOutputChannelCount // 16-bit PCM output
        val requiredOutputBytes = numFrames * outputFrameSize

        if (outputBuffer.capacity() < requiredOutputBytes) {
            outputBuffer = ByteBuffer.allocateDirect(requiredOutputBytes).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val slice = input.slice().order(input.order())
        val inChannels = inputChannelCount
        val outChannels = configuredOutputChannelCount
        val activeRoute = routeMode
        val isSmart = smartAudioEnabled
        val currentAmplify = amplify
        val useLimiter = isSmart || currentAmplify > 1.0001f

        val shouldDownmixToStereo = inChannels > 2 && activeRoute != AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL

        for (frameIndex in 0 until numFrames) {
            for (ch in 0 until inChannels) {
                inFrameScratch[ch] = readSampleAsFloat(slice, inputEncoding)
            }

            val activeMixChannels: Int
            if (shouldDownmixToStereo) {
                downmixFrameToStereo(
                    inFrame = inFrameScratch,
                    inChannels = inChannels,
                    outFrame = outFrameScratch,
                    smartMode = isSmart,
                    route = activeRoute,
                )
                // If configuredOutputChannelCount > 2 (e.g. switched from Spatializer to speaker
                // mid-stream before next configure()), zero the remaining channels so frame size
                // stays consistent with DefaultAudioSink.
                for (ch in 2 until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = min(2, outChannels)
            } else {
                val copyChannels = min(inChannels, outChannels)
                for (ch in 0 until copyChannels) {
                    outFrameScratch[ch] = inFrameScratch[ch]
                }
                for (ch in copyChannels until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = copyChannels
            }

            val frameGain = if (useLimiter) {
                runKodiLimiter(outFrameScratch, activeMixChannels, currentAmplify)
            } else {
                currentAmplify
            }

            for (ch in 0 until outChannels) {
                val sample = (outFrameScratch[ch] * frameGain).coerceIn(-1.0f, 1.0f)
                val pcm16 = (sample * 32767.0f).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                outputBuffer.putShort(pcm16)
            }
        }

        outputBuffer.flip()
        return outputBuffer
    }

    /**
     * Direct float-to-float frame processor for unit testing and inspection of exact floating-point
     * envelope behavior before 16-bit quantization.
     */
    fun processFloatInterleaved(
        inputSamples: FloatArray,
        numFrames: Int = inputSamples.size / inputChannelCount,
    ): FloatArray {
        val inChannels = inputChannelCount
        val outChannels = configuredOutputChannelCount
        val output = FloatArray(numFrames * outChannels)
        val activeRoute = routeMode
        val isSmart = smartAudioEnabled
        val currentAmplify = amplify
        val useLimiter = isSmart || currentAmplify > 1.0001f
        val shouldDownmixToStereo = inChannels > 2 && activeRoute != AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL

        for (frameIndex in 0 until numFrames) {
            val inOffset = frameIndex * inChannels
            for (ch in 0 until inChannels) {
                val v = inputSamples[inOffset + ch]
                inFrameScratch[ch] = if (v.isFinite()) v else 0.0f
            }

            val activeMixChannels: Int
            if (shouldDownmixToStereo) {
                downmixFrameToStereo(
                    inFrame = inFrameScratch,
                    inChannels = inChannels,
                    outFrame = outFrameScratch,
                    smartMode = isSmart,
                    route = activeRoute,
                )
                for (ch in 2 until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = min(2, outChannels)
            } else {
                val copyChannels = min(inChannels, outChannels)
                for (ch in 0 until copyChannels) {
                    outFrameScratch[ch] = inFrameScratch[ch]
                }
                for (ch in copyChannels until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = copyChannels
            }

            val frameGain = if (useLimiter) {
                runKodiLimiter(outFrameScratch, activeMixChannels, currentAmplify)
            } else {
                currentAmplify
            }

            val outOffset = frameIndex * outChannels
            for (ch in 0 until outChannels) {
                output[outOffset + ch] = (outFrameScratch[ch] * frameGain).coerceIn(-1.0f, 1.0f)
            }
        }
        return output
    }

    /**
     * Exact port of `CAELimiter::Run` from `xbmc/cores/AudioEngine/Utils/AELimiter.cpp`.
     */
    private fun runKodiLimiter(
        frame: FloatArray,
        channels: Int,
        currentAmplify: Float,
    ): Float {
        var highest = 0.0f
        for (i in 0 until channels) {
            val absVal = abs(frame[i])
            if (absVal > highest) {
                highest = absVal
            }
        }

        val sample = highest * currentAmplify
        if (sample * attenuation > LIMITER_CEILING) {
            attenuation = LIMITER_CEILING / sample
            holdCounter = (sampleRate * LIMITER_HOLD_SECONDS).roundToInt()
            val normalizedPeak = min(sample / LIMITER_CEILING, 10000.0f)
            increase = normalizedPeak.pow(1.0f / (LIMITER_RELEASE_SECONDS * sampleRate))
        }

        val currentAttenuation = attenuation

        if (holdCounter > 0) {
            holdCounter--
        } else if (increase > 0.0f) {
            attenuation *= increase
            if (attenuation > 1.0f) {
                increase = 0.0f
                attenuation = 1.0f
            }
        }

        return currentAttenuation * currentAmplify
    }

    /**
     * Downmixes multichannel audio (3.0, 4.0, 5.0, 5.1, 6.1, 7.1+) to 2.0 stereo.
     *
     * Standard FFmpeg / WAVE / Android channel layouts:
     * - 3ch (3.0): FL(0), FR(1), FC(2)
     * - 4ch (Quad): FL(0), FR(1), BL(2), BR(3)
     * - 5ch (5.0): FL(0), FR(1), FC(2), BL(3), BR(4)
     * - 6ch (5.1): FL(0), FR(1), FC(2), LFE(3), BL/SL(4), BR/SR(5)
     * - 7ch (6.1): FL(0), FR(1), FC(2), LFE(3), BL(4), BR(5), BC(6)
     * - 8ch (7.1): FL(0), FR(1), FC(2), LFE(3), BL(4), BR(5), SL(6), SR(7)
     */
    private fun downmixFrameToStereo(
        inFrame: FloatArray,
        inChannels: Int,
        outFrame: FloatArray,
        smartMode: Boolean,
        route: AudioOutputRouteMode,
    ) {
        val fl = inFrame[0]
        val fr = inFrame[1]

        val centerGain = if (smartMode) SMART_CENTER_GAIN else ITU_CENTER_GAIN
        val surroundGain = when {
            !smartMode -> ITU_SURROUND_GAIN
            route == AudioOutputRouteMode.SPEAKER -> SMART_SPEAKER_SURROUND_GAIN
            else -> SMART_HEADPHONE_SURROUND_GAIN
        }
        val lfeGain = when {
            !smartMode -> 0.0f
            route == AudioOutputRouteMode.STEREO_HEADPHONES -> SMART_HEADPHONE_LFE_GAIN
            else -> 0.0f
        }

        var left = fl
        var right = fr
        var maxChannelWeightSum = 1.0f

        when (inChannels) {
            3 -> {
                val fc = inFrame[2] * centerGain
                left += fc
                right += fc
                maxChannelWeightSum += centerGain
            }

            4 -> {
                left += inFrame[2] * surroundGain
                right += inFrame[3] * surroundGain
                maxChannelWeightSum += surroundGain
            }

            5 -> {
                val fc = inFrame[2] * centerGain
                left += fc + inFrame[3] * surroundGain
                right += fc + inFrame[4] * surroundGain
                maxChannelWeightSum += centerGain + surroundGain
            }

            6 -> {
                val fc = inFrame[2] * centerGain
                val lfe = inFrame[3] * lfeGain
                left += fc + lfe + inFrame[4] * surroundGain
                right += fc + lfe + inFrame[5] * surroundGain
                maxChannelWeightSum += centerGain + lfeGain + surroundGain
            }

            7 -> {
                val fc = inFrame[2] * centerGain
                val lfe = inFrame[3] * lfeGain
                val bc = inFrame[6] * BACK_CENTER_GAIN
                left += fc + lfe + inFrame[4] * surroundGain + bc
                right += fc + lfe + inFrame[5] * surroundGain + bc
                maxChannelWeightSum += centerGain + lfeGain + surroundGain + BACK_CENTER_GAIN
            }

            else -> {
                // 8ch (7.1) and higher
                val fc = inFrame[2] * centerGain
                val lfe = inFrame[3] * lfeGain
                left += fc + lfe + (inFrame[4] + inFrame[6]) * surroundGain
                right += fc + lfe + (inFrame[5] + inFrame[7]) * surroundGain
                maxChannelWeightSum += centerGain + lfeGain + 2.0f * surroundGain
            }
        }

        if (!smartMode && maxChannelWeightSum > 1.0f) {
            // Kodi's normalized downmix mode (divide by sum of weights so peaks <= 1.0f)
            val normFactor = 1.0f / maxChannelWeightSum
            left *= normFactor
            right *= normFactor
        }

        outFrame[0] = left
        outFrame[1] = right
    }

    private fun getBytesPerSample(@C.PcmEncoding encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private fun readSampleAsFloat(buffer: ByteBuffer, @C.PcmEncoding encoding: Int): Float {
        return when (encoding) {
            C.ENCODING_PCM_FLOAT -> {
                val f = buffer.float
                if (f.isFinite()) f else 0.0f
            }

            C.ENCODING_PCM_16BIT -> {
                buffer.short / 32768.0f
            }

            C.ENCODING_PCM_24BIT -> {
                val b0 = buffer.get().toInt() and 0xFF
                val b1 = buffer.get().toInt() and 0xFF
                val b2 = buffer.get().toInt() // sign-extended MSByte in little-endian
                val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                sample24 / 8388608.0f
            }

            C.ENCODING_PCM_32BIT -> {
                buffer.int / 2147483648.0f
            }

            else -> 0.0f
        }
    }

    companion object {
        const val MAX_GAIN_MILLIBELS = 2000 // +20 dB (200% boost in NextPlayer UI)
        private const val MAX_CHANNELS = 16

        // Kodi CAELimiter constants (from AdvancedSettings.cpp)
        const val LIMITER_HOLD_SECONDS = 0.025f // 25 ms
        const val LIMITER_RELEASE_SECONDS = 0.100f // 100 ms
        const val LIMITER_CEILING = 0.98f // -0.17 dBFS ceiling to prevent inter-sample DAC clipping

        // Downmix coefficients
        const val ITU_CENTER_GAIN = 0.70710677f // -3.0 dB
        const val ITU_SURROUND_GAIN = 0.70710677f // -3.0 dB
        const val SMART_CENTER_GAIN = 0.85f // -1.4 dB for clear movie dialogue on mobile
        const val SMART_SPEAKER_SURROUND_GAIN = 0.60f // -4.4 dB so ambient effects don't mask speech
        const val SMART_HEADPHONE_SURROUND_GAIN = 0.70710677f // -3.0 dB
        const val SMART_HEADPHONE_LFE_GAIN = 0.35f // -9.1 dB warm sub-bass on headphones
        const val BACK_CENTER_GAIN = 0.50f // -6.0 dB

        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
