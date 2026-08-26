package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistWithItemCount
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistItemEntity
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailItem(
    val playlistItem: PlaylistItemEntity,
    val video: Video?,
)

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState
    data object Empty : PlaylistsUiState
    data class Success(val playlists: List<PlaylistWithItemCount>) : PlaylistsUiState
}

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data object Empty : PlaylistDetailUiState
    data class Success(val items: List<PlaylistDetailItem>) : PlaylistDetailUiState
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
) : ViewModel() {

    val uiState: StateFlow<PlaylistsUiState> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        .let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { playlists ->
                    emit(
                        when {
                            playlists == null -> PlaylistsUiState.Loading
                            playlists.isEmpty() -> PlaylistsUiState.Empty
                            else -> PlaylistsUiState.Success(playlists)
                        }
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistsUiState.Loading)
        }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistDao.createPlaylist(PlaylistEntity(name = name))
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch {
            playlistDao.renamePlaylist(id, name)
        }
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private var _playlistId: Long = 0

    fun setPlaylistId(playlistId: Long): StateFlow<PlaylistDetailUiState> {
        _playlistId = playlistId
        return combine(
            playlistDao.getPlaylistItems(playlistId),
            mediaRepository.observeVideos(),
        ) { items, videos ->
            val videoMap = videos.associateBy { it.uriString }
            val detailItems = items.map { item ->
                PlaylistDetailItem(
                    playlistItem = item,
                    video = videoMap[item.videoUri],
                )
            }
            if (detailItems.isEmpty()) PlaylistDetailUiState.Empty
            else PlaylistDetailUiState.Success(detailItems)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState.Loading)
    }

    fun removeItem(id: Long) {
        viewModelScope.launch {
            playlistDao.removeItem(id)
        }
    }
}
