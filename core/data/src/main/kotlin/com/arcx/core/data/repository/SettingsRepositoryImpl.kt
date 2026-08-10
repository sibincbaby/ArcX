package com.arcx.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arcx.core.data.di.SettingsDataStore
import com.arcx.core.domain.repository.SettingsRepository
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
        )
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")
        val DEFAULT_PROVIDER_ID = stringPreferencesKey("default_provider_id")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
    }
}
