package com.arcx.integration.entrypoints.di

import android.content.Context
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Manual access to the graph for components Hilt cannot inject into.
 *
 * Two of them here. Glance builds its content inside a `GlanceAppWidget`, a plain object created by
 * the receiver rather than an Android entry point, so there are no fields for `@AndroidEntryPoint`
 * to fill. And `@AndroidEntryPoint` on a BroadcastReceiver requires calling `super.onReceive`,
 * which Kotlin refuses to compile because the superclass it can see declares that method abstract —
 * the generated Hilt base only replaces it later, during bytecode transformation.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EntrypointsEntryPoint {
    fun workflowRepository(): WorkflowRepository
    fun settingsRepository(): SettingsRepository
}

internal fun Context.entrypointsGraph(): EntrypointsEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, EntrypointsEntryPoint::class.java)
