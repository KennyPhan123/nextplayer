package dev.anilbeesetti.nextplayer.feature.videopicker.screens.recentlyplayed

import android.net.Uri
import android.text.format.DateUtils
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.NetworkUrlDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
object RecentlyPlayedRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentlyPlayedScreen(
    onPlayVideo: (Uri) -> Unit,
    viewModel: RecentlyPlayedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let { onPlayVideo(it) } },
    )
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showUrlDialog) {
        dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.NetworkUrlDialog(
            onDismiss = { showUrlDialog = false },
            onDone = { onPlayVideo(it.toUri()) },
        )
    }

    if (showClearConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(coreUiR.string.clear_history)) },
            text = { Text("Are you sure you want to clear your playback history?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showClearConfirmation = false
                        viewModel.clearAllHistory()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showClearConfirmation = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(coreUiR.string.recents)) },
                actions = {
                    if (uiState is RecentlyPlayedUiState.Success) {
                        androidx.compose.material3.IconButton(
                            onClick = { showClearConfirmation = true },
                        ) {
                            Icon(
                                imageVector = NextIcons.DeleteSweep,
                                contentDescription = stringResource(coreUiR.string.clear_history),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = isFabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = isFabExpanded,
                        onCheckedChange = { isFabExpanded = !isFabExpanded },
                        modifier = Modifier
                    ) {
                        val icon by remember {
                            androidx.compose.runtime.derivedStateOf {
                                if (checkedProgress > 0.5f) NextIcons.Close else NextIcons.Play
                            }
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.animateIcon(checkedProgress = { checkedProgress }),
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        showUrlDialog = true
                    },
                    icon = {
                        Icon(
                            imageVector = NextIcons.Network,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(text = stringResource(id = dev.anilbeesetti.nextplayer.core.ui.R.string.open_network_stream))
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        selectVideoFileLauncher.launch("video/*")
                    },
                    icon = {
                        Icon(
                            imageVector = NextIcons.FileOpen,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(text = stringResource(id = dev.anilbeesetti.nextplayer.core.ui.R.string.open_local_video))
                    },
                )
            }
        },
    ) { paddingValues ->
        when (val state = uiState) {
            RecentlyPlayedUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }

            RecentlyPlayedUiState.Empty -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = NextIcons.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(coreUiR.string.no_recently_played),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(coreUiR.string.no_recently_played_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            is RecentlyPlayedUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                ) {
                    items(
                        items = state.videos,
                        key = { it.video.uriString },
                    ) { recentVideo ->
                        var showMenu by remember { mutableStateOf(false) }

                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onPlayVideo(recentVideo.video.uriString.toUri()) },
                                        onLongClick = { showMenu = true },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .width(128.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp)),
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(recentVideo.video.uriString)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    // Duration badge
                                    if (recentVideo.video.duration > 0) {
                                        Text(
                                            text = formatDuration(recentVideo.video.duration),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .then(
                                                    Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                ),
                                        )
                                    }
                                    // Progress bar
                                    if (recentVideo.playedPercentage > 0f) {
                                        LinearProgressIndicator(
                                            progress = recentVideo.playedPercentage / 100f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .align(Alignment.BottomCenter),
                                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = recentVideo.video.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = DateUtils.getRelativeTimeSpanString(
                                            recentVideo.lastPlayedTime,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS,
                                        ).toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(coreUiR.string.remove_from_history)) },
                                    onClick = {
                                        viewModel.removeFromHistory(recentVideo.video.uriString)
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(NextIcons.DeleteSweep, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
