package dev.anilbeesetti.nextplayer.feature.player.service

import android.os.Bundle
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import dev.anilbeesetti.nextplayer.feature.player.model.selectableDecoderModes
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    sdk = [28],
)
class DecoderServiceStateTest {
    @Test
    fun extrasCarryActiveModesAndRecoveryWithoutPlayerEvents() {
        val extras = Bundle().apply {
            putString(
                CustomCommands.VIDEO_DECODER_MODE_KEY,
                DecoderMode.FFMPEG.name,
            )
            putString(
                CustomCommands.AUDIO_DECODER_MODE_KEY,
                DecoderMode.SOFTWARE.name,
            )
            putString(
                CustomCommands.DECODER_RECOVERY_STATUS_KEY,
                DecoderRecoveryStatus.AWAITING_CONFIRMATION.name,
            )
            putString(
                CustomCommands.DECODER_RECOVERY_TRACK_TYPE_KEY,
                DecoderTrackType.VIDEO.name,
            )
            putString(
                CustomCommands.UNSUPPORTED_DECODER_MODE_KEY,
                DecoderMode.HARDWARE.name,
            )
        }
        val state = extras.decoderServiceState()
        assertEquals(
            DecoderMode.FFMPEG,
            state.videoMode,
        )
        assertEquals(
            DecoderMode.SOFTWARE,
            state.audioMode,
        )
        assertEquals(
            DecoderRecoveryStatus.AWAITING_CONFIRMATION,
            state.recoveryState.status,
        )
        assertEquals(
            DecoderTrackType.VIDEO,
            state.recoveryState.trackType,
        )
        assertEquals(
            DecoderMode.HARDWARE,
            state.recoveryState.unsupportedMode,
        )

        extras.putString(
            CustomCommands.VIDEO_DECODER_MODE_KEY,
            null,
        )
        assertNull(extras.decoderServiceState().videoMode)
        extras.putString(
            CustomCommands.AUDIO_DECODER_MODE_KEY,
            "invalid",
        )
        assertNull(extras.decoderMode(CustomCommands.AUDIO_DECODER_MODE_KEY))
        assertEquals(
            DecoderMode.AUTO,
            state.requestedVideoMode,
        )
        assertEquals(
            DecoderMode.AUTO,
            state.requestedAudioMode,
        )
    }

    @Test
    fun selectableModesIncludeAutoAndExtrasCarryRequestedModes() {
        assertEquals(
            listOf(
                DecoderMode.AUTO,
                DecoderMode.HARDWARE,
                DecoderMode.SOFTWARE,
                DecoderMode.FFMPEG,
            ),
            selectableDecoderModes,
        )
        val extras = Bundle().apply {
            putString(
                CustomCommands.REQUESTED_VIDEO_DECODER_MODE_KEY,
                DecoderMode.HARDWARE.name,
            )
            putString(
                CustomCommands.REQUESTED_AUDIO_DECODER_MODE_KEY,
                DecoderMode.FFMPEG.name,
            )
        }
        val state = extras.decoderServiceState()
        assertEquals(
            DecoderMode.HARDWARE,
            state.requestedVideoMode,
        )
        assertEquals(
            DecoderMode.FFMPEG,
            state.requestedAudioMode,
        )
    }
}
