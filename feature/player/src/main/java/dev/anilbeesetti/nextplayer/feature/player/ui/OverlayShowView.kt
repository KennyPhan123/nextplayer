package dev.anilbeesetti.nextplayer.feature.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitleOptionsEvent

@Composable
fun BoxScope.OverlayShowView(
    player: Player,
    overlayView: OverlayView?,
    videoContentScale: VideoContentScale,
    enablePanGesture: Boolean = false,
    independentAxisZoom: Boolean = true,
    enableVolumeBoost: Boolean = false,
    centerMixLevelDb: Int = PlayerPreferences.DEFAULT_CENTER_MIX_LEVEL_DB,
    onDismiss: () -> Unit = {},
    onSelectSubtitleClick: () -> Unit = {},
    onSubtitleOptionEvent: (SubtitleOptionsEvent) -> Unit = {},
    onVideoContentScaleChanged: (VideoContentScale) -> Unit = {},
    onEnablePanGestureChanged: (Boolean) -> Unit = {},
    onIndependentAxisZoomChanged: (Boolean) -> Unit = {},
    onEnableVolumeBoostChanged: (Boolean) -> Unit = {},
    onCenterMixLevelChanged: (Int) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .then(
                if (overlayView != null) {
                    Modifier.noRippleClickable(onClick = onDismiss)
                } else {
                    Modifier
                },
            ),
    )

    AudioTrackSelectorView(
        show = overlayView == OverlayView.AUDIO_SELECTOR,
        player = player,
        enableVolumeBoost = enableVolumeBoost,
        centerMixLevelDb = centerMixLevelDb,
        onEnableVolumeBoostChanged = onEnableVolumeBoostChanged,
        onCenterMixLevelChanged = onCenterMixLevelChanged,
        onDismiss = onDismiss,
    )

    SubtitleSelectorView(
        show = overlayView == OverlayView.SUBTITLE_SELECTOR,
        player = player,
        onSelectSubtitleClick = onSelectSubtitleClick,
        onEvent = onSubtitleOptionEvent,
        onDismiss = onDismiss,
    )

    PlaybackSpeedSelectorView(
        show = overlayView == OverlayView.PLAYBACK_SPEED,
        player = player,
    )

    VideoContentScaleSelectorView(
        show = overlayView == OverlayView.VIDEO_CONTENT_SCALE,
        videoContentScale = videoContentScale,
        enablePanGesture = enablePanGesture,
        independentAxisZoom = independentAxisZoom,
        onVideoContentScaleChanged = onVideoContentScaleChanged,
        onEnablePanGestureChanged = onEnablePanGestureChanged,
        onIndependentAxisZoomChanged = onIndependentAxisZoomChanged,
        onDismiss = onDismiss,
    )

    PlaylistView(
        show = overlayView == OverlayView.PLAYLIST,
        player = player,
    )
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

enum class OverlayView {
    AUDIO_SELECTOR,
    SUBTITLE_SELECTOR,
    PLAYBACK_SPEED,
    VIDEO_CONTENT_SCALE,
    PLAYLIST,
}
