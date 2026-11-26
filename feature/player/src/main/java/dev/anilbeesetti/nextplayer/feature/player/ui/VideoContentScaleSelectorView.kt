package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextSwitch
import dev.anilbeesetti.nextplayer.feature.player.extensions.nameRes

@Composable
fun BoxScope.VideoContentScaleSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    videoContentScale: VideoContentScale,
    enablePanGesture: Boolean = false,
    independentAxisZoom: Boolean = true,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    onEnablePanGestureChanged: (Boolean) -> Unit = {},
    onIndependentAxisZoomChanged: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.video_zoom),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .padding(horizontal = 24.dp),
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                VideoContentScale.entries.forEach { contentScale ->
                    RadioButtonRow(
                        selected = contentScale == videoContentScale,
                        text = stringResource(contentScale.nameRes()),
                        onClick = {
                            onVideoContentScaleChanged(contentScale)
                            onDismiss()
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.size(8.dp))
            ZoomOptionSwitchRow(
                title = stringResource(R.string.pan_gesture),
                description = stringResource(R.string.pan_gesture_description),
                checked = enablePanGesture,
                onCheckedChange = onEnablePanGestureChanged,
            )
            ZoomOptionSwitchRow(
                title = stringResource(R.string.independent_xy_zoom),
                description = stringResource(R.string.independent_xy_zoom_description),
                checked = independentAxisZoom,
                onCheckedChange = onIndependentAxisZoomChanged,
            )
        }
    }
}

@Composable
private fun ZoomOptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
            )
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NextSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
