package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToPlaylistDetail
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.playlistDetailEntry
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.playlistsEntry

fun EntryProviderScope<NavKey>.playlistsNavGraph(
    context: Context,
    backStack: NavBackStack<NavKey>,
) {
    playlistsEntry(
        onOpenPlaylist = { id, name -> backStack.navigateToPlaylistDetail(id, name) },
    )

    playlistDetailEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onPlayVideo = { uri -> context.startPlayback(uri) },
    )
}
