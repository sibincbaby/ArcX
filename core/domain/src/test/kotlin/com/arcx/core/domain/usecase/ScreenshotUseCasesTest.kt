package com.arcx.core.domain.usecase

import com.arcx.core.model.ScreenshotRetention
import com.arcx.core.model.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotUseCasesTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun purge(retention: ScreenshotRetention, store: FakeScreenshotStore) =
        PurgeExpiredScreenshotsUseCase(
            screenshots = store,
            settings = FakeSettingsRepository(UserSettings(screenshotRetention = retention)),
            time = FakeTimeSource(now),
        )

    @Test
    fun `a week of retention purges everything older than seven days`() = runTest {
        val store = FakeScreenshotStore()

        purge(ScreenshotRetention.WEEK, store)()

        assertEquals(now - 7 * day, store.purgedBefore)
    }

    @Test
    fun `a month of retention purges everything older than thirty days`() = runTest {
        val store = FakeScreenshotStore()

        purge(ScreenshotRetention.MONTH, store)()

        assertEquals(now - 30 * day, store.purgedBefore)
    }

    @Test
    fun `forever purges nothing at all`() = runTest {
        val store = FakeScreenshotStore()

        purge(ScreenshotRetention.FOREVER, store)()

        assertNull(store.purgedBefore)
    }

    @Test
    fun `deleting all screenshots does not wait for the retention cutoff`() = runTest {
        val store = FakeScreenshotStore()
        store.save("r1", byteArrayOf(1))

        DeleteAllScreenshotsUseCase(store)()

        assertTrue(store.clearedAll)
        assertTrue(store.saved.isEmpty())
    }
}
