package dev.anilbeesetti.nextplayer.feature.player.audio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KodiAudioProcessorTest {

    @Test
    fun stereoPassThrough_atZeroGain_preservesSignalWithinCeiling() {
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = true
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = 0
            configure(sampleRate = 48000, inputChannelCount = 2, inputEncoding = C.ENCODING_PCM_FLOAT)
        }

        val input = floatArrayOf(0.25f, -0.25f, 0.5f, -0.5f, 0.8f, -0.8f)
        val output = processor.processFloatInterleaved(input)

        for (i in input.indices) {
            assertEquals(input[i], output[i], 1e-5f)
        }
    }

    @Test
    fun kodiLimiter_withMax20dBBoostAndOver0dBFloatInput_neverClips() {
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = true
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = KodiAudioProcessor.MAX_GAIN_MILLIBELS // +20 dB (10x linear)
            configure(sampleRate = 48000, inputChannelCount = 2, inputEncoding = C.ENCODING_PCM_FLOAT)
        }

        // Simulate unclipped > 0 dBFS float peaks (1.8f) from FFmpeg TrueHD/E-AC3 decoder
        val input = FloatArray(2000) { idx -> if (idx % 2 == 0) 1.8f else -1.8f }
        val output = processor.processFloatInterleaved(input)

        for (sample in output) {
            assertTrue(
                "Sample $sample exceeded limiter ceiling ${KodiAudioProcessor.LIMITER_CEILING}",
                abs(sample) <= KodiAudioProcessor.LIMITER_CEILING + 1e-4f,
            )
        }
        assertEquals(KodiAudioProcessor.LIMITER_CEILING, abs(output[0]), 1e-4f)
    }

    @Test
    fun kodiLimiter_holdAndExponentialRelease_recoversSmoothlyAfterTransient() {
        val sampleRate = 1000 // 1 frame = 1 ms for easy hold/release verification
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = true
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = 0
            configure(sampleRate = sampleRate, inputChannelCount = 1, inputEncoding = C.ENCODING_PCM_FLOAT)
        }

        // Frame 0: transient explosion at 2.0f (+6 dBFS)
        // Frames 1..25 (25 ms hold): quiet dialogue at 0.2f -> should stay attenuated by ~0.49x
        // Frames 26..150 (100 ms release): quiet dialogue at 0.2f -> should recover back to 0.2f
        val input = FloatArray(160) { idx -> if (idx == 0) 2.0f else 0.2f }
        val output = processor.processFloatInterleaved(input)

        val expectedAttenuated = 0.2f * (KodiAudioProcessor.LIMITER_CEILING / 2.0f)
        // During the 25-frame hold window, gain stays constant
        assertEquals(expectedAttenuated, output[1], 1e-4f)
        assertEquals(expectedAttenuated, output[25], 1e-4f)

        // Mid-release (frame 75 = 50 ms into 100 ms release): gain has increased significantly
        assertTrue(output[75] > expectedAttenuated * 1.3f)
        assertTrue(output[75] < 0.2f)

        // After 100 ms release completes (frame 140): gain is fully restored to 1.0x (output == 0.2f)
        assertEquals(0.2f, output[140], 1e-4f)
    }

    @Test
    fun downmix51_speakerVsHeadphones_adaptsLfeAndBoostsCenterDialogue() {
        val processor = KodiAudioProcessor()

        // 5.1 frame: FL=0, FR=0, FC=0.5 (dialogue), LFE=0.8 (sub-bass), BL=0, BR=0
        val frame51 = floatArrayOf(0.0f, 0.0f, 0.5f, 0.8f, 0.0f, 0.0f)

        // 1. Built-in phone speaker: LFE must be 0.0f, Center = 0.5 * 0.85 = 0.425f
        processor.routeMode = AudioOutputRouteMode.SPEAKER
        processor.smartAudioEnabled = true
        processor.gainMillibels = 0
        val speakerOutChannels = processor.configure(48000, 6, C.ENCODING_PCM_FLOAT)
        assertEquals(2, speakerOutChannels)
        val speakerOut = processor.processFloatInterleaved(frame51)
        assertEquals(0.5f * KodiAudioProcessor.SMART_CENTER_GAIN, speakerOut[0], 1e-4f)
        assertEquals(0.5f * KodiAudioProcessor.SMART_CENTER_GAIN, speakerOut[1], 1e-4f)

        // 2. Stereo headphones: LFE retained at 0.35f -> 0.5*0.85 + 0.8*0.35 = 0.425 + 0.28 = 0.705f
        processor.routeMode = AudioOutputRouteMode.STEREO_HEADPHONES
        processor.configure(48000, 6, C.ENCODING_PCM_FLOAT)
        val headphoneOut = processor.processFloatInterleaved(frame51)
        val expectedHeadphone =
            0.5f * KodiAudioProcessor.SMART_CENTER_GAIN + 0.8f * KodiAudioProcessor.SMART_HEADPHONE_LFE_GAIN
        assertEquals(expectedHeadphone, headphoneOut[0], 1e-4f)
        assertEquals(expectedHeadphone, headphoneOut[1], 1e-4f)

        // 3. Spatial Audio / HDMI multichannel: preserves all 6 channels
        processor.routeMode = AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL
        val spatialOutChannels = processor.configure(48000, 6, C.ENCODING_PCM_FLOAT)
        assertEquals(6, spatialOutChannels)
        val spatialOut = processor.processFloatInterleaved(frame51)
        assertEquals(6, spatialOut.size)
        assertEquals(0.5f, spatialOut[2], 1e-4f)
        assertEquals(0.8f, spatialOut[3], 1e-4f)
    }

    @Test
    fun downmix71_smartModeOff_normalizesWeightsAndStillLimitsWhenBoosted() {
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = false
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = 0
            configure(sampleRate = 48000, inputChannelCount = 8, inputEncoding = C.ENCODING_PCM_FLOAT)
        }

        // All 8 channels at full scale 1.0f
        val fullScale71 = FloatArray(8) { 1.0f }
        val normalizedOut = processor.processFloatInterleaved(fullScale71)
        assertEquals(2, normalizedOut.size)
        // With normalized ITU weights and LFE=0, (1 + 0.7071 + 2*0.7071) / (1 + 0.7071 + 2*0.7071) == 1.0f
        assertEquals(1.0f, normalizedOut[0], 1e-4f)
        assertEquals(1.0f, normalizedOut[1], 1e-4f)

        // Now enable +12 dB boost (1200 mb) with smartMode OFF -> limiter still prevents clipping
        processor.gainMillibels = 1200
        val boostedOut = processor.processFloatInterleaved(fullScale71)
        assertTrue(abs(boostedOut[0]) <= KodiAudioProcessor.LIMITER_CEILING + 1e-4f)
    }

    @Test
    fun processByteBuffer_floatInputToPcm16Output_preservesCallerBufferPosition() {
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = true
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = 0
            configure(sampleRate = 48000, inputChannelCount = 6, inputEncoding = C.ENCODING_PCM_FLOAT)
        }

        val inputBuffer = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder())
        // FL=0.5, FR=-0.5, FC=0, LFE=0, BL=0, BR=0
        inputBuffer.putFloat(0.5f)
        inputBuffer.putFloat(-0.5f)
        inputBuffer.putFloat(0.0f)
        inputBuffer.putFloat(0.0f)
        inputBuffer.putFloat(0.0f)
        inputBuffer.putFloat(0.0f)
        inputBuffer.flip()

        val outputBuffer = processor.process(inputBuffer)
        // Input position must remain 0 until KodiAudioSink confirms downstream consumption
        assertEquals(0, inputBuffer.position())
        assertEquals(4, outputBuffer.remaining()) // 1 stereo frame = 4 bytes (2 x Int16)

        val leftSample = outputBuffer.short / 32767.0f
        val rightSample = outputBuffer.short / 32767.0f
        assertEquals(0.5f, leftSample, 1e-3f)
        assertEquals(-0.5f, rightSample, 1e-3f)
    }
}
