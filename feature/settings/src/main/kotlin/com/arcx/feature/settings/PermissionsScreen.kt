package com.arcx.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.arcx.core.designsystem.R as DesignSystemR
import com.arcx.core.designsystem.theme.Spacing

/**
 * What ArcX is allowed to do, as opposed to where it appears.
 *
 * The three grants a user can hold live here and nowhere else, so "did I give ArcX permission for
 * this" has one address. Entry points still lists screen reading as a *capability* — that answers
 * "is it working", which is a different question and is why the count over there still includes it.
 *
 * Screen reading is last on purpose. Its disclosure is the longest thing in Settings, and putting
 * it first would push the two short grants off the bottom of the screen.
 */
@Composable
internal fun PermissionsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onEnableScreenReading: () -> Unit,
) {
    SettingsScaffold(title = "Permissions", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "${state.permissionsGranted} of 3 granted",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(
                        start = Spacing.Gutter,
                        end = Spacing.Gutter,
                        top = Spacing.Sm,
                    )
                    // Polite, for the same reason as the count on Entry points: every grant here is
                    // made in system Settings, so this sentence changes on a resume the user did
                    // not do anything on this screen to cause.
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            Text(
                // "each one buys one thing, named below" was the screen describing its own
                // layout, which the layout does on its own.
                text = "None of these is asked for on launch, and ArcX keeps working without " +
                    "all three.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Spacing.Gutter,
                    end = Spacing.Gutter,
                    top = Spacing.Xs,
                ),
            )
            Spacer(Modifier.height(Spacing.Md))

            // No SectionHeader over any of the three, unlike Appearance or Privacy. Those screens
            // hold four subjects each and their groups need naming; this one holds a single
            // subject, and each group here is one row that already carries the permission's name.
            // "Draw over other apps" over a row reading "Drawing over other apps" said it twice —
            // to the eye as a repeated line, and to a screen reader as an extra stop now that
            // SectionHeader is a real heading. The introduction is the sentence above: each of
            // these buys one thing, named below.
            SettingsGroup {
                SettingsRow(
                    title = "Drawing over other apps",
                    subtitle = if (state.permissions.overlayGranted) {
                        "Granted — the edge sidebar can appear over whatever you are in"
                    } else {
                        "Not granted, so the edge sidebar has nowhere to appear"
                    },
                    icon = Icons.Outlined.Layers,
                    trailing = {
                        if (!state.permissions.overlayGranted) {
                            TextButton(onClick = onOpenOverlaySettings) { Text("Grant") }
                        }
                    },
                )
            }
            SettingsNote("Only the edge sidebar needs this — every other way in works without it.")

            // Whether there is still a dialog to show. Android puts POST_NOTIFICATIONS up once and
            // never again, and below Android 13 there was never a dialog at all — notifications are
            // switched off in system settings, which is then the only place they can be switched
            // back on. Offering "Allow" in either case is a button that provably does nothing,
            // which is what this row did for a whole session's worth of users.
            val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !state.notificationsBlocked
            SettingsGroup {
                SettingsRow(
                    title = "Notifications",
                    subtitle = when {
                        state.permissions.notificationsEnabled -> "Allowed"
                        canAsk -> "Not allowed yet"
                        else -> "Turned off in system settings"
                    },
                    icon = Icons.Outlined.Notifications,
                    trailing = {
                        if (!state.permissions.notificationsEnabled) {
                            TextButton(
                                onClick = if (canAsk) {
                                    onRequestNotifications
                                } else {
                                    onOpenNotificationSettings
                                },
                            ) {
                                Text(if (canAsk) "Allow" else "Open settings")
                            }
                        }
                    },
                )
            }
            // Two things need it, so it is written as two things rather than as a sentence with
            // a subordinate clause carrying the second one.
            SettingsNote(
                "Needed by workflows that answer with a notification, and by the edge sidebar — " +
                    "Android runs it behind an ongoing notification.",
            )

            // The disclosure sits above the control, which is the placement Play's policy asks for:
            // in front of the user before they act, never underneath the button it describes. The
            // first tap on that button also puts it up on its own screen with an accept action —
            // see ScreenReadingDisclosureScreen — because a disclosure only reachable by walking
            // into Settings is the placement the policy names as not acceptable.
            ScreenReadingDisclosure()
            SettingsGroup {
                // The one control in ArcX that opens Android's Accessibility screen. Everything
                // else that wants the permission — the Entry points list, Home's screen-text chip —
                // routes here, so there is a single place where the disclosure can be guaranteed to
                // have been seen first.
                //
                // The subtitle names Accessibility because that is where Android keeps this and
                // ArcX's own name for it appears nowhere in system Settings. The quoted entry is
                // arcx_accessibility_label in :integration:entrypoints; keep the two in step.
                SettingsRow(
                    title = "Screen reading",
                    subtitle = if (state.permissions.screenReadingEnabled) {
                        "Granted — Android lists it under Accessibility as \"ArcX screen reading\""
                    } else {
                        "Not granted — turn on \"ArcX screen reading\" under Accessibility to let " +
                            "workflows use what is on screen"
                    },
                    icon = Icons.Outlined.Accessibility,
                    trailing = {
                        TextButton(onClick = onEnableScreenReading) {
                            Text(if (state.permissions.screenReadingEnabled) "Manage" else "Enable")
                        }
                    },
                )
            }
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }
}

/**
 * The disclosure, shown on its own with an explicit accept before ArcX will send anyone to the
 * system Accessibility screen.
 *
 * Play's User Data policy is specific about all three halves of this: the disclosure has to be
 * reachable in normal use rather than only by digging through a menu, the user has to *act* to
 * accept it, and dismissing or navigating away must not be taken as consent. So this screen is
 * pushed the moment the user first asks to enable screen reading, its accept button is the only
 * thing that both records consent and opens the grant, and backing out of it writes nothing.
 *
 * The buttons sit at the end of the scroll rather than pinned to the bottom, so reaching accept
 * means having scrolled past the text it is agreeing to.
 */
@Composable
internal fun ScreenReadingDisclosureScreen(
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    SettingsScaffold(title = "Screen reading", onBack = onCancel) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Before you turn this on",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm)
                    .semantics { heading() },
            )
            ScreenReadingDisclosure()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter, vertical = Spacing.Md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Not now") }
                Spacer(Modifier.width(Spacing.Sm))
                Button(onClick = onAccept) { Text("I agree, open settings") }
            }
            SettingsNote(
                "Agreeing here opens Android's Accessibility screen, where you still have to turn " +
                    "\"ArcX screen reading\" on yourself. Nothing is granted by this button.",
            )
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }
}

/**
 * The disclosure text, in one composable used by both places that show it.
 *
 * `arcx_accessibility_description` verbatim, never a paraphrase: Android renders the same string on
 * its own Accessibility screen, and two descriptions of one permission that differ by a word is
 * exactly what a reviewer reads as a misleading disclosure.
 */
@Composable
private fun ScreenReadingDisclosure() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Xs),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(Spacing.Lg)) {
            // Names the permission, because on Permissions this card is now the only thing
            // introducing the group under it — the "Screen reading" heading that used to sit above
            // it was repeating the row's own title. "This permission" was only ever unambiguous on
            // the disclosure screen, where the app bar says which one.
            Text("What screen reading does", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.Sm))
            Text(
                stringResource(DesignSystemR.string.arcx_accessibility_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
