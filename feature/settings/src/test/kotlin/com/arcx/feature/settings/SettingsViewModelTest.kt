package com.arcx.feature.settings

import android.content.Intent
import android.net.Uri
import com.arcx.core.domain.capture.ScreenshotStore
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowBundleRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.ClearHistoryUseCase
import com.arcx.core.domain.usecase.DeleteAllScreenshotsUseCase
import com.arcx.core.domain.usecase.PurgeExpiredScreenshotsUseCase
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunOutcome
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunSummary
import com.arcx.core.model.UserSettings
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The three settings behaviours that used to fail without saying anything, and the one that has
 * to hold for the Play submission.
 *
 * All of them are state that outlives the composable it used to be remembered in, which is why
 * they are worth a test at all: each was invisible precisely because nothing kept it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var store: FakeSettingsRepository
    private lateinit var surfaces: FakeSystemSurfaces

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeSettingsRepository()
        surfaces = FakeSystemSurfaces()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `asking for the sidebar without the overlay permission sends the user to grant it`() =
        runTest {
            val viewModel = viewModel()
            val effects = collectEffects(viewModel)
            viewModel.onResume()

            viewModel.onBubbleEnabledChange(true)

            // Not written — the flag alone would start a service with nothing to draw on. What
            // must not happen is what used to: the switch springing back with nothing said.
            assertFalse(store.value.bubbleEnabled)
            assertEquals(listOf(SettingsEffect.RequestOverlayPermission), effects)

            // Coming back with the permission finishes the job the tap started.
            surfaces.overlayGranted = true
            viewModel.onResume()
            assertTrue(store.value.bubbleEnabled)
        }

    @Test
    fun `a refused notification permission is still known in the next session`() = runTest {
        val first = viewModel()
        first.onResume()
        first.onNotificationPermissionResult(granted = false)

        // A second ViewModel over the same store stands in for the next launch: the process that
        // saw the refusal is gone, and with it the `remember` this state used to live in.
        val next = viewModel()
        val states = collectState(next)
        next.onResume()

        assertFalse(states().permissions.notificationsEnabled)
        assertTrue(states().notificationsBlocked)
    }

    @Test
    fun `screen reading consent is recorded by the accept action and by nothing else`() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)

        // Opening the screen, and coming back to it from system Settings with the permission
        // granted, are both things Play's policy says must not count as consent.
        viewModel.onResume()
        surfaces.screenReadingEnabled = true
        viewModel.onResume()
        assertFalse(store.value.screenReadingConsented)

        viewModel.onScreenReadingConsent()
        assertTrue(store.value.screenReadingConsented)
    }

    private fun viewModel() = SettingsViewModel(
        providers = FakeProviderRepository(),
        workflows = FakeWorkflowRepository(),
        settings = store,
        clearHistory = ClearHistoryUseCase(FakeHistoryRepository()),
        deleteAllScreenshots = DeleteAllScreenshotsUseCase(FakeScreenshotStore()),
        purgeExpiredScreenshots = PurgeExpiredScreenshotsUseCase(
            FakeScreenshotStore(),
            store,
        ) { 0L },
        bundles = FakeBundleRepository(),
        surfaces = surfaces,
    )

    /** `stateIn(WhileSubscribed)` emits nothing without a collector, so every test needs one. */
    private fun TestScope.collectState(viewModel: SettingsViewModel): () -> SettingsUiState {
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        return { viewModel.uiState.value }
    }

    private fun TestScope.collectEffects(viewModel: SettingsViewModel): List<SettingsEffect> {
        val seen = mutableListOf<SettingsEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.toList(seen) }
        return seen
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(UserSettings())
    val value: UserSettings get() = state.value
    override val settings: Flow<UserSettings> = state
    override suspend fun current(): UserSettings = state.value
    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        state.update(transform)
    }
}

/** Everything the ViewModel reads on resume, with the two grants the tests move. */
private class FakeSystemSurfaces : SystemSurfaces {
    var overlayGranted = false
    var screenReadingEnabled = false
    var notificationsEnabled = false

    override fun isScreenReadingEnabled(): Boolean = screenReadingEnabled
    override fun isScreenReadingRunning(): Boolean = screenReadingEnabled
    override fun isOverlayGranted(): Boolean = overlayGranted
    override fun areNotificationsEnabled(): Boolean = notificationsEnabled
    override fun isIgnoringBatteryOptimisation(): Boolean = false
    override fun autostartIntent(): Intent? = null
    override fun isLauncherIconEnabled(): Boolean = true
    override fun setLauncherIconEnabled(enabled: Boolean) = Unit
    override fun isAccessibilityButtonAssigned(): Boolean = false
    override fun canAddQuickTile(): Boolean = false
    override fun requestAddQuickTile() = Unit
    override fun canPinShortcut(): Boolean = false
    override fun pinWorkflowShortcut(workflow: Workflow): Boolean = false

    // Intents are the one thing a JVM unit test cannot build — android.jar is stubbed — and no
    // test here taps a button that needs one, so asking for one is a test that has gone wrong.
    override fun screenReadingSettingsIntent(): Intent = error("no intents under unit test")
    override fun overlaySettingsIntent(): Intent = error("no intents under unit test")
    override fun batteryOptimisationIntent(): Intent = error("no intents under unit test")
    override fun notificationSettingsIntent(): Intent = error("no intents under unit test")
}

private class FakeProviderRepository : ProviderRepository {
    override fun observeAll(): Flow<List<ProviderConfig>> = flowOf(emptyList())
    override suspend fun get(id: String): ProviderConfig? = null
    override suspend fun resolve(providerId: String?): ProviderConfig? = null
    override suspend fun upsert(config: ProviderConfig, apiKey: String?) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun apiKey(id: String): String? = null
    override suspend fun hasKey(id: String): Boolean = false
}

private class FakeWorkflowRepository : WorkflowRepository {
    override fun observeAll(): Flow<List<Workflow>> = flowOf(emptyList())
    override fun observeFavorites(): Flow<List<Workflow>> = flowOf(emptyList())
    override fun observePinned(): Flow<List<Workflow>> = flowOf(emptyList())
    override fun observeRecent(limit: Int): Flow<List<Workflow>> = flowOf(emptyList())
    override suspend fun get(id: String): Workflow? = null
    override suspend fun upsert(workflow: Workflow) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
    override suspend fun setPinned(id: String, pinned: Boolean) = Unit
    override suspend fun installNewBuiltIns() = Unit
}

private class FakeHistoryRepository : HistoryRepository {
    override fun observeRecent(limit: Int): Flow<List<RunSummary>> = flowOf(emptyList())
    override fun observeSince(since: Long): Flow<List<RunOutcome>> = flowOf(emptyList())
    override fun observeAverageDurations(): Flow<Map<String, Long>> = flowOf(emptyMap())
    override suspend fun get(id: String): RunRecord? = null
    override suspend fun record(run: RunRecord) = Unit
    override suspend fun clear() = Unit
}

private class FakeScreenshotStore : ScreenshotStore {
    override suspend fun save(runId: String, jpeg: ByteArray): String? = null
    override suspend fun delete(paths: List<String>) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun purgeOlderThan(cutoffMillis: Long) = Unit
}

private class FakeBundleRepository : WorkflowBundleRepository {
    override suspend fun readGallery(): List<WorkflowSpec> = emptyList()
    override suspend fun read(uri: Uri): List<WorkflowSpec> = emptyList()
    override suspend fun install(specs: List<WorkflowSpec>): List<Workflow> = emptyList()
    override suspend fun write(uri: Uri, workflows: List<Workflow>) = Unit
}
