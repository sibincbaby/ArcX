package com.arcx.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class OnboardingUiState(
    val type: ProviderType = ProviderType.GEMINI,
    val apiKey: String = "",
    val revealKey: Boolean = false,
    val test: TestState = TestState.Idle,
    /** Set once a test has actually reached the provider — the gate on finishing with a key. */
    val connected: Boolean = false,
    val finishing: Boolean = false,
) {
    val canTest: Boolean get() = apiKey.isNotBlank() && test != TestState.Running
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val workflows: WorkflowRepository,
    private val settings: SettingsRepository,
    private val registry: AiProviderRegistry,
) : ViewModel() {

    // Fixed for the flow so the config that was tested is the config that gets saved.
    private val configId = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onApiKeyChange(value: String) =
        // A new key invalidates the previous verdict, so the gate closes again.
        _uiState.update { it.copy(apiKey = value, test = TestState.Idle, connected = false) }

    fun onToggleReveal() = _uiState.update { it.copy(revealKey = !it.revealKey) }

    fun onTest() {
        val state = _uiState.value
        val provider = registry[state.type]
        if (provider == null) {
            _uiState.update { it.copy(test = TestState.Failure("${displayName(state.type)} is not supported yet.")) }
            return
        }

        _uiState.update { it.copy(test = TestState.Running) }
        viewModelScope.launch {
            val result = runCatching { provider.listModels(configFor(state.type), state.apiKey) }
            _uiState.update {
                result.fold(
                    onSuccess = { models -> it.copy(test = TestState.Success(models.size), connected = true) },
                    onFailure = { error -> it.copy(test = TestState.Failure(error.readable(state.type)), connected = false) },
                )
            }
        }
    }

    /**
     * The key is held in memory until this point and only then written to the vault, so a user
     * who backs out of onboarding leaves nothing behind. Seeding and the onboarding flag happen
     * either way: ArcX has to be explorable before anyone commits a key to it.
     */
    fun onFinish(onDone: () -> Unit) {
        if (_uiState.value.finishing) return
        _uiState.update { it.copy(finishing = true) }

        viewModelScope.launch {
            val state = _uiState.value
            val providerId = if (state.connected) {
                val config = configFor(state.type)
                providers.upsert(config, apiKey = state.apiKey)
                config.id
            } else {
                null
            }

            workflows.installNewBuiltIns()
            settings.update {
                it.copy(
                    hasOnboarded = true,
                    defaultProviderId = providerId ?: it.defaultProviderId,
                )
            }
            _uiState.update { it.copy(apiKey = "", finishing = false) }
            onDone()
        }
    }

    private fun configFor(type: ProviderType) = ProviderConfig(
        id = configId,
        type = type,
        label = displayName(type),
        baseUrl = defaultBaseUrl(type),
        defaultModel = defaultModel(type),
    )
}
