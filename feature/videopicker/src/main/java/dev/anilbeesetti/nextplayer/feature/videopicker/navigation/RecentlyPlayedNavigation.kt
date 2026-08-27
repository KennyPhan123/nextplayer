package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.recentlyplayed.RecentlyPlayedRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.recentlyplayed.RecentlyPlayedScreen

fun EntryProviderScope<NavKey>.recentlyPlayedEntry(
    onSettingsClick: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
) {
    entry<RecentlyPlayedRoute> {
        RecentlyPlayedScreen(
            onSettingsClick = onSettingsClick,
            onPlayVideo = onPlayVideo,
        )
    }
}
