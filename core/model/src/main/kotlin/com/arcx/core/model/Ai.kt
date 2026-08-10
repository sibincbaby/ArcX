package com.arcx.core.model

/** A binary part sent alongside the prompt (screenshot, shared image, PDF). */
class Attachment(
    val mimeType: String,
    val bytes: ByteArray,
    val fileName: String? = null,
)

/** Everything a provider needs to produce one completion. */
data class AiRequest(
    val model: String,
    val userPrompt: String,
    val systemPrompt: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val temperature: Float? = null,
    val maxTokens: Int? = null,
)

/** Streamed output. Providers emit many [Text] then exactly one [Done]. */
sealed interface AiChunk {
    data class Text(val delta: String) : AiChunk
    data class Done(val promptTokens: Int? = null, val completionTokens: Int? = null) : AiChunk
}

/**
 * Failures the UI can actually act on. Anything a provider throws gets mapped into one of
 * these so the result sheet can say "your key is invalid" instead of showing a stack trace.
 */
sealed class AiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingKey(val providerLabel: String) :
        AiError("No API key saved for $providerLabel")

    class InvalidKey(val providerLabel: String) :
        AiError("$providerLabel rejected the API key")

    class RateLimited(val retryAfterSeconds: Long? = null) :
        AiError("Rate limited by the provider")

    class Network(cause: Throwable?) :
        AiError("Could not reach the provider", cause)

    class Server(val code: Int, val body: String?) :
        AiError("Provider returned HTTP $code")

    class ContentBlocked(val reason: String) :
        AiError("The provider blocked this request: $reason")

    class NoProvider :
        AiError("No AI provider configured yet")

    /** The workflow works on text, and no text could be found to work on. */
    class NoInput :
        AiError("This workflow needs some text to work on")

    /** A screenshot workflow ran but no image could be captured. */
    class NoScreenshot :
        AiError("Could not capture the screen")

    class Unknown(cause: Throwable?) :
        AiError(cause?.message ?: "Something went wrong", cause)
}
