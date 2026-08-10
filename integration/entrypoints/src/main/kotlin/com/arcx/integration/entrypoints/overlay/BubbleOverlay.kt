package com.arcx.integration.entrypoints.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
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
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val host = OverlayViewHost()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var view: ComposeView? = null
    private var snapAnimator: ValueAnimator? = null

    private var expanded by mutableStateOf(false)
    private var workflows by mutableStateOf(emptyList<Workflow>())

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
            setOnTouchListener(::onBubbleTouch)
            // Only reachable while expanded, since the collapsed window is not focusable.
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (expanded && keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    collapse()
                    true
                } else {
                    false
                }
            }
        }

        host.create()
        return runCatching {
            windowManager.addView(composeView, params)
            view = composeView
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

    private fun expand() {
        collapsedX = params.x
        collapsedY = params.y
        expanded = true
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        // Dropping NOT_FOCUSABLE is what makes the panel interactive at all, and it is also what
        // lets a workflow read the clipboard: since Android 10 only the focused window may.
        params.flags = EXPANDED_FLAGS
        applyLayout()
        view?.requestFocus()
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.x = collapsedX
        params.y = collapsedY
        // Focusable again would steal every key press in the foreground app.
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

        const val EXPANDED_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
