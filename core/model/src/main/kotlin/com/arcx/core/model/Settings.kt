package com.arcx.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Serializable
data class UserSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
    val historyEnabled: Boolean = true,
    val hasOnboarded: Boolean = false,
    val defaultProviderId: String? = null,
    val bubbleEnabled: Boolean = false,
)
