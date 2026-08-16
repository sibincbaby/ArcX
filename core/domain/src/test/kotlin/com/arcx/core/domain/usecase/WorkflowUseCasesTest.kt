package com.arcx.core.domain.usecase

import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunStatus
import com.arcx.core.model.Workflow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowUseCasesTest {

    private val now = 1_700_000_000_000L
    private val time = FakeTimeSource(now)

    @Test
    fun `save generates an id and stamps both timestamps for a new workflow`() = runTest {
        val repo = FakeWorkflowRepository()
        val saved = SaveWorkflowUseCase(repo, time)(Workflow(id = "", name = "New", prompt = "p"))

        assertTrue(saved.id.isNotBlank())
        assertEquals(now, saved.createdAt)
        assertEquals(now, saved.updatedAt)
        assertEquals(saved, repo.stored.value[saved.id])
    }

    @Test
    fun `save keeps id and createdAt when editing`() = runTest {
        val repo = FakeWorkflowRepository()
        val existing = Workflow(id = "w1", name = "Old", prompt = "p", createdAt = 5L, updatedAt = 5L)

        val saved = SaveWorkflowUseCase(repo, time)(existing.copy(name = "Renamed"))

        assertEquals("w1", saved.id)
        assertEquals(5L, saved.createdAt)
        assertEquals(now, saved.updatedAt)
        assertEquals("Renamed", repo.stored.value["w1"]?.name)
    }

    @Test
    fun `delete removes the workflow`() = runTest {
        val repo = FakeWorkflowRepository(listOf(Workflow(id = "w1", name = "A", prompt = "p")))
        DeleteWorkflowUseCase(repo)("w1")
        assertEquals("w1", repo.deleted)
        assertNull(repo.stored.value["w1"])
    }

    @Test
    fun `toggles flip the current flag`() = runTest {
        val repo = FakeWorkflowRepository()
        val workflow = Workflow(id = "w1", name = "A", prompt = "p", isFavorite = true, isPinned = false)

        ToggleFavoriteUseCase(repo)(workflow)
        TogglePinnedUseCase(repo)(workflow)

        assertEquals(false, repo.favorites["w1"])
        assertEquals(true, repo.pinned["w1"])
    }

    // -- Switching a workflow off ---------------------------------------------------------------

    @Test
    fun `toggling enabled switches a workflow off and back on`() = runTest {
        val workflow = Workflow(id = "w1", name = "A", prompt = "p")
        val repo = FakeWorkflowRepository(listOf(workflow))

        ToggleEnabledUseCase(repo)(workflow)
        assertFalse(repo.stored.value["w1"]!!.enabled)

        ToggleEnabledUseCase(repo)(repo.stored.value["w1"]!!)
        assertTrue(repo.stored.value["w1"]!!.enabled)
    }

    /**
     * Off is not delete, and this is the assertion that says so: the row, its name, its prompt and
     * its place in the library are all exactly as they were. Nothing cascades to history either —
     * nothing ever has, since a run keeps its own copy of what it ran.
     */
    @Test
    fun `switching a workflow off changes nothing but the switch`() = runTest {
        val workflow = Workflow(
            id = "w1",
            name = "Fix Grammar",
            prompt = "p",
            isPinned = true,
            isFavorite = true,
            sortOrder = 3,
            createdAt = 5L,
        )
        val repo = FakeWorkflowRepository(listOf(workflow))

        ToggleEnabledUseCase(repo)(workflow)

        assertEquals(workflow.copy(enabled = false), repo.stored.value["w1"])
        assertNull("switching off deleted the workflow", repo.deleted)
    }

    /**
     * A copy of something switched off arrives switched on. The user asked for it just now; one
     * that appeared in no picker would read as the duplicate having failed.
     */
    @Test
    fun `a duplicate of a switched-off workflow arrives switched on`() = runTest {
        val off = Workflow(id = "w1", name = "Quiet", prompt = "p", enabled = false)
        val repo = FakeWorkflowRepository(listOf(off))

        val copy = DuplicateWorkflowUseCase(repo, SaveWorkflowUseCase(repo, time))("w1")!!

        assertTrue(copy.enabled)
        assertFalse("the original was changed", repo.stored.value["w1"]!!.enabled)
    }

    @Test
    fun `duplicate makes an editable copy of a built-in`() = runTest {
        val builtIn = Workflow(
            id = "builtin-1",
            name = "Summarise",
            prompt = "p",
            isBuiltIn = true,
            isPinned = true,
            isFavorite = true,
            createdAt = 1L,
        )
        val repo = FakeWorkflowRepository(listOf(builtIn))

        val copy = DuplicateWorkflowUseCase(repo, SaveWorkflowUseCase(repo, time))("builtin-1")!!

        assertNotEquals(builtIn.id, copy.id)
        assertEquals("Summarise Copy", copy.name)
        assertFalse(copy.isBuiltIn)
        assertFalse(copy.isPinned)
        assertFalse(copy.isFavorite)
        assertEquals(now, copy.createdAt)
        assertEquals(builtIn, repo.stored.value["builtin-1"])
    }

    @Test
    fun `duplicate returns null for a missing workflow`() = runTest {
        val repo = FakeWorkflowRepository()
        assertNull(DuplicateWorkflowUseCase(repo, SaveWorkflowUseCase(repo, time))("nope"))
    }

    @Test
    fun `clear history empties the store`() = runTest {
        val history = FakeHistoryRepository()
        history.record(
            RunRecord(
                id = "r1",
                workflowId = "w1",
                workflowName = "A",
                workflowIcon = "✨",
                startedAt = 0L,
                durationMs = 1L,
                providerLabel = "OpenAI",
                model = "gpt-5",
                status = RunStatus.SUCCESS,
                inputPreview = "in",
            ),
        )

        ClearHistoryUseCase(history)()

        assertTrue(history.cleared)
        assertTrue(history.records.isEmpty())
    }
}
