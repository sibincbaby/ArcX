package com.arcx.core.domain.usecase

import app.cash.turbine.test
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.model.AiError
import com.arcx.core.model.Attachment
import com.arcx.core.model.InputSource
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ProviderType
import com.arcx.core.model.RunStatus
import com.arcx.core.model.UserSettings
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowInput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteWorkflowUseCaseTest {

    private val workflow = Workflow(
        id = "w1",
        name = "Summarise",
        prompt = "Summarise {{selected_text}} for {{current_app}}",
        systemPrompt = "You are terse. Screen says: {{screen_text}}",
    )

    /** A picture of the screen is the whole input: no text, nothing for the launch to carry. */
    private val vision = Workflow(
        id = "w2",
        name = "Explain this screen",
        input = InputSource.SCREENSHOT,
        prompt = "What is on this screen?",
    )

    private val openAi = ProviderConfig(
        id = "p1",
        type = ProviderType.OPENAI,
        label = "OpenAI",
        baseUrl = "https://api.openai.com",
        defaultModel = "gpt-5",
    )

    private val ollama = openAi.copy(id = "p2", type = ProviderType.OLLAMA, label = "Ollama", defaultModel = "llama4")

    private val input = WorkflowInput(text = "hello", sourcePackage = "com.example.notes")

    private fun useCase(
        workflows: FakeWorkflowRepository = FakeWorkflowRepository(listOf(workflow)),
        providers: FakeProviderRepository = FakeProviderRepository(openAi, "sk-test"),
        history: FakeHistoryRepository = FakeHistoryRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        provider: FakeAiProvider? = FakeAiProvider(deltas = listOf("Hel", "lo!")),
        screen: FakeScreenContextProvider = FakeScreenContextProvider(available = true, text = "on screen"),
        screenshots: FakeScreenshotStore = FakeScreenshotStore(),
        clipboard: FakeClipboard = FakeClipboard("clip"),
    ) = ExecuteWorkflowUseCase(
        workflows = workflows,
        providers = providers,
        history = history,
        settings = settings,
        registry = FakeRegistry(provider),
        screen = screen,
        screenshots = screenshots,
        clipboard = clipboard,
        time = FakeTimeSource(),
    )

    @Test
    fun `happy path streams accumulated text then succeeds`() = runTest {
        val history = FakeHistoryRepository()
        val provider = FakeAiProvider(deltas = listOf("Hel", "lo!"))

        useCase(history = history, provider = provider).invoke(workflow, input).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            assertEquals(ExecutionState.Streaming("Hel"), awaitItem())
            assertEquals(ExecutionState.Streaming("Hello!"), awaitItem())
            val success = awaitItem() as ExecutionState.Success
            assertEquals("Hello!", success.text)
            assertTrue(success.durationMs >= 0)
            awaitComplete()
        }

        val request = provider.lastRequest!!
        assertEquals("gpt-5", request.model)
        assertEquals("Summarise hello for com.example.notes", request.userPrompt)
        assertEquals("You are terse. Screen says: on screen", request.systemPrompt)

        val run = history.records.single()
        assertEquals(RunStatus.SUCCESS, run.status)
        assertEquals("hello", run.inputPreview)
        assertEquals("Hello!", run.outputPreview)
        assertEquals("OpenAI", run.providerLabel)
        assertNull(run.error)
    }

    @Test
    fun `no provider fails with NoProvider`() = runTest {
        val useCase = useCase(providers = FakeProviderRepository(config = null))

        useCase(workflow, input).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            val failed = awaitItem() as ExecutionState.Failed
            assertTrue(failed.error is AiError.NoProvider)
            awaitComplete()
        }
    }

    @Test
    fun `missing key on a remote provider fails with MissingKey`() = runTest {
        val useCase = useCase(providers = FakeProviderRepository(openAi, key = "  "))

        useCase(workflow, input).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            val error = (awaitItem() as ExecutionState.Failed).error as AiError.MissingKey
            assertEquals("OpenAI", error.providerLabel)
            awaitComplete()
        }
    }

    @Test
    fun `local provider runs without a key`() = runTest {
        val useCase = useCase(
            providers = FakeProviderRepository(ollama, key = null),
            provider = FakeAiProvider(type = ProviderType.OLLAMA, deltas = listOf("Hel", "lo!")),
        )

        useCase(workflow, input).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            assertEquals(ExecutionState.Streaming("Hel"), awaitItem())
            assertEquals(ExecutionState.Streaming("Hello!"), awaitItem())
            assertTrue(awaitItem() is ExecutionState.Success)
            awaitComplete()
        }
    }

    @Test
    fun `unregistered provider type fails with NoProvider`() = runTest {
        useCase(provider = null).invoke(workflow, input).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            assertTrue((awaitItem() as ExecutionState.Failed).error is AiError.NoProvider)
            awaitComplete()
        }
    }

    @Test
    fun `provider error is reported and still recorded`() = runTest {
        val history = FakeHistoryRepository()
        val boom = AiError.RateLimited(retryAfterSeconds = 30)

        useCase(
            history = history,
            provider = FakeAiProvider(deltas = listOf("partial"), error = boom),
        ).invoke(workflow, input).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            assertEquals(ExecutionState.Streaming("partial"), awaitItem())
            assertEquals(boom, (awaitItem() as ExecutionState.Failed).error)
            awaitComplete()
        }

        val run = history.records.single()
        assertEquals(RunStatus.FAILED, run.status)
        assertEquals("partial", run.outputPreview)
        assertNotNull(run.error)
    }

    @Test
    fun `non-AiError from a provider is mapped to Unknown`() = runTest {
        useCase(provider = FakeAiProvider(error = IllegalStateException("bad json")))
            .invoke(workflow, input).test {
                assertEquals(ExecutionState.Preparing, awaitItem())
                assertTrue((awaitItem() as ExecutionState.Failed).error is AiError.Unknown)
                awaitComplete()
            }
    }

    @Test
    fun `history disabled writes no run record`() = runTest {
        val history = FakeHistoryRepository()
        val settings = FakeSettingsRepository(UserSettings(historyEnabled = false))

        useCase(history = history, settings = settings).invoke(workflow, input).test {
            skipItems(3)
            assertTrue(awaitItem() is ExecutionState.Success)
            awaitComplete()
        }
        assertTrue(history.records.isEmpty())

        useCase(
            history = history,
            settings = settings,
            providers = FakeProviderRepository(config = null),
        ).invoke(workflow, input).test {
            skipItems(1)
            assertTrue(awaitItem() is ExecutionState.Failed)
            awaitComplete()
        }
        assertTrue(history.records.isEmpty())
    }

    @Test
    fun `accessibility unavailable leaves screen_text empty and still succeeds`() = runTest {
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val screen = FakeScreenContextProvider(available = false, text = "should not be read", packageName = "com.other")

        useCase(provider = provider, screen = screen)
            .invoke(workflow.copy(prompt = "[{{screen_text}}]"), WorkflowInput(text = "hello"))
            .test {
                assertEquals(ExecutionState.Preparing, awaitItem())
                assertEquals(ExecutionState.Streaming("ok"), awaitItem())
                assertTrue(awaitItem() is ExecutionState.Success)
                awaitComplete()
            }

        assertEquals("[]", provider.lastRequest!!.userPrompt)
        assertFalse(provider.lastRequest!!.systemPrompt!!.contains("{{"))
    }

    @Test
    fun `clipboard and time variables are filled and the key is never recorded`() = runTest {
        val history = FakeHistoryRepository()
        val provider = FakeAiProvider(deltas = listOf("ok"))

        useCase(history = history, provider = provider, clipboard = FakeClipboard("copied"))
            .invoke(
                workflow.copy(prompt = "{{clipboard}}|{{today}}|{{now}}|{{share_subject}}"),
                WorkflowInput(text = "hello"),
            ).test {
                skipItems(2)
                assertTrue(awaitItem() is ExecutionState.Success)
                awaitComplete()
            }

        val prompt = provider.lastRequest!!.userPrompt
        val parts = prompt.split("|")
        assertEquals("copied", parts[0])
        assertTrue(parts[1].matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        assertTrue(parts[2].matches(Regex("""\d{2}:\d{2}""")))
        assertEquals("", parts[3])
        assertEquals("sk-test", provider.lastApiKey)
        assertTrue(history.records.none { it.inputPreview.contains("sk-test") })
    }

    @Test
    fun `previews are truncated`() = runTest {
        val history = FakeHistoryRepository()
        val long = "x".repeat(5_000)

        useCase(history = history, provider = FakeAiProvider(deltas = listOf(long)))
            .invoke(workflow, WorkflowInput(text = long)).test {
                skipItems(2)
                assertTrue(awaitItem() is ExecutionState.Success)
                awaitComplete()
            }

        val run = history.records.single()
        assertEquals(2_000, run.inputPreview.length)
        assertEquals(2_000, run.outputPreview!!.length)
    }

    @Test
    fun `byId fails cleanly for a workflow that no longer exists`() = runTest {
        useCase(workflows = FakeWorkflowRepository(emptyList()))
            .byId("gone", input).test {
                assertEquals(ExecutionState.Preparing, awaitItem())
                assertTrue((awaitItem() as ExecutionState.Failed).error is AiError.Unknown)
                awaitComplete()
            }
    }

    @Test
    fun `byId runs the stored workflow`() = runTest {
        useCase().byId("w1", input).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            skipItems(2)
            assertTrue(awaitItem() is ExecutionState.Success)
            awaitComplete()
        }
    }

    // A shortcut, widget or tile launch carries only an id — the text has to come from somewhere.
    @Test
    fun `deep link launch with no text falls back to the clipboard`() = runTest {
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val useCase = useCase(provider = provider, clipboard = FakeClipboard("copied paragraph"))

        useCase(workflow, WorkflowInput()).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            skipItems(1)
            assertTrue(awaitItem() is ExecutionState.Success)
            awaitComplete()
        }

        assertTrue(provider.lastRequest!!.userPrompt.contains("copied paragraph"))
    }

    @Test
    fun `no text anywhere fails as NoInput without calling the provider`() = runTest {
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val useCase = useCase(
            provider = provider,
            clipboard = FakeClipboard(null),
            screen = FakeScreenContextProvider(available = false),
        )

        useCase(workflow, WorkflowInput()).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            val state = awaitItem()
            assertTrue(state is ExecutionState.Failed && state.error is AiError.NoInput)
            awaitComplete()
        }

        // The whole point: an empty prompt must never reach the provider and cost a call.
        assertNull(provider.lastRequest)
    }

    @Test
    fun `explicit input still wins over the clipboard`() = runTest {
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val useCase = useCase(provider = provider, clipboard = FakeClipboard("clipboard text"))

        useCase(workflow, WorkflowInput(text = "shared text")).test {
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(provider.lastRequest!!.userPrompt.contains("shared text"))
        assertFalse(provider.lastRequest!!.userPrompt.contains("Summarise clipboard text"))
    }


    // Tapping the bubble while reading an article must act on that article.
    @Test
    fun `selected-text workflow with empty clipboard falls back to screen text`() = runTest {
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val useCase = useCase(
            provider = provider,
            clipboard = FakeClipboard(null),
            screen = FakeScreenContextProvider(available = true, text = "article on the screen"),
        )

        useCase(workflow, WorkflowInput()).test { cancelAndIgnoreRemainingEvents() }

        assertTrue(provider.lastRequest!!.userPrompt.contains("article on the screen"))
    }

    @Test
    fun `clipboard still wins over screen text when both are present`() = runTest {
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val useCase = useCase(
            provider = provider,
            clipboard = FakeClipboard("copied on purpose"),
            screen = FakeScreenContextProvider(available = true, text = "whatever is on screen"),
        )

        useCase(workflow, WorkflowInput()).test { cancelAndIgnoreRemainingEvents() }

        val prompt = provider.lastRequest!!.userPrompt
        assertTrue(prompt.contains("copied on purpose"))
        assertFalse(prompt.contains("Summarise whatever is on screen"))
    }


    @Test
    fun `screenshot workflow sends the captured image and records where it was stored`() = runTest {
        val history = FakeHistoryRepository()
        val screenshots = FakeScreenshotStore()
        val provider = FakeAiProvider(deltas = listOf("a cat"))
        val jpeg = byteArrayOf(1, 2, 3)

        useCase(
            history = history,
            provider = provider,
            screen = FakeScreenContextProvider(canCapture = true, jpeg = jpeg),
            screenshots = screenshots,
        ).invoke(vision, WorkflowInput()).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            skipItems(1)
            assertTrue(awaitItem() is ExecutionState.Success)
            awaitComplete()
        }

        val attachment = provider.lastRequest!!.attachments.single()
        assertEquals("image/jpeg", attachment.mimeType)
        assertArrayEquals(jpeg, attachment.bytes)

        val run = history.records.single()
        assertEquals(screenshots.saved.keys.single(), run.id)
        assertEquals("/data/screenshots/${run.id}.jpg", run.screenshotPath)
    }

    @Test
    fun `no image available fails as NoScreenshot without calling the provider`() = runTest {
        val history = FakeHistoryRepository()
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val screenshots = FakeScreenshotStore()

        useCase(
            history = history,
            provider = provider,
            screen = FakeScreenContextProvider(canCapture = false, jpeg = byteArrayOf(1)),
            screenshots = screenshots,
        ).invoke(vision, WorkflowInput()).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            val state = awaitItem()
            assertTrue(state is ExecutionState.Failed && state.error is AiError.NoScreenshot)
            awaitComplete()
        }

        // The point of the guard: an imageless vision prompt must never cost an API call.
        assertNull(provider.lastRequest)
        assertTrue(screenshots.saved.isEmpty())
        assertNull(history.records.single().screenshotPath)
    }

    // Seeing what a failed run acted on is most of the reason to keep the image at all.
    @Test
    fun `a failed screenshot run still keeps the image it acted on`() = runTest {
        val history = FakeHistoryRepository()
        val screenshots = FakeScreenshotStore()

        useCase(
            history = history,
            provider = FakeAiProvider(error = AiError.RateLimited()),
            screen = FakeScreenContextProvider(canCapture = true, jpeg = byteArrayOf(9)),
            screenshots = screenshots,
        ).invoke(vision, WorkflowInput()).test {
            assertEquals(ExecutionState.Preparing, awaitItem())
            assertTrue(awaitItem() is ExecutionState.Failed)
            awaitComplete()
        }

        val run = history.records.single()
        assertEquals(RunStatus.FAILED, run.status)
        assertEquals("/data/screenshots/${run.id}.jpg", run.screenshotPath)
    }

    // A shared image is what the user chose to act on; the screen must not overrule it.
    @Test
    fun `a launch that brought its own attachment is not given a screenshot`() = runTest {
        val provider = FakeAiProvider(deltas = listOf("ok"))
        val shared = Attachment(mimeType = "image/png", bytes = byteArrayOf(7))

        useCase(
            provider = provider,
            screen = FakeScreenContextProvider(canCapture = true, jpeg = byteArrayOf(1)),
        ).invoke(vision, WorkflowInput(attachments = listOf(shared))).test {
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("image/png"), provider.lastRequest!!.attachments.map { it.mimeType })
    }

    // History off means nothing is written down — including bytes far more sensitive than a row.
    @Test
    fun `history disabled stores no screenshot`() = runTest {
        val screenshots = FakeScreenshotStore()

        useCase(
            settings = FakeSettingsRepository(UserSettings(historyEnabled = false)),
            screen = FakeScreenContextProvider(canCapture = true, jpeg = byteArrayOf(1)),
            screenshots = screenshots,
        ).invoke(vision, WorkflowInput()).test { cancelAndIgnoreRemainingEvents() }

        assertTrue(screenshots.saved.isEmpty())
    }

    // History must show what was actually sent, not the empty WorkflowInput a bubble launch carries.
    @Test
    fun `history records the resolved text, not the empty launch input`() = runTest {
        val history = FakeHistoryRepository()
        val useCase = useCase(
            history = history,
            clipboard = FakeClipboard(null),
            screen = FakeScreenContextProvider(available = true, text = "text read off the screen"),
        )

        useCase(workflow, WorkflowInput()).test { cancelAndIgnoreRemainingEvents() }

        assertEquals("text read off the screen", history.records.single().inputPreview)
    }


    // The picture is the input and the picture is what is stored. Recording the screen's text too
    // would put the one thing never sent to the provider — a text dump of the screen — on disk.
    @Test
    fun `screenshot run stores the image and no screen text`() = runTest {
        val history = FakeHistoryRepository()
        val shot = Workflow(id = "s1", name = "Look", prompt = "Describe it", input = InputSource.SCREENSHOT)
        val useCase = useCase(
            workflows = FakeWorkflowRepository(listOf(shot)),
            history = history,
            screen = FakeScreenContextProvider(
                available = true,
                text = "words visible on the screen",
                canCapture = true,
                jpeg = byteArrayOf(1, 2, 3),
            ),
        )

        useCase(shot, WorkflowInput()).test { cancelAndIgnoreRemainingEvents() }

        val record = history.records.single()
        assertEquals("", record.inputPreview)
        assertNotNull(record.screenshotPath)
    }

}
