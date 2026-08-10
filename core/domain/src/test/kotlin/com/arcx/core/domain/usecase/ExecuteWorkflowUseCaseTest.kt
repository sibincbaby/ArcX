package com.arcx.core.domain.usecase

import app.cash.turbine.test
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.model.AiError
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ProviderType
import com.arcx.core.model.RunStatus
import com.arcx.core.model.UserSettings
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowInput
import kotlinx.coroutines.test.runTest
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
        clipboard: FakeClipboard = FakeClipboard("clip"),
    ) = ExecuteWorkflowUseCase(
        workflows = workflows,
        providers = providers,
        history = history,
        settings = settings,
        registry = FakeRegistry(provider),
        screen = screen,
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

}
