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
    val screenshotRetention: ScreenshotRetention = ScreenshotRetention.MONTH,
)
