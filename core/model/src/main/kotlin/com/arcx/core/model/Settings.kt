package com.arcx.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreference { SYSTEM, LIGHT, DARK }

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
     * What a tap on the bubble opens. The overlay panel by default, because it is the only one of
     * the two that leaves the user's app in front — see BubbleOverlay's window flags. The full
     * list is the same sheet every other entry point shows, search included, at the cost of
     * covering the screen a workflow might have wanted to read.
     */
    val bubbleOpensFullList: Boolean = false,
    /**
     * Whether the picker every non-bubble entry point shows drops its search box for the shorter
     * list the bubble's panel uses. Cosmetic only — nothing about a focused Activity forces the
     * search box, it is simply the one thing the bubble's window can never have.
     */
    val compactPicker: Boolean = false,
    val screenshotRetention: ScreenshotRetention = ScreenshotRetention.MONTH,
)
