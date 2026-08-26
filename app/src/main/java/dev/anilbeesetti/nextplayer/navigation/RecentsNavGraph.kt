package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.recentlyPlayedEntry

fun EntryProviderScope<NavKey>.recentsNavGraph(
    context: Context,
    backStack: NavBackStack<NavKey>,
) {
    recentlyPlayedEntry(
        onPlayVideo = { uri -> context.startPlayback(uri) },
    )
}
