package dev.anilbeesetti.nextplayer.settings.screens.audio

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AudioPreferencesViewModel(
    private val preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<AudioPreferencesUiState, AudioPreferencesUiEvent>() {

    data class Output(val navigateUp: () -> Unit)

    private val stateInternal = MutableStateFlow(
        value = AudioPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value,
        ),
    )
    override val state: StateFlow<AudioPreferencesUiState> = stateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                stateInternal.update { currentState ->
                    currentState.copy(preferences = preferences)
                }
            }
        }
    }

    override fun onAction(action: AudioPreferencesUiEvent) {
        when (action) {
            is AudioPreferencesUiEvent.NavigateUp -> output.navigateUp()

            is AudioPreferencesUiEvent.ShowDialog -> showDialog(action.value)

            is AudioPreferencesUiEvent.UpdateAudioLanguage -> updateAudioLanguage(action.value)

            is AudioPreferencesUiEvent.TogglePauseOnHeadsetDisconnect ->
                togglePauseOnHeadsetDisconnect()

            is AudioPreferencesUiEvent.ToggleShowSystemVolumePanel -> toggleShowSystemVolumePanel()

            is AudioPreferencesUiEvent.ToggleRequireAudioFocus -> toggleRequireAudioFocus()

            is AudioPreferencesUiEvent.ToggleVolumeBoost -> toggleVolumeBoost()

            is AudioPreferencesUiEvent.ToggleSmartAudioProcessing -> toggleSmartAudioProcessing()

            is AudioPreferencesUiEvent.UpdateCenterMixLevel -> updateCenterMixLevel(action.value)
        }
    }

    private fun showDialog(value: AudioPreferenceDialog?) {
        stateInternal.update { it.copy(showDialog = value) }
    }

    private fun updateAudioLanguage(value: String) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(preferredAudioLanguage = value)
            }
        }
    }

    private fun togglePauseOnHeadsetDisconnect() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(pauseOnHeadsetDisconnect = !it.pauseOnHeadsetDisconnect)
            }
        }
    }

    private fun toggleShowSystemVolumePanel() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(showSystemVolumePanel = !it.showSystemVolumePanel)
            }
        }
    }

    private fun toggleRequireAudioFocus() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(requireAudioFocus = !it.requireAudioFocus)
            }
        }
    }

    private fun toggleVolumeBoost() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(enableVolumeBoost = !it.enableVolumeBoost)
            }
        }
    }

    private fun toggleSmartAudioProcessing() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(enableSmartAudioProcessing = !it.enableSmartAudioProcessing)
            }
        }
    }

    private fun updateCenterMixLevel(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    centerMixLevelDb = value.coerceIn(
                        minimumValue = PlayerPreferences.MIN_CENTER_MIX_LEVEL_DB,
                        maximumValue = PlayerPreferences.MAX_CENTER_MIX_LEVEL_DB,
                    ),
                )
            }
        }
    }
}

@Stable
data class AudioPreferencesUiState(
    val showDialog: AudioPreferenceDialog? = null,
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface AudioPreferenceDialog {
    data object AudioLanguageDialog : AudioPreferenceDialog
}

sealed interface AudioPreferencesUiEvent {
    data object NavigateUp : AudioPreferencesUiEvent

    data class ShowDialog(val value: AudioPreferenceDialog?) : AudioPreferencesUiEvent
    data class UpdateAudioLanguage(val value: String) : AudioPreferencesUiEvent
    data object TogglePauseOnHeadsetDisconnect : AudioPreferencesUiEvent
    data object ToggleShowSystemVolumePanel : AudioPreferencesUiEvent
    data object ToggleRequireAudioFocus : AudioPreferencesUiEvent
    data object ToggleVolumeBoost : AudioPreferencesUiEvent
    data object ToggleSmartAudioProcessing : AudioPreferencesUiEvent
    data class UpdateCenterMixLevel(val value: Int) : AudioPreferencesUiEvent
}
