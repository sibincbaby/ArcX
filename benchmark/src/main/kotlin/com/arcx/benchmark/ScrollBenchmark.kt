package com.arcx.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for the two interactions that dominate a session: scrolling Activity and moving
 * between tabs.
 *
 * **These numbers depend on how much history is on the device.** Activity is the only list in
 * ArcX long enough to fling, and it is bounded by `RunRecord.HISTORY_LIMIT` — so a phone with
 * three runs on it measures an empty screen and reports something meaningless. Seed a realistic
 * history before comparing runs, and compare only runs seeded the same way. See
 * `docs/benchmarking.md`.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun activityScroll() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForApp()
            openTab("Activity")
        },
    ) {
        flingList()
    }

    @Test
    fun libraryScroll() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForApp()
            openTab("Library")
        },
    ) {
        flingList()
    }

    /**
     * Every tab in turn and back to Home. This is the interaction the navigation transitions
     * were tuned against, and the one that regressed worst as history grew.
     */
    @Test
    fun tabSwitching() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForApp()
        },
    ) {
        openTab("Library")
        openTab("Discover")
        openTab("Activity")
        openTab("Home")
    }
}
