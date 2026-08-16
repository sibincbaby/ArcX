package com.arcx.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Screenshot
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.ArcxGlassSurface
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.designsystem.theme.PanelScrim
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.model.ScreenshotRetention
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings
import kotlin.math.roundToInt

@Composable
internal fun AppearanceScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPopupTransparencyChange: (Float) -> Unit,
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

            SectionHeader("Popups")
            SettingsGroup {
                // Above the slider rather than below it: a thumb is dragged with a finger, and a
                // finger covers what is under it — which here is the only thing the control does.
                PopupPreview()
                SettingsSlider(
                    title = "Transparency",
                    value = settings.popupTransparency,
                    valueRange = 0f..UserSettings.POPUP_MAX_TRANSPARENCY,
                    step = PercentStep,
                    format = ::percentLabel,
                    // The cap is a legibility floor rather than a taste one, and a user who reaches
                    // it deserves to know why it will not go further. See POPUP_MAX_TRANSPARENCY.
                    supporting = "How much of the app behind shows through the workflow panel and " +
                        "the popup an answer comes back in. Stops at " +
                        "${(UserSettings.POPUP_MAX_TRANSPARENCY * 100).roundToInt()}% because " +
                        "past that the text stops being readable over a bright app underneath.",
                    onValueChange = onPopupTransparencyChange,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A pane of the real thing, over a stand-in for someone else's app.
 *
 * Worth the twenty lines: every other setting on this screen changes something the user is already
 * looking at, and this one changes a surface that only ever appears over a *different* app — so
 * without a preview it is a slider with no visible effect until the next time they use the sidebar.
 * The backdrop is deliberately busy and bright, because that is the case transparency costs
 * something in; a preview over a flat colour would flatter the setting at every value.
 */
@Composable
private fun PopupPreview() {
    Box(
        modifier = Modifier
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm)
            .fillMaxWidth()
            .height(112.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ),
            )
            // The same dim the real hosts paint behind a popup, so the preview is not flattering
            // the setting by leaving out the one thing that helps it.
            .background(PanelScrim),
        contentAlignment = Alignment.Center,
    ) {
        ArcxGlassSurface(
            modifier = Modifier.padding(horizontal = Spacing.Xl),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md)) {
                Text("Rewrite Professionally", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Selection → Replace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PrivacyScreen(
    historyEnabled: Boolean,
    screenshotRetention: ScreenshotRetention,
    onBack: () -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onScreenshotRetentionChange: (ScreenshotRetention) -> Unit,
    onDeleteScreenshots: () -> Unit,
    onClearHistory: () -> Unit,
    onDeleteAllLocalData: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmDeleteScreenshots by remember { mutableStateOf(false) }

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

            SectionHeader("Screenshots")
            SettingsGroup {
                ScreenshotRetention.entries.forEach { retention ->
                    SettingsRow(
                        title = retentionLabel(retention),
                        onClick = { onScreenshotRetentionChange(retention) },
                        trailing = {
                            RadioButton(
                                selected = screenshotRetention == retention,
                                onClick = { onScreenshotRetentionChange(retention) },
                            )
                        },
                    )
                }
            }
            SettingsNote(retentionNote(screenshotRetention))
            // Its own group: inside the retention one it would read as a fourth way to keep
            // screenshots rather than the one control that throws them away.
            SettingsGroup {
                SettingsRow(
                    title = "Delete screenshots now",
                    subtitle = "Remove every saved screenshot, whatever its age.",
                    icon = Icons.Outlined.Screenshot,
                    onClick = { confirmDeleteScreenshots = true },
                )
            }
            SettingsNote(
                "Workflows that act on your screen save a picture of it here so History can " +
                    "show you what they were given. Those pictures are the only screen " +
                    "contents ArcX ever writes to storage.",
            )

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

    if (confirmDeleteScreenshots) {
        ConfirmDialog(
            title = "Delete screenshots?",
            body = "Every screenshot ArcX has saved is removed from this device. The runs stay " +
                "in History, they just stop showing a picture.",
            confirmLabel = "Delete",
            onConfirm = onDeleteScreenshots,
            onDismiss = { confirmDeleteScreenshots = false },
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

private fun retentionLabel(retention: ScreenshotRetention): String = when (retention) {
    ScreenshotRetention.WEEK -> "Keep for a week"
    ScreenshotRetention.MONTH -> "Keep for a month"
    ScreenshotRetention.FOREVER -> "Keep until I delete them"
}

/**
 * Said next to the choice rather than once above it, because "older than this are deleted" is
 * a promise the FOREVER option does not make and must not appear to.
 */
private fun retentionNote(retention: ScreenshotRetention): String = when (retention) {
    ScreenshotRetention.FOREVER ->
        "Screenshots are kept until you delete them here or clear your history."
    else -> "Screenshots older than this are deleted automatically."
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
