package dev.anilbeesetti.nextplayer.feature.videopicker.screens.recentlyplayed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentVideo(
    val video: Video,
    val lastPlayedTime: Long,
    val playbackPosition: Long,
    val playedPercentage: Float,
)

sealed interface RecentlyPlayedUiState {
    data object Loading : RecentlyPlayedUiState
    data object Empty : RecentlyPlayedUiState
    data class Success(val videos: List<RecentVideo>) : RecentlyPlayedUiState
}

@HiltViewModel
class RecentlyPlayedViewModel @Inject constructor(
    private val mediumStateDao: MediumStateDao,
    mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<ApplicationPreferences> = preferencesRepository
        .applicationPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicationPreferences())

    val uiState: StateFlow<RecentlyPlayedUiState> = combine(
        mediumStateDao.getAll(),
        mediaRepository.observeVideos(),
    ) { mediaStates, videos ->
        val videoMap = videos.associateBy { it.uriString }
        val recentVideos = mediaStates
            .filter { (it.lastPlayedTime ?: 0L) > 0L }
            .sortedByDescending { it.lastPlayedTime ?: 0L }
            .mapNotNull { state ->
                var video = videoMap[state.uriString]
                if (video == null) {
                    val isNetwork = state.uriString.let { uri ->
                        uri.startsWith("http", ignoreCase = true) ||
                        uri.startsWith("smb", ignoreCase = true) ||
                        uri.startsWith("rtmp", ignoreCase = true) ||
                        uri.startsWith("rtsp", ignoreCase = true) ||
                        uri.startsWith("tcp", ignoreCase = true) ||
                        uri.startsWith("udp", ignoreCase = true)
                    }
                    if (isNetwork) {
                        video = Video(
                            id = 0,
                            path = state.uriString,
                            parentPath = "",
                            duration = 0,
                            uriString = state.uriString,
                            nameWithExtension = state.uriString.substringAfterLast('/').substringBefore('?').ifEmpty { state.uriString },
                            width = 0,
                            height = 0,
                            size = 0,
                            formattedDuration = "",
                            formattedFileSize = ""
                        )
                    } else {
                        return@mapNotNull null
                    }
                }
                val percentage = if (video!!.duration > 0) {
                    (state.playbackPosition.toFloat() / video.duration * 100).coerceIn(0f, 100f)
                } else 0f
                RecentVideo(
                    video = video,
                    lastPlayedTime = state.lastPlayedTime ?: 0L,
                    playbackPosition = state.playbackPosition,
                    playedPercentage = percentage,
                )
            }
        if (recentVideos.isEmpty()) RecentlyPlayedUiState.Empty
        else RecentlyPlayedUiState.Success(recentVideos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentlyPlayedUiState.Loading)

    fun removeFromHistory(uri: String) {
        viewModelScope.launch {
            val existing = mediumStateDao.get(uri) ?: return@launch
            mediumStateDao.upsert(existing.copy(lastPlayedTime = null))
        }
    }

        fun removeMultipleFromHistory(uris: Set<String>) {
        viewModelScope.launch {
            val existing = uris.mapNotNull { mediumStateDao.get(it) }
            val updated = existing.map { it.copy(lastPlayedTime = null) }
            mediumStateDao.upsertAll(updated)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            mediumStateDao.clearHistory()
        }
    }
}
