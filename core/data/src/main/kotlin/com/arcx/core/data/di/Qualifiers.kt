package com.arcx.core.data.di

import javax.inject.Qualifier

/** Preferences file holding [com.arcx.core.model.UserSettings]. Never holds secrets. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore

/**
 * Preferences file holding Keystore-encrypted API keys. Kept in its own file so that clearing,
 * exporting or backing up settings can never touch the vault.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KeyVaultDataStore
