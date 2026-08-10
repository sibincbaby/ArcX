package com.arcx.core.ai.di

import com.arcx.core.model.ProviderType
import dagger.MapKey

/** Map key for the provider multibinding: one entry per implemented [ProviderType]. */
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class ProviderKey(val value: ProviderType)
