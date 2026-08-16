package com.arcx.integration.entrypoints.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arcx.core.designsystem.theme.ArcXTheme
import com.arcx.core.designsystem.theme.ThemeMode
import com.arcx.core.model.SidebarSide
import com.arcx.core.model.UserSettings
import com.arcx.core.model.Workflow
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * How long SurfaceFlinger is given to get the strip-less frame onto the display after the view
 * hierarchy has handed it over. One frame at 60Hz, and the display is the only party left that has
 * not confirmed anything — `registerFrameCommitCallback` says the frame reached the compositor, not
 * that it was composited.
 */
private const val COMPOSITE_SETTLE_MS = 16L

/**
 * Ceiling on how long the panel is held back waiting for a frame grab. Nothing should get close to
 * it — a grab is tens of milliseconds — but a callback that never arrives would otherwise leave the
 * strip invisible and the panel unopened, which is the one failure the user cannot recover from.
 */
private const val FRAME_GRAB_TIMEOUT_MS = 500L

/**
 * The sidebar: one overlay window that is a thin strip docked to a screen edge most of the time,
 * and the whole screen while its panel is open.
 *
 * One window rather than two, because the two-window version has to keep their positions and
 * z-order in sync and still ends up toggling the same focus flag. Collapsing and expanding here is
 * a size change plus a flag change on the same [WindowManager.LayoutParams].
 *
 * It replaced a draggable circle. The circle owned its own position, which meant snapping to an
 * edge, correcting itself back on screen after a rotation, and telling a drag from a tap on every
 * touch. None of that exists now: the strip is where the settings say it is, and every gesture that
 * starts on it means the same thing.
 */
internal class BubbleOverlay(
    private val context: Context,
    private val onWorkflow: (Workflow) -> Unit,
    private val onMore: () -> Unit,
    /** Fired the instant the tap resolves — the last moment the app underneath is still readable. */
    private val onExpanded: () -> Unit = {},
    /**
     * Whether a pixel capture is worth blanking the strip and delaying the panel for. Answers no
     * on old platforms, without the accessibility service, and while the platform's capture
     * interval has not elapsed.
     */
    private val canCaptureImage: () -> Boolean = { false },
    /**
     * Grabs a frame of the screen. The callback lands on the main thread once the pixels are off
     * the display, which is the moment the strip may be drawn again — not when the image has
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
     * Owns the collapsed strip's tap and swipe.
     *
     * The gesture has to live on a parent of the ComposeView rather than on a touch listener
     * attached to it. A ComposeView is a ViewGroup, and a ViewGroup only consults its
     * OnTouchListener when no child consumed the event — the AndroidComposeView child always
     * consumes the stream, so `setOnTouchListener` on a ComposeView is silently never called.
     * That is what made the bubble inert: the tap was delivered to the window and then dropped.
     *
     * Kept for the sidebar, not inherited by accident. A swipe cannot be recognised from anything
     * Compose exposes here either — the finger leaves this window within a few pixels of the
     * ACTION_DOWN, and raw MotionEvent coordinates are the only thing that still describes it once
     * it has.
     *
     * While expanded we stop intercepting, so the panel's own Compose gestures work normally.
     */
    private inner class GestureHost(context: Context) : FrameLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !expanded

        override fun onTouchEvent(ev: MotionEvent): Boolean = onStripTouch(ev)

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            // Here rather than next to the layout call: this is the one place the view's real size
            // is known, and it runs again for free on every settings change and every rotation.
            updateGestureExclusion(this)
        }
    }

    private var expanded by mutableStateOf(false)

    /** Mirrors UserSettings.bubbleOpensFullList; see [updateOpensFullList]. */
    private var opensFullList = false
    private var workflows by mutableStateOf(emptyList<Workflow>())

    /**
     * Mirrors UserSettings.theme and UserSettings.dynamicColor; see [updateTheme]. Compose state
     * rather than plain fields, unlike [opensFullList], because these are read during composition
     * and a settings change has to repaint the strip where it stands.
     */
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var dynamicColor by mutableStateOf(true)
    private var popupTransparency by mutableStateOf(DEFAULTS.popupTransparency)

    /**
     * The user's sidebar geometry; see [updateSidebar].
     *
     * Four are Compose state. Three of them are read while drawing the strip, and [verticalPercent]
     * joined them when the panel started opening out of the strip rather than appearing centred:
     * the transition needs to know where the strip was, and by then the window is the whole screen
     * and nothing else on it says. [lengthDp] only ever reaches the window's layout params, which
     * are not Compose's business — making it state as well would invalidate a composition that
     * cannot see the difference.
     */
    private var side by mutableStateOf(DEFAULTS.sidebarSide)
    private var stripWidthDp by mutableStateOf(DEFAULTS.sidebarWidthDp)
    private var opacity by mutableStateOf(DEFAULTS.sidebarOpacity)
    private var verticalPercent by mutableStateOf(DEFAULTS.sidebarVerticalPercent)
    private var lengthDp = DEFAULTS.sidebarLengthDp

    /** True from the tap until the panel actually opens, while a frame grab is in flight. */
    private var expanding = false

    private var downRawX = 0f
    private var downRawY = 0f

    /** Set once the gesture has committed to opening, so ACTION_UP does not open it a second time. */
    private var swiped = false

    /** Set once the gesture has moved far enough to no longer be a tap. */
    private var strayed = false

    private val params = WindowManager.LayoutParams(
        // Sized explicitly rather than WRAP_CONTENT: the window is deliberately wider than the
        // strip drawn inside it, so there is nothing to wrap to.
        touchWidthPx(),
        lengthPx(),
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
     * The user's theme, pushed in from the service like the workflows are.
     *
     * The overlay cannot read it for itself: there is no Activity and no ViewModel here, and the
     * settings flow lives on the service's scope. Without this the overlay drew whatever the system
     * happened to be doing, so LIGHT/DARK and the dynamic-colour switch were ignored by the one
     * surface the user sees most often.
     */
    fun updateTheme(mode: ThemeMode, dynamic: Boolean, transparency: Float) {
        themeMode = mode
        dynamicColor = dynamic
        popupTransparency = transparency
    }

    /**
     * The strip's edge, position and size, pushed in from the service for the same reason the theme
     * is: there is no Activity here and the settings flow lives on the service's scope.
     *
     * All five arrive together because four of them end in the same WindowManager layout pass, and
     * splitting them would mean up to four passes for one settings screen edit.
     */
    fun updateSidebar(
        side: SidebarSide,
        verticalPercent: Float,
        lengthDp: Int,
        widthDp: Int,
        opacity: Float,
    ) {
        this.side = side
        this.verticalPercent = verticalPercent
        this.lengthDp = lengthDp
        this.stripWidthDp = widthDp
        this.opacity = opacity
        // Only while collapsed. Expanded, the window is the whole screen and these values describe
        // nothing that is on it; collapse() reads them again on the way back.
        if (view != null && !expanded) applyCollapsedGeometry()
    }

    /**
     * Adds the sidebar to the window manager. Returns false if the overlay permission was revoked
     * between the caller's check and this call, which the system reports by throwing.
     */
    fun show(): Boolean {
        if (view != null) return true

        // Safe before the view exists: applyLayout is a no-op until there is a window to update, so
        // this only fills in the params that addView is about to be handed.
        applyCollapsedGeometry()

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
                // The same theme the two Activities use, from a Service context. That is only safe
                // because ArcXTheme's status-bar SideEffect treats a non-Activity view context as
                // "no status bar to tint" rather than casting blindly — an overlay window has none.
                ArcXTheme(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    popupTransparency = popupTransparency,
                ) {
                    // One or the other, never both — the strip is not drawn behind the panel.
                    //
                    // Drawing it there is the tempting other half of the idea the panel's
                    // transition is built on: the card comes out of something that is still
                    // visible. It cannot be made to hold still, though. The strip's place is a
                    // setting in display coordinates, and expanded this composition fills a
                    // window that starts 85px down the display — see [StripAnchor], which
                    // measured the same disagreement — so a strip drawn back in would sit 85px
                    // below where it had been standing. A jump is worse than an absence, and the
                    // scrim covers that stretch of the edge anyway.
                    if (expanded) {
                        BubblePanel(
                            workflows = workflows,
                            // Where the strip is, so the panel can come out of it. Both are read
                            // during composition, which is why verticalPercent is Compose state.
                            side = side,
                            verticalPercent = verticalPercent,
                            // Collapsed on the spot, not after an exit transition. This is the
                            // fastest path in the product — the whole reason for tapping the strip
                            // is what happens next — and the runner is about to cover this window
                            // anyway, so a retraction here would cost latency to play behind it.
                            onWorkflow = { workflow ->
                                collapse()
                                onWorkflow(workflow)
                            },
                            onMore = {
                                collapse()
                                onMore()
                            },
                            // Dismissal is the one exit the user actually watches, so this arrives
                            // only once the card has finished retracting; see [BubblePanel].
                            onDismiss = ::collapse,
                        )
                    } else {
                        SidebarStrip(side = side, widthDp = stripWidthDp, opacity = opacity)
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
            // MATCH_PARENT, not WRAP_CONTENT: the drawing has to be able to see the whole touch
            // window so it can align the strip to the docked edge of it. Wrapping would shrink the
            // composition to the strip and leave the extra touch width with nothing to align to.
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
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
     * The two ways in: a tap, and a swipe inward off the edge. Both open the same panel.
     *
     * Both, rather than one, because they are the two things a user tries. A strip against the edge
     * looks like something to pull, and it is also just a button.
     *
     * Measured in raw (screen) coordinates. Local MotionEvent x/y are useless here: an inward swipe
     * is by definition leaving this window, and past its edge the local coordinate stops tracking
     * the finger. Raw coordinates are screen-absolute and keep describing the gesture after it has
     * left.
     */
    private fun onStripTouch(event: MotionEvent): Boolean {
        // While expanded the panel is a normal Compose surface and owns its own gestures.
        if (expanded) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                swiped = false
                strayed = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!swiped && !strayed) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    // "Inward" is away from the docked edge, so its sign depends on which edge that
                    // is: rightward off the left edge, leftward off the right one.
                    val inward = if (side == SidebarSide.LEFT) dx else -dx
                    when {
                        // Slop, not a longer distance of its own. The panel is the only outcome
                        // either gesture has, so opening early costs nothing, and holding out for a
                        // longer pull would only make the strip feel unresponsive.
                        inward > touchSlop -> {
                            swiped = true
                            expand()
                        }
                        // Anything else past the slop was a different gesture — a drag along the
                        // strip, or a push back out at the edge. Once it has strayed the ACTION_UP
                        // is no longer a tap, or every abandoned swipe would open the panel anyway.
                        hypot(dx, dy) > touchSlop -> strayed = true
                    }
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                if (!swiped && !strayed) expand()
                true
            }

            MotionEvent.ACTION_CANCEL -> true

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
     * Blanks the strip, has a frame of the screen taken, then opens the panel. Returns false when
     * there is nothing to take, in which case the caller opens the panel with no delay at all.
     *
     * This is the only moment in ArcX's life when a usable frame exists. `takeScreenshot` captures
     * the composited display, so unlike an accessibility tree walk it cannot be asked to leave ArcX
     * out — whatever is drawn is in the picture. A moment later the panel covers the very screen the
     * workflow is about to be asked about, and right now the strip is sitting on top of it. So the
     * content is hidden for the length of the grab and the panel is held back until it is over.
     *
     * The strip is a smaller thing to hide than the circle was, but it is hidden the same way and
     * for the same reason — a 6dp line down the edge of a screenshot is still ArcX in a picture the
     * user did not ask ArcX to be in.
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
        // strip in the picture or a guess that makes the panel feel slow.
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
        // Back to a plain top-left anchor. The panel is the whole screen, so an edge gravity would
        // only change what x and y mean for no gain.
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        params.flags = EXPANDED_FLAGS
        applyLayout(corner = CornerMove.YES)
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        params.flags = COLLAPSED_FLAGS
        applyCollapsedGeometry(corner = CornerMove.YES)
    }

    /**
     * Puts the window back where the settings say the strip belongs.
     *
     * Docking with gravity rather than a computed x is what keeps it welded to the edge. The
     * draggable bubble had to place itself at `screenWidth - width` and then correct itself in
     * onLayout, because `params.x` is measured from the window's parent frame while [screenSize]
     * describes the whole display — on a device with a cutout those differ, and the right-edge
     * position was off by an inset that only exists in some rotations. `Gravity.END` is the same
     * intent stated in a way the window manager resolves itself, so there is nothing left to
     * correct and no rotation callback to miss.
     */
    private fun applyCollapsedGeometry(corner: CornerMove = CornerMove.NO) {
        val (_, screenHeight) = screenSize()
        val length = lengthPx()
        params.width = touchWidthPx()
        params.height = length
        params.gravity = Gravity.TOP or
            if (side == SidebarSide.LEFT) Gravity.START else Gravity.END
        params.x = 0
        // The setting names the strip's centre — the part of it a user aims at — and the window
        // wants its top edge. Clamped so a percent near 0 or 1 shortens nothing and hides nothing.
        params.y = (screenHeight * verticalPercent - length / 2f).roundToInt()
            .coerceIn(0, (screenHeight - length).coerceAtLeast(0))
        applyLayout(corner)
    }

    /**
     * Asks the platform to keep its own edge gestures off the sidebar.
     *
     * On a gesture-navigation device the system owns a strip down both screen edges for the back
     * gesture, and it owns it first: a swipe that starts there is consumed by the system and never
     * reaches this window. That is precisely the swipe the sidebar is listening for, so without an
     * exclusion the inward swipe would be a back press on every gesture-nav device.
     *
     * The rect is the whole window rather than only the drawn strip. What has to be excluded is
     * where a finger may come down, and that is the full 48dp touch width — for exactly the reason
     * the window is that wide in the first place.
     *
     * The platform grants **at most 200dp of exclusion per screen edge** and silently ignores the
     * rest, which is what caps `UserSettings.SIDEBAR_MAX_LENGTH_DP` at 200: a longer strip would
     * have a stretch that the back gesture still wins.
     */
    private fun updateGestureExclusion(v: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        // Nothing to protect while expanded: the window covers the screen, the panel is dismissed
        // by a tap rather than a swipe, and asking to exclude all of it would be a request the
        // platform mostly refuses anyway.
        v.systemGestureExclusionRects =
            if (expanded) emptyList() else listOf(Rect(0, 0, v.width, v.height))
    }

    /**
     * Whether this layout change moves the window's top-left corner. See [applyLayout].
     */
    private enum class CornerMove { YES, NO }

    /**
     * Hands [params] to the window manager, and — when the window's corner is moving — keeps the
     * platform from sliding it there.
     *
     * **The strip used to fly across the screen, and this is why.** WindowManagerService plays a
     * translate animation on any visible window whose frame origin moves while its size changes
     * too (`WindowState.handleWindowMovedIfNeeded`, gated on `hasContentChanged`). Expanding and
     * collapsing is exactly that: the frame goes from the strip's dock to the whole screen and
     * back. Measured on device at `window_animation_scale=10`: on collapse the strip was drawn at
     * the *expanded* window's origin — 135x338 at (0,85) — and slid the full diagonal to its dock
     * at (945,673) over 3.0s, which is ~300ms at 1x. Expanding did the mirror image, taking the
     * strip up to the top corner with it. Nothing in the strip's drawing moved; the window did.
     *
     * No public flag turns that off — `PRIVATE_FLAG_NO_MOVE_ANIMATION` is `@hide` — but the
     * platform skips the animation for a window whose surface is already hidden. So the window is
     * hidden for the single frame the move lands on and shown again on the next one. `View.post`
     * is what makes that exactly one frame: the traversal that carries the move runs behind a
     * sync barrier, and an ordinary posted message is held until it is over.
     *
     * `INVISIBLE` rather than `GONE` on purpose — GONE is the state that also tears the surface
     * down, and getting a new one back is the slow, visible path `grabFrameThenOpen` avoids for
     * the same reason.
     *
     * Only for the two transitions the user is watching. A settings edit also moves the corner,
     * but it arrives while the user is on the Settings screen with the strip behind it, and
     * blinking the window on every tick of a slider would be the worse trade.
     */
    private fun applyLayout(corner: CornerMove = CornerMove.NO) {
        val attached = view ?: return
        if (corner == CornerMove.YES) attached.visibility = View.INVISIBLE
        // Throws once the view has been removed, which races with a settings change in flight.
        runCatching { windowManager.updateViewLayout(attached, params) }
        if (corner == CornerMove.YES) attached.post { attached.visibility = View.VISIBLE }
    }

    private fun touchWidthPx(): Int = dpToPx(SidebarTouchWidth.value)

    private fun lengthPx(): Int = dpToPx(lengthDp.toFloat())

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).roundToInt()

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
         * What the strip is drawn with until the first settings emission arrives — which is a real
         * moment, not a theoretical one: the service adds the window and only then starts
         * collecting. Taken from the model so the defaults are stated in exactly one place.
         */
        val DEFAULTS = UserSettings()

        /**
         * NOT_TOUCH_MODAL lets touches outside the strip reach the app behind it, and
         * LAYOUT_NO_LIMITS lets it sit over the status bar and the gesture area rather than being
         * pushed inside the safe insets — an edge strip that stops short of both ends of the edge
         * is not docked to anything.
         */
        const val COLLAPSED_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // LAYOUT_IN_SCREEN is what makes the vertical setting mean what it says.
                //
                // params.y is measured from the window's *parent* frame, and for an overlay that
                // frame is not the display: dumpsys reported parent=[0,85][1080,2340] on this
                // device, inset by the status bar. So a strip asked for the middle of a 2340px
                // screen landed 85px low, and 1.0 would have gone off the bottom by the same
                // amount. NO_LIMITS does not fix it — it lets the window *extend* past the bars,
                // not lay out against them — and neither does the modern `fitInsetsTypes = 0`,
                // which was tried on device and left the parent frame exactly where it was.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        /**
         * Identical to collapsed, and NOT_FOCUSABLE staying on is the important part.
         *
         * Dropping it — the obvious thing to do when a panel appears — silently breaks the feature
         * the sidebar exists for. Measured on device: the moment this window takes focus, Android
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
