package com.arcx.core.common.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tick is what stops Home and Activity disagreeing about the age of the same run, and it is
 * not something a device can show you without an hour of waiting. `runTest` runs the interval on
 * virtual time, so a fake clock reading that same virtual time proves both halves of the contract
 * in milliseconds: that the first value costs no wait, and that later ones land an interval apart.
 */
@OptIn(ExperimentalCoroutinesApi::class) // testScheduler.currentTime
class TimeTickerTest {

    @Test
    fun `emits without waiting for the first interval`() = runTest {
        val clock = TimeSource { testScheduler.currentTime }

        val first = timeTicker(clock, intervalMs = 60_000L).first()

        assertEquals(0L, first)
        // No virtual time was consumed getting it — a screen does not sit blank for a minute.
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `re-reads the clock once per interval`() = runTest {
        val clock = TimeSource { testScheduler.currentTime }

        val ticks = timeTicker(clock, intervalMs = 60_000L).take(3).toList()

        assertEquals(listOf(0L, 60_000L, 120_000L), ticks)
    }

    @Test
    fun `honours a custom interval`() = runTest {
        val clock = TimeSource { testScheduler.currentTime }

        val ticks = timeTicker(clock, intervalMs = 250L).take(3).toList()

        assertEquals(listOf(0L, 250L, 500L), ticks)
    }
}
