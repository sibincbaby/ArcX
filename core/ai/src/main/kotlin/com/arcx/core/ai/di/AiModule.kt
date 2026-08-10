package com.arcx.core.ai.di

import com.arcx.core.ai.DefaultAiProviderRegistry
import com.arcx.core.ai.gemini.GeminiProvider
import com.arcx.core.domain.ai.AiProvider
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.model.ProviderType
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Adding a provider is one `@Binds @IntoMap` entry here plus its implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @IntoMap
    @ProviderKey(ProviderType.GEMINI)
    abstract fun bindGeminiProvider(provider: GeminiProvider): AiProvider

    @Binds
    abstract fun bindRegistry(registry: DefaultAiProviderRegistry): AiProviderRegistry
}
