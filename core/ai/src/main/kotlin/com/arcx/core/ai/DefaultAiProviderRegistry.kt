package com.arcx.core.ai

import com.arcx.core.domain.ai.AiProvider
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.model.ProviderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAiProviderRegistry @Inject constructor(
    private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
) : AiProviderRegistry {

    override fun get(type: ProviderType): AiProvider? = providers[type]

    override fun supportedTypes(): Set<ProviderType> = providers.keys
}
