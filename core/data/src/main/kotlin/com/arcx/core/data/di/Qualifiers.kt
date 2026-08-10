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

/**
 * Directory holding run screenshots, inside `filesDir`. Injected as a directory rather than
 * resolved from a Context inside the store, so the store is plain file IO that a test can point
 * at a temporary folder — and so the one decision that matters, *where* these bytes live, is
 * made in one visible place.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScreenshotDirectory
