package com.arcx.integration.entrypoints.di

import com.arcx.core.domain.capture.ScreenContextProvider
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.integration.entrypoints.ArcxEntrypoints
import com.arcx.integration.entrypoints.accessibility.AccessibilityScreenContextProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The only binding this module owes the rest of the app. Everything else it exposes — permission
 * state, settings intents, shortcut publishing — is reachable without the graph, because the
 * screens that need it are handed a Context anyway.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class EntrypointsModule {

    @Binds
    @Singleton
    abstract fun screenContextProvider(
        impl: AccessibilityScreenContextProvider,
    ): ScreenContextProvider

    /** Lets :feature:settings show and request these permissions without depending on this module. */
    @Binds
    @Singleton
    abstract fun systemSurfaces(impl: ArcxEntrypoints): SystemSurfaces
}
