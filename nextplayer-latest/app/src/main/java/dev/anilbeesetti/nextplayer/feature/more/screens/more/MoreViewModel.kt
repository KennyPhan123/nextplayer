package dev.anilbeesetti.nextplayer.feature.more.screens.more

import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.common.extensions.hasKnownMediaExtension
import dev.anilbeesetti.nextplayer.core.common.extensions.isProbeableHttpUrl
import dev.anilbeesetti.nextplayer.core.common.extensions.resolveHttpStreamFilename
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MoreViewModel(
    private val mediaRepository: MediaRepository,
    preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<MoreUiState, MoreAction>() {

    private val stateInternal = MutableStateFlow(value = MoreUiState())
    override val state: StateFlow<MoreUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            mediaRepository.observePlaybackHistory()
                .catch { error ->
                    stateInternal.update { it.copy(history = DataState.Error(value = error)) }
                }
                .collect { history ->
                    stateInternal.update { it.copy(history = DataState.Success(value = history)) }
                    resolveMissingHistoryTitles(videos = history)
                }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                stateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    override fun onAction(action: MoreAction) {
        when (action) {
            is MoreAction.OpenHistory -> output.openHistory()
            is MoreAction.PlayVideo -> output.playVideo(action.uri)
            is MoreAction.OpenSettings -> output.openSettings()
            is MoreAction.OpenTrash -> output.openTrash()
            is MoreAction.OpenVault -> output.openVault()
        }
    }

    private fun resolveMissingHistoryTitles(videos: List<Video>) {
        for (video in videos) {
            val uri = video.uriString
            if (!isProbeableHttpUrl(uriString = uri)) continue
            if (video.nameWithExtension.hasKnownMediaExtension()) continue
            if (!attemptedHistoryTitleUris.add(uri)) continue
            viewModelScope.launch {
                val resolvedTitle = resolveHttpStreamFilename(url = uri) ?: return@launch
                mediaRepository.updateMediumTitle(
                    uri = uri,
                    title = resolvedTitle,
                )
            }
        }
    }

    data class Output(
        val openHistory: () -> Unit,
        val playVideo: (String) -> Unit,
        val openSettings: () -> Unit,
        val openTrash: () -> Unit,
        val openVault: () -> Unit,
    )

    private companion object {
        val attemptedHistoryTitleUris: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}

data class MoreUiState(
    val history: DataState<List<Video>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface MoreAction {
    data object OpenHistory : MoreAction
    data class PlayVideo(val uri: String) : MoreAction
    data object OpenSettings : MoreAction
    data object OpenTrash : MoreAction
    data object OpenVault : MoreAction
}
