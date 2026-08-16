package com.arcx.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.Spacing

/**
 * A quiet all-caps divider between runs of rows. Louder than nothing, quieter than a heading —
 * to the eye. To a screen reader it is a heading, because it does a heading's job: it names the
 * run of rows under it, and Entry points, the library and Activity are all long enough that
 * without one there is no way past a group but through every row in it.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp,
        modifier = modifier
            .semantics { heading() }
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
    )
}

/**
 * A workflow's wiring in one line: where its content comes from, where the answer goes. Two
 * mono chips and an arrow rather than a sentence, because in a dense list the sentence is the
 * first thing that gets truncated and it is the part worth keeping.
 */
@Composable
fun WiringChips(input: String, output: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WireChip(input)
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "to",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(horizontal = 5.dp)
                .size(12.dp),
        )
        WireChip(output)
    }
}

@Composable
private fun WireChip(text: String) {
    Text(
        text = text,
        style = MetaTextStyle,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * The small squarish pill, in all the jobs it does: the library and gallery filters, the "Live /
 * Off" surface chips on Home, and the gallery's Install button — which was, literally, this
 * component's unselected style rebuilt in a feature module.
 *
 * Three sets of numbers collapsed into one here. All three were 32dp tall (now a floor, not a
 * fixed height, so a large font scale grows the pill instead of clipping it) and padded by 13,
 * 13 and 11dp, which is one value pretending to be two.
 *
 * The colour parameters exist for the two callers that are not a filter: a surface chip states
 * its own tint, and Install draws its label in primary while keeping the outlined shell.
 *
 * A null [onClick] makes the pill a label rather than a control — the gallery's "Added" state is
 * this, and it deliberately does not claim a touch target it would do nothing with.
 */
@Composable
fun ArcxPill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    count: Int? = null,
    /** A status dot, a warning icon, a tick. */
    leading: (@Composable () -> Unit)? = null,
    container: Color = Color.Unspecified,
    content: Color = Color.Unspecified,
    /**
     * Whether to draw the edge, or null to infer it from the wash — which is what every filter and
     * the gallery's Install want, and so is the default.
     *
     * It is a parameter because inference cannot express a pill that is transparent *and*
     * borderless. Home's always-on surface chips are exactly that, and had to fake it by asking
     * for the screen's own background colour as their wash — right over a Scaffold, wrong the
     * moment such a pill sits on anything else.
     */
    outlined: Boolean? = null,
    /**
     * Whether [selected] is a state this pill is reporting, or merely the highlight it happens
     * not to have. Only the filter rows are the former, so only [CountPill] asks for it.
     *
     * It changes nothing on screen and everything to a screen reader: without it the wash is the
     * *only* thing saying which filter is on, and a pill announces "Writing 6, button" whether it
     * is the active category or not. With it the same pill says "selected". Off by default
     * because the two pills that are plainly buttons — Home's surface chips, the gallery's
     * Install — would otherwise each announce "not selected", which is an answer to a question
     * nobody asked of them.
     */
    selectable: Boolean = false,
) {
    val shape = MaterialTheme.shapes.small
    val background = when {
        container != Color.Unspecified -> container
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        content != Color.Unspecified -> content
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // The outline is what gives an unfilled pill an edge. A pill with a wash already has one, and
    // drawing both makes it read as selected-and-then-some — which is the inference a caller gets
    // by saying nothing.
    val hasBorder = outlined ?: (background == Color.Transparent)

    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .heightIn(min = PillMinHeight)
            .clip(shape)
            .background(background)
            .then(
                if (hasBorder) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                } else {
                    Modifier
                },
            )
            .then(
                when {
                    onClick == null -> Modifier
                    // Still Role.Button — a filter pill is a button that reports a state, not a
                    // radio in a group, and nothing here promises the group is single-select.
                    // `selectable` adds the state and leaves the role alone.
                    selectable -> Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    else -> Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            )
            .padding(horizontal = Spacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            maxLines = 1,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MetaTextStyle,
                // Quieter than the label it belongs to, whichever colour that label is.
                color = if (selected) foreground.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** All three copies were 32dp; a floor rather than a fixed height so a big font scale survives. */
private val PillMinHeight = 32.dp

/**
 * The filter pill used by the library and the gallery, carrying a count — "Writing 6" answers
 * "is it worth tapping" before the tap. Kept as its own name because that is what the two filter
 * rows mean; it is [ArcxPill] underneath.
 */
@Composable
fun CountPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    ArcxPill(
        label = label,
        modifier = modifier,
        onClick = onClick,
        selected = selected,
        count = count,
        // The one caller for which `selected` is a fact about the list rather than a highlight.
        selectable = true,
    )
}

/** Filled reads as the thing to do; outlined reads as one of the things you may do. */
enum class ArcxActionStyle { Filled, Outlined }

/**
 * The compact action button — denser than a Material [androidx.compose.material3.Button] and
 * squarer than its pill, for the rows of actions under a result and beside the editor's Continue.
 *
 * Four copies of this existed at 38, 38, 32 and 50dp tall with 12, 12, 10 and 16dp corners, and
 * every one of them was a hand-rolled Row with a plain `clickable` — which is how the fastest
 * paths in the product ended up with touch targets under the 48dp minimum. That is fixed here
 * rather than at the four call sites, so nothing built on this can regress it.
 *
 * [content] replaces the icon for the one caller that swaps it for a spinner while a test prompt
 * is in flight.
 */
@Composable
fun ArcxAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    style: ArcxActionStyle = ArcxActionStyle.Outlined,
    enabled: Boolean = true,
    /** Says what an icon-only action does, for the click label and for the icon itself. */
    contentDescription: String? = null,
    contentColor: Color = Color.Unspecified,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val filled = style == ArcxActionStyle.Filled
    val foreground = when {
        contentColor != Color.Unspecified -> contentColor
        filled -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = ActionMinHeight)
            .clip(shape)
            .then(
                if (filled) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                },
            )
            .clickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick,
            )
            // An icon on its own needs less room around it than a word does.
            .padding(horizontal = if (label == null) Spacing.Md else Spacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        when {
            content != null -> content()

            icon != null -> Icon(
                imageVector = icon,
                // Described only when there is no label to describe it — otherwise a screen
                // reader says the same thing twice.
                contentDescription = contentDescription.takeIf { label == null },
                tint = foreground,
                modifier = Modifier.size(ActionIconSize),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
            )
        }
    }
}

/** 40dp: the 38 the two result actions drew, on the grid. A floor, so a label can wrap it taller. */
private val ActionMinHeight = 40.dp

/** 18dp, the icon size the rest of the app uses beside labelLarge text. */
private val ActionIconSize = 18.dp

/**
 * Progress through a short, known sequence. Bars rather than dots: three steps read as a
 * distance to cover, and a dot row reads as a carousel the user is free to swipe past.
 */
@Composable
fun StepBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index <= current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
}

/** A Material icon in a category-tinted tile, the same shape [WorkflowIcon] gives a workflow. */
@Composable
fun TintedIcon(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
