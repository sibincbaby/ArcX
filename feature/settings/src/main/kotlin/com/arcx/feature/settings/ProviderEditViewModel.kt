package com.arcx.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.AiError
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

/** Outcome of the last "Test connection" tap. */
sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Success(val modelCount: Int) : TestState
    data class Failure(val message: String) : TestState
}

data class ProviderEditUiState(
    val id: String? = null,
    val type: ProviderType = ProviderType.GEMINI,
    val label: String = "",
    val baseUrl: String = "",
    /** Only ever what the user typed in this session; a stored key is never read back into it. */
    val apiKey: String = "",
    val hasSavedKey: Boolean = false,
    val revealKey: Boolean = false,
    val defaultModel: String = "",
    val streaming: Boolean = true,
    val makeDefault: Boolean = false,
    val supportedTypes: Set<ProviderType> = emptySet(),
    val createdAt: Long = 0L,
    val test: TestState = TestState.Idle,
    val loading: Boolean = true,
) {
    val isNew: Boolean get() = id == null

    /** Local providers are reached over the LAN, so a key is neither required nor expected. */
    val needsKey: Boolean get() = !type.isLocal

    val canTest: Boolean
        get() = test != TestState.Running &&
            (!needsKey || apiKey.isNotBlank() || hasSavedKey)

    val canSave: Boolean get() = label.isNotBlank() && type in supportedTypes
}

@HiltViewModel
class ProviderEditViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val settings: SettingsRepository,
    private val registry: AiProviderRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderEditUiState())
    val uiState: StateFlow<ProviderEditUiState> = _uiState.asStateFlow()

    private var started = false

    /** Idempotent so a recomposition after a configuration change does not discard typed input. */
    fun start(providerId: String?) {
        if (started) return
        started = true

        viewModelScope.launch {
            val supported = registry.supportedTypes()
            val existing = providerId?.let { providers.get(it) }
            val defaultId = settings.current().defaultProviderId

            _uiState.value = if (existing == null) {
                val type = supported.firstOrNull() ?: ProviderType.GEMINI
                ProviderEditUiState(
                    type = type,
                    label = displayName(type),
                    baseUrl = defaultBaseUrl(type),
                    defaultModel = defaultModel(type),
                    supportedTypes = supported,
                    // The first provider a user adds is the one everything should use.
                    makeDefault = defaultId == null,
                    loading = false,
                )
            } else {
                ProviderEditUiState(
                    id = existing.id,
                    type = existing.type,
                    label = existing.label,
                    baseUrl = existing.baseUrl,
                    hasSavedKey = providers.hasKey(existing.id),
                    defaultModel = existing.defaultModel,
                    streaming = existing.streaming,
                    makeDefault = defaultId == existing.id,
                    supportedTypes = supported,
                    createdAt = existing.createdAt,
                    loading = false,
                )
            }
        }
    }

    /**
     * Switching type re-primes the endpoint and model, but only when the user has not edited
     * them away from the previous type's defaults — otherwise a typo fix would cost their URL.
     */
    fun onTypeChange(type: ProviderType) = _uiState.update { state ->
        state.copy(
            type = type,
            label = if (state.label == displayName(state.type)) displayName(type) else state.label,
            baseUrl = if (state.baseUrl == defaultBaseUrl(state.type)) defaultBaseUrl(type) else state.baseUrl,
            defaultModel = if (state.defaultModel == defaultModel(state.type)) defaultModel(type) else state.defaultModel,
            test = TestState.Idle,
        )
    }

    fun onLabelChange(value: String) = _uiState.update { it.copy(label = value) }

    fun onBaseUrlChange(value: String) = _uiState.update { it.copy(baseUrl = value, test = TestState.Idle) }

    fun onApiKeyChange(value: String) = _uiState.update { it.copy(apiKey = value, test = TestState.Idle) }

    fun onToggleReveal() = _uiState.update { it.copy(revealKey = !it.revealKey) }

    fun onDefaultModelChange(value: String) = _uiState.update { it.copy(defaultModel = value) }


    fun onMakeDefaultChange(enabled: Boolean) = _uiState.update { it.copy(makeDefault = enabled) }

    /**
     * Lists models against the settings as typed. Reaching the provider and getting a usable
     * model list is the only proof that a key works, short of spending tokens on a completion.
     */
    fun onTestConnection() {
        val state = _uiState.value
        val provider = registry[state.type]
        if (provider == null) {
            _uiState.update {
                it.copy(test = TestState.Failure("${displayName(state.type)} is not supported yet."))
            }
            return
        }

        _uiState.update { it.copy(test = TestState.Running) }
        viewModelScope.launch {
            val key = state.apiKey.ifBlank { state.id?.let { providers.apiKey(it) }.orEmpty() }
            val result = runCatching { provider.listModels(state.toConfig(), key.ifBlank { null }) }
            _uiState.update {
                it.copy(
                    test = result.fold(
                        onSuccess = { models -> TestState.Success(models.size) },
                        onFailure = { error -> TestState.Failure(error.readable(state.type)) },
                    ),
                )
            }
        }
    }

    fun onSave(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            val config = state.toConfig()
            // Null leaves the vault untouched, which is what an edit that never focused the key
            // field means. A blank typed key is a deliberate removal and the repository honours it.
            providers.upsert(config, apiKey = state.apiKey.takeIf { it.isNotBlank() })
            if (state.makeDefault) {
                settings.update { it.copy(defaultProviderId = config.id) }
            }
            _uiState.update { it.copy(id = config.id, apiKey = "", hasSavedKey = it.hasSavedKey || state.apiKey.isNotBlank()) }
            onSaved()
        }
    }

    fun onDelete(onDeleted: () -> Unit) {
        val id = _uiState.value.id ?: return
        viewModelScope.launch {
            providers.delete(id)
            settings.update { current ->
                if (current.defaultProviderId == id) current.copy(defaultProviderId = null) else current
            }
            onDeleted()
        }
    }

    private fun ProviderEditUiState.toConfig() = ProviderConfig(
        id = id ?: UUID.randomUUID().toString(),
        type = type,
        label = label.trim().ifBlank { displayName(type) },
        baseUrl = baseUrl.trim().ifBlank { defaultBaseUrl(type) },
        defaultModel = defaultModel.trim(),
        streaming = streaming,
        // Zero lets the repository stamp a new config; an edit keeps the original date.
        createdAt = createdAt,
    )
}

/**
 * Providers already map transport failures onto [AiError], which exists precisely so the UI can
 * say something a person can act on. Anything else is a bug, and its message is all we have.
 */
internal fun Throwable.readable(type: ProviderType): String = when (this) {
    is AiError.MissingKey -> "Enter an API key for ${displayName(type)} first."
    is AiError.InvalidKey -> "${displayName(type)} rejected that key."
    is AiError.RateLimited -> "Rate limited. Try again in a moment."
    is AiError.Network -> "Could not reach ${displayName(type)}. Check the base URL and your connection."
    is AiError.Server -> "${displayName(type)} returned HTTP $code."
    is AiError.ContentBlocked -> "Blocked by the provider: $reason"
    is AiError.NoProvider -> "${displayName(type)} is not supported yet."
    else -> message ?: "Something went wrong."
}
