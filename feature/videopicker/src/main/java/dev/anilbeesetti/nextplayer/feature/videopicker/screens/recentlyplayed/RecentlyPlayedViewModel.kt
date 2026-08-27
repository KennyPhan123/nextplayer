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
                val video = videoMap[state.uriString] ?: return@mapNotNull null
                val percentage = if (video.duration > 0) {
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
}
