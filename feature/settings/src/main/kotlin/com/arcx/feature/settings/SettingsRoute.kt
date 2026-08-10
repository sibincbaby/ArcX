package com.arcx.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.model.ThemePreference

/**
 * Settings navigates itself. A nested NavHost would buy route strings and deep links for a
 * handful of leaves that nothing outside this module ever links to, so a screen enum plus
 * [BackHandler] is the whole router.
 */
private enum class SettingsScreen { ROOT, PROVIDERS, PROVIDER_EDIT, APPEARANCE, PRIVACY, ABOUT }

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(SettingsScreen.ROOT) }
    var editingProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped on every entry into the editor so each visit gets its own ViewModel rather than
    // inheriting the previous provider's half-typed form.
    var editSession by rememberSaveable { mutableIntStateOf(0) }

    fun openEditor(providerId: String?) {
        editingProviderId = providerId
        editSession++
        screen = SettingsScreen.PROVIDER_EDIT
    }

    BackHandler(enabled = screen != SettingsScreen.ROOT) {
        screen = if (screen == SettingsScreen.PROVIDER_EDIT) {
            SettingsScreen.PROVIDERS
        } else {
            SettingsScreen.ROOT
        }
    }

    when (screen) {
        SettingsScreen.ROOT -> SettingsRootScreen(
            state = state,
            onOpen = { screen = it },
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

        SettingsScreen.APPEARANCE -> AppearanceScreen(
            settings = state.settings,
            onBack = { screen = SettingsScreen.ROOT },
            onThemeChange = viewModel::onThemeChange,
            onDynamicColorChange = viewModel::onDynamicColorChange,
        )

        SettingsScreen.PRIVACY -> PrivacyScreen(
            historyEnabled = state.settings.historyEnabled,
            onBack = { screen = SettingsScreen.ROOT },
            onHistoryEnabledChange = viewModel::onHistoryEnabledChange,
            onClearHistory = viewModel::onClearHistory,
            onDeleteAllLocalData = viewModel::onDeleteAllLocalData,
        )

        SettingsScreen.ABOUT -> AboutScreen(onBack = { screen = SettingsScreen.ROOT })
    }
}

@Composable
private fun SettingsRootScreen(
    state: SettingsUiState,
    onOpen: (SettingsScreen) -> Unit,
) {
    // Back out of Settings itself belongs to whoever navigated here, so the root has no arrow.
    SettingsScaffold(title = "Settings", onBack = null) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup {
                SettingsRow(
                    title = "Providers",
                    subtitle = providersSubtitle(state),
                    icon = Icons.Outlined.Cloud,
                    onClick = { onOpen(SettingsScreen.PROVIDERS) },
                )
                SettingsRow(
                    title = "Appearance",
                    subtitle = themeLabel(state.settings.theme),
                    icon = Icons.Outlined.Palette,
                    onClick = { onOpen(SettingsScreen.APPEARANCE) },
                )
                SettingsRow(
                    title = "Privacy",
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
