package com.arcx.feature.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.ArcxListRow
import com.arcx.core.designsystem.component.ArcxListRowIconSize
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.LoadingState
import com.arcx.core.designsystem.component.NoticeCard
import com.arcx.core.designsystem.component.NoticeSeverity
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.designsystem.component.TintedIcon
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.format.formatDuration
import com.arcx.core.designsystem.format.relativeTime
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.warningTint
import com.arcx.core.model.RunStatus
import com.arcx.core.model.RunSummary

/**
 * 18dp beside a `titleSmall` row, 16dp inside a notice — icon sizes, which are not spacing and are
 * deliberately absent from the [Spacing] scale. Named here so the three glyphs on this screen
 * cannot drift the way the five list rows once did.
 */
private val StatusIconSize = 18.dp
private val InlineGlyphSize = 16.dp

@Composable
fun HomeRoute(
    onCreateWorkflow: () -> Unit,
    onSeeActivity: () -> Unit,
    /** Opens Activity with this run's detail sheet — where its error, and Run again, already live. */
    onOpenRun: (String) -> Unit,
    onOpenSettings: () -> Unit,
    /** Called with the subject of the row that was tapped; see [SurfaceSubject]. */
    onOpenSurfaceSettings: (SurfaceSubject) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    HomeScreen(
        state = state,
        onCreateWorkflow = onCreateWorkflow,
        onSeeActivity = onSeeActivity,
        onOpenRun = onOpenRun,
        onOpenSettings = onOpenSettings,
        onOpenSurfaceSettings = onOpenSurfaceSettings,
    )
}

/**
 * The status screen: is ArcX reachable right now, and what did it just do.
 *
 * Nothing here runs a workflow, and that is the point rather than an omission. A run fired from
 * Home has no selection to read and no other app's screen to look at — ArcX's own window is what is
 * in front — so the input silently degrades to the clipboard, and "summarise what's on screen"
 * summarises ArcX. Every honest way to start a run is somewhere else, which is what the list of
 * ways in is for.
 *
 * Four situations, each drawn deliberately, because a status screen is only as good as its worst
 * state:
 *
 *  1. **No provider.** Nothing downstream can happen, so nothing downstream is shown. Four ways in
 *     reported as "on" above a prompt that cannot be answered would be four true statements adding
 *     up to a lie.
 *  2. **Something broke.** A card per broken surface, above everything, each one tap from the
 *     screen that owns its fix.
 *  3. **Healthy, with runs.** No amber anywhere, because nothing is wrong. The two lists and
 *     nothing else.
 *  4. **Healthy, nothing run yet.** The one state where a suggestion earns its place — and the
 *     suggestion names a way in that is working *now*, not the app the user is already looking at.
 */
@Composable
private fun HomeScreen(
    state: HomeUiState,
    onCreateWorkflow: () -> Unit,
    onSeeActivity: () -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSurfaceSettings: (SurfaceSubject) -> Unit,
) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.Xxl),
        ) {
            item(key = "header") {
                Header(
                    greeting = state.greeting,
                    // Blank until the first read lands. "0 workflows · 0 runs today" is a claim,
                    // and on a cold start it is one this screen has no basis for yet.
                    subtitle = if (state.loading) "" else subtitleFor(state),
                    onOpenSettings = onOpenSettings,
                )
            }

            when {
                // Ahead of every empty state: a failed read leaves this screen exactly as bare as
                // a fresh install does, and "no provider connected" would be blaming the user's
                // setup for the app's problem.
                state.error != null -> item(key = "error") {
                    ErrorCard(
                        title = "Couldn't load your workflows",
                        message = state.error,
                        modifier = Modifier.padding(
                            horizontal = Spacing.Gutter,
                            vertical = Spacing.Sm,
                        ),
                    )
                }

                // Not a blank screen. An unconfigured ArcX and one whose first read is still in
                // flight look identical, and one of the two is an accusation.
                state.loading -> item(key = "loading") { LoadingState() }

                // State 1. The whole screen, because everything else is downstream of this.
                !state.providerReady -> item(key = "no-provider") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "No provider connected",
                        body = "ArcX runs on your own API key and has nowhere to send a prompt " +
                            "yet. Connect a provider and every way into ArcX starts working at " +
                            "once.",
                        actionLabel = "Connect a provider",
                        onAction = onOpenSettings,
                    )
                }

                // A library emptied out by hand. Starters are installed on first launch, so this is
                // rare — but the ways in below would all lead to an empty picker, so it is the same
                // kind of dead end as having no provider and gets the same treatment.
                state.workflowCount == 0 -> item(key = "no-workflows") {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "No workflows yet",
                        body = "Build an AI action once — rewrite, summarise, translate — then " +
                            "fire it from anywhere on your phone.",
                        actionLabel = "Create your first workflow",
                        onAction = onCreateWorkflow,
                    )
                }

                else -> {
                    // State 2, above everything else it would otherwise be buried under. One card
                    // per broken surface rather than one card summarising them, so each carries the
                    // remedy that actually applies to it and each reaches its own fix in one tap.
                    // There have never been more than two at once; there are only two that can
                    // break at all.
                    items(
                        items = state.surfaces.filter { it.state == SurfaceState.BROKEN },
                        key = { "broken-${it.subject}" },
                    ) { surface ->
                        NoticeCard(
                            severity = NoticeSeverity.Warning,
                            title = "${surface.label} has stopped",
                            message = surface.remedy.orEmpty(),
                            icon = Icons.Outlined.ErrorOutline,
                            actionLabel = "Fix this",
                            onAction = { onOpenSurfaceSettings(surface.subject) },
                            // Fading and settling rather than snapping. This card's whole life is
                            // the round trip out to system Settings and back: onResume re-reads the
                            // permission, so it disappears the moment the user returns having
                            // granted it, and the list under it moves up by a card's height.
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(Motion.Medium, easing = Motion.Standard),
                                    placementSpec = tween(
                                        Motion.Emphasis,
                                        easing = Motion.Decelerate,
                                    ),
                                    fadeOutSpec = tween(Motion.Fast, easing = Motion.Accelerate),
                                )
                                .padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
                        )
                    }

                    // "Ways in", the same words Entry points uses for the same six things. The
                    // sketch this came from said "Ready", which stops being true the moment one of
                    // them is not — a heading should not have to be re-read to be believed.
                    item(key = "ways-in") { SectionHeader("Ways in") }
                    itemsIndexed(
                        items = state.surfaces,
                        key = { _, surface -> "surface-${surface.subject}" },
                    ) { index, surface ->
                        SurfaceRow(
                            status = surface,
                            showDivider = index < state.surfaces.lastIndex,
                            onClick = { onOpenSurfaceSettings(surface.subject) },
                        )
                    }

                    if (state.recentRuns.isEmpty()) {
                        // State 4. No heading above it: "Recent" over an empty box is a section
                        // that failed to load, and this is not one.
                        item(key = "no-runs") {
                            val sidebarLive = state.surfaces.any {
                                it.subject == SurfaceSubject.SIDEBAR &&
                                    it.state == SurfaceState.LIVE
                            }
                            EmptyState(
                                icon = Icons.Outlined.Bolt,
                                title = "Nothing has run yet",
                                body = firstRunHint(sidebarLive),
                                // Offered only when there is something left to set up. With the
                                // sidebar already out, the sentence above names two ways in that
                                // work this second, and a button would be sending the user off to
                                // change a setting that is already right.
                                actionLabel = if (sidebarLive) null else "Turn on the sidebar",
                                onAction = if (sidebarLive) {
                                    null
                                } else {
                                    { onOpenSurfaceSettings(SurfaceSubject.SIDEBAR) }
                                },
                            )
                        }
                    } else {
                        item(key = "recent") {
                            SectionHeader(
                                title = "Recent",
                                actionLabel = "See all",
                                onAction = onSeeActivity,
                            )
                        }
                        recentRuns(state.recentRuns, state.nowMillis, onOpenRun)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(greeting: String, subtitle: String, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.Gutter, end = Spacing.Sm, top = Spacing.Md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Spacing.Xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The only way into Settings in the whole app — it left the bottom bar to give the four
        // tabs their width back.
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One way in, as a row rather than the chip it used to be.
 *
 * The strip of chips this replaces could only ever say a word; a row has room for what the surface
 * is *for*, which is the difference between reporting a state and being usable by someone who has
 * not read Settings. [ArcxListRow] brings the 48dp target and `Role.Button` with it.
 *
 * The state is a word — "On", "Always on", "Off", "Not working" — not only a colour and a tint, so
 * a screen reader hears it as part of the row and nobody has to distinguish amber from primary.
 * That is the same guarantee `SurfaceRow` on Entry points buys with `stateDescription`, taken here
 * by simply saying it out loud, which has the advantage of also being visible.
 */
@Composable
private fun SurfaceRow(status: SurfaceStatus, showDivider: Boolean, onClick: () -> Unit) {
    val warning = warningTint()
    val broken = status.state == SurfaceState.BROKEN
    val working = status.state == SurfaceState.LIVE || status.state == SurfaceState.ALWAYS_ON
    val accent = when {
        broken -> warning.content
        working -> MaterialTheme.colorScheme.primary
        // An "off" surface is a decision the user made, so it is drawn as calmly as one. Amber
        // here would mean the screen nags about four choices and has nothing louder left for the
        // one thing that is actually wrong.
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ArcxListRow(
        title = status.label,
        leading = {
            TintedIcon(
                icon = status.subject.icon(),
                container = when {
                    broken -> warning.container
                    working -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                content = accent,
                size = ArcxListRowIconSize,
            )
        },
        subtitle = {
            Text(
                text = status.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
            ) {
                if (broken) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(InlineGlyphSize),
                    )
                }
                Text(
                    text = status.state.word(),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
        },
        onClick = onClick,
        showDivider = showDivider,
    )
}

/**
 * The last few runs. Tapping one opens it in Activity, where the full error, the input it was
 * given and "Run again" already live — a second copy of that sheet on Home would be a second
 * answer to the same question, free to drift from the first.
 *
 * There is no re-run button here, unlike the strip this replaces. Re-running from Home is the one
 * context in which a workflow cannot see what it was written to act on.
 */
private fun LazyListScope.recentRuns(
    runs: List<RunSummary>,
    nowMillis: Long,
    onOpenRun: (String) -> Unit,
) {
    itemsIndexed(runs, key = { _, run -> "run-${run.id}" }) { index, run ->
        val failed = run.status == RunStatus.FAILED
        val warning = warningTint()
        ArcxListRow(
            title = run.workflowName,
            // Five rows, so unlike Activity's thousand this costs nothing — and every completed
            // run pushes all of them down by one, which is a move worth watching rather than
            // a list that flinches.
            modifier = Modifier.animateItem(
                fadeInSpec = tween(Motion.Medium, easing = Motion.Standard),
                placementSpec = tween(Motion.Emphasis, easing = Motion.Decelerate),
                fadeOutSpec = tween(Motion.Fast, easing = Motion.Accelerate),
            ),
            leading = {
                WorkflowIcon(
                    icon = run.workflowIcon,
                    size = ArcxListRowIconSize,
                    container = if (failed) {
                        warning.container
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                )
            },
            subtitle = {
                Text(
                    text = runSubtitle(run, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) {
                        warning.content
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailing = {
                // A failure's duration says nothing useful, so the row spends that space on why —
                // the same trade Activity's own list makes.
                if (!failed) {
                    Text(
                        text = formatDuration(run.durationMs),
                        style = MetaTextStyle,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(Spacing.Sm))
                }
                StatusIcon(run.status)
            },
            onClick = { onOpenRun(run.id) },
            showDivider = index < runs.lastIndex,
        )
    }
}

/** Described, never only tinted: green tick and amber cross are the same glyph to a screen reader. */
@Composable
private fun StatusIcon(status: RunStatus) {
    Icon(
        imageVector = when (status) {
            RunStatus.SUCCESS -> Icons.Outlined.CheckCircle
            RunStatus.FAILED -> Icons.Outlined.ErrorOutline
            RunStatus.CANCELLED -> Icons.Outlined.Cancel
        },
        contentDescription = when (status) {
            RunStatus.SUCCESS -> "Succeeded"
            RunStatus.FAILED -> "Failed"
            RunStatus.CANCELLED -> "Cancelled"
        },
        modifier = Modifier.size(StatusIconSize),
        tint = when (status) {
            RunStatus.SUCCESS -> MaterialTheme.colorScheme.primary
            RunStatus.FAILED -> warningTint().content
            RunStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** The same glyphs Entry points draws for the same six things, so one screen teaches the other. */
private fun SurfaceSubject.icon(): ImageVector = when (this) {
    SurfaceSubject.SIDEBAR -> Icons.AutoMirrored.Outlined.ViewSidebar
    SurfaceSubject.SHARE -> Icons.Outlined.Share
    SurfaceSubject.SELECTION -> Icons.Outlined.TextFields
    SurfaceSubject.SCREEN_TEXT -> Icons.Outlined.Visibility
}

private fun SurfaceState.word(): String = when (this) {
    SurfaceState.LIVE -> "On"
    SurfaceState.ALWAYS_ON -> "Always on"
    SurfaceState.OFF -> "Off"
    // Not "Off". The user did switch this on; saying otherwise sends them to turn on something
    // the system already insists is on, which is the loop this wording exists to break.
    SurfaceState.BROKEN -> "Not working"
}

/**
 * What to do first, naming a way in that works *right now*.
 *
 * Generic advice would be worse than none here: telling someone to swipe out a sidebar they have
 * switched off is how a first-run hint teaches a user that the app is lying to them. The selection
 * menu is the fallback because the manifest guarantees it — it is the one suggestion that can never
 * be wrong.
 */
private fun firstRunHint(sidebarLive: Boolean): String {
    val opener = if (sidebarLive) {
        "Swipe out the sidebar from the edge of any app, or highlight some text and pick ArcX " +
            "from the popup."
    } else {
        "Highlight some text in any app and pick ArcX from the popup, or share a page to ArcX."
    }
    return "$opener Whatever you fire, and wherever from, lands here."
}

private fun subtitleFor(state: HomeUiState): String {
    val workflows = "${state.workflowCount} " +
        if (state.workflowCount == 1) "workflow" else "workflows"
    val runs = "${state.runsToday} " + if (state.runsToday == 1) "run" else "runs"
    return "$workflows · $runs today"
}

private fun runSubtitle(run: RunSummary, nowMillis: Long): String {
    // A failed run leads with why it failed. The mapper has already cut the error to one line, and
    // that line is the only part of a failure worth a list row — the rest is in the sheet a tap
    // away. The model name a success shows is worthless here: nothing failed for want of it.
    val detail = if (run.status == RunStatus.FAILED) {
        run.error.orEmpty().ifBlank { "failed" }
    } else {
        run.model.ifBlank { run.providerLabel }.ifBlank { "no provider" }
    }
    return "${relativeTime(run.startedAt, nowMillis)} · $detail"
}
