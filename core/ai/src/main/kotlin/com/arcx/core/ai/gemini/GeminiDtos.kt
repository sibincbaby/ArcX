package com.arcx.core.ai.gemini

import kotlinx.serialization.Serializable

@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList(),
)

@Serializable
internal data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
)

@Serializable
internal data class GeminiInlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
internal data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
)

@Serializable
internal data class GeminiStreamChunk(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
    val usageMetadata: GeminiUsage? = null,
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class GeminiPromptFeedback(
    val blockReason: String? = null,
)

@Serializable
internal data class GeminiUsage(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
)

@Serializable
internal data class GeminiModelsResponse(
    val models: List<GeminiModel> = emptyList(),
)

@Serializable
internal data class GeminiModel(
    val name: String,
    val displayName: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
internal data class GeminiErrorEnvelope(
    val error: GeminiErrorBody? = null,
)

@Serializable
internal data class GeminiErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)
