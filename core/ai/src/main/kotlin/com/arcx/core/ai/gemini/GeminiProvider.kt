package com.arcx.core.ai.gemini

import com.arcx.core.domain.ai.AiProvider
import com.arcx.core.model.AiChunk
import com.arcx.core.model.AiError
import com.arcx.core.model.AiRequest
import com.arcx.core.model.ModelInfo
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : AiProvider {

    override val type: ProviderType = ProviderType.GEMINI

    // Retrofit fixes the base URL at build time and the user may point a config at a proxy,
    // so services are built per base URL and kept.
    private val services = ConcurrentHashMap<String, GeminiService>()

    override suspend fun listModels(config: ProviderConfig, apiKey: String?): List<ModelInfo> {
        val key = requireKey(apiKey, config)
        return withContext(Dispatchers.IO) {
            val response = mapFailures { serviceFor(config).listModels(key).execute() }
            if (!response.isSuccessful) throw response.toAiError(config)
            val payload = mapFailures { response.body()?.use { it.string() }.orEmpty() }
            val models = mapFailures { json.decodeFromString<GeminiModelsResponse>(payload) }.models
            models
                .filter { GENERATE_CONTENT in it.supportedGenerationMethods }
                .map { model ->
                    val id = model.name.removePrefix(MODEL_NAME_PREFIX)
                    ModelInfo(
                        id = id,
                        displayName = model.displayName ?: id,
                        supportsVision = supportsVision(id),
                    )
                }
        }
    }

    override fun generate(
        request: AiRequest,
        config: ProviderConfig,
        apiKey: String?,
    ): Flow<AiChunk> = flow {
        val key = requireKey(apiKey, config)
        val body = json.encodeToString(request.toGeminiRequest()).toRequestBody(JSON_MEDIA_TYPE)
        val call = serviceFor(config).streamGenerateContent(modelFor(request, config), key, body)
        // A blocking read on the SSE body cannot see coroutine cancellation, so cancelling the
        // collector has to close the socket explicitly. Job.invokeOnCompletion is no good here:
        // it only runs once this coroutine has finished, which is what the read is blocking.
        val aborter = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            val response = mapFailures { call.execute() }
            if (!response.isSuccessful) throw response.toAiError(config)
            val stream = response.body() ?: throw AiError.Server(response.code(), null)

            var usage: GeminiUsage? = null
            try {
                val source = stream.source()
                while (true) {
                    val line = mapFailures { source.readUtf8Line() } ?: break
                    val payload = ssePayload(line) ?: continue
                    val chunk = mapFailures { json.decodeFromString<GeminiStreamChunk>(payload) }
                    chunk.blockReason()?.let { throw AiError.ContentBlocked(it) }
                    chunk.usageMetadata?.let { usage = it }
                    chunk.texts().forEach { emit(AiChunk.Text(it)) }
                }
            } finally {
                stream.close()
            }
            emit(AiChunk.Done(usage?.promptTokenCount, usage?.candidatesTokenCount))
        } finally {
            aborter.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun serviceFor(config: ProviderConfig): GeminiService {
        val baseUrl = config.baseUrl.trim().ifEmpty { DEFAULT_BASE_URL }
            .let { if (it.endsWith("/")) it else "$it/" }
        return services.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .build()
                .create(GeminiService::class.java)
        }
    }

    private fun modelFor(request: AiRequest, config: ProviderConfig): String =
        request.model.trim()
            .ifEmpty { config.defaultModel.trim() }
            .ifEmpty { DEFAULT_MODEL }
            .removePrefix(MODEL_NAME_PREFIX)

    private fun requireKey(apiKey: String?, config: ProviderConfig): String =
        apiKey?.takeIf { it.isNotBlank() } ?: throw AiError.MissingKey(label(config))

    private fun label(config: ProviderConfig): String = config.label.ifBlank { "Gemini" }

    private fun Response<ResponseBody>.toAiError(config: ProviderConfig): AiError {
        val body = runCatching { errorBody()?.use { it.string() } }.getOrNull()
        blockReasonOf(body)?.let { return AiError.ContentBlocked(it) }
        val code = code()
        return when {
            code == 429 -> AiError.RateLimited(headers()["Retry-After"]?.trim()?.toLongOrNull())
            code == 401 || code == 403 -> AiError.InvalidKey(label(config))
            code == 400 && mentionsApiKey(body) -> AiError.InvalidKey(label(config))
            // Server carries the status code, which is more useful to the result sheet than
            // Unknown would be for the 404/409 tail as well.
            else -> AiError.Server(code, body)
        }
    }

    private fun mentionsApiKey(body: String?): Boolean {
        val message = body?.let {
            runCatching { json.decodeFromString<GeminiErrorEnvelope>(it).error?.message }.getOrNull()
        } ?: body
        return message != null &&
            (message.contains("api key", ignoreCase = true) ||
                message.contains("api_key", ignoreCase = true))
    }

    private fun blockReasonOf(body: String?): String? = body
        ?.let { runCatching { json.decodeFromString<GeminiStreamChunk>(it) }.getOrNull() }
        ?.blockReason()

    private fun AiRequest.toGeminiRequest(): GeminiRequest {
        val parts = buildList {
            add(GeminiPart(text = userPrompt))
            attachments.forEach { attachment ->
                add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = attachment.mimeType,
                            data = Base64.getEncoder().encodeToString(attachment.bytes),
                        ),
                    ),
                )
            }
        }
        return GeminiRequest(
            contents = listOf(GeminiContent(role = "user", parts = parts)),
            systemInstruction = systemPrompt
                ?.takeIf { it.isNotBlank() }
                ?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) },
            generationConfig = if (temperature != null || maxTokens != null) {
                GeminiGenerationConfig(temperature = temperature, maxOutputTokens = maxTokens)
            } else {
                null
            },
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/"
        /** Flash-lite has by far the most generous free-tier limits, which matters for BYOK. */
        const val DEFAULT_MODEL = "gemini-flash-lite-latest"

        private const val MODEL_NAME_PREFIX = "models/"
        private const val GENERATE_CONTENT = "generateContent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun GeminiStreamChunk.texts(): List<String> =
    candidates.firstOrNull()?.content?.parts.orEmpty().mapNotNull { it.text }.filter { it.isNotEmpty() }

private fun GeminiStreamChunk.blockReason(): String? {
    promptFeedback?.blockReason?.let { return it }
    return candidates.firstOrNull()?.finishReason?.takeIf { it in BLOCKED_FINISH_REASONS }
}

/** Returns the JSON payload of one `data:` line, or null for blanks and `:` comments. */
private fun ssePayload(line: String): String? {
    if (line.isBlank() || line.startsWith(":")) return null
    if (!line.startsWith("data:")) return null
    return line.removePrefix("data:").trim().takeIf { it.isNotEmpty() }
}

/**
 * Gemini 1.5 and everything after it (2.x, 3.x) accept image parts; the only families still
 * text-only are the original gemini-1.0 / gemini-pro models.
 */
private fun supportsVision(id: String): Boolean {
    if (!id.startsWith("gemini-")) return false
    // The unversioned aliases — gemini-flash-latest, gemini-flash-lite-latest, gemini-pro-latest
    // — always resolve to a current generation, so they take images even though there is no
    // version number to parse out of the id.
    if (id.endsWith("-latest")) return true
    val major = id.removePrefix("gemini-").substringBefore('-').substringBefore('.').toIntOrNull()
        ?: return false
    return when {
        major >= 2 -> true
        major == 1 -> id.startsWith("gemini-1.5")
        else -> false
    }
}

/** No transport or decoding failure may leave the flow as anything but an [AiError]. */
private suspend inline fun <T> mapFailures(block: () -> T): T =
    try {
        block()
    } catch (e: AiError) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        // A cancelled call surfaces here as a closed socket, not as a CancellationException.
        currentCoroutineContext().ensureActive()
        throw AiError.Network(e)
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        throw AiError.Unknown(e)
    }

private val BLOCKED_FINISH_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT")
