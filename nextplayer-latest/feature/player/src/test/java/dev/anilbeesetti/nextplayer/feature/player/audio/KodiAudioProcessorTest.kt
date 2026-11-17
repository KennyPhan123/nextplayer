package dev.anilbeesetti.nextplayer.feature.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
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
            configure(
                sampleRate = 48000,
                inputChannelCount = 2,
                inputEncoding = C.ENCODING_PCM_FLOAT,
            )
        }

        val input = floatArrayOf(
            0.25f,
            -0.25f,
            0.5f,
            -0.5f,
            0.8f,
            -0.8f,
        )
        val output = processor.processFloatInterleaved(input)

        for (i in input.indices) {
            assertEquals(
                input[i],
                output[i],
                1e-5f,
            )
        }
    }

    @Test
    fun kodiLimiter_withMax60dBBoostAndOver0dBFloatInput_neverClips() {
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = true
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = KodiAudioProcessor.MAX_GAIN_MILLIBELS
            configure(
                sampleRate = 48000,
                inputChannelCount = 2,
                inputEncoding = C.ENCODING_PCM_FLOAT,
            )
        }

        assertEquals(
            1000.0f,
            processor.amplify,
            1e-2f,
        )

        val input = FloatArray(2000) { idx -> if (idx % 2 == 0) 1.8f else -1.8f }
        val output = processor.processFloatInterleaved(input)

        for (sample in output) {
            assertTrue(
                "Sample $sample exceeded limiter ceiling ${KodiAudioProcessor.LIMITER_CEILING}",
                abs(sample) <= KodiAudioProcessor.LIMITER_CEILING + 1e-4f,
            )
        }
        assertEquals(
            KodiAudioProcessor.LIMITER_CEILING,
            abs(output[0]),
            1e-4f,
        )
    }

    @Test
    fun downmix51_centerMixLevelDb_matchesKodiFormula() {
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = true
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = 0
        }

        val frame51 = floatArrayOf(
            0.0f,
            0.0f,
            0.2f,
            0.8f,
            0.0f,
            0.0f,
        )

        processor.centerMixLevelDb = 0
        processor.configure(
            sampleRate = 48000,
            inputChannelCount = 6,
            inputEncoding = C.ENCODING_PCM_FLOAT,
        )
        val out0dB = processor.processFloatInterleaved(frame51)
        assertEquals(
            0.2f * KodiAudioProcessor.ITU_CENTER_GAIN,
            out0dB[0],
            1e-4f,
        )

        processor.centerMixLevelDb = 6
        processor.configure(
            sampleRate = 48000,
            inputChannelCount = 6,
            inputEncoding = C.ENCODING_PCM_FLOAT,
        )
        val outPlus6dB = processor.processFloatInterleaved(frame51)
        val expectedGain6dB = KodiAudioProcessor.ITU_CENTER_GAIN * 10.0f.pow(6.0f / 20.0f)
        assertEquals(
            0.2f * expectedGain6dB,
            outPlus6dB[0],
            1e-4f,
        )

        processor.centerMixLevelDb = -10
        processor.configure(
            sampleRate = 48000,
            inputChannelCount = 6,
            inputEncoding = C.ENCODING_PCM_FLOAT,
        )
        val outMinus10dB = processor.processFloatInterleaved(frame51)
        val expectedGainMinus10dB = KodiAudioProcessor.ITU_CENTER_GAIN * 10.0f.pow(-10.0f / 20.0f)
        assertEquals(
            0.2f * expectedGainMinus10dB,
            outMinus10dB[0],
            1e-4f,
        )
    }

    @Test
    fun queueInput_51Pcm16WithBigEndianBufferOrder_downmixesCleanlyToStereo() {
        val processor = KodiAudioProcessor().apply {
            smartAudioEnabled = true
            routeMode = AudioOutputRouteMode.SPEAKER
            gainMillibels = 0
        }

        val outFormat = processor.configure(
            AudioProcessor.AudioFormat(
                48000,
                6,
                C.ENCODING_PCM_16BIT,
            ),
        )
        @Suppress("DEPRECATION")
        processor.flush()

        assertEquals(
            2,
            outFormat.channelCount,
        )
        assertEquals(
            C.ENCODING_PCM_16BIT,
            outFormat.encoding,
        )

        // Write Little-Endian 16-bit PCM bytes, then wrap in a BIG_ENDIAN ByteBuffer
        // (matching JNI NewDirectByteBuffer returned by MediaCodec.getOutputBuffer).
        val rawBuffer = ByteBuffer.allocateDirect(6 * 2).order(ByteOrder.LITTLE_ENDIAN)
        rawBuffer.putShort(16384)
        rawBuffer.putShort(-16384)
        rawBuffer.putShort(0)
        rawBuffer.putShort(0)
        rawBuffer.putShort(0)
        rawBuffer.putShort(0)
        rawBuffer.flip()
        val bigEndianView = rawBuffer.order(ByteOrder.BIG_ENDIAN)

        processor.queueInput(bigEndianView)
        assertEquals(
            0,
            bigEndianView.remaining(),
        )

        val outputBuffer = processor.output
        assertEquals(
            4,
            outputBuffer.remaining(),
        )

        val leftSample = outputBuffer.short / 32767.0f
        val rightSample = outputBuffer.short / 32767.0f
        assertEquals(
            0.5f,
            leftSample,
            1e-3f,
        )
        assertEquals(
            -0.5f,
            rightSample,
            1e-3f,
        )
    }
}
