package com.arcx.integration.entrypoints.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import com.arcx.integration.entrypoints.ArcxDeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

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

/** How long two captures are assumed to be of the same screen. See `store`. */
private const val SAME_SCREEN_WINDOW_MS = 8_000L

/**
 * Long edge of the kept frame, in pixels.
 *
 * This device captures at 1080x2400, and that bitmap is ~10MB raw. The frame is base64'd into a
 * prompt and written to disk for history, so the native resolution is paid for three times over for
 * detail a vision model does not use — 1568 is the point past which Gemini's image tiling stops
 * resolving anything new, and it still leaves screen text comfortably legible.
 */
private const val MAX_SCREENSHOT_EDGE = 1568

/** Screens are flat colour and glyph edges; 80 is where JPEG stops visibly smearing the text. */
private const val SCREENSHOT_JPEG_QUALITY = 80

/**
 * The platform refuses captures taken too close together with
 * `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`. That refusal is handled, so this interval is not
 * about avoiding the error — it is about not hiding the bubble and stalling the panel for a request
 * that was going to be refused anyway. A frame under a second old is of the same screen regardless.
 */
private const val SCREENSHOT_MIN_INTERVAL_MS = 1_000L

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
 *  - **Live**, whenever ArcX is not the top task. The floating bubble is an overlay window, so the
 *    user's app stays the top task and stays readable — but only for as long as the bubble does not
 *    take input focus. See `BubbleOverlay.EXPANDED_FLAGS`; taking focus is just as fatal as
 *    launching an activity. The bubble calls [captureScreen] the moment its panel opens.
 *  - **Snapshot**, taken when another app brings a window to the front, for every other entry
 *    point — the tile, the widget, a launcher shortcut — all of which necessarily put something
 *    else in front before ArcX can run.
 *
 * The snapshot is still not the push-everything design this file originally warned about.
 * `typeWindowContentChanged` remains unsubscribed, because it fires dozens of times a second in
 * any scrolling app and would mean a tree walk per frame. `typeWindowStateChanged` fires when the
 * user opens a screen: rare, throttled here, and walked off the main thread.
 *
 * The costs are worth naming, and the user-facing description in `:core:designsystem` names them:
 * a snapshot is what the user opened rather than what they have since scrolled to, and it lives in
 * memory for [SNAPSHOT_TTL_MS].
 *
 * ## The image path is not the text path
 *
 * Vision workflows need pixels, and [captureScreenImage] gets them from
 * [AccessibilityService.takeScreenshot] rather than MediaProjection so that firing one stays a
 * single tap with no system consent dialog. The two paths look alike — capture early, keep it
 * briefly, serve it later — but the image one is far more constrained:
 *
 *  - it needs API 30, whereas text works back to ArcX's minSdk of 26;
 *  - the platform rate-limits it to roughly one capture a second;
 *  - it captures the *composited display*, so unlike a tree walk it cannot filter ArcX out. There
 *    is no window to skip: whatever is drawn is in the picture. Only the caller can fix that, by
 *    not drawing, which is why the grab is driven from the bubble rather than from here.
 *
 * Because of that last point there is exactly one moment a usable frame exists — the tap that
 * opens the panel, with the handle hidden — so unlike text there is no speculative snapshot on
 * window changes and no second capture at pick time.
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

    @Volatile
    private var frame: ScreenFrame? = null

    @Volatile
    private var lastScreenshotAt = 0L

    /** Tree walks are binder-heavy, so they never run on the thread delivering events. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** A frame grab releases the caller's hidden UI, so its callback has to land on the main thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.attach(this)
        // Registering does not claim the button — the user assigns it, and this callback is simply
        // never invoked until they do. Throws on a platform that has no such control, which is not
        // worth taking the whole service down for.
        runCatching {
            accessibilityButtonController.registerAccessibilityButtonCallback(accessibilityButton)
        }
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
        // A single walk, not the stabilising one. This snapshot is speculative — most are never
        // read — so it does not get to spend four tree walks on every app switch the user makes.
        // The bubble, which knows the user wants an answer right now, is the one that stabilises.
        scope.launch { captureScreen(stabilise = false) }
    }

    /**
     * The accessibility button — floating in gesture navigation, in the nav bar with three-button,
     * or held on both volume keys — opens the picker.
     *
     * Only fires once the user has assigned ArcX to it in Settings > Accessibility, so nobody gets
     * this without asking for it. Unlike the bubble it costs no screen space and no foreground
     * service, and unlike the tile it does not need the shade pulled down.
     *
     * A background activity start would normally be refused, but this callback is the system
     * reporting that the user just pressed a system button, which is one of the exemptions. If a
     * future platform tightens that, the failure is this launch doing nothing — hence runCatching
     * rather than a crash in a service the user cannot easily restart.
     */
    private val accessibilityButton = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            runCatching { startActivity(ArcxDeepLinks.intent(this@ArcxAccessibilityService)) }
        }
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
        runCatching {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(accessibilityButton)
        }
        // Nothing the user looked at outlives the service being switched off.
        snapshot = null
        frame = null
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
    suspend fun captureScreen(stabilise: Boolean = false) {
        val fresh = if (stabilise) readUntilStable() else readForegroundWindow()
        store(fresh ?: return)
    }

    /**
     * Stores a capture unless it would be a downgrade of the same screen.
     *
     * The bubble captures twice — generously when its panel opens, then cheaply again when a
     * workflow is picked — and the cheap second read can legitimately come back shorter, because an
     * app's accessibility tree is rebuilt on demand and the first walk after a pause is sometimes
     * only half-populated. Without this guard that second read would throw away the good one.
     *
     * Scoped tightly: only the same app, and only for a few seconds, which is exactly the span
     * where the panel is covering the screen and the user cannot have navigated anywhere.
     */
    private fun store(fresh: ScreenSnapshot) {
        val current = snapshot
        val isRetryOfSameScreen = current != null &&
            current.packageName == fresh.packageName &&
            SystemClock.elapsedRealtime() - current.takenAt < SAME_SCREEN_WINDOW_MS
        if (isRetryOfSameScreen && fresh.text.length < current.text.length) return
        snapshot = fresh
    }

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
    private suspend fun readUntilStable(): ScreenSnapshot? {
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

    /**
     * Whether pixel capture is possible at all.
     *
     * The capability is read back from [getServiceInfo] rather than assumed from
     * `android:canTakeScreenshot` in the service config: asking for it and having been granted it
     * are different things, and a user on Android 10 has a perfectly valid accessibility service
     * that can never do this. `serviceInfo` is null before the system has configured the service
     * and throws once it has been unbound, so both collapse into the same "no".
     */
    fun canScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val capabilities = runCatching { serviceInfo?.capabilities }.getOrNull() ?: return false
        return capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
    }

    /**
     * Whether a capture is worth the caller hiding its own UI for. Distinct from [canScreenshot]:
     * this also answers no while the platform's capture interval has not elapsed, so a second tap
     * in quick succession does not blank the bubble and delay the panel to earn a refusal.
     */
    fun canCaptureScreenImageNow(): Boolean = canScreenshot() &&
        SystemClock.elapsedRealtime() - lastScreenshotAt >= SCREENSHOT_MIN_INTERVAL_MS

    /**
     * Grabs the composited display and keeps it as JPEG, replacing any older frame.
     *
     * [onGrabbed] is posted to the main thread as soon as the pixels are off the display — not when
     * the JPEG is ready. The caller is holding its own UI off screen for the duration of the grab,
     * so the two have to be decoupled: the copy, downscale and encode that follow are tens of
     * milliseconds that nobody is waiting on, and running the panel behind them would be visible.
     *
     * It fires exactly once on every path, including the paths that give up, because a caller that
     * never hears back stays hidden forever.
     */
    fun captureScreenImage(onGrabbed: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !canScreenshot()) {
            mainHandler.post(onGrabbed)
            return
        }
        lastScreenshotAt = SystemClock.elapsedRealtime()

        val started = runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                // The callback arrives on this executor, and everything it does is CPU-bound work
                // on a bitmap, so it is handed straight to the pool that already exists rather
                // than to a thread of its own.
                Dispatchers.IO.asExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        mainHandler.post(onGrabbed)
                        keepFrame(screenshot)
                    }

                    /**
                     * Every failure means the same thing here: keep whatever frame is already held.
                     * `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` is the common one and is not an
                     * error at all — the previous frame is at most a second old. A secure window
                     * (banking, DRM) refuses outright on API 34+, and that is the platform doing
                     * exactly what it should.
                     */
                    override fun onFailure(errorCode: Int) {
                        mainHandler.post(onGrabbed)
                    }
                },
            )
            true
        }.getOrDefault(false)

        if (!started) mainHandler.post(onGrabbed)
    }

    /**
     * The most recent frame, or null once it is older than [SNAPSHOT_TTL_MS] — the same TTL as the
     * text snapshot, with more at stake, since this is a picture of everything that happened to be
     * on screen rather than a summary of it.
     */
    fun latestScreenImage(): ByteArray? = frame
        ?.takeIf { SystemClock.elapsedRealtime() - it.takenAt < SNAPSHOT_TTL_MS }
        ?.jpeg

    /**
     * Turns a [ScreenshotResult] into the JPEG that is kept. Runs on the screenshot executor.
     *
     * The [android.hardware.HardwareBuffer] is a graphics allocation whose ownership the platform
     * hands over, and closing it is not tidiness: a leaked one is several megabytes of memory the
     * JVM heap has no idea about, so nothing will ever collect it, on every single capture. Hence
     * the `finally` — the wrap, the copy and the encode below all have their own ways to fail.
     *
     * The copy out of the buffer is unavoidable. `wrapHardwareBuffer` yields a `Config.HARDWARE`
     * bitmap, which cannot be read back or drawn onto a software canvas, so nothing can be scaled
     * or compressed until the pixels are in the heap.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun keepFrame(screenshot: ScreenshotResult) {
        val buffer = screenshot.hardwareBuffer
        val jpeg = try {
            val wrapped = runCatching {
                Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
            }.getOrNull()
            val software = wrapped
                ?.runCatching { copy(Bitmap.Config.ARGB_8888, false) }
                ?.getOrNull()
            wrapped?.recycle()
            software?.let { encodeFrame(it) }
        } finally {
            buffer.close()
        }

        if (jpeg == null) return
        frame = ScreenFrame(jpeg = jpeg, takenAt = SystemClock.elapsedRealtime())
        // Encoding takes long enough that the user can have switched the service off underneath
        // it, and the teardown that cleared `frame` would then have run before this wrote to it.
        if (!scope.isActive) frame = null
    }

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

/** The same idea as [ScreenSnapshot], in pixels. Never written to disk from here. */
private class ScreenFrame(
    val jpeg: ByteArray,
    val takenAt: Long,
)

/**
 * Downscales to [MAX_SCREENSHOT_EDGE] and JPEG-encodes, recycling [source] and anything it makes
 * along the way. Returns null if the encoder refuses.
 *
 * Call this off the main thread. Scaling a full-screen bitmap and compressing it are each tens of
 * milliseconds of pure CPU — several dropped frames if it ran while the bubble's panel was opening.
 */
private fun encodeFrame(source: Bitmap): ByteArray? {
    val longEdge = maxOf(source.width, source.height)
    val scaled = if (longEdge <= MAX_SCREENSHOT_EDGE) {
        source
    } else {
        val ratio = MAX_SCREENSHOT_EDGE.toDouble() / longEdge
        runCatching {
            source.scale(
                (source.width * ratio).roundToInt().coerceAtLeast(1),
                (source.height * ratio).roundToInt().coerceAtLeast(1),
                // Filtered: nearest-neighbour on a downscale this large turns body text into
                // speckle, and text is most of what a vision workflow is asked to read.
                filter = true,
            )
        }.getOrNull() ?: source
    }

    val stream = ByteArrayOutputStream(DEFAULT_JPEG_BUFFER)
    val encoded = runCatching {
        scaled.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_JPEG_QUALITY, stream)
    }.getOrDefault(false)

    if (scaled !== source) scaled.recycle()
    source.recycle()
    return if (encoded) stream.toByteArray() else null
}

/** Roughly what a 1568px-long-edge screen encodes to, so the stream almost never has to regrow. */
private const val DEFAULT_JPEG_BUFFER = 256 * 1024

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
