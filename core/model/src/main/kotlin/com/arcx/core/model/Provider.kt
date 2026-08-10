package com.arcx.core.model

import kotlinx.serialization.Serializable

/**
 * BYOK: every provider the user can connect. Most of these speak the OpenAI wire format,
 * so a single adapter covers them; Gemini and Anthropic get their own.
 */
@Serializable
enum class ProviderType {
    GEMINI,
    OPENAI,
    ANTHROPIC,
    OPENROUTER,
    GROQ,
    OLLAMA,
    LMSTUDIO,
    OPENAI_COMPATIBLE,
    ;

    /** True when the provider is reached over the local network and needs no key. */
    val isLocal: Boolean get() = this == OLLAMA || this == LMSTUDIO
}

/**
 * A configured provider. The API key is never held here — it lives in the encrypted vault,
 * keyed by [id], so that dumping this object (logs, exports, crash reports) cannot leak it.
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val type: ProviderType,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val streaming: Boolean = true,
    val createdAt: Long = 0L,
)

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val supportsVision: Boolean = false,
)
