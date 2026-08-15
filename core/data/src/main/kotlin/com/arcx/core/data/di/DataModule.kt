package com.arcx.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.arcx.core.data.clipboard.ClipboardAccessImpl
import com.arcx.core.data.database.ArcxDatabase
import com.arcx.core.data.database.MIGRATION_1_2
import com.arcx.core.data.database.MIGRATION_2_3
import com.arcx.core.data.database.ProviderDao
import com.arcx.core.data.database.RunDao
import com.arcx.core.data.database.WorkflowDao
import com.arcx.core.data.repository.HistoryRepositoryImpl
import com.arcx.core.data.repository.ProviderRepositoryImpl
import com.arcx.core.data.repository.SettingsRepositoryImpl
import com.arcx.core.data.repository.WorkflowRepositoryImpl
import com.arcx.core.data.screenshot.ScreenshotStoreImpl
import com.arcx.core.domain.capture.ClipboardAccess
import com.arcx.core.domain.capture.ScreenshotStore
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

private val Context.settingsPreferences by preferencesDataStore(name = "arcx_settings")
private val Context.keyVaultPreferences by preferencesDataStore(name = "arcx_keys")

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ArcxDatabase =
        Room.databaseBuilder(context, ArcxDatabase::class.java, ArcxDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun workflowDao(database: ArcxDatabase): WorkflowDao = database.workflowDao()

    @Provides
    fun providerDao(database: ArcxDatabase): ProviderDao = database.providerDao()

    @Provides
    fun runDao(database: ArcxDatabase): RunDao = database.runDao()

    @Provides
    @Singleton
    @SettingsDataStore
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsPreferences

    @Provides
    @Singleton
    @KeyVaultDataStore
    fun keyVaultDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.keyVaultPreferences

    /** `filesDir`, so screen captures are readable by this app alone and excluded from backup. */
    @Provides
    @Singleton
    @ScreenshotDirectory
    fun screenshotDirectory(@ApplicationContext context: Context): File =
        File(context.filesDir, "screenshots")
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun workflowRepository(impl: WorkflowRepositoryImpl): WorkflowRepository

    @Binds
    @Singleton
    abstract fun providerRepository(impl: ProviderRepositoryImpl): ProviderRepository

    @Binds
    @Singleton
    abstract fun historyRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun screenshotStore(impl: ScreenshotStoreImpl): ScreenshotStore

    @Binds
    abstract fun clipboardAccess(impl: ClipboardAccessImpl): ClipboardAccess
}
