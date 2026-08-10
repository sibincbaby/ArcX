package com.arcx.core.ai.gemini

import app.cash.turbine.test
import com.arcx.core.ai.di.NetworkModule
import com.arcx.core.model.AiChunk
import com.arcx.core.model.AiError
import com.arcx.core.model.AiRequest
import com.arcx.core.model.Attachment
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ProviderType
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class GeminiProviderTest {

    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    private val provider = GeminiProvider(OkHttpClient(), NetworkModule.provideJson())

    private fun config() = ProviderConfig(
        id = "gemini",
        type = ProviderType.GEMINI,
        label = "Gemini",
        baseUrl = server.url("/").toString(),
        defaultModel = "gemini-2.5-flash",
    )

    private fun request(
        prompt: String = "Summarise this",
        systemPrompt: String? = null,
        attachments: List<Attachment> = emptyList(),
    ) = AiRequest(
        model = "gemini-2.5-flash",
        userPrompt = prompt,
        systemPrompt = systemPrompt,
        attachments = attachments,
    )

    private fun sse(vararg payloads: String) = payloads.joinToString("") { "data: $it\n\n" }

    private fun eventStream(body: String) = MockResponse.Builder()
        .setHeader("Content-Type", "text/event-stream")
        .body(body)
        .build()

    @Test
    fun `emits every data frame then exactly one done`() = runBlocking {
        server.enqueue(
            eventStream(
                sse(
                    """{"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}]}""",
                    """{"candidates":[{"content":{"role":"model","parts":[{"text":", world"}]}}]}""",
                    """{"candidates":[{"content":{"role":"model","parts":[{"text":"!"}]},"finishReason":"STOP"}],""" +
                        """"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":3}}""",
                ),
            ),
        )

        provider.generate(request(), config(), "test-key").test {
            assertEquals(AiChunk.Text("Hello"), awaitItem())
            assertEquals(AiChunk.Text(", world"), awaitItem())
            assertEquals(AiChunk.Text("!"), awaitItem())
            assertEquals(AiChunk.Done(promptTokens = 11, completionTokens = 3), awaitItem())
            awaitComplete()
        }

        val recorded = server.takeRequest()
        assertTrue(recorded.target, recorded.target.contains(":streamGenerateContent"))
        assertTrue(recorded.target, recorded.target.contains("alt=sse"))
        assertEquals("test-key", recorded.headers["x-goog-api-key"])
    }

    @Test
    fun `parses a frame split across chunk boundaries`() = runBlocking {
        val body = sse(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"a long single frame"}]}}]}""",
        )
        // 8-byte HTTP chunks put the frame's newline in a different chunk than its payload.
        server.enqueue(
            MockResponse.Builder()
                .setHeader("Content-Type", "text/event-stream")
                .chunkedBody(body, 8)
                .build(),
        )

        provider.generate(request(), config(), "test-key").test {
            assertEquals(AiChunk.Text("a long single frame"), awaitItem())
            assertEquals(AiChunk.Done(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `429 maps to rate limited with retry after`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("Retry-After", "42")
                .body("""{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}""")
                .build(),
        )

        provider.generate(request(), config(), "test-key").test {
            val error = awaitError()
            assertTrue(error.toString(), error is AiError.RateLimited)
            assertEquals(42L, (error as AiError.RateLimited).retryAfterSeconds)
        }
    }

    @Test
    fun `400 about the api key maps to invalid key`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body(
                    """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.",""" +
                        """"status":"INVALID_ARGUMENT"}}""",
                )
                .build(),
        )

        provider.generate(request(), config(), "bad-key").test {
            val error = awaitError()
            assertTrue(error.toString(), error is AiError.InvalidKey)
            assertEquals("Gemini", (error as AiError.InvalidKey).providerLabel)
        }
    }

    @Test
    fun `prompt feedback block reason maps to content blocked`() = runBlocking {
        server.enqueue(eventStream(sse("""{"promptFeedback":{"blockReason":"SAFETY"}}""")))

        provider.generate(request(), config(), "test-key").test {
            val error = awaitError()
            assertTrue(error.toString(), error is AiError.ContentBlocked)
            assertEquals("SAFETY", (error as AiError.ContentBlocked).reason)
        }
    }

    @Test
    fun `missing key fails without touching the network`() = runBlocking {
        provider.generate(request(), config(), "  ").test {
            assertTrue(awaitError() is AiError.MissingKey)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `request carries the system prompt and inline attachments`() = runBlocking {
        server.enqueue(eventStream(sse("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")))

        provider.generate(
            request(
                systemPrompt = "Be terse",
                attachments = listOf(Attachment(mimeType = "image/png", bytes = byteArrayOf(1, 2, 3))),
            ),
            config(),
            "test-key",
        ).test {
            assertEquals(AiChunk.Text("ok"), awaitItem())
            assertEquals(AiChunk.Done(), awaitItem())
            awaitComplete()
        }

        val sent = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(sent, sent.contains(""""role":"user""""))
        assertTrue(sent, sent.contains(""""systemInstruction":{"parts":[{"text":"Be terse"}]}"""))
        assertTrue(sent, sent.contains(""""inlineData":{"mimeType":"image/png","data":"AQID"}"""))
        // temperature/maxTokens were null, so generationConfig must not be sent at all.
        assertTrue(sent, !sent.contains("generationConfig"))
    }

    @Test(timeout = 20_000)
    fun `cancelling the collector aborts the http call`() = runBlocking {
        // The server sits on the response, so the flow is parked inside a blocking read.
        server.enqueue(
            eventStream(sse("""{"candidates":[{"content":{"parts":[{"text":"never"}]}}]}"""))
                .newBuilder()
                .headersDelay(3, TimeUnit.SECONDS)
                .build(),
        )

        val elapsed = measureTimeMillis {
            val job = launch { runCatching { provider.generate(request(), config(), "test-key").collect {} } }
            delay(300)
            job.cancelAndJoin()
        }
        assertTrue("cancellation took ${elapsed}ms", elapsed < 2_000)
    }

    @Test
    fun `listModels keeps generateContent models and strips the models prefix`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    {"models":[
                      {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash",
                       "supportedGenerationMethods":["generateContent","countTokens"]},
                      {"name":"models/gemini-1.0-pro","supportedGenerationMethods":["generateContent"]},
                      {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
                    ]}
                    """.trimIndent(),
                )
                .build(),
        )

        val models = provider.listModels(config(), "test-key")

        assertEquals(listOf("gemini-2.5-flash", "gemini-1.0-pro"), models.map { it.id })
        assertEquals("Gemini 2.5 Flash", models[0].displayName)
        assertTrue(models[0].supportsVision)
        assertTrue(!models[1].supportsVision)
    }
}
