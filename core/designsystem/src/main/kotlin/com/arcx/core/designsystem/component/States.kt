package com.arcx.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.warningTint

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.Lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.Xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.Xl))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The gap between "nothing to show yet" and "here it is". All four tabs drew a blank screen
 * while their first read was in flight — every one of them had a bare `state.loading -> Unit`
 * branch, which is indistinguishable from an empty library on a slow first frame.
 *
 * A [CircularProgressIndicator], not the [StreamingIndicator] dots, and not because the dots look
 * wrong here. The dots are the one animation in ArcX that means something specific — a model is
 * producing tokens — and spending them on a Room read would leave a user unable to tell a
 * database from a provider. The spinner is also the only one of the two that a screen reader
 * announces, because it carries the platform's own progress semantics; three animated boxes are
 * silent.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    /** Say what is being waited for when the wait can be long enough to wonder. */
    label: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(28.dp))
        if (label != null) {
            Spacer(Modifier.height(Spacing.Md))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Which of the two things a notice can be. There is no third: ArcX either failed at something it
 * promised to do, or it is telling the user about the platform behaving as designed.
 */
enum class NoticeSeverity {
    /** Advice, a caution, a platform limitation. Nothing is broken and nothing is blocked. */
    Warning,

    /** ArcX tried and could not. A read failed, a run failed, a key was rejected. */
    Error,
}

/**
 * One surface for "there is something you should know".
 *
 * There were three, for two meanings: the error card in red `errorContainer`, the entry-points
 * warning in the amber [warningTint], and the editor's advice in `tertiaryContainer` — the same
 * caution wearing two different colours depending on which screen it appeared on.
 *
 * Amber carries both warnings, and `tertiaryContainer` carries neither. Amber is already ArcX's
 * "this is off, not broken" colour and it is a fixed hue; `tertiaryContainer` is a Material You
 * slot, so under a user's wallpaper it can land close enough to primary that a caution reads as
 * an ordinary highlight — which is the one thing a caution must never do.
 *
 * Takes plain strings rather than a domain error type so the design system stays independent of
 * `:core:model`.
 */
@Composable
fun NoticeCard(
    severity: NoticeSeverity,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /**
     * The rest of the story, under the message — today, an [ExpandableNote]. A caution whose
     * mechanism runs to a paragraph belongs inside the card that raises it rather than stranded
     * below it, where it would read as a note about the next group down.
     *
     * Warnings only. An error card merges its descendants into one live region so the whole card
     * is announced at once, and that would swallow a disclosure header's own role and state.
     */
    details: (@Composable () -> Unit)? = null,
) {
    val warning = warningTint()
    val container = when (severity) {
        NoticeSeverity.Warning -> warning.container
        NoticeSeverity.Error -> MaterialTheme.colorScheme.errorContainer
    }
    // What the title and the icon are drawn in. On an error the whole card is already the error
    // colour, so the accent is just its content colour; on a warning the wash is a low-alpha tint
    // over the live surface, and the amber has to be stated.
    val accent = when (severity) {
        NoticeSeverity.Warning -> warning.content
        NoticeSeverity.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    val body = when (severity) {
        NoticeSeverity.Warning -> MaterialTheme.colorScheme.onSurfaceVariant
        NoticeSeverity.Error -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // An error card is always the answer to something the user just did, and it is as
                // often as not drawn below whatever they were looking at — so on a screen reader
                // it currently arrives silently and the run simply appears to have gone nowhere.
                // Polite, so it waits for whatever TalkBack is already mid-sentence on.
                //
                // Errors only. A warning here is standing advice that was on the screen before the
                // user arrived — Entry points' platform caution, the editor's — and announcing
                // those on every visit is noise rather than news.
                if (severity == NoticeSeverity.Error) {
                    // Merged so the announcement is the whole card. Left unmerged the live node
                    // has no text of its own and there is nothing to say; the action button sets
                    // its own semantics and stays a separate stop either way.
                    Modifier.semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                    }
                } else {
                    Modifier
                },
            ),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = body,
        // A warning's wash is thin enough to disappear against a busy surface; the error's is not.
        border = when (severity) {
            NoticeSeverity.Warning -> BorderStroke(1.dp, accent.copy(alpha = 0.3f))
            NoticeSeverity.Error -> null
        },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    // Nudged down to sit on the title's baseline rather than above its cap height.
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(20.dp),
                )
            }
            Column {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
                    Spacer(Modifier.height(Spacing.Xs))
                }
                // bodyMedium, not the bodySmall two of the three copies used: a notice is the one
                // thing on screen the user has to actually read rather than scan.
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (details != null) {
                    Spacer(Modifier.height(Spacing.Xs))
                    details()
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(Spacing.Sm))
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

/**
 * Kept so the failure surfaces already in the features keep compiling; it is [NoticeCard] with
 * the severity filled in.
 */
@Composable
fun ErrorCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    NoticeCard(
        severity = NoticeSeverity.Error,
        message = message,
        modifier = modifier,
        title = title,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The gutter, not the card interior. A heading names the group under it, so it has to
            // start where that group starts — and Settings' groups moved from 16 to 20 when they
            // adopted the scale, which left every heading on Appearance, Privacy and About sitting
            // 4dp left of the card it labels.
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // A heading to the platform as well as to the eye. Without this a screen reader has
            // no way through Appearance, Privacy or Entry points but one row at a time — heading
            // navigation is the gesture that skips a group, and it can only find groups that say
            // they are one. On the Text rather than the Row so the heading node is the one
            // carrying the title; a heading with no text of its own is one TalkBack lands on and
            // then has nothing to read.
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
