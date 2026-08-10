package com.arcx.core.data.screenshot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScreenshotStoreImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val directory: File get() = File(temp.root, "screenshots")

    private fun store() = ScreenshotStoreImpl(directory, Dispatchers.Unconfined)

    @Test
    fun `save writes the jpeg under the run id and returns its path`() = runTest {
        val path = store().save("run-1", byteArrayOf(1, 2, 3))!!

        val file = File(path)
        assertEquals("run-1.jpg", file.name)
        assertEquals(directory.absolutePath, file.parentFile!!.absolutePath)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test
    fun `delete removes the named files and leaves the rest`() = runTest {
        val store = store()
        val gone = store.save("run-1", byteArrayOf(1))!!
        val kept = store.save("run-2", byteArrayOf(2))!!

        store.delete(listOf(gone))

        assertFalse(File(gone).exists())
        assertTrue(File(kept).exists())
    }

    // Paths come out of the database, so a bad row must not be able to reach another file.
    @Test
    fun `delete ignores paths outside the screenshot directory`() = runTest {
        val outsider = temp.newFile("keys.preferences_pb")

        store().delete(listOf(outsider.absolutePath))

        assertTrue(outsider.exists())
    }

    @Test
    fun `deleteAll leaves nothing behind and the store still works afterwards`() = runTest {
        val store = store()
        store.save("run-1", byteArrayOf(1))
        store.save("run-2", byteArrayOf(2))

        store.deleteAll()
        assertFalse(directory.exists())

        val later = store.save("run-3", byteArrayOf(3))!!
        assertTrue(File(later).exists())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test
    fun `purge removes only files older than the cutoff`() = runTest {
        val store = store()
        val old = File(store.save("old", byteArrayOf(1))!!)
        val fresh = File(store.save("fresh", byteArrayOf(2))!!)
        old.setLastModified(1_000L)
        fresh.setLastModified(9_000L)

        store.purgeOlderThan(5_000L)

        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `purge before anything was ever captured is not an error`() = runTest {
        store().purgeOlderThan(Long.MAX_VALUE)

        assertFalse(directory.exists())
    }
}
