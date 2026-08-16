package com.arcx.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.ArcxListRowIconSize
import com.arcx.core.designsystem.theme.Spacing
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
            )
        },
        content = content,
    )
}

/** A rounded container that turns a run of rows into one visual group. */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 6dp is off the scale on purpose: two stacked groups meet at 12dp, which is the gap
            // that belongs between them. Halving a gap is the one place a half-step is right.
            .padding(horizontal = Spacing.Gutter, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = Spacing.Xs), content = content)
    }
}

/**
 * The width every settings row reserves for its leading icon, whether that icon is a bare glyph
 * or a tinted tile.
 *
 * It exists because Entry points draws two row anatomies on one screen — [SettingsRow] with a
 * 24dp glyph and its own SurfaceRow with a 38dp tinted tile — and they put their titles 12dp
 * apart, so two rows about the same permission did not line up with each other. Reserving one
 * column and centring whatever is smaller inside it fixes that without shrinking the tile, which
 * carries the live/off tint and is the point of that row.
 *
 * [ArcxListRowIconSize] rather than a number of its own: that is the column the library, the
 * gallery, Activity and the runner's picker already lead with, so settings titles now start where
 * every other list's do.
 */
internal val SettingsRowIconSize: Dp = ArcxListRowIconSize

@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // A settings row is tall enough already; the floor is here so a row that ever loses
            // its subtitle cannot quietly drop under the 48dp minimum. Role, because a bare
            // `clickable` announces nothing — the same pairing the shared primitives use.
            .then(
                if (onClick != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            // The card's interior, not the screen gutter — SettingsGroup already stated that one
            // further out, and a row is only ever drawn inside a group. 14dp vertical is between
            // two steps and stays there: it puts a two-line row on the Material list-item rhythm.
            .padding(horizontal = Spacing.Lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // Centred in a fixed column rather than padded on one side: the glyph is 24dp and the
            // tinted tile beside it on Entry points is 38dp, so only a shared column can line the
            // two title runs up. The gap then matches ArcxListRow's, which is Md.
            Box(
                modifier = Modifier.size(SettingsRowIconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.Md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.Md))
            trailing()
        }
    }
}

/**
 * One setting, one control.
 *
 * It used to be two. The row took a `clickable` with [Role.Button] *and* the [Switch] kept a live
 * `onCheckedChange`, which is two focusable nodes for one preference: a screen reader stopped
 * first on "Dynamic colour, button" — a button carrying no on or off — and then again on the
 * switch, which had the state but not the name of the thing it belonged to.
 *
 * `toggleable` is the shape Android's guidance gives for exactly this. The row carries the value
 * and the [Role.Switch], so the whole row announces "Dynamic colour, on, switch" and one gesture
 * flips it; the Switch keeps drawing the state and stops claiming to own it.
 *
 * Handed in as the modifier rather than through [SettingsRow]'s `onClick`, because that one can
 * only ever be a button. It lands in the same place in the chain — outside `fillMaxWidth` and the
 * row's own padding — so the ripple still covers the whole row and nothing moves by a pixel.
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        modifier = modifier
            // Kept from the clickable it replaces: a row that ever loses its subtitle must not
            // quietly drop under the 48dp minimum.
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        subtitle = subtitle,
        icon = icon,
        onClick = null,
        // null, not a second handler. The row is the control; a Switch with its own callback is
        // the second focusable node this whole comment is about.
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * One number behind a live surface, dragged live.
 *
 * The thumb is driven from local state and the setting is written only when the drag crosses a
 * step the value is actually stored at — a whole dp, a whole percent. Driving the thumb straight
 * from the stored value would put a DataStore write and a Flow round trip inside the gesture,
 * which shows as the thumb trailing the finger; writing every intermediate float would rewrite
 * the preferences file a few hundred times for one drag. Stepping keeps the live redraw and bounds
 * the writes at one per step — 152 for the longest drag in Entry points, and 100 for the fractions.
 *
 * Here rather than beside the sidebar sliders it was written for, because Appearance's
 * transparency slider is the same control with the same two-value dance, and a second copy of this
 * is a second place for that dance to be got subtly wrong.
 *
 * The opt-in buys the `track` slot, which is the only way to declare `steps` without also getting
 * Material's tick marks — see the comment on it below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    supporting: String,
    onValueChange: (Float) -> Unit,
) {
    var thumb by remember { mutableFloatStateOf(value) }
    var written by remember { mutableFloatStateOf(value) }
    // Re-seed only when the stored value moved without this slider moving it — a reset elsewhere,
    // or the clamp in SettingsRepositoryImpl refusing what was asked for. Re-seeding on the echo
    // of this slider's own write would jump the thumb out from under the finger mid-drag.
    LaunchedEffect(value) {
        if (value != written) {
            thumb = value
            written = value
        }
    }

    // Material counts the values *between* the ends, so a 1% step across 0..85% is 84 of them.
    // Declared rather than left at 0: with no steps the platform makes one up — TalkBack, a
    // keyboard and Switch Access all adjust by a twentieth of the range, so a swipe moved the
    // transparency by about 4% when the control's own step, and the number beside it, is 1%.
    val steps = remember(valueRange, step) {
        (((valueRange.endInclusive - valueRange.start) / step).roundToInt() - 1).coerceAtLeast(0)
    }

    Column(Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Sm)) {
        Row(
            // The title and the number are this slider's label and its value, and the Slider below
            // now says both. Left as nodes of their own they are two more swipes to get past and
            // one of them announces a bare "40%" belonging to nothing.
            modifier = Modifier.clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // The committed value, not the raw thumb: the number and the surface should never
            // disagree, and the surface only ever sees committed values.
            Text(
                text = format(written),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            modifier = Modifier.semantics {
                contentDescription = title
                // The row's own number, said with the control instead of beside it. Slider would
                // otherwise announce a percentage of the range — which is the same thing for the
                // fractions and quite wrong for the two sliders measured in dp, where 152dp of
                // 100..252 would be read out as "34%".
                stateDescription = format(written)
            },
            value = thumb,
            onValueChange = { raw ->
                thumb = raw
                val stepped = (round(raw / step) * step).coerceIn(valueRange)
                if (stepped != written) {
                    written = stepped
                    onValueChange(stepped)
                }
            },
            valueRange = valueRange,
            steps = steps,
            track = { sliderState ->
                // steps is declared for the stepping, not for the scenery. Material draws a tick
                // per step by default, and at 1% that is 84 dots on a 300dp bar — a dashed line,
                // not a scale. Drawing none of them leaves the track exactly as it was.
                SliderDefaults.Track(sliderState = sliderState, drawTick = { _, _ -> })
            },
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Every fraction is stored and shown as whole percent, so that is the step they all move in. */
internal const val PercentStep = 0.01f

internal fun percentLabel(value: Float): String = "${(value * 100).roundToInt()}%"

/** Quiet explanatory copy; the BYOK story needs saying, not shouting. */
@Composable
internal fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Deliberately not the gutter: a note is subordinate to the group above it and is indented
        // past that edge to say so. It happens to be the top step of the scale, not a screen inset.
        modifier = modifier.padding(horizontal = Spacing.Xxxl, vertical = Spacing.Sm),
    )
}
