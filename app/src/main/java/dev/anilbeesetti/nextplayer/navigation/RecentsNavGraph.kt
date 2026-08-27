package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.recentlyPlayedEntry
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings

fun EntryProviderScope<NavKey>.recentsNavGraph(
    context: Context,
    backStack: NavBackStack<NavKey>,
) {
    recentlyPlayedEntry(
        onSettingsClick = backStack::navigateToSettings,
        onPlayVideo = { uri -> context.startPlayback(uri) },
    )
}
