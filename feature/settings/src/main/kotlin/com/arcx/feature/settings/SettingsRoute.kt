package com.arcx.feature.settings

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.LocalSnackbarHostState
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.model.ThemePreference

/**
 * Settings navigates itself. A nested NavHost would buy route strings and deep links for a
 * handful of leaves that nothing outside this module ever links to, so a screen enum plus
 * [BackHandler] is the whole router.
 */
private enum class SettingsScreen {
    ROOT,
    PROVIDERS,
    PROVIDER_EDIT,
    ENTRY_POINTS,
    PERMISSIONS,
    SCREEN_READING_DISCLOSURE,
    APPEARANCE,
    PRIVACY,
    ABOUT,
}

/**
 * [startAtEntryPoints] is for the surface strip on Home. A chip that says the sidebar is off is
 * only useful if tapping it reaches the switch, and landing on the Settings index instead would
 * make the strip a signpost rather than a control.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    startAtEntryPoints: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = LocalSnackbarHostState.current

    // Overlay and accessibility can both be revoked from system Settings while ArcX is away,
    // and the root row summarises them, so the refresh lives at the route rather than inside
    // the one screen that shows them in full.
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    var screen by rememberSaveable {
        mutableStateOf(if (startAtEntryPoints) SettingsScreen.ENTRY_POINTS else SettingsScreen.ROOT)
    }
    var editingProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped on every entry into the editor so each visit gets its own ViewModel rather than
    // inheriting the previous provider's half-typed form.
    var editSession by rememberSaveable { mutableIntStateOf(0) }

    fun openEditor(providerId: String?) {
        editingProviderId = providerId
        editSession++
        screen = SettingsScreen.PROVIDER_EDIT
    }

    /**
     * The one and only place in ArcX that starts ACTION_ACCESSIBILITY_SETTINGS.
     *
     * There used to be two controls doing it, with the disclosure sitting between them, so the
     * upper one described itself below the button. Now everything that wants the grant — the
     * Permissions row, the accept button on the disclosure — comes through here, which is what
     * makes "the disclosure has been seen" something the code can actually promise.
     */
    fun openAccessibilitySettings() {
        context.startActivity(viewModel.screenReadingSettingsIntent())
    }

    // Android shows this dialog once and never again. The launcher has to live in a composable,
    // but the answer goes to the ViewModel, because a `remember` here dies with the screen and
    // took the difference between "never asked" and "refused" with it.
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotificationPermissionResult(granted) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::onExportWorkflows) }

    // One collector for everything settings has to *do*: send the user to a grant, or say
    // something back. Both are one-shot, which is why neither is in the UI state.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.RequestOverlayPermission ->
                    context.startActivity(viewModel.overlaySettingsIntent())
                is SettingsEffect.Message -> snackbars.showSnackbar(effect.text)
            }
        }
    }

    BackHandler(enabled = screen != SettingsScreen.ROOT) {
        screen = when (screen) {
            SettingsScreen.PROVIDER_EDIT -> SettingsScreen.PROVIDERS
            // Backing out of the disclosure is not consent, so it goes back to the screen that
            // asked and writes nothing — see UserSettings.screenReadingConsented.
            SettingsScreen.SCREEN_READING_DISCLOSURE -> SettingsScreen.PERMISSIONS
            else -> SettingsScreen.ROOT
        }
    }

    when (screen) {
        SettingsScreen.ROOT -> SettingsRootScreen(
            state = state,
            onOpen = { screen = it },
            onBack = onBack,
        )

        SettingsScreen.PROVIDERS -> ProvidersScreen(
            providers = state.providers,
            onBack = { screen = SettingsScreen.ROOT },
            onAdd = { openEditor(null) },
            onEdit = { openEditor(it) },
            onSetDefault = viewModel::onSetDefaultProvider,
        )

        SettingsScreen.PROVIDER_EDIT -> ProviderEditScreen(
            providerId = editingProviderId,
            onDone = { screen = SettingsScreen.PROVIDERS },
            viewModel = hiltViewModel(key = "provider-edit-$editSession"),
        )

        SettingsScreen.ENTRY_POINTS -> EntryPointsScreen(
            live = state.liveEntryPoints,
            bubbleLive = state.bubbleActive,
            overlayGranted = state.permissions.overlayGranted,
            screenReadingEnabled = state.permissions.screenReadingEnabled,
            screenReadingLive = state.screenReadingLive,
            batteryExempt = state.permissions.batteryExempt,
            hasAutostartScreen = state.permissions.hasAutostartScreen,
            launcherIconEnabled = state.permissions.launcherIconEnabled,
            accessibilityButtonAssigned = state.permissions.accessibilityButtonAssigned,
            accessibilityButtonOffered = state.settings.accessibilityButtonOffered,
            canAddQuickTile = state.permissions.canAddQuickTile,
            onBack = { screen = SettingsScreen.ROOT },
            onBubbleEnabledChange = viewModel::onBubbleEnabledChange,
            onLauncherIconChange = viewModel::onLauncherIconChange,
            onAccessibilityButtonOfferedChange = viewModel::onAccessibilityButtonOfferedChange,
            onAddQuickTile = viewModel::onAddQuickTile,
            onOpenOverlaySettings = { context.startActivity(viewModel.overlaySettingsIntent()) },
            onOpenPermissions = { screen = SettingsScreen.PERMISSIONS },
            onOpenAppearance = { screen = SettingsScreen.APPEARANCE },
            onOpenBatterySettings = {
                context.startActivity(viewModel.batteryOptimisationIntent())
            },
            // Vendor screens vanish between OEM versions, so an Intent that resolved when the row
            // was drawn can still be gone by the time it is tapped. It used to fail into silence,
            // which is indistinguishable from a dead button; now it says so and names what to look
            // for, since the setting still exists on the phone under some other name.
            onOpenAutostart = {
                val intent = viewModel.autostartIntent()
                val opened = intent != null &&
                    runCatching { context.startActivity(intent) }.isSuccess
                if (!opened) viewModel.onAutostartUnavailable()
            },
        )

        SettingsScreen.PERMISSIONS -> PermissionsScreen(
            state = state,
            onBack = { screen = SettingsScreen.ROOT },
            onOpenOverlaySettings = { context.startActivity(viewModel.overlaySettingsIntent()) },
            onRequestNotifications = {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onOpenNotificationSettings = {
                context.startActivity(viewModel.notificationSettingsIntent())
            },
            // The gate. First time through, the disclosure is put up on its own with an accept
            // button; afterwards this is a plain trip to the system screen, because re-reading two
            // thousand words to toggle a permission you already agreed to is not consent, it is an
            // obstacle. Consent is remembered, the grant is Android's.
            onEnableScreenReading = {
                if (state.settings.screenReadingConsented) {
                    openAccessibilitySettings()
                } else {
                    screen = SettingsScreen.SCREEN_READING_DISCLOSURE
                }
            },
        )

        SettingsScreen.SCREEN_READING_DISCLOSURE -> ScreenReadingDisclosureScreen(
            onAccept = {
                viewModel.onScreenReadingConsent()
                screen = SettingsScreen.PERMISSIONS
                openAccessibilitySettings()
            },
            onCancel = { screen = SettingsScreen.PERMISSIONS },
        )

        SettingsScreen.APPEARANCE -> AppearanceScreen(
            settings = state.settings,
            sidebarLive = state.bubbleActive,
            onBack = { screen = SettingsScreen.ROOT },
            onThemeChange = viewModel::onThemeChange,
            onDynamicColorChange = viewModel::onDynamicColorChange,
            onPopupTransparencyChange = viewModel::onPopupTransparencyChange,
            onSidebarSideChange = viewModel::onSidebarSideChange,
            onSidebarVerticalPercentChange = viewModel::onSidebarVerticalPercentChange,
            onSidebarLengthChange = viewModel::onSidebarLengthChange,
            onSidebarWidthChange = viewModel::onSidebarWidthChange,
            onSidebarOpacityChange = viewModel::onSidebarOpacityChange,
            onBubbleOpensFullListChange = viewModel::onBubbleOpensFullListChange,
            onCompactPickerChange = viewModel::onCompactPickerChange,
        )

        SettingsScreen.PRIVACY -> PrivacyScreen(
            historyEnabled = state.settings.historyEnabled,
            screenshotRetention = state.settings.screenshotRetention,
            onBack = { screen = SettingsScreen.ROOT },
            onHistoryEnabledChange = viewModel::onHistoryEnabledChange,
            onScreenshotRetentionChange = viewModel::onScreenshotRetentionChange,
            onDeleteScreenshots = viewModel::onDeleteScreenshots,
            onClearHistory = viewModel::onClearHistory,
            onExportWorkflows = { exportLauncher.launch(EXPORT_FILE_NAME) },
            onDeleteAllLocalData = viewModel::onDeleteAllLocalData,
        )

        SettingsScreen.ABOUT -> AboutScreen(onBack = { screen = SettingsScreen.ROOT })
    }
}

/** The same name Discover writes, so a second export overwrites rather than accumulates. */
private const val EXPORT_FILE_NAME = "arcx-workflows.json"

/**
 * Six rows under three headings, grouped by the question the user arrived with: where does ArcX
 * appear, what is ArcX allowed to do, what does it look like, what does it keep.
 *
 * There was no organising principle before this, and it showed — five rows, four different
 * taxonomies, and no headings at all, so a new control landed on whichever screen the feature that
 * introduced it happened to be built on. That is how Entry points ended up holding the accessibility
 * disclosure, the notification permission, five appearance sliders and forty-five per cent of every
 * control in Settings. The principle is not "one screen per subsystem"; it is one screen per
 * question, which is why Permissions is now its own row and why the sidebar's looks live with the
 * theme rather than with the switch that turns the sidebar on.
 */
@Composable
private fun SettingsRootScreen(
    state: SettingsUiState,
    onOpen: (SettingsScreen) -> Unit,
    onBack: () -> Unit,
) {
    // Settings is a full-screen push off the Home header now, not a tab, so its root carries the
    // arrow back out — there is no bottom bar underneath to leave by.
    SettingsScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("What runs your workflows")
            SettingsGroup {
                SettingsRow(
                    title = "Providers",
                    subtitle = providersSubtitle(state),
                    icon = Icons.Outlined.Cloud,
                    onClick = { onOpen(SettingsScreen.PROVIDERS) },
                )
            }

            SectionHeader("Where ArcX appears, and what it may do")
            SettingsGroup {
                SettingsRow(
                    title = "Entry points",
                    subtitle = "${state.liveEntryPoints} of 6 live",
                    icon = Icons.Outlined.Bolt,
                    onClick = { onOpen(SettingsScreen.ENTRY_POINTS) },
                )
                SettingsRow(
                    title = "Permissions",
                    subtitle = "${state.permissionsGranted} of 3 granted",
                    icon = Icons.Outlined.VerifiedUser,
                    onClick = { onOpen(SettingsScreen.PERMISSIONS) },
                )
            }

            SectionHeader("The app itself")
            SettingsGroup {
                SettingsRow(
                    title = "Appearance",
                    subtitle = themeLabel(state.settings.theme),
                    icon = Icons.Outlined.Palette,
                    onClick = { onOpen(SettingsScreen.APPEARANCE) },
                )
                SettingsRow(
                    title = "Privacy & data",
                    subtitle = if (state.settings.historyEnabled) "History on" else "History off",
                    icon = Icons.Outlined.Shield,
                    onClick = { onOpen(SettingsScreen.PRIVACY) },
                )
                SettingsRow(
                    title = "About",
                    icon = Icons.Outlined.Info,
                    onClick = { onOpen(SettingsScreen.ABOUT) },
                )
            }

            SettingsNote(
                "ArcX has no account and no server of its own. Your workflows and keys stay on " +
                    "this device, and your text goes only to the AI provider you connected.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun providersSubtitle(state: SettingsUiState): String = when {
    state.loading -> ""
    state.providers.isEmpty() -> "None connected"
    else -> state.providers.joinToString { it.config.label }
}

internal fun themeLabel(theme: ThemePreference): String = when (theme) {
    ThemePreference.SYSTEM -> "System default"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}
