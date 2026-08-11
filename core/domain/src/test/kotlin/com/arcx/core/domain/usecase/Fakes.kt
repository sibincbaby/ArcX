package com.arcx.core.domain.usecase

import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.ai.AiProvider
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.domain.capture.ClipboardAccess
import com.arcx.core.domain.capture.ScreenContextProvider
import com.arcx.core.domain.capture.ScreenshotStore
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.AiChunk
import com.arcx.core.model.AiRequest
import com.arcx.core.model.ModelInfo
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ProviderType
import com.arcx.core.model.RunRecord
import com.arcx.core.model.UserSettings
import com.arcx.core.model.Workflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeWorkflowRepository(
    initial: List<Workflow> = emptyList(),
) : WorkflowRepository {
    val stored = MutableStateFlow(initial.associateBy { it.id })
    var deleted: String? = null
    val favorites = mutableMapOf<String, Boolean>()
    val pinned = mutableMapOf<String, Boolean>()
    var seeded = false

    override fun observeAll(): Flow<List<Workflow>> = stored.map { it.values.toList() }
    override fun observeFavorites(): Flow<List<Workflow>> = stored.map { it.values.filter(Workflow::isFavorite) }
    override fun observePinned(): Flow<List<Workflow>> = stored.map { it.values.filter(Workflow::isPinned) }
    override fun observeRecent(limit: Int): Flow<List<Workflow>> = stored.map { it.values.take(limit) }
    override suspend fun get(id: String): Workflow? = stored.value[id]
    override suspend fun upsert(workflow: Workflow) {
        stored.value = stored.value + (workflow.id to workflow)
    }

    override suspend fun delete(id: String) {
        deleted = id
        stored.value = stored.value - id
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        favorites[id] = favorite
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        this.pinned[id] = pinned
    }

    override suspend fun installNewBuiltIns() {
        seeded = true
    }
}

class FakeProviderRepository(
    var config: ProviderConfig? = null,
    var key: String? = null,
) : ProviderRepository {
    override fun observeAll(): Flow<List<ProviderConfig>> = MutableStateFlow(listOfNotNull(config))
    override suspend fun get(id: String): ProviderConfig? = config?.takeIf { it.id == id }
    override suspend fun resolve(providerId: String?): ProviderConfig? = config
    override suspend fun upsert(config: ProviderConfig, apiKey: String?) {
        this.config = config
        this.key = apiKey
    }

    override suspend fun delete(id: String) {
        config = null
    }

    override suspend fun apiKey(id: String): String? = key
    override suspend fun hasKey(id: String): Boolean = !key.isNullOrBlank()
}

class FakeHistoryRepository : HistoryRepository {
    val records = mutableListOf<RunRecord>()
    var cleared = false

    override fun observeAll(): Flow<List<RunRecord>> = MutableStateFlow(records.toList())
    override suspend fun get(id: String): RunRecord? = records.firstOrNull { it.id == id }
    override suspend fun record(run: RunRecord) {
        records += run
    }

    override suspend fun clear() {
        cleared = true
        records.clear()
    }
}

class FakeSettingsRepository(
    initial: UserSettings = UserSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state
    override suspend fun current(): UserSettings = state.value
    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        state.value = transform(state.value)
    }
}

/** Replays a canned chunk script, or fails with [error] instead. */
class FakeAiProvider(
    override val type: ProviderType = ProviderType.OPENAI,
    var deltas: List<String> = emptyList(),
    var error: Throwable? = null,
) : AiProvider {
    var lastRequest: AiRequest? = null
    var lastApiKey: String? = null

    override suspend fun listModels(config: ProviderConfig, apiKey: String?): List<ModelInfo> = emptyList()

    override fun generate(request: AiRequest, config: ProviderConfig, apiKey: String?): Flow<AiChunk> = flow {
        lastRequest = request
        lastApiKey = apiKey
        deltas.forEach { emit(AiChunk.Text(it)) }
        error?.let { throw it }
        emit(AiChunk.Done())
    }
}

class FakeRegistry(private val provider: AiProvider?) : AiProviderRegistry {
    override fun get(type: ProviderType): AiProvider? = provider?.takeIf { it.type == type }
    override fun supportedTypes(): Set<ProviderType> = setOfNotNull(provider?.type)
}

class FakeScreenContextProvider(
    var available: Boolean = true,
    var text: String? = null,
    var packageName: String? = null,
    /** Capture is a separate permission from screen reading, so it toggles separately. */
    var canCapture: Boolean = false,
    var jpeg: ByteArray? = null,
) : ScreenContextProvider {
    override fun isAvailable(): Boolean = available
    override suspend fun screenText(): String? = if (available) text else null
    override fun canScreenshot(): Boolean = canCapture
    override suspend fun screenshot(): ByteArray? = if (canCapture) jpeg else null
    override fun currentPackage(): String? = if (available) packageName else null
    override suspend fun replaceFocusedText(text: String): Boolean = false
}

/** Records what was stored and removed instead of touching a filesystem. */
class FakeScreenshotStore : ScreenshotStore {
    val saved = mutableMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()
    var clearedAll = false
    var purgedBefore: Long? = null

    override suspend fun save(runId: String, jpeg: ByteArray): String? {
        saved[runId] = jpeg
        return "/data/screenshots/$runId.jpg"
    }

    override suspend fun delete(paths: List<String>) {
        deleted += paths
    }

    override suspend fun deleteAll() {
        clearedAll = true
        saved.clear()
    }

    override suspend fun purgeOlderThan(cutoffMillis: Long) {
        purgedBefore = cutoffMillis
    }
}

class FakeClipboard(var contents: String? = null) : ClipboardAccess {
    var written: Pair<String, String>? = null
    override fun read(): String? = contents
    override fun write(label: String, text: String) {
        written = label to text
    }
}

/** Fixed clock; [step] advances it by a known amount on every read when a test wants durations. */
class FakeTimeSource(private var now: Long = 1_700_000_000_000L, private val step: Long = 0L) : TimeSource {
    override fun nowMillis(): Long = now.also { now += step }
}
