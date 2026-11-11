package dev.anilbeesetti.nextplayer.feature.player.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * An [AudioSink] wrapper that intercepts raw PCM buffers (including 32-bit float `C.ENCODING_PCM_FLOAT`
 * from `nextlib`'s `FfmpegAudioRenderer` and `MediaCodecAudioRenderer`), processes them through
 * [KodiAudioProcessor] (32-bit float headroom recovery, smart 5.1/7.1 downmixing, dialogue boost,
 * route-aware LFE filtering, and Kodi's `CAELimiter`), and forwards peak-limited 16-bit PCM to
 * Media3's [androidx.media3.exoplayer.audio.DefaultAudioSink] so playback speed (`SonicAudioProcessor`)
 * and silence skipping (`SilenceSkippingAudioProcessor`) remain 100% functional.
 */
@UnstableApi
class KodiAudioSink(
    delegateSink: AudioSink,
    private val processor: KodiAudioProcessor,
) : ForwardingAudioSink(delegateSink) {

    private var isPcmProcessingActive: Boolean = false
    private var currentUpstreamBuffer: ByteBuffer? = null
    private var processedDownstreamBuffer: ByteBuffer? = null

    override fun supportsFormat(format: Format): Boolean {
        return getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    }

    override fun getFormatSupport(format: Format): Int {
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType && isSupportedLinearPcm(format.pcmEncoding)) {
            val outChannels = processor.resolveConfiguredOutputChannels(format.channelCount)
            val downstreamFormat = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .setChannelCount(outChannels)
                .build()
            if (super.getFormatSupport(downstreamFormat) != AudioSink.SINK_FORMAT_UNSUPPORTED) {
                // Report direct support so FfmpegAudioRenderer.shouldOutputFloat() and
                // MediaCodecAudioRenderer output 32-bit float PCM (C.ENCODING_PCM_FLOAT)
                // directly to KodiAudioSink without 16-bit truncation.
                return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            }
        }
        return super.getFormatSupport(format)
    }

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        clearPendingBuffers()
        if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType && isSupportedLinearPcm(inputFormat.pcmEncoding)) {
            isPcmProcessingActive = true
            val outChannels = processor.configure(
                sampleRate = inputFormat.sampleRate,
                inputChannelCount = inputFormat.channelCount,
                inputEncoding = inputFormat.pcmEncoding,
            )
            val downstreamFormat = inputFormat.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .setChannelCount(outChannels)
                .build()
            val downstreamOutputChannels = if (outChannels != inputFormat.channelCount) null else outputChannels
            val downstreamBufferSize = if (outChannels != inputFormat.channelCount || inputFormat.pcmEncoding != C.ENCODING_PCM_16BIT) {
                0
            } else {
                specifiedBufferSize
            }
            super.configure(downstreamFormat, downstreamBufferSize, downstreamOutputChannels)
        } else {
            isPcmProcessingActive = false
            super.configure(inputFormat, specifiedBufferSize, outputChannels)
        }
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!isPcmProcessingActive || !buffer.hasRemaining()) {
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        if (currentUpstreamBuffer !== buffer) {
            currentUpstreamBuffer = buffer
            processedDownstreamBuffer = processor.process(buffer)
        }

        val downstream = processedDownstreamBuffer ?: return true
        val handled = super.handleBuffer(downstream, presentationTimeUs, encodedAccessUnitCount)
        if (handled) {
            buffer.position(buffer.limit())
            currentUpstreamBuffer = null
            processedDownstreamBuffer = null
        }
        return handled
    }

    override fun flush() {
        clearPendingBuffers()
        processor.flush()
        super.flush()
    }

    override fun reset() {
        clearPendingBuffers()
        processor.reset()
        super.reset()
    }

    private fun clearPendingBuffers() {
        currentUpstreamBuffer = null
        processedDownstreamBuffer = null
    }

    private fun isSupportedLinearPcm(encoding: Int): Boolean {
        return encoding == C.ENCODING_PCM_FLOAT ||
            encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT
    }
}
