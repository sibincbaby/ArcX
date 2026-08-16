package com.arcx.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreference { SYSTEM, LIGHT, DARK }

/**
 * Which screen edge the sidebar docks to.
 *
 * LEFT is the default because the right edge is already spoken for on a lot of hardware — Samsung
 * ships its Edge panel handle there and it is on out of the box — and two handles on the same edge
 * compete for the same inward swipe.
 */
@Serializable
enum class SidebarSide { LEFT, RIGHT }

/**
 * How long a screenshot kept for history survives. Screenshots can contain far more than the
 * user meant to act on, so they expire on their own rather than accumulating forever.
 */
@Serializable
enum class ScreenshotRetention(val days: Int?) {
    WEEK(7),
    MONTH(30),
    FOREVER(null),
}

@Serializable
data class UserSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val historyEnabled: Boolean = true,
    val hasOnboarded: Boolean = false,
    val defaultProviderId: String? = null,
    val bubbleEnabled: Boolean = false,
    /**
     * What a tap on the sidebar opens. The overlay panel by default, because it is the only one of
     * the two that leaves the user's app in front — see BubbleOverlay's window flags. The full
     * list is the same sheet every other entry point shows, search included, at the cost of
     * covering the screen a workflow might have wanted to read.
     */
    val bubbleOpensFullList: Boolean = false,
    /** Which edge the sidebar is docked to; see [SidebarSide]. */
    val sidebarSide: SidebarSide = SidebarSide.LEFT,
    /**
     * Where the middle of the strip sits down the screen — 0 at the top, 1 at the bottom.
     *
     * A fraction rather than a dp offset so the same setting means the same place after a rotation
     * or on a second display, and so it can never name a position that is off the screen.
     */
    val sidebarVerticalPercent: Float = 0.5f,
    /** How long the strip is, between [SIDEBAR_MIN_LENGTH_DP] and [SIDEBAR_MAX_LENGTH_DP]. */
    val sidebarLengthDp: Int = 120,
    /**
     * How wide the strip is *drawn*, between [SIDEBAR_MIN_WIDTH_DP] and [SIDEBAR_MAX_WIDTH_DP].
     *
     * Not how wide it is to touch. The overlay window stays a fixed 48dp whatever this says, so a
     * 6dp hairline still catches a thumb — see `SidebarTouchWidth` in :integration:entrypoints.
     */
    val sidebarWidthDp: Int = 6,
    /**
     * Applied to the drawn strip alone, so it can sit quietly over another app without disappearing
     * from under the finger. Neither the overlay window nor the region it takes touches in is
     * faded with it.
     */
    val sidebarOpacity: Float = 0.9f,
    /**
     * How much of the app behind shows through ArcX's floating surfaces — the workflow panel, the
     * compact picker, and the popup and sheet an answer comes back in. 0 is a solid card.
     *
     * Stated as transparency rather than as the alpha it becomes, because that is the direction
     * the user is thinking in when they reach for it: they want to see more of what is behind, not
     * less of ArcX. The drawing code takes `1 - this`.
     *
     * Capped at [POPUP_MAX_TRANSPARENCY]; the default is the most transparent value that still
     * clears the contrast floor on every backdrop.
     */
    val popupTransparency: Float = 0.18f,
    /**
     * Whether the picker every non-bubble entry point shows drops its search box for the shorter
     * list the bubble's panel uses. Cosmetic only — nothing about a focused Activity forces the
     * search box, it is simply the one thing the bubble's window can never have.
     */
    val compactPicker: Boolean = false,
    val screenshotRetention: ScreenshotRetention = ScreenshotRetention.MONTH,
    /**
     * Whether ArcX offers itself as a target for the accessibility button and the volume-key
     * shortcut.
     *
     * Off by default, and a setting rather than a manifest flag, because appearing in that list is
     * not something an app should decide for the user — it is a system control they may have
     * assigned to something they depend on. Turning it on only makes ArcX *offerable*; the
     * assignment is still theirs to make in system Settings.
     */
    val accessibilityButtonOffered: Boolean = false,
) {
    companion object {

        /** Short enough to still read as a handle rather than as a bar the user has to aim past. */
        const val SIDEBAR_MIN_LENGTH_DP = 48

        /**
         * The hard cap, and it is not a taste judgement.
         *
         * The sidebar asks the platform to keep the system back gesture off itself with
         * `setSystemGestureExclusionRects`, and the platform grants **at most 200dp of exclusion
         * per screen edge** — anything past that is silently ignored. A longer strip would have a
         * section that the back gesture still wins on a gesture-navigation device, which reads to
         * the user as the sidebar working in some places and not others.
         */
        const val SIDEBAR_MAX_LENGTH_DP = 200

        /** Below a pixel or two the strip stops being visible at all on a light background. */
        const val SIDEBAR_MIN_WIDTH_DP = 2

        /**
         * Past this the strip stops being an edge and starts being a bar over the user's app. It is
         * well under the 48dp touch window, which is the point: the width the user picks is a
         * drawing decision, never a reachability one.
         */
        const val SIDEBAR_MAX_WIDTH_DP = 16

        /**
         * The cap on [popupTransparency], and like [SIDEBAR_MAX_LENGTH_DP] it is not a taste
         * judgement — it is where the text stops being readable.
         *
         * A popup floats over whatever the user was looking at, so the honest worst case is the
         * highest-contrast backdrop against the palest text: dark theme, a white document behind,
         * and the subtitle under the brightest part of the panel's sheen. Measured against the
         * colours this device actually resolves, that pair reads 4.8:1 at the default, 3.2:1 here,
         * and 2.9:1 at 0.45 — so this is about the last value that still clears the 3:1 floor
         * Android's own contrast checks use. Past it the setting would be offering the user a panel
         * they cannot read on a backdrop they did not choose.
         */
        const val POPUP_MAX_TRANSPARENCY = 0.40f
    }
}
