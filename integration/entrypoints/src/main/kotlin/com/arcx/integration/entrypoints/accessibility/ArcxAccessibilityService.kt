package com.arcx.integration.entrypoints.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Past this the prompt costs more than the answer is worth, and long feeds are mostly chrome. */
private const val MAX_SCREEN_TEXT = 8_000

/** A real hierarchy is rarely deeper than ~25; the cap is a fuse, not a budget. */
private const val MAX_DEPTH = 60

/**
 * How long a snapshot stays usable. Long enough to cover reading a screen and then reaching for a
 * widget or the tile; short enough that ArcX is never holding, or answering from, something the
 * user looked at in a different sitting.
 */
private const val SNAPSHOT_TTL_MS = 2 * 60 * 1000L

/** Window transitions arrive in bursts of two or three; this collapses them into one walk. */
private const val SNAPSHOT_MIN_INTERVAL_MS = 500L

/**
 * Backs [com.arcx.core.domain.capture.ScreenContextProvider].
 *
 * ## Why this is not purely on-demand
 *
 * The obvious design — read the screen only when a workflow asks — cannot work on Android, and it
 * took a device to prove it. Accessibility only ever exposes the application window of the *top
 * task*: `getWindows()` returns exactly one TYPE_APPLICATION entry, and windows underneath are
 * absent even when they are still drawn and visible. Verified on Android 15 with ArcX's runner
 * open over Wi-Fi settings — WindowManager reported the settings window as
 * `mHasSurface=true isReadyForDisplay()=true`, and the accessibility window list did not contain
 * it at all.
 *
 * Since workflows run in a translucent activity that launches over the app the user was reading,
 * by the time anything calls [readScreenText] the only readable window is ArcX's own. The screen
 * has to have been read *before* that.
 *
 * So there are two paths, and the cheap one is preferred:
 *
 *  - **Live**, whenever ArcX is not the top task. This is the case for the floating bubble, which
 *    is an overlay window and so never displaces the user's app. [captureScreen] exists for it.
 *  - **Snapshot**, taken when another app brings a window to the front, for every other entry
 *    point — the tile, the widget, a launcher shortcut — all of which necessarily put something
 *    else in front before ArcX can run.
 *
 * The snapshot is still not the push-everything design this file originally warned about.
 * `typeWindowContentChanged` remains unsubscribed, because it fires dozens of times a second in
 * any scrolling app and would mean a tree walk per frame. `typeWindowStateChanged` fires when the
 * user opens a screen: rare, throttled here, and walked off the main thread.
 *
 * The cost is honest and worth naming: the snapshot is what the user opened, not necessarily what
 * they have since scrolled to, and it lives in memory for [SNAPSHOT_TTL_MS]. The user-facing
 * description in `strings.xml` says so.
 */
class ArcxAccessibilityService : AccessibilityService() {

    /**
     * Last *other* app to bring a window to the front. Written on the main thread, read from any.
     *
     * ArcX's own package is filtered out on the way in, for the same reason the snapshot exists:
     * ArcX is technically the foreground app whenever anything asks, and answering "com.arcx.app"
     * makes `{{current_app}}` useless in exactly the case it exists for.
     */
    @Volatile
    var foregroundPackage: String? = null
        private set

    @Volatile
    private var snapshot: ScreenSnapshot? = null

    @Volatile
    private var lastSnapshotAt = 0L

    /** Tree walks are binder-heavy, so they never run on the thread delivering events. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.attach(this)
    }

    /**
     * Two fields and a throttled hand-off; no tree is walked on this thread.
     *
     * Subscribing to `typeWindowContentChanged` as well would make snapshots perfectly fresh and is
     * the single most reliable way to build an accessibility service that drains a battery: a chat
     * or a feed fires it continuously, and each one would mean walking a node tree over IPC. Window
     * *state* changes are user-paced — opening a screen, switching app — and that is the resolution
     * this feature actually needs.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // ArcX appearing over the user's app is not a change of what the user is doing, and
        // snapshotting it would overwrite the very thing the workflow is about to ask for.
        if (pkg == packageName) return

        foregroundPackage = pkg

        val now = SystemClock.elapsedRealtime()
        if (now - lastSnapshotAt < SNAPSHOT_MIN_INTERVAL_MS) return
        lastSnapshotAt = now
        scope.launch { captureScreen() }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        AccessibilityServiceHolder.detach(this)
        // Nothing the user looked at outlives the service being switched off.
        snapshot = null
        scope.cancel()
    }

    /**
     * Reads the current screen and stores it, replacing any older snapshot. No-op when ArcX itself
     * is the top task, so a late call cannot overwrite a good snapshot with ArcX's own UI.
     *
     * Called synchronously by the bubble just before it launches a workflow: the overlay does not
     * displace the user's app, so this is a genuinely live read of what they are looking at, and
     * paying a few milliseconds on a tap is better than racing the runner activity.
     */
    fun captureScreen() {
        val fresh = readForegroundWindow() ?: return
        snapshot = fresh
    }

    /**
     * Visible text of the window the *user* is looking at, or null when there is nothing usable.
     * Call this off the main thread — every node access is a blocking binder call into the app
     * being inspected.
     *
     * Nodes are not recycled: [AccessibilityNodeInfo.recycle] has been a deprecated no-op since
     * API 33 and the platform pools them itself.
     */
    fun readScreenText(): String? {
        // Live first: it is always fresher, and it succeeds whenever ArcX has not taken the front.
        readForegroundWindow()?.let {
            snapshot = it
            return it.text
        }
        return snapshot
            ?.takeIf { SystemClock.elapsedRealtime() - it.takenAt < SNAPSHOT_TTL_MS }
            ?.text
    }

    /** Package of the window the last snapshot came from, for `{{current_app}}`. */
    fun snapshotPackage(): String? = snapshot?.packageName

    private fun readForegroundWindow(): ScreenSnapshot? {
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
    private fun topApplicationWindowRoot(): AccessibilityNodeInfo? {
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
     * Writes [text] into whatever editable field currently has input focus.
     *
     * Returns false rather than throwing when nothing is focused or the focus is not editable —
     * that is the ordinary case (a web page, a read-only screen) and callers fall back to putting
     * the result on the clipboard.
     */
    fun setFocusedText(text: String): Boolean {
        val focused = runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            ?: return false
        if (!focused.isEditable) return false
        // Same trap as the screen read, with worse consequences: if the runner sheet has a field of
        // its own focused, writing here would overwrite what the user typed into ArcX instead of
        // replacing text in the app they came from. Falling back to the clipboard is the safe loss.
        if (focused.packageName?.toString() == packageName) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return runCatching {
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
    }
}

/** What was on screen, and when — held only in memory, and only until it goes stale. */
private class ScreenSnapshot(
    val text: String,
    val packageName: String?,
    val takenAt: Long,
)

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
