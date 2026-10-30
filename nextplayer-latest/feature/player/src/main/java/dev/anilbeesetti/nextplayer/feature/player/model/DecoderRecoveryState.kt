package dev.anilbeesetti.nextplayer.feature.player.model

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

/** Player-service recovery state exposed to the controller while handling a decoder failure. */
enum class DecoderRecoveryStatus {
    NONE,
    RECOVERING,
    AWAITING_CONFIRMATION,
    FAILED,
}

enum class DecoderTrackType {
    VIDEO,
    AUDIO,
}

data class DecoderRecoveryState(
    val status: DecoderRecoveryStatus = DecoderRecoveryStatus.NONE,
    val trackType: DecoderTrackType? = null,
    val unsupportedMode: DecoderMode? = null,
)

data class DecoderServiceState(
    val videoMode: DecoderMode? = null,
    val audioMode: DecoderMode? = null,
    val requestedVideoMode: DecoderMode = DecoderMode.AUTO,
    val requestedAudioMode: DecoderMode = DecoderMode.AUTO,
    val recoveryState: DecoderRecoveryState = DecoderRecoveryState(),
)
