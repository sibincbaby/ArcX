package com.arcx.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Whether we know enough to draw anything yet — avoids a theme flash on cold start. */
sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val settings: UserSettings) : AppUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = settingsRepository.settings
        .map<UserSettings, AppUiState> { AppUiState.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppUiState.Loading,
        )
}
