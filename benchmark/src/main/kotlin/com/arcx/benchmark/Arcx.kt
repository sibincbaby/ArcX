package com.arcx.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

internal const val PACKAGE = "com.arcx.app"

/** Long enough that a slow cold start is a failure, not a flake. */
private const val TIMEOUT_MS = 10_000L

/**
 * Clicks through onboarding if it is up.
 *
 * Necessary because `connectedBenchmarkAndroidTest` uninstalls the app when it finishes, which
 * takes the preference store with it — so the very next run starts at "Bring your own key" and
 * every measurement past the first screen would time out looking for a tab that is not there.
 * A harness that only works on a device somebody prepared by hand is not a harness.
 */
internal fun MacrobenchmarkScope.skipOnboarding() {
    repeat(MAX_ONBOARDING_PAGES) {
        if (device.hasObject(By.text("Home"))) return
        val next = device.findObject(By.text("Start using ArcX"))
            ?: device.findObject(By.text("Skip for now"))
            ?: device.findObject(By.text("Continue"))
            ?: return
        next.click()
        device.waitForIdle()
    }
}

private const val MAX_ONBOARDING_PAGES = 8

/**
 * Waits for the bottom bar rather than for idle.
 *
 * `waitForIdle` returns as soon as the window stops changing, which on a screen that loads its
 * content asynchronously is *before* there is anything to measure. The tab labels are the last
 * thing composed on every top-level screen, so they are the honest signal that one has arrived.
 */
internal fun MacrobenchmarkScope.waitForApp() {
    skipOnboarding()
    device.wait(Until.hasObject(By.text("Home")), TIMEOUT_MS)
}

/** Switches to a top-level tab by its label and waits for the destination to settle. */
internal fun MacrobenchmarkScope.openTab(label: String) {
    device.findObject(By.text(label))?.click()
    device.wait(Until.hasObject(By.text(label)), TIMEOUT_MS)
    device.waitForIdle()
}

/**
 * Flings the first scrollable on screen, [times] times.
 *
 * The gesture margin matters: without it UiAutomator starts the swipe at the very edge of the
 * display, where the system's own back gesture claims it and the list never moves — a benchmark
 * that silently measures a still screen.
 */
internal fun MacrobenchmarkScope.flingList(times: Int = 3) {
    repeat(times) {
        // Re-found every time, not cached. A Compose list replaces its accessibility nodes as it
        // recomposes, so a handle held across a fling throws StaleObjectException — and the
        // benchmark fails after it has already done the scrolling, which reads like a bug in the
        // app rather than in the harness.
        val list = device.findObject(By.scrollable(true)) ?: return
        // Without a margin the swipe starts at the very edge of the display, where the system's
        // own back gesture claims it and the list never moves.
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    device.findObject(By.scrollable(true))?.apply {
        setGestureMargin(device.displayWidth / 5)
        fling(Direction.UP)
    }
    device.waitForIdle()
}
