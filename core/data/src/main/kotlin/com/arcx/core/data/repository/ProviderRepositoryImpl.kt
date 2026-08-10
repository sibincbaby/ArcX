package com.arcx.core.data.repository

import com.arcx.core.common.time.TimeSource
import com.arcx.core.data.database.ProviderDao
import com.arcx.core.data.mapper.toEntity
import com.arcx.core.data.mapper.toModel
import com.arcx.core.data.security.KeystoreVault
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class ProviderRepositoryImpl @Inject constructor(
    private val dao: ProviderDao,
    private val vault: KeystoreVault,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : ProviderRepository {

    override fun observeAll(): Flow<List<ProviderConfig>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun get(id: String): ProviderConfig? = dao.get(id)?.toModel()

    override suspend fun resolve(providerId: String?): ProviderConfig? {
        providerId?.let { id -> get(id)?.let { return it } }
        return settings.current().defaultProviderId?.let { get(it) }
    }

    /**
     * A null [apiKey] means "leave the stored key alone" — the settings screen re-saves a config
     * every time a label or model changes, and must not wipe the key by omitting it.
     */
    override suspend fun upsert(config: ProviderConfig, apiKey: String?) {
        val stamped = config.copy(createdAt = config.createdAt.takeIf { it > 0L } ?: time.nowMillis())
        dao.upsert(stamped.toEntity())
        when {
            apiKey == null -> Unit
            apiKey.isBlank() -> vault.remove(config.id)
            else -> vault.put(config.id, apiKey)
        }
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
        vault.remove(id)
    }

    override suspend fun apiKey(id: String): String? = vault.get(id)

    override suspend fun hasKey(id: String): Boolean = vault.has(id)
}
