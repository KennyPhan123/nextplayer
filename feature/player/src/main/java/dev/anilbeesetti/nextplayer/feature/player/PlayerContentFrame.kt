package dev.anilbeesetti.nextplayer.feature.player

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import dev.anilbeesetti.nextplayer.feature.player.extensions.toContentScale
import dev.anilbeesetti.nextplayer.feature.player.state.ControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.PictureInPictureState
import dev.anilbeesetti.nextplayer.feature.player.state.SeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.TapGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomAndContentScaleState
import dev.anilbeesetti.nextplayer.feature.player.state.VolumeAndBrightnessGestureState
import dev.anilbeesetti.nextplayer.feature.player.ui.PlayerGestures
import dev.anilbeesetti.nextplayer.feature.player.ui.ShutterView
import dev.anilbeesetti.nextplayer.feature.player.ui.SubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.ui.SubtitleView

@OptIn(UnstableApi::class)
@Composable
fun PlayerContentFrame(
    modifier: Modifier = Modifier,
    player: Player,
    pictureInPictureState: PictureInPictureState,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    subtitleConfiguration: SubtitleConfiguration,
) {
    val presentationState = rememberPresentationState(player)
    val sourceSizeDp = presentationState.videoSizeDp?.let { size ->
        size.copy(
            width = with(LocalDensity.current) { size.width.toDp().value },
            height = with(LocalDensity.current) { size.height.toDp().value },
        )
    }

    val videoModifier = modifier
        .resizeWithContentScale(
            contentScale = videoZoomAndContentScaleState.videoContentScale.toContentScale(),
            sourceSizeDp = sourceSizeDp,
        )
        .onGloballyPositioned {
            val bounds = it.boundsInWindow()
            val rect = Rect(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt(),
            )
            pictureInPictureState.setVideoViewRect(rect)
        }
        .graphicsLayer {
            scaleX = videoZoomAndContentScaleState.zoomX
            scaleY = videoZoomAndContentScaleState.zoomY
            translationX = videoZoomAndContentScaleState.offset.x
            translationY = videoZoomAndContentScaleState.offset.y
        }

    if (subtitleConfiguration.pinToVideo) {
        // Subtitle is pinned to the video: wrap both in the same transformed container
        // so subtitles move with zoom/pan and PGS renders at the correct aspect ratio
        Box(modifier = videoModifier) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
            SubtitleView(
                modifier = Modifier.fillMaxSize(),
                player = player,
                isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
                configuration = subtitleConfiguration,
            )
        }
    } else {
        // Subtitle stays fixed on screen, does not follow zoom/pan
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = videoModifier,
        )

        // Constrain SubtitleView to video aspect ratio for correct PGS/bitmap rendering
        val videoAspectRatio = sourceSizeDp?.let { size ->
            if (size.height > 0f) size.width / size.height else null
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            SubtitleView(
                modifier = if (videoAspectRatio != null && videoAspectRatio > 0f) {
                    Modifier.aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = true)
                } else {
                    Modifier.fillMaxSize()
                },
                player = player,
                isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
                configuration = subtitleConfiguration,
            )
        }
    }

    PlayerGestures(
        controlsVisibilityState = controlsVisibilityState,
        tapGestureState = tapGestureState,
        pictureInPictureState = pictureInPictureState,
        seekGestureState = seekGestureState,
        videoZoomAndContentScaleState = videoZoomAndContentScaleState,
        volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
    )

    if (presentationState.coverSurface) {
        ShutterView()
    }
}
