package com.arcx.integration.entrypoints.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Past this the prompt costs more than the answer is worth, and long feeds are mostly chrome. */
private const val MAX_SCREEN_TEXT = 8_000

/** A real hierarchy is rarely deeper than ~25; the cap is a fuse, not a budget. */
private const val MAX_DEPTH = 60

/**
 * Backs [com.arcx.core.domain.capture.ScreenContextProvider].
 *
 * The whole design of this service is *pull*, not push: it holds no cached snapshot of the screen
 * and does no work between workflow runs. See [onAccessibilityEvent] for why.
 */
class ArcxAccessibilityService : AccessibilityService() {

    /** Last app to bring a window to the front. Written on the main thread, read from any. */
    @Volatile
    var foregroundPackage: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.attach(this)
    }

    /**
     * Deliberately almost empty, and it must stay that way.
     *
     * The tempting design is to also subscribe to typeWindowContentChanged and keep a live cache of
     * the screen so reads are instant. That is the classic accessibility-service battery drain: a
     * chat or a feed fires content-changed dozens of times a second, and servicing each one means
     * walking a node tree over IPC into another process. Users notice it as "this app eats my
     * battery" long before they notice the saved milliseconds.
     *
     * So the only thing kept here is the foreground package — one string copy per window switch.
     * Everything else is read on demand in [readScreenText], when a workflow has actually asked.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackage = event.packageName?.toString()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityServiceHolder.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityServiceHolder.detach(this)
        super.onDestroy()
    }

    /**
     * Visible text of the foreground window in traversal order, or null when there is nothing to
     * read. Call this off the main thread — every node access is a blocking binder call into the
     * app being inspected.
     *
     * Nodes are not recycled: [AccessibilityNodeInfo.recycle] has been a deprecated no-op since
     * API 33 and the platform pools them itself.
     */
    fun readScreenText(): String? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val accumulator = ScreenTextAccumulator(MAX_SCREEN_TEXT)
        // equals() on AccessibilityNodeInfo compares the source node id and window, so an ancestor
        // reachable again through a malformed hierarchy compares equal and is caught here.
        collect(root, depth = 0, visited = HashSet(), into = accumulator)
        return accumulator.result()
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
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return runCatching {
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
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
