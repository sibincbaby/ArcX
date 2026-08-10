package com.arcx.core.domain.ai

import com.arcx.core.model.AiChunk
import com.arcx.core.model.AiRequest
import com.arcx.core.model.ModelInfo
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ProviderType
import kotlinx.coroutines.flow.Flow

/**
 * One AI backend. Implementations live in :core:ai; the domain only ever sees this.
 * Adding OpenAI/Anthropic/Ollama later means a new implementation and a registry entry —
 * nothing above this line changes.
 */
interface AiProvider {
    val type: ProviderType

    /** Populates the model dropdown in the workflow builder. */
    suspend fun listModels(config: ProviderConfig, apiKey: String?): List<ModelInfo>

    /**
     * Streams a completion. Implementations must map every failure to an
     * [com.arcx.core.model.AiError] rather than letting transport exceptions escape.
     */
    fun generate(request: AiRequest, config: ProviderConfig, apiKey: String?): Flow<AiChunk>
}

interface AiProviderRegistry {
    operator fun get(type: ProviderType): AiProvider?

    /** Provider types that have a working implementation right now. */
    fun supportedTypes(): Set<ProviderType>
}
