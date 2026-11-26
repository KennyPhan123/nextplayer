package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextSwitch
import dev.anilbeesetti.nextplayer.feature.player.extensions.getName
import dev.anilbeesetti.nextplayer.feature.player.state.rememberTracksState
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.AudioTrackSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    player: Player,
    enableVolumeBoost: Boolean = false,
    centerMixLevelDb: Int = PlayerPreferences.DEFAULT_CENTER_MIX_LEVEL_DB,
    onEnableVolumeBoostChanged: (Boolean) -> Unit = {},
    onCenterMixLevelChanged: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val audioTracksState = rememberTracksState(player, C.TRACK_TYPE_AUDIO)
    val hapticFeedback = LocalHapticFeedback.current

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.select_audio_track),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .padding(horizontal = 24.dp),
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                audioTracksState.tracks.forEachIndexed { index, track ->
                    RadioButtonRow(
                        selected = track.isSelected,
                        text = track.mediaTrackGroup.getName(C.TRACK_TYPE_AUDIO, index),
                        onClick = {
                            audioTracksState.switchTrack(index)
                            onDismiss()
                        },
                    )
                }
                RadioButtonRow(
                    selected = audioTracksState.tracks.none { it.isSelected },
                    text = stringResource(R.string.disable),
                    onClick = {
                        audioTracksState.switchTrack(-1)
                        onDismiss()
                    },
                )
            }

            Spacer(modifier = Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.size(8.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .toggleable(
                        value = enableVolumeBoost,
                        onValueChange = onEnableVolumeBoostChanged,
                    )
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.volume_boost),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.volume_boost_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NextSwitch(
                    checked = enableVolumeBoost,
                    onCheckedChange = null,
                )
            }

            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.downmix_center_mix_level),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.center_mix_level_db, centerMixLevelDb),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = centerMixLevelDb.toFloat(),
                    valueRange = PlayerPreferences.MIN_CENTER_MIX_LEVEL_DB.toFloat()..PlayerPreferences.MAX_CENTER_MIX_LEVEL_DB.toFloat(),
                    onValueChange = { value ->
                        val rounded = value.roundToInt()
                        if (rounded != centerMixLevelDb) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCenterMixLevelChanged(rounded)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onCenterMixLevelChanged(PlayerPreferences.DEFAULT_CENTER_MIX_LEVEL_DB) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_reset),
                        contentDescription = stringResource(R.string.reset_center_mix_level),
                    )
                }
            }
        }
    }
}
