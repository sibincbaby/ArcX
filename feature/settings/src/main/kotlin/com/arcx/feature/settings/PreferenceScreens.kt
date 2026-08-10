package com.arcx.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings

@Composable
internal fun AppearanceScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    // Material You only exists from Android 12; offering the toggle below that would be a
    // switch that does nothing.
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SettingsScaffold(title = "Appearance", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Theme")
            SettingsGroup {
                ThemePreference.entries.forEach { preference ->
                    SettingsRow(
                        title = themeLabel(preference),
                        onClick = { onThemeChange(preference) },
                        trailing = {
                            RadioButton(
                                selected = settings.theme == preference,
                                onClick = { onThemeChange(preference) },
                            )
                        },
                    )
                }
            }

            SectionHeader("Colour")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Dynamic colour",
                    subtitle = if (dynamicColorAvailable) {
                        "Take the palette from your wallpaper."
                    } else {
                        "Needs Android 12 or newer."
                    },
                    checked = settings.dynamicColor && dynamicColorAvailable,
                    onCheckedChange = onDynamicColorChange,
                    enabled = dynamicColorAvailable,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun PrivacyScreen(
    historyEnabled: Boolean,
    onBack: () -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteAllLocalData: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    SettingsScaffold(title = "Privacy", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Save run history",
                    subtitle = "Keep a local record of what you ran, with truncated previews.",
                    icon = Icons.Outlined.History,
                    checked = historyEnabled,
                    onCheckedChange = onHistoryEnabledChange,
                )
                SettingsRow(
                    title = "Clear history",
                    subtitle = "Delete every recorded run.",
                    onClick = { confirmClear = true },
                )
            }

            SectionHeader("Danger zone")
            SettingsGroup {
                SettingsRow(
                    title = "Delete all local data",
                    subtitle = "Workflows, history, providers and saved API keys.",
                    icon = Icons.Outlined.DeleteForever,
                    onClick = { confirmWipe = true },
                )
            }

            SettingsNote(
                "ArcX has no account and no backend. Nothing here is synced or backed up, so " +
                    "deleting it deletes it. The only thing that ever leaves this device is the " +
                    "text you send to the AI provider you configured, and it goes straight there.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history?",
            body = "Every recorded run is deleted from this device. Your workflows stay.",
            confirmLabel = "Clear",
            onConfirm = onClearHistory,
            onDismiss = { confirmClear = false },
        )
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "Delete all local data?",
            body = "This removes every workflow you built, your entire run history, all " +
                "provider configurations and the API keys saved in this device's encrypted " +
                "keystore. ArcX restarts as if freshly installed. There is no backup to " +
                "restore from — nothing was ever uploaded anywhere.",
            confirmLabel = "Delete everything",
            onConfirm = onDeleteAllLocalData,
            onDismiss = { confirmWipe = false },
        )
    }
}

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "unknown" }
    }

    SettingsScaffold(title = "About", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup {
                SettingsRow(title = "ArcX", subtitle = "Version $version")
            }

            SectionHeader("Bring your own key")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "ArcX has no account, no subscription and no server of its own.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You connect your own AI provider with your own API key. The key is " +
                            "encrypted on this device and used only to talk to that provider. " +
                            "You see exactly what it costs, because you are billed by them " +
                            "directly, and you can revoke it at any time without asking us.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Links")
            SettingsGroup {
                SettingsRow(
                    title = "Website and source",
                    subtitle = "Not published yet — links land with the first public build.",
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
