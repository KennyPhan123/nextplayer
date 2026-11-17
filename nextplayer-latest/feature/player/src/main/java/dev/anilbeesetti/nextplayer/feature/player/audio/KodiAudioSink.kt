package dev.anilbeesetti.nextplayer.feature.player.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

@UnstableApi
class KodiAudioSink(
    delegateSink: AudioSink,
    private val processor: KodiAudioProcessor,
) : ForwardingAudioSink(delegateSink) {

    private var isPcmProcessingActive: Boolean = false
    private var currentUpstreamBuffer: ByteBuffer? = null
    private var processedDownstreamBuffer: ByteBuffer? = null

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int {
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType && isSupportedLinearPcm(format.pcmEncoding)) {
            val outChannels = processor.resolveConfiguredOutputChannels(format.channelCount)
            val downstreamFormat = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .setChannelCount(outChannels)
                .build()
            if (super.getFormatSupport(downstreamFormat) != AudioSink.SINK_FORMAT_UNSUPPORTED) {
                return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            }
        }
        return super.getFormatSupport(format)
    }

    @Suppress("DEPRECATION")
    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        clearPendingBuffers()
        val inputFormat = audioSinkConfig.format
        if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType &&
            isSupportedLinearPcm(inputFormat.pcmEncoding)
        ) {
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
            val downstreamChannelMapping =
                if (outChannels != inputFormat.channelCount) {
                    null
                } else {
                    audioSinkConfig.outputChannelMapping
                }
            val downstreamBufferSize =
                if (outChannels != inputFormat.channelCount ||
                    inputFormat.pcmEncoding != C.ENCODING_PCM_16BIT
                ) {
                    0
                } else {
                    audioSinkConfig.preferredBufferSizeOverride
                }
            val downstreamConfig = AudioSink.AudioSinkConfig.Builder(downstreamFormat)
                .setPreferredBufferSizeOverride(downstreamBufferSize)
                .setOutputChannelMapping(downstreamChannelMapping)
                .setTimeline(audioSinkConfig.timeline)
                .setMediaPeriodId(audioSinkConfig.mediaPeriodId)
                .build()
            super.configure(downstreamConfig)
        } else {
            isPcmProcessingActive = false
            super.configure(audioSinkConfig)
        }
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!isPcmProcessingActive || !buffer.hasRemaining()) {
            return super.handleBuffer(
                buffer,
                presentationTimeUs,
                encodedAccessUnitCount,
            )
        }

        if (currentUpstreamBuffer !== buffer) {
            currentUpstreamBuffer = buffer
            processedDownstreamBuffer = processor.process(buffer)
        }

        val downstream = processedDownstreamBuffer ?: return true
        val handled = super.handleBuffer(
            downstream,
            presentationTimeUs,
            encodedAccessUnitCount,
        )
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

    private fun isSupportedLinearPcm(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_FLOAT ||
            encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT
}
