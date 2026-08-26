package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.recentlyplayed.RecentlyPlayedRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.recentlyplayed.RecentlyPlayedScreen

fun EntryProviderScope<NavKey>.recentlyPlayedEntry(
    onPlayVideo: (Uri) -> Unit,
) {
    entry<RecentlyPlayedRoute> {
        RecentlyPlayedScreen(
            onPlayVideo = onPlayVideo,
        )
    }
}
