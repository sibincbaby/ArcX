package com.arcx.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.ClearHistoryUseCase
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A configured provider as the list needs it. [hasKey] is a boolean on purpose: the settings
 * screen has to show that a key is saved without ever pulling the key itself out of the vault.
 */
data class ProviderRow(
    val config: ProviderConfig,
    val hasKey: Boolean,
    val isDefault: Boolean,
)

/**
 * The two permissions ArcX's out-of-app surfaces need. Both are revocable from system Settings
 * while ArcX is backgrounded, so this is re-read on every resume rather than observed.
 */
data class SurfacePermissions(
    val overlayGranted: Boolean = false,
    val screenReadingEnabled: Boolean = false,
)

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val providers: List<ProviderRow> = emptyList(),
    val permissions: SurfacePermissions = SurfacePermissions(),
    val loading: Boolean = true,
) {
    /** A stored flag with no overlay permission behind it is not a bubble the user can see. */
    val bubbleActive: Boolean get() = settings.bubbleEnabled && permissions.overlayGranted
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val workflows: WorkflowRepository,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val clearHistory: ClearHistoryUseCase,
    private val surfaces: SystemSurfaces,
) : ViewModel() {

    private val permissions = MutableStateFlow(SurfacePermissions())

    // Remembers a bubble the user asked for but could not have yet, so the trip out to the
    // overlay permission screen and back finishes the job they started.
    private var bubbleRequested = false

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        providers.observeAll().map { configs ->
            configs.map { it to providers.hasKey(it.id) }
        },
        permissions,
    ) { user, configs, granted ->
        SettingsUiState(
            settings = user,
            providers = configs.map { (config, hasKey) ->
                ProviderRow(config, hasKey, isDefault = config.id == user.defaultProviderId)
            },
            permissions = granted,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    /**
     * Called on every resume. Both permissions can be taken away in system Settings while ArcX
     * is in the background, and a cached "not granted" shown to someone who just granted it
     * reads as a broken app.
     */
    fun onResume() {
        permissions.value = SurfacePermissions(
            overlayGranted = surfaces.isOverlayGranted(),
            screenReadingEnabled = surfaces.isScreenReadingEnabled(),
        )
        if (bubbleRequested && permissions.value.overlayGranted) {
            bubbleRequested = false
            update { it.copy(bubbleEnabled = true) }
        }
    }

    /**
     * Without the overlay permission the flag would start a service that cannot draw anything,
     * so the request is parked instead of written and the screen explains what is missing.
     */
    fun onBubbleEnabledChange(enabled: Boolean) {
        if (enabled && !permissions.value.overlayGranted) {
            bubbleRequested = true
            return
        }
        bubbleRequested = false
        update { it.copy(bubbleEnabled = enabled) }
    }

    fun overlaySettingsIntent(): Intent = surfaces.overlaySettingsIntent()

    fun screenReadingSettingsIntent(): Intent = surfaces.screenReadingSettingsIntent()

    fun onThemeChange(theme: ThemePreference) = update { it.copy(theme = theme) }

    fun onDynamicColorChange(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun onHistoryEnabledChange(enabled: Boolean) = update { it.copy(historyEnabled = enabled) }

    fun onSetDefaultProvider(id: String) = update { it.copy(defaultProviderId = id) }

    fun onClearHistory() {
        viewModelScope.launch { clearHistory() }
    }

    fun onDeleteProvider(id: String) {
        viewModelScope.launch {
            providers.delete(id)
            // Leaving a dangling default would make every workflow fall back to a provider
            // that no longer exists, which reads as "nothing works" rather than "reconnect".
            settings.update { current ->
                if (current.defaultProviderId == id) current.copy(defaultProviderId = null)
                else current
            }
        }
    }

    /**
     * A genuine factory reset: workflows, run history, provider configurations and the API keys
     * in the encrypted vault, plus every preference including onboarding. Nothing survives,
     * because nothing of this ever left the device to survive anywhere else.
     */
    fun onDeleteAllLocalData() {
        viewModelScope.launch {
            history.clear()
            providers.observeAll().first().forEach { providers.delete(it.id) }
            workflows.observeAll().first().forEach { workflows.delete(it.id) }
            settings.update { UserSettings() }
        }
    }

    private fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settings.update(transform) }
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
