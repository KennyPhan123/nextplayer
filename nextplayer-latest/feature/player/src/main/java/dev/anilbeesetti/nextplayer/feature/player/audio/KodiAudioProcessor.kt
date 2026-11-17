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
 * multichannel downmixing + `VideoPlayerAudio.cpp` Center Mix Level & +60 dB Volume Amplification)
 * to NextPlayer.
 */
@UnstableApi
class KodiAudioProcessor {

    @Volatile
    var smartAudioEnabled: Boolean = true

    @Volatile
    var routeMode: AudioOutputRouteMode = AudioOutputRouteMode.SPEAKER

    @Volatile
    var centerMixLevelDb: Int = DEFAULT_CENTER_MIX_LEVEL_DB
        set(value) {
            val clamped = value.coerceIn(
                minimumValue = MIN_CENTER_MIX_LEVEL_DB,
                maximumValue = MAX_CENTER_MIX_LEVEL_DB,
            )
            field = clamped
            centerMixGain = ITU_CENTER_GAIN * 10.0f.pow(clamped / 20.0f)
        }

    @Volatile
    var centerMixGain: Float = ITU_CENTER_GAIN
        private set

    @Volatile
    var gainMillibels: Int = 0
        set(value) {
            val clamped = value.coerceIn(
                minimumValue = 0,
                maximumValue = MAX_GAIN_MILLIBELS,
            )
            field = clamped
            amplify = if (clamped == 0) {
                1.0f
            } else {
                10.0f.pow(clamped / 2000.0f).coerceIn(
                    minimumValue = 1.0f,
                    maximumValue = MAX_AMPLIFICATION,
                )
            }
        }

    @Volatile
    var amplify: Float = 1.0f
        private set

    var sampleRate: Int = 48000
        private set

    var inputChannelCount: Int = 2
        private set

    var inputEncoding: Int = C.ENCODING_PCM_FLOAT
        private set

    var configuredOutputChannelCount: Int = 2
        private set

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
        inputEncoding: Int,
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

    fun process(input: ByteBuffer): ByteBuffer {
        val bytesPerInputSample = getBytesPerSample(inputEncoding)
        val inputFrameSize = bytesPerInputSample * inputChannelCount
        val remainingBytes = input.remaining()
        if (inputFrameSize <= 0 || remainingBytes < inputFrameSize) {
            return EMPTY_BUFFER
        }

        val numFrames = remainingBytes / inputFrameSize
        val outputFrameSize = 2 * configuredOutputChannelCount
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
        val currentCenterGain = centerMixGain
        val currentAmplify = amplify
        val useLimiter = isSmart || currentAmplify > 1.0001f || centerMixLevelDb > 0

        val shouldDownmixToStereo =
            inChannels > 2 && activeRoute != AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL

        for (frameIndex in 0 until numFrames) {
            for (ch in 0 until inChannels) {
                inFrameScratch[ch] = readSampleAsFloat(
                    buffer = slice,
                    encoding = inputEncoding,
                )
            }

            val activeMixChannels: Int
            if (shouldDownmixToStereo) {
                downmixFrameToStereo(
                    inFrame = inFrameScratch,
                    inChannels = inChannels,
                    outFrame = outFrameScratch,
                    smartMode = isSmart,
                    route = activeRoute,
                    activeCenterGain = currentCenterGain,
                )
                for (ch in 2 until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = min(
                    a = 2,
                    b = outChannels,
                )
            } else {
                val copyChannels = min(
                    a = inChannels,
                    b = outChannels,
                )
                for (ch in 0 until copyChannels) {
                    outFrameScratch[ch] = inFrameScratch[ch]
                }
                for (ch in copyChannels until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = copyChannels
            }

            val frameGain = if (useLimiter) {
                runKodiLimiter(
                    frame = outFrameScratch,
                    channels = activeMixChannels,
                    currentAmplify = currentAmplify,
                )
            } else {
                currentAmplify
            }

            for (ch in 0 until outChannels) {
                val sample = (outFrameScratch[ch] * frameGain).coerceIn(
                    minimumValue = -1.0f,
                    maximumValue = 1.0f,
                )
                val pcm16 = (sample * 32767.0f).roundToInt()
                    .coerceIn(
                        minimumValue = Short.MIN_VALUE.toInt(),
                        maximumValue = Short.MAX_VALUE.toInt(),
                    )
                    .toShort()
                outputBuffer.putShort(pcm16)
            }
        }

        outputBuffer.flip()
        return outputBuffer
    }

    fun processFloatInterleaved(
        inputSamples: FloatArray,
        numFrames: Int = inputSamples.size / inputChannelCount,
    ): FloatArray {
        val inChannels = inputChannelCount
        val outChannels = configuredOutputChannelCount
        val output = FloatArray(numFrames * outChannels)
        val activeRoute = routeMode
        val isSmart = smartAudioEnabled
        val currentCenterGain = centerMixGain
        val currentAmplify = amplify
        val useLimiter = isSmart || currentAmplify > 1.0001f || centerMixLevelDb > 0
        val shouldDownmixToStereo =
            inChannels > 2 && activeRoute != AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL

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
                    activeCenterGain = currentCenterGain,
                )
                for (ch in 2 until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = min(
                    a = 2,
                    b = outChannels,
                )
            } else {
                val copyChannels = min(
                    a = inChannels,
                    b = outChannels,
                )
                for (ch in 0 until copyChannels) {
                    outFrameScratch[ch] = inFrameScratch[ch]
                }
                for (ch in copyChannels until outChannels) {
                    outFrameScratch[ch] = 0.0f
                }
                activeMixChannels = copyChannels
            }

            val frameGain = if (useLimiter) {
                runKodiLimiter(
                    frame = outFrameScratch,
                    channels = activeMixChannels,
                    currentAmplify = currentAmplify,
                )
            } else {
                currentAmplify
            }

            val outOffset = frameIndex * outChannels
            for (ch in 0 until outChannels) {
                output[outOffset + ch] = (outFrameScratch[ch] * frameGain).coerceIn(
                    minimumValue = -1.0f,
                    maximumValue = 1.0f,
                )
            }
        }
        return output
    }

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
            val normalizedPeak = min(
                a = sample / LIMITER_CEILING,
                b = 10000.0f,
            )
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

    private fun downmixFrameToStereo(
        inFrame: FloatArray,
        inChannels: Int,
        outFrame: FloatArray,
        smartMode: Boolean,
        route: AudioOutputRouteMode,
        activeCenterGain: Float,
    ) {
        val fl = inFrame[0]
        val fr = inFrame[1]

        val centerGain = activeCenterGain
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
                val fc = inFrame[2] * centerGain
                val lfe = inFrame[3] * lfeGain
                left += fc + lfe + (inFrame[4] + inFrame[6]) * surroundGain
                right += fc + lfe + (inFrame[5] + inFrame[7]) * surroundGain
                maxChannelWeightSum += centerGain + lfeGain + 2.0f * surroundGain
            }
        }

        if (!smartMode && maxChannelWeightSum > 1.0f) {
            val normFactor = 1.0f / maxChannelWeightSum
            left *= normFactor
            right *= normFactor
        }

        outFrame[0] = left
        outFrame[1] = right
    }

    private fun getBytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private fun readSampleAsFloat(
        buffer: ByteBuffer,
        encoding: Int,
    ): Float = when (encoding) {
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
            val b2 = buffer.get().toInt()
            val sample24 = (b2 shl 16) or (b1 shl 8) or b0
            sample24 / 8388608.0f
        }

        C.ENCODING_PCM_32BIT -> {
            buffer.int / 2147483648.0f
        }

        else -> 0.0f
    }

    companion object {
        // Matches Kodi's VOLUME_DRC_MAXIMUM = 6000 (+60.0 dB = 1000.0f linear amplification)
        const val MAX_GAIN_MILLIBELS = 6000
        const val MAX_AMPLIFICATION = 1000.0f

        // Matches Kodi's Downmix: Center mix level (-10 dB to +30 dB, default 0 dB)
        const val DEFAULT_CENTER_MIX_LEVEL_DB = 0
        const val MIN_CENTER_MIX_LEVEL_DB = -10
        const val MAX_CENTER_MIX_LEVEL_DB = 30

        private const val MAX_CHANNELS = 16

        // Kodi CAELimiter constants (from AdvancedSettings.cpp)
        const val LIMITER_HOLD_SECONDS = 0.025f
        const val LIMITER_RELEASE_SECONDS = 0.100f
        const val LIMITER_CEILING = 0.98f

        // Downmix coefficients
        const val ITU_CENTER_GAIN = 0.70710677f
        const val ITU_SURROUND_GAIN = 0.70710677f
        const val SMART_SPEAKER_SURROUND_GAIN = 0.60f
        const val SMART_HEADPHONE_SURROUND_GAIN = 0.70710677f
        const val SMART_HEADPHONE_LFE_GAIN = 0.35f
        const val BACK_CENTER_GAIN = 0.50f

        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
