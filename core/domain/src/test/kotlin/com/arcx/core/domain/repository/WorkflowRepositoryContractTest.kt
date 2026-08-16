package com.arcx.core.domain.repository

import com.arcx.core.domain.usecase.FakeWorkflowRepository
import com.arcx.core.model.Workflow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split every picker in the app depends on: [WorkflowRepository.observeAll] means *all*, and
 * everything else means "what the user has left switched on".
 *
 * This is worth pinning here rather than only at the Room queries because the promise is what
 * callers are written against, and the callers are spread across four modules — the runner's
 * picker, the sidebar panel, the widget, the launcher's shortcut menu and Home's tiles. If the
 * split ever inverts, every one of them breaks the same way and none of them would say so.
 */
class WorkflowRepositoryContractTest {

    private val on = Workflow(id = "on", name = "On", prompt = "p", isPinned = true, isFavorite = true)
    private val off = on.copy(id = "off", name = "Off", enabled = false)

    private fun repository() = FakeWorkflowRepository(listOf(on, off))

    @Test
    fun `observeAll keeps switched-off workflows, because the Library has to show them`() = runTest {
        val all = repository().observeAll().first()

        assertEquals(setOf("on", "off"), all.mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun `observeEnabled drops them, because a picker must not offer them`() = runTest {
        val enabled = repository().observeEnabled().first()

        assertEquals(listOf("on"), enabled.map { it.id })
    }

    @Test
    fun `pinned and favourites drop them too — those feed the panel, the widget and shortcuts`() = runTest {
        val repository = repository()

        assertEquals(listOf("on"), repository.observePinned().first().map { it.id })
        assertEquals(listOf("on"), repository.observeFavorites().first().map { it.id })
        assertEquals(listOf("on"), repository.observeRecent().first().map { it.id })
    }

    /**
     * `updatedAt` unchanged is part of the contract, not an accident: the Library's default sort is
     * "Recently updated", and a stamp here would slide the row to the top of its section on every
     * flip — on the one control a user reaches for several times in a row.
     */
    @Test
    fun `setEnabled is reversible and counts as neither a delete nor an edit`() = runTest {
        val repository = repository()

        repository.setEnabled("on", false)
        assertTrue(repository.observeEnabled().first().isEmpty())
        assertEquals(on.copy(enabled = false), repository.get("on"))

        repository.setEnabled("on", true)
        assertEquals(on, repository.get("on"))
    }
}
