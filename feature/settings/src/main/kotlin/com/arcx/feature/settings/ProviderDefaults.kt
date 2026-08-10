package com.arcx.feature.settings

import com.arcx.core.model.ProviderType

/**
 * Endpoint and model defaults per provider, so adding one is a paste of a key rather than a
 * research task. Every value is only a starting point — the fields stay editable because
 * proxies, self-hosted gateways and new model names are the user's business, not ours.
 */
internal fun defaultBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/"
    ProviderType.OPENAI -> "https://api.openai.com/v1/"
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/v1/"
    ProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/"
    ProviderType.GROQ -> "https://api.groq.com/openai/v1/"
    ProviderType.OLLAMA -> "http://localhost:11434/"
    ProviderType.LMSTUDIO -> "http://localhost:1234/v1/"
    ProviderType.OPENAI_COMPATIBLE -> ""
}

internal fun defaultModel(type: ProviderType): String = when (type) {
    // Flash-lite: the free tier a BYOK user is most likely to be on.
    ProviderType.GEMINI -> "gemini-flash-lite-latest"
    ProviderType.OPENAI -> "gpt-4o-mini"
    ProviderType.ANTHROPIC -> "claude-sonnet-4-5"
    ProviderType.OPENROUTER -> "openai/gpt-4o-mini"
    ProviderType.GROQ -> "llama-3.3-70b-versatile"
    ProviderType.OLLAMA -> "llama3.2"
    ProviderType.LMSTUDIO -> ""
    ProviderType.OPENAI_COMPATIBLE -> ""
}

internal fun displayName(type: ProviderType): String = when (type) {
    ProviderType.GEMINI -> "Google Gemini"
    ProviderType.OPENAI -> "OpenAI"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.OPENROUTER -> "OpenRouter"
    ProviderType.GROQ -> "Groq"
    ProviderType.OLLAMA -> "Ollama"
    ProviderType.LMSTUDIO -> "LM Studio"
    ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible"
}

/** Where the user goes to mint a key. Local providers have none, hence the null. */
internal fun apiKeyUrl(type: ProviderType): String? = when (type) {
    ProviderType.GEMINI -> "https://aistudio.google.com/apikey"
    ProviderType.OPENAI -> "https://platform.openai.com/api-keys"
    ProviderType.ANTHROPIC -> "https://console.anthropic.com/settings/keys"
    ProviderType.OPENROUTER -> "https://openrouter.ai/keys"
    ProviderType.GROQ -> "https://console.groq.com/keys"
    ProviderType.OLLAMA, ProviderType.LMSTUDIO -> null
    ProviderType.OPENAI_COMPATIBLE -> null
}
