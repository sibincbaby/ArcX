package com.arcx.core.common.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * How often "now" has to be re-read for a relative label to stay honest.
 *
 * A minute, because a minute is the smallest unit `relativeTime` prints — anything finer would
 * re-emit a string that reads exactly the same, and anything coarser would leave a label wrong
 * for longer than the unit it shows.
 */
const val RELATIVE_TIME_TICK_MS = 60_000L

/**
 * The current time, immediately and every [intervalMs] thereafter.
 *
 * "9m ago" is a function of two values and only one of them — the run's timestamp — arrives on a
 * flow. Read the other one inside a `combine` fold and it is only ever as fresh as the last
 * upstream emission, so a screen whose sources happen to sit still keeps measuring against the
 * instant it started: Home combines five flows and re-emits constantly, Activity combines four
 * and barely does, which is how the two came to print different ages for the same run 1.2 seconds
 * apart. This makes "now" an input like any other, so the label refreshes on its own.
 *
 * Emits before the first [delay] on purpose. A screen must not sit on a stale or empty label for
 * a minute waiting for the first tick.
 *
 * The battery cost is bounded by the collector, not by this flow, and that is the contract to
 * keep: both ViewModels feed it into `stateIn(WhileSubscribed(...))`, so the loop only runs while
 * the screen is actually subscribed and is cancelled shortly after the user navigates away.
 * Collecting this from anything longer-lived — a singleton, a service, a `SharingStarted.Eagerly`
 * — turns it into a wakeup a minute for the life of the process.
 *
 * Takes [TimeSource] rather than reading the clock directly so a test can pin it and step it.
 */
fun timeTicker(time: TimeSource, intervalMs: Long = RELATIVE_TIME_TICK_MS): Flow<Long> = flow {
    while (true) {
        emit(time.nowMillis())
        delay(intervalMs)
    }
}
