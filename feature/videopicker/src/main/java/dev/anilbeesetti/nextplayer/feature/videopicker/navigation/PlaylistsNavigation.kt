package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlists.PlaylistDetailRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlists.PlaylistDetailScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlists.PlaylistsRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlists.PlaylistsScreen

fun EntryProviderScope<NavKey>.playlistsEntry(
    onOpenPlaylist: (Long, String) -> Unit,
) {
    entry<PlaylistsRoute> {
        PlaylistsScreen(
            onOpenPlaylist = onOpenPlaylist,
        )
    }
}

fun EntryProviderScope<NavKey>.playlistDetailEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
) {
    entry<PlaylistDetailRoute> { key ->
        PlaylistDetailScreen(
            playlistId = key.playlistId,
            playlistName = key.playlistName,
            onNavigateUp = onNavigateUp,
            onPlayVideo = onPlayVideo,
        )
    }
}

fun NavBackStack<NavKey>.navigateToPlaylistDetail(playlistId: Long, playlistName: String) {
    add(PlaylistDetailRoute(playlistId = playlistId, playlistName = playlistName))
}
