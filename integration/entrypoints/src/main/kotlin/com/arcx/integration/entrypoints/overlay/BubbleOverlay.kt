package com.arcx.integration.entrypoints.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.animation.doOnEnd
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arcx.core.model.Workflow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val SNAP_DURATION_MS = 180L

/**
 * How long SurfaceFlinger is given to get the bubble-less frame onto the display after the view
 * hierarchy has handed it over. One frame at 60Hz, and the display is the only party left that has
 * not confirmed anything — `registerFrameCommitCallback` says the frame reached the compositor, not
 * that it was composited.
 */
private const val COMPOSITE_SETTLE_MS = 16L

/**
 * Ceiling on how long the panel is held back waiting for a frame grab. Nothing should get close to
 * it — a grab is tens of milliseconds — but a callback that never arrives would otherwise leave the
 * bubble invisible and the panel unopened, which is the one failure the user cannot recover from.
 */
private const val FRAME_GRAB_TIMEOUT_MS = 500L

/**
 * The floating bubble: one overlay window that is a small circle most of the time and the whole
 * screen while its panel is open.
 *
 * One window rather than two, because the two-window version has to keep their positions and
 * z-order in sync and still ends up toggling the same focus flag. Collapsing and expanding here is
 * a size change plus a flag change on the same [WindowManager.LayoutParams].
 */
internal class BubbleOverlay(
    private val context: Context,
    private val onWorkflow: (Workflow) -> Unit,
    private val onMore: () -> Unit,
    /** Fired the instant the tap resolves — the last moment the app underneath is still readable. */
    private val onExpanded: () -> Unit = {},
    /**
     * Whether a pixel capture is worth blanking the bubble and delaying the panel for. Answers no
     * on old platforms, without the accessibility service, and while the platform's capture
     * interval has not elapsed.
     */
    private val canCaptureImage: () -> Boolean = { false },
    /**
     * Grabs a frame of the screen. The callback lands on the main thread once the pixels are off
     * the display, which is the moment the bubble may be drawn again — not when the image has
     * finished encoding.
     */
    private val captureImage: (onGrabbed: () -> Unit) -> Unit = { it() },
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val host = OverlayViewHost()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var view: GestureHost? = null

    /** Held separately from [view] because the frame grab hides the drawn content, not the window. */
    private var content: View? = null

    /**
     * Owns the collapsed bubble's tap and drag.
     *
     * The gesture has to live on a parent of the ComposeView rather than on a touch listener
     * attached to it. A ComposeView is a ViewGroup, and a ViewGroup only consults its
     * OnTouchListener when no child consumed the event — the AndroidComposeView child always
     * consumes the stream, so `setOnTouchListener` on a ComposeView is silently never called.
     * That is what made the bubble inert: the tap was delivered to the window and then dropped.
     *
     * While expanded we stop intercepting, so the panel's own Compose gestures work normally.
     */
    private inner class GestureHost(context: Context) : FrameLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !expanded

        override fun onTouchEvent(ev: MotionEvent): Boolean = onBubbleTouch(this, ev)

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            if (!expanded) pullOnScreen(this)
        }
    }
    private var snapAnimator: ValueAnimator? = null

    private var expanded by mutableStateOf(false)

    /** Mirrors UserSettings.bubbleOpensFullList; see [updateOpensFullList]. */
    private var opensFullList = false
    private var workflows by mutableStateOf(emptyList<Workflow>())

    /** True from the tap until the panel actually opens, while a frame grab is in flight. */
    private var expanding = false

    /** Where the bubble sits when collapsed; preserved across expand/collapse. */
    private var collapsedX = 0
    private var collapsedY = 0

    private var downRawX = 0f
    private var downRawY = 0f
    private var downParamX = 0
    private var downParamY = 0
    private var dragging = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        // The only overlay type a normal app may use since Oreo; TYPE_PHONE and friends are gone.
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        COLLAPSED_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    fun updateWorkflows(list: List<Workflow>) {
        workflows = list
    }

    /**
     * Whether a tap opens the full picker instead of the panel. Held rather than read at tap time
     * so the touch handler stays synchronous; the settings collector sets it long before any tap.
     */
    fun updateOpensFullList(opens: Boolean) {
        opensFullList = opens
    }

    /**
     * Adds the bubble to the window manager. Returns false if the overlay permission was revoked
     * between the caller's check and this call, which the system reports by throwing.
     */
    fun show(): Boolean {
        if (view != null) return true

        val metrics = screenSize()
        val bubblePx = (BubbleSize.value * context.resources.displayMetrics.density).roundToInt()
        collapsedX = metrics.first - bubblePx
        collapsedY = metrics.second / 3
        params.x = collapsedX
        params.y = collapsedY

        val composeView = ComposeView(context).apply {
            // Owners first, then content, then attach. Compose resolves all three from the view
            // tree the moment the view is attached to the window, and an overlay window has no
            // Activity to have installed them.
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            // With the owners in place the default window recomposer factory builds a
            // CompositionContext tied to that lifecycle, so this also decides when composition is
            // torn down: exactly when the host is destroyed, never on a stray detach.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OverlayTheme {
                    if (expanded) {
                        BubblePanel(
                            workflows = workflows,
                            onWorkflow = { workflow ->
                                collapse()
                                onWorkflow(workflow)
                            },
                            onMore = {
                                collapse()
                                onMore()
                            },
                            onDismiss = ::collapse,
                        )
                    } else {
                        BubbleHandle()
                    }
                }
            }
        }

        val gestureHost = GestureHost(context).apply {
            // The ViewTree owners must be on the window's root view, not only on the
            // ComposeView: Compose builds the window recomposer by resolving the lifecycle
            // owner from the root, so once the ComposeView stopped being the root it would
            // throw "ViewTreeLifecycleOwner not found" the moment it attached.
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        host.create()
        return runCatching {
            windowManager.addView(gestureHost, params)
            view = gestureHost
            content = composeView
            host.resume()
            true
        }.getOrDefault(false)
    }

    fun hide() {
        snapAnimator?.cancel()
        snapAnimator = null
        view?.let { attached ->
            runCatching { windowManager.removeView(attached) }
        }
        view = null
        content = null
        // Order matters the other way round from what you would expect: DisposeOnViewTreeLifecycle-
        // Destroyed deliberately survives a detach, so removing the view does not end the
        // composition. Moving the host to DESTROYED is what actually releases it.
        host.destroy()
    }

    /**
     * All positioning is done in raw (screen) coordinates.
     *
     * The obvious implementation — a Compose drag modifier, or local MotionEvent x/y — feeds back on
     * itself: moving the window moves the view under the finger, so the next local delta is close
     * to zero and the bubble crawls. rawX/rawY are screen-absolute and immune to that.
     */
    private fun onBubbleTouch(v: View, event: MotionEvent): Boolean {
        // While expanded the panel is a normal Compose surface and owns its own gestures.
        if (expanded) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                downParamX = params.x
                downParamY = params.y
                dragging = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                // Slop is the difference between "I meant to move it" and "I meant to tap it". A
                // bubble that fires a workflow because the user's thumb travelled two pixels while
                // repositioning it is worse than no bubble at all.
                if (!dragging && hypot(dx, dy) > touchSlop) dragging = true
                if (dragging) {
                    params.x = downParamX + dx.roundToInt()
                    params.y = downParamY + dy.roundToInt()
                    applyLayout()
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) snapToEdge(v) else expand()
                dragging = false
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) snapToEdge(v)
                dragging = false
                true
            }

            else -> false
        }
    }

    /**
     * The tap has resolved and the panel is about to open. Everything that needs the user's app to
     * still be the only thing on screen happens here, in the gap before it does.
     */
    private fun expand() {
        if (expanding || expanded) return
        expanding = true
        collapsedX = params.x
        collapsedY = params.y
        // Fired before the frame grab rather than after it: the text read wants the earliest moment
        // it can get and does not care what is drawn, so it must not inherit the grab's delay.
        onExpanded()
        if (!grabFrameThenOpen()) finishOpen()
    }

    /**
     * Both destinations go through here so neither can skip the capture above. Handing off to the
     * full picker still has to happen *after* the screen has been read and photographed — that
     * Activity is about to cover the very screen a workflow may be asked about, and the snapshot
     * taken here is what it will answer from.
     */
    private fun finishOpen() {
        if (opensFullList) {
            expanding = false
            onMore()
        } else {
            openPanel()
        }
    }

    /**
     * Blanks the bubble, has a frame of the screen taken, then opens the panel. Returns false when
     * there is nothing to take, in which case the caller opens the panel with no delay at all.
     *
     * This is the only moment in ArcX's life when a usable frame exists. `takeScreenshot` captures
     * the composited display, so unlike an accessibility tree walk it cannot be asked to leave ArcX
     * out — whatever is drawn is in the picture. A moment later the panel covers the very screen the
     * workflow is about to be asked about, and right now the handle is sitting on top of it. So the
     * content is hidden for the length of the grab and the panel is held back until it is over.
     *
     * Hiding the ComposeView rather than the window is deliberate: the window and its surface stay
     * alive and only the content stops being drawn, which is one draw pass. Taking the window down
     * instead means a surface teardown and a fresh one on the way back, which is both slower and
     * visible.
     */
    private fun grabFrameThenOpen(): Boolean {
        val drawn = content ?: return false
        // Redundant with canCaptureImage, which cannot be true below R — but stated here so the
        // API-29 frame callback below is guarded by something the compiler can see.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (!canCaptureImage()) return false

        drawn.visibility = View.INVISIBLE

        var released = false
        val release = Runnable {
            if (released) return@Runnable
            released = true
            drawn.visibility = View.VISIBLE
            finishOpen()
        }
        drawn.postDelayed(release, FRAME_GRAB_TIMEOUT_MS)

        // registerFrameCommitCallback is the only public signal that a specific frame has left the
        // view hierarchy; polling with a fixed delay instead would be either a guess that shows the
        // handle in the picture or a guess that makes the panel feel slow.
        drawn.viewTreeObserver.registerFrameCommitCallback {
            drawn.postDelayed({ captureImage { release.run() } }, COMPOSITE_SETTLE_MS)
        }
        return true
    }

    private fun openPanel() {
        expanding = false
        expanded = true
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        params.flags = EXPANDED_FLAGS
        applyLayout()
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.x = collapsedX
        params.y = collapsedY
        params.flags = COLLAPSED_FLAGS
        applyLayout()
    }

    /** Sends the bubble to whichever side edge it is nearer, and keeps it on screen vertically. */
    private fun snapToEdge(v: View) {
        val (screenWidth, screenHeight) = screenSize()
        val width = if (v.width > 0) v.width else 0
        val height = if (v.height > 0) v.height else 0
        val targetX = if (params.x + width / 2 < screenWidth / 2) 0 else screenWidth - width
        params.y = params.y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        applyLayout()

        val from = params.x
        if (from == targetX) {
            collapsedX = targetX
            collapsedY = params.y
            return
        }

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(from, targetX).apply {
            duration = (SNAP_DURATION_MS * abs(targetX - from) / screenWidth.coerceAtLeast(1))
                .coerceIn(80L, SNAP_DURATION_MS)
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                applyLayout()
            }
            doOnEnd {
                collapsedX = params.x
                collapsedY = params.y
            }
            start()
        }
    }

    /**
     * Drags the bubble back inside the display if any of it ended up outside.
     *
     * `params.x` is measured from the window's parent frame, which excludes system insets,
     * while [screenSize] describes the whole display. On a device with a display cutout those
     * differ, and placing the bubble at `screenWidth - bubbleWidth` pushed all but a sliver of
     * it past the right edge — visible enough to look fine in a screenshot, too small to hit.
     * Measuring where the view actually landed is immune to whichever inset is in play, and to
     * rotation, which changes the inset without any callback of its own.
     */
    private fun pullOnScreen(v: View) {
        if (v.width == 0 || v.height == 0) return
        val (screenWidth, screenHeight) = screenSize()
        val location = IntArray(2).also { v.getLocationOnScreen(it) }

        var x = params.x
        var y = params.y
        if (location[0] + v.width > screenWidth) x -= (location[0] + v.width) - screenWidth
        if (location[0] < 0) x -= location[0]
        if (location[1] + v.height > screenHeight) y -= (location[1] + v.height) - screenHeight
        if (location[1] < 0) y -= location[1]
        if (x == params.x && y == params.y) return

        params.x = x
        params.y = y
        collapsedX = x
        collapsedY = y
        // Posted rather than applied inline: this runs from onLayout, and updating the window
        // synchronously from there re-enters layout.
        v.post { applyLayout() }
    }

    private fun applyLayout() {
        val attached = view ?: return
        // Throws once the view has been removed, which races with a fling that is still animating.
        runCatching { windowManager.updateViewLayout(attached, params) }
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
            metrics.widthPixels to metrics.heightPixels
        }

    private companion object {
        /**
         * NOT_TOUCH_MODAL lets touches outside the bubble reach the app behind it, and
         * LAYOUT_NO_LIMITS lets the user park it over the status bar or gesture area rather than
         * having it snap back inside the safe insets.
         */
        const val COLLAPSED_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        /**
         * Identical to collapsed, and NOT_FOCUSABLE staying on is the important part.
         *
         * Dropping it — the obvious thing to do when a panel appears — silently breaks the feature
         * the bubble exists for. Measured on device: the moment this window takes focus, Android
         * stops exposing the app underneath to accessibility entirely. `getWindows()` went from
         * listing Chrome to listing nothing but two system bars and this overlay, so the workflow
         * fell back to a stale snapshot and summarised a toolbar.
         *
         * Nothing is lost by staying unfocusable. A non-focusable window still receives touches, so
         * the panel is fully interactive; it has no text input, so it never needs the IME; and the
         * clipboard is read later by the runner activity, which has focus of its own. The one real
         * cost is that the hardware back key cannot be observed, which is why tapping outside the
         * panel dismisses it.
         */
        const val EXPANDED_FLAGS = COLLAPSED_FLAGS
    }
}
