package com.arcx.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arcx.core.data.di.SettingsDataStore
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.ScreenshotRetention
import com.arcx.core.model.SidebarSide
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

internal class SettingsRepositoryImpl @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        // A corrupt or unreadable preferences file must not take the whole app down with it;
        // falling back to defaults loses a few toggles, which the user can set again.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { it.toUserSettings() }

    override suspend fun current(): UserSettings = settings.first()

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toUserSettings())
            prefs[Keys.THEME] = updated.theme.name
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.HISTORY_ENABLED] = updated.historyEnabled
            prefs[Keys.HAS_ONBOARDED] = updated.hasOnboarded
            prefs[Keys.BUBBLE_ENABLED] = updated.bubbleEnabled
            prefs[Keys.BUBBLE_OPENS_FULL_LIST] = updated.bubbleOpensFullList
            prefs[Keys.COMPACT_PICKER] = updated.compactPicker
            prefs[Keys.SCREENSHOT_RETENTION] = updated.screenshotRetention.name
            prefs[Keys.ACCESSIBILITY_BUTTON_OFFERED] = updated.accessibilityButtonOffered
            // The sidebar's four numbers are clamped on the way in as well as on the way out.
            // Clamping only on read would let a slider store 400dp and read back 200, so the next
            // thing to write the settings would round-trip a value the user never sees.
            prefs[Keys.SIDEBAR_SIDE] = updated.sidebarSide.name
            prefs[Keys.SIDEBAR_VERTICAL_PERCENT] = updated.sidebarVerticalPercent.coerceIn(0f, 1f)
            prefs[Keys.SIDEBAR_LENGTH_DP] = updated.sidebarLengthDp.coerceLength()
            prefs[Keys.SIDEBAR_WIDTH_DP] = updated.sidebarWidthDp.coerceWidth()
            prefs[Keys.SIDEBAR_OPACITY] = updated.sidebarOpacity.coerceIn(0f, 1f)
            prefs[Keys.POPUP_TRANSPARENCY] = updated.popupTransparency.coerceTransparency()
            val defaultProvider = updated.defaultProviderId
            if (defaultProvider == null) prefs.remove(Keys.DEFAULT_PROVIDER_ID)
            else prefs[Keys.DEFAULT_PROVIDER_ID] = defaultProvider
        }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            theme = this[Keys.THEME]?.let { runCatching { enumValueOf<ThemePreference>(it) }.getOrNull() }
                ?: defaults.theme,
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            historyEnabled = this[Keys.HISTORY_ENABLED] ?: defaults.historyEnabled,
            hasOnboarded = this[Keys.HAS_ONBOARDED] ?: defaults.hasOnboarded,
            defaultProviderId = this[Keys.DEFAULT_PROVIDER_ID],
            bubbleEnabled = this[Keys.BUBBLE_ENABLED] ?: defaults.bubbleEnabled,
            bubbleOpensFullList = this[Keys.BUBBLE_OPENS_FULL_LIST]
                ?: defaults.bubbleOpensFullList,
            compactPicker = this[Keys.COMPACT_PICKER] ?: defaults.compactPicker,
            // An unreadable value falls back to the default rather than to FOREVER: the safe
            // reading of a broken preference is "expire these", not "keep them all".
            screenshotRetention = this[Keys.SCREENSHOT_RETENTION]
                ?.let { runCatching { enumValueOf<ScreenshotRetention>(it) }.getOrNull() }
                ?: defaults.screenshotRetention,
            accessibilityButtonOffered = this[Keys.ACCESSIBILITY_BUTTON_OFFERED]
                ?: defaults.accessibilityButtonOffered,
            sidebarSide = this[Keys.SIDEBAR_SIDE]
                ?.let { runCatching { enumValueOf<SidebarSide>(it) }.getOrNull() }
                ?: defaults.sidebarSide,
            sidebarVerticalPercent = this[Keys.SIDEBAR_VERTICAL_PERCENT]?.coerceIn(0f, 1f)
                ?: defaults.sidebarVerticalPercent,
            // Clamped on read too, because a preferences file can also arrive from a restore or
            // from a hand edit, and an over-long strip is a gesture the user cannot make.
            sidebarLengthDp = this[Keys.SIDEBAR_LENGTH_DP]?.coerceLength()
                ?: defaults.sidebarLengthDp,
            sidebarWidthDp = this[Keys.SIDEBAR_WIDTH_DP]?.coerceWidth() ?: defaults.sidebarWidthDp,
            sidebarOpacity = this[Keys.SIDEBAR_OPACITY]?.coerceIn(0f, 1f) ?: defaults.sidebarOpacity,
            popupTransparency = this[Keys.POPUP_TRANSPARENCY]?.coerceTransparency()
                ?: defaults.popupTransparency,
        )
    }

    /**
     * Clamped on both sides of the store, like the sidebar's numbers above and for a sharper
     * reason: the cap is a legibility floor, so a restored or hand-edited file must not be able to
     * hand the panel an alpha its own settings screen refuses to offer.
     */
    private fun Float.coerceTransparency(): Float =
        coerceIn(0f, UserSettings.POPUP_MAX_TRANSPARENCY)

    private fun Int.coerceLength(): Int =
        coerceIn(UserSettings.SIDEBAR_MIN_LENGTH_DP, UserSettings.SIDEBAR_MAX_LENGTH_DP)

    private fun Int.coerceWidth(): Int =
        coerceIn(UserSettings.SIDEBAR_MIN_WIDTH_DP, UserSettings.SIDEBAR_MAX_WIDTH_DP)

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")
        val DEFAULT_PROVIDER_ID = stringPreferencesKey("default_provider_id")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_OPENS_FULL_LIST = booleanPreferencesKey("bubble_opens_full_list")
        val COMPACT_PICKER = booleanPreferencesKey("compact_picker")
        val SCREENSHOT_RETENTION = stringPreferencesKey("screenshot_retention")
        val ACCESSIBILITY_BUTTON_OFFERED = booleanPreferencesKey("accessibility_button_offered")
        val SIDEBAR_SIDE = stringPreferencesKey("sidebar_side")
        val SIDEBAR_VERTICAL_PERCENT = floatPreferencesKey("sidebar_vertical_percent")
        val SIDEBAR_LENGTH_DP = intPreferencesKey("sidebar_length_dp")
        val SIDEBAR_WIDTH_DP = intPreferencesKey("sidebar_width_dp")
        val SIDEBAR_OPACITY = floatPreferencesKey("sidebar_opacity")
        val POPUP_TRANSPARENCY = floatPreferencesKey("popup_transparency")
    }
}
