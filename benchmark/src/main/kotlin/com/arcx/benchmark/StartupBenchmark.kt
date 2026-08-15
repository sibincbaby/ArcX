package com.arcx.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start: the one number every entry point pays. A share, a selection, a tile tap and a
 * bubble tap all wait on this, so it is the closest thing ArcX has to a single latency figure.
 *
 * Measured twice, on purpose. [coldStartupNoProfile] is what a user gets on the very first launch
 * after install, before ART has profiled anything; [coldStartup] is the steady state. The gap
 * between them is what a baseline profile would be worth — worth knowing before adding one.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForApp()
    }

    @Test
    fun coldStartupNoProfile() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForApp()
    }
}

/**
 * Ten is the smallest count that gives macrobenchmark a usable confidence interval. Raise it
 * before trusting a difference of a few milliseconds; the point of this harness is that the
 * interval is reported, so a result that does not clear it says so.
 */
internal const val ITERATIONS = 10
