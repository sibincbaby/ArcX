package com.arcx.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowBundleRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.ClearHistoryUseCase
import com.arcx.core.domain.usecase.DeleteAllScreenshotsUseCase
import com.arcx.core.domain.usecase.PurgeExpiredScreenshotsUseCase
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.ScreenshotRetention
import com.arcx.core.model.SidebarSide
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A configured provider as the list needs it. [hasKey] is a boolean on purpose: the settings
 * screen has to show that a key is saved without ever pulling the key itself out of the vault.
 */
data class ProviderRow(
    val config: ProviderConfig,
    val hasKey: Boolean,
    val isDefault: Boolean,
)

/**
 * The system state ArcX's out-of-app surfaces depend on. Every one of these is changeable from
 * system Settings while ArcX is backgrounded, so this is re-read on every resume rather than
 * observed.
 */
data class SurfacePermissions(
    val overlayGranted: Boolean = false,
    val screenReadingEnabled: Boolean = false,
    /** Switched on *and* actually bound. False with [screenReadingEnabled] true is the stranded state. */
    val screenReadingRunning: Boolean = false,
    val batteryExempt: Boolean = false,
    /** Null when this device exposes no autostart screen we can open. */
    val hasAutostartScreen: Boolean = false,
    /** The separate "ArcX Actions" launcher icon; on unless the user has switched it off. */
    val launcherIconEnabled: Boolean = true,
    val accessibilityButtonAssigned: Boolean = false,
    /** False below Android 13, where the tile can only be added by hand from the shade. */
    val canAddQuickTile: Boolean = false,
    /**
     * Read here rather than remembered in the composable that draws the row. It used to be a
     * `remember`, so a refusal in a previous session was forgotten: the row said "Not allowed yet"
     * and offered an Allow button that opened a dialog Android had already stopped showing.
     */
    val notificationsEnabled: Boolean = false,
)

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val providers: List<ProviderRow> = emptyList(),
    val permissions: SurfacePermissions = SurfacePermissions(),
    val loading: Boolean = true,
) {
    /** A stored flag with no overlay permission behind it is not a sidebar the user can see. */
    val bubbleActive: Boolean get() = settings.bubbleEnabled && permissions.overlayGranted

    /**
     * Refused rather than never asked. Android shows the POST_NOTIFICATIONS dialog once and then
     * stops, and it will not say whether it has; the only honest way to tell the two apart is that
     * ArcX remembers having asked. Only when this is true is "Open settings" the useful button.
     */
    val notificationsBlocked: Boolean
        get() = !permissions.notificationsEnabled && settings.notificationPermissionAsked

    /**
     * What the Permissions row on the Settings index reports. Three, because these are the three
     * a user can hold: drawing over other apps, reading the screen, and posting notifications.
     */
    val permissionsGranted: Int get() = listOf(
        permissions.overlayGranted,
        permissions.screenReadingEnabled,
        permissions.notificationsEnabled,
    ).count { it }

    /**
     * The number behind "N of 6 are live", shared with the Entry points screen so the index and the
     * screen it opens can never disagree.
     *
     * Six, not seven: the Quick Settings tile and the home-screen widget are placed by the shade
     * and the launcher, and Android exposes no way to ask whether the user has done so. A guess
     * here would make the whole number worthless. Counts *running*, not merely switched on — a
     * service Android lists but is not hosting is not a way in, however the setting reads.
     */
    val liveEntryPoints: Int get() = listOf(
        bubbleActive,
        true, // text selection menu — a manifest activity, always present
        true, // share sheet — likewise
        permissions.launcherIconEnabled,
        permissions.accessibilityButtonAssigned && settings.accessibilityButtonOffered,
        screenReadingLive,
    ).count { it }

    val screenReadingLive: Boolean
        get() = permissions.screenReadingEnabled && permissions.screenReadingRunning
}

/**
 * The few things settings has to *do* rather than show. Both are one-shot and neither belongs in
 * [SettingsUiState]: replaying "the file was exported" on every recomposition, or on the next
 * resume, would be a lie about something that happened once.
 */
sealed interface SettingsEffect {
    /**
     * The user asked for the sidebar without the overlay permission behind it. The switch used to
     * park the request and write nothing, so it snapped back with no explanation; this sends them
     * to the grant, and [SettingsViewModel.onResume] finishes the job when they come back.
     */
    data object RequestOverlayPermission : SettingsEffect
    data class Message(val text: String) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val workflows: WorkflowRepository,
    private val settings: SettingsRepository,
    private val clearHistory: ClearHistoryUseCase,
    private val deleteAllScreenshots: DeleteAllScreenshotsUseCase,
    private val purgeExpiredScreenshots: PurgeExpiredScreenshotsUseCase,
    private val bundles: WorkflowBundleRepository,
    private val surfaces: SystemSurfaces,
) : ViewModel() {

    private val permissions = MutableStateFlow(SurfacePermissions())

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    // Remembers a bubble the user asked for but could not have yet, so the trip out to the
    // overlay permission screen and back finishes the job they started.
    private var bubbleRequested = false

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        providers.observeAll().map { configs ->
            configs.map { it to providers.hasKey(it.id) }
        },
        permissions,
    ) { user, configs, granted ->
        SettingsUiState(
            settings = user,
            providers = configs.map { (config, hasKey) ->
                ProviderRow(config, hasKey, isDefault = config.id == user.defaultProviderId)
            },
            permissions = granted,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    /**
     * Called on every resume. Both permissions can be taken away in system Settings while ArcX
     * is in the background, and a cached "not granted" shown to someone who just granted it
     * reads as a broken app.
     */
    fun onResume() {
        permissions.value = SurfacePermissions(
            overlayGranted = surfaces.isOverlayGranted(),
            screenReadingEnabled = surfaces.isScreenReadingEnabled(),
            screenReadingRunning = surfaces.isScreenReadingRunning(),
            batteryExempt = surfaces.isIgnoringBatteryOptimisation(),
            hasAutostartScreen = surfaces.autostartIntent() != null,
            launcherIconEnabled = surfaces.isLauncherIconEnabled(),
            accessibilityButtonAssigned = surfaces.isAccessibilityButtonAssigned(),
            canAddQuickTile = surfaces.canAddQuickTile(),
            notificationsEnabled = surfaces.areNotificationsEnabled(),
        )
        if (bubbleRequested && permissions.value.overlayGranted) {
            bubbleRequested = false
            update { it.copy(bubbleEnabled = true) }
        }
    }

    /**
     * Without the overlay permission the flag would start a service that cannot draw anything, so
     * the request is parked rather than written — and the user is sent to grant it.
     *
     * Parking alone was the bug: the switch sprang back with nothing said, which reads as a broken
     * control rather than as a missing permission. Asking is the only thing that can be done about
     * it, and it is what the user's tap meant.
     */
    fun onBubbleEnabledChange(enabled: Boolean) {
        if (enabled && !permissions.value.overlayGranted) {
            bubbleRequested = true
            _effects.trySend(SettingsEffect.RequestOverlayPermission)
            return
        }
        bubbleRequested = false
        update { it.copy(bubbleEnabled = enabled) }
    }

    /**
     * The affirmative consent behind screen reading, written only from the accept button on the
     * disclosure screen. Nothing else may call this: a dismiss, a back press or a resume must not
     * count as agreement, which is the whole point of recording it separately from the grant.
     */
    fun onScreenReadingConsent() = update { it.copy(screenReadingConsented = true) }

    /**
     * Remembers that Android has now had its one chance to show the notification dialog, so a later
     * session can tell a refusal from a permission never asked for.
     */
    fun onNotificationPermissionResult(granted: Boolean) {
        permissions.value = permissions.value.copy(notificationsEnabled = granted)
        update { it.copy(notificationPermissionAsked = true) }
    }

    fun notificationSettingsIntent(): Intent = surfaces.notificationSettingsIntent()

    /**
     * The same export Discover offers, reachable from Privacy where a user looking for "what does
     * ArcX keep, and can I take it with me" will actually look for it. It goes through
     * [WorkflowBundleRepository] rather than assembling a file here, so there is still exactly one
     * place that decides what travels in a bundle.
     */
    fun onExportWorkflows(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val all = workflows.observeAll().first()
                bundles.write(uri, all)
                all.size
            }
            _effects.send(
                SettingsEffect.Message(
                    result.fold(
                        onSuccess = { n -> "Exported $n ${if (n == 1) "workflow" else "workflows"}" },
                        onFailure = { "Could not write that file" },
                    ),
                ),
            )
        }
    }

    /** Said out loud when a vendor screen that resolved a moment ago will not open — see below. */
    fun onAutostartUnavailable() {
        _effects.trySend(
            SettingsEffect.Message(
                "This phone's Autostart screen would not open. Look for Autostart, Auto-launch or " +
                    "Background start in your phone's own security or battery settings.",
            ),
        )
    }

    /**
     * Written straight through to the package manager rather than to preferences, then mirrored
     * into state so the switch moves now — the launcher takes a moment to drop the icon, and
     * waiting for the next resume to re-read would leave the switch looking stuck.
     */
    fun onLauncherIconChange(enabled: Boolean) {
        surfaces.setLauncherIconEnabled(enabled)
        permissions.value = permissions.value.copy(launcherIconEnabled = enabled)
    }

    fun onAddQuickTile() = surfaces.requestAddQuickTile()

    fun onAccessibilityButtonOfferedChange(offered: Boolean) =
        update { it.copy(accessibilityButtonOffered = offered) }

    fun onBubbleOpensFullListChange(enabled: Boolean) =
        update { it.copy(bubbleOpensFullList = enabled) }

    fun onCompactPickerChange(enabled: Boolean) = update { it.copy(compactPicker = enabled) }

    // The five the sidebar is shaped by. Each is written straight through and nothing here clamps
    // them: SettingsRepositoryImpl does that on the way in *and* on the way out, so a second copy
    // of the bounds in this class would be one more place for them to disagree with the model's.
    fun onSidebarSideChange(side: SidebarSide) = update { it.copy(sidebarSide = side) }

    fun onSidebarVerticalPercentChange(percent: Float) =
        update { it.copy(sidebarVerticalPercent = percent) }

    fun onSidebarLengthChange(lengthDp: Int) = update { it.copy(sidebarLengthDp = lengthDp) }

    fun onSidebarWidthChange(widthDp: Int) = update { it.copy(sidebarWidthDp = widthDp) }

    fun onSidebarOpacityChange(opacity: Float) = update { it.copy(sidebarOpacity = opacity) }

    fun onPopupTransparencyChange(transparency: Float) =
        update { it.copy(popupTransparency = transparency) }

    fun overlaySettingsIntent(): Intent = surfaces.overlaySettingsIntent()

    fun batteryOptimisationIntent(): Intent = surfaces.batteryOptimisationIntent()

    fun autostartIntent(): Intent? = surfaces.autostartIntent()

    fun screenReadingSettingsIntent(): Intent = surfaces.screenReadingSettingsIntent()

    fun onThemeChange(theme: ThemePreference) = update { it.copy(theme = theme) }

    fun onDynamicColorChange(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun onHistoryEnabledChange(enabled: Boolean) = update { it.copy(historyEnabled = enabled) }

    fun onSetDefaultProvider(id: String) = update { it.copy(defaultProviderId = id) }

    /**
     * Sweeps immediately after writing the choice. Shortening the window is something a user
     * does *because* they want the old images gone, and waiting for whenever the next scheduled
     * sweep runs would make the setting look like it did nothing.
     */
    fun onScreenshotRetentionChange(retention: ScreenshotRetention) {
        viewModelScope.launch {
            settings.update { it.copy(screenshotRetention = retention) }
            purgeExpiredScreenshots()
        }
    }

    fun onDeleteScreenshots() {
        viewModelScope.launch { deleteAllScreenshots() }
    }

    fun onClearHistory() {
        viewModelScope.launch { clearHistory() }
    }

    /**
     * A genuine factory reset: workflows, run history, provider configurations and the API keys
     * in the encrypted vault, plus every preference including onboarding. Nothing survives,
     * because nothing of this ever left the device to survive anywhere else.
     */
    fun onDeleteAllLocalData() {
        viewModelScope.launch {
            clearHistory()
            providers.observeAll().first().forEach { providers.delete(it.id) }
            workflows.observeAll().first().forEach { workflows.delete(it.id) }
            settings.update { UserSettings() }
        }
    }

    private fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settings.update(transform) }
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
