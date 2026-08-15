package com.arcx.integration.entrypoints.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.delay

/** Past this the prompt costs more than the answer is worth, and long feeds are mostly chrome. */
private const val MAX_SCREEN_TEXT = 8_000

/** A real hierarchy is rarely deeper than ~25; the cap is a fuse, not a budget. */
private const val MAX_DEPTH = 60

/**
 * Below this a read is treated as suspect rather than final.
 *
 * Chrome is the motivating case: measured on device, a freshly opened page yields 131 characters of
 * pure toolbar ("Connection is secure", "See 2 tabs", "Customise and control Google Chrome") for
 * about 1.3 seconds before the renderer's accessibility tree appears and the count jumps past 1900.
 * Any real screen clears this bar easily, so the retry below almost never runs.
 */
private const val SUBSTANTIAL_TEXT_CHARS = 400

/**
 * Ceiling on *starting* another attempt at a thin read; a walk already under way is allowed to
 * finish, so a huge tree can overrun this by the cost of one walk. Only the bubble's panel-open
 * capture ever spends it, and nothing is waiting on that.
 */
private const val STABILISE_BUDGET_MS = 500L

private const val STABILISE_STEP_MS = 100L

/** What was on screen, and when — held only in memory, and only until it goes stale. */
internal class ScreenSnapshot(
    val text: String,
    val packageName: String?,
    val takenAt: Long,
)

/**
 * Reads once, and only pays for more if the first read looks half-built.
 *
 * The fast path is the whole point: any screen with real content clears
 * [SUBSTANTIAL_TEXT_CHARS] on the first walk and this returns with no delay whatsoever, which
 * is what keeps tapping a workflow feeling instant.
 *
 * When the first read *is* thin, growth is the signal rather than a guess about what browser
 * chrome looks like — re-read, keep the longer result, and stop the moment a read fails to
 * beat the one before it. A screen that is genuinely short (a dialog, a settings toggle) costs
 * exactly one extra walk before that check ends the loop.
 */
internal suspend fun AccessibilityService.readUntilStable(): ScreenSnapshot? {
    var best = readForegroundWindow() ?: return null
    if (best.text.length >= SUBSTANTIAL_TEXT_CHARS) return best

    val deadline = SystemClock.elapsedRealtime() + STABILISE_BUDGET_MS
    while (SystemClock.elapsedRealtime() < deadline) {
        delay(STABILISE_STEP_MS)
        // Null here means ArcX took the front mid-poll; the best read so far is still good.
        val next = readForegroundWindow() ?: break
        if (next.text.length <= best.text.length) break
        best = next
        if (best.text.length >= SUBSTANTIAL_TEXT_CHARS) break
    }
    return best
}

internal fun AccessibilityService.readForegroundWindow(): ScreenSnapshot? {
    val root = topApplicationWindowRoot() ?: return null
    val accumulator = ScreenTextAccumulator(MAX_SCREEN_TEXT)
    // equals() on AccessibilityNodeInfo compares the source node id and window, so an ancestor
    // reachable again through a malformed hierarchy compares equal and is caught here.
    collect(root, depth = 0, visited = HashSet(), into = accumulator)
    val text = accumulator.result() ?: return null
    return ScreenSnapshot(
        text = text,
        packageName = root.packageName?.toString(),
        takenAt = SystemClock.elapsedRealtime(),
    )
}

/**
 * The top application window, provided it is not ArcX.
 *
 * Restricting to TYPE_APPLICATION drops the IME, the status bar and notification shade
 * (TYPE_SYSTEM) and the bubble's own overlay in one condition. Returning null rather than
 * ArcX's own window is deliberate: describing our own result sheet back to the user is a
 * confidently wrong answer, which is worse than the honest "nothing to work on".
 */
private fun AccessibilityService.topApplicationWindowRoot(): AccessibilityNodeInfo? {
    val self = packageName
    val fromWindowList = runCatching { windows }.getOrNull().orEmpty()
        .asSequence()
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        // Higher layer means closer to the user.
        .sortedByDescending { it.layer }
        .mapNotNull { runCatching { it.root }.getOrNull() }
        .firstOrNull { it.packageName?.toString()?.let { pkg -> pkg != self } == true }
    if (fromWindowList != null) return fromWindowList

    // Some OEM builds hand back an empty window list however the service is configured, and it
    // is empty on the lock screen. The active window is the only other thing we can ask for.
    val active = runCatching { rootInActiveWindow }.getOrNull() ?: return null
    return active.takeIf { it.packageName?.toString()?.let { pkg -> pkg != self } == true }
}

private fun collect(
    node: AccessibilityNodeInfo,
    depth: Int,
    visited: MutableSet<AccessibilityNodeInfo>,
    into: ScreenTextAccumulator,
) {
    if (depth > MAX_DEPTH || into.isFull) return
    if (!visited.add(node)) return
    // Recycled rows scrolled off a list stay in the tree. Including them would put text the
    // user cannot see — and did not mean to send anywhere — into the prompt.
    if (!runCatching { node.isVisibleToUser }.getOrDefault(false)) return

    if (!node.isPassword) {
        into.add(node.text)
        // Only when it differs: most widgets set both to the same string.
        val description = node.contentDescription
        if (description != null && description.toString() != node.text?.toString()) {
            into.add(description)
        }
    }

    for (index in 0 until node.childCount) {
        val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
        collect(child, depth + 1, visited, into)
    }
}

/**
 * Joins node text into one newline-separated block, dropping consecutive repeats and stopping hard
 * at [limit].
 *
 * Only *consecutive* repeats are dropped: a label duplicated between a toolbar and its content is
 * noise, but a word that legitimately recurs further down the screen is signal.
 */
private class ScreenTextAccumulator(private val limit: Int) {

    private val builder = StringBuilder()
    private var previous: String? = null

    val isFull: Boolean get() = builder.length >= limit

    fun add(raw: CharSequence?) {
        val text = raw?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text == previous || isFull) return
        if (builder.isNotEmpty()) builder.append('\n')
        val room = limit - builder.length
        builder.append(if (text.length <= room) text else text.substring(0, room))
        previous = text
    }

    fun result(): String? = builder.toString().trim().takeIf { it.isNotEmpty() }
}
