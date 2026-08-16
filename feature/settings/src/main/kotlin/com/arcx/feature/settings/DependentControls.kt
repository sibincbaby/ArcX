package com.arcx.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * A run of controls that only means something while something else is on — drawn either way, and
 * greyed out with the reason stated above it when it is not.
 *
 * Hiding them was the previous behaviour and it is the one thing not to do here. The five sidebar
 * sliders vanished along with the sidebar switch, so a user who wanted the strip thinner first had
 * to guess that turning the sidebar on would produce controls they had never seen. Material, AOSP's
 * own settings and Nielsen all land in the same place: a disabled control that says why is
 * discoverable, and a control that is not drawn cannot be looked for.
 *
 * The scrim is how a whole group is disabled at once. [SettingsSlider] takes no `enabled` flag and
 * lives in a file this change does not own, so touches are swallowed on the way *down* — the
 * Initial pass, before any child sees them — rather than by asking each control to refuse them.
 * `disabled()` semantics go on the group so a screen reader announces what the dimming shows.
 */
@Composable
internal fun DependentControls(
    enabled: Boolean,
    reason: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        // Above the controls, not below: it is the answer to "why can I not move this", and that
        // question is asked before the finger lands, not after.
        if (!enabled) SettingsNote(reason)
        Box {
            Column(
                Modifier
                    .alpha(if (enabled) 1f else DisabledAlpha)
                    .then(if (enabled) Modifier else Modifier.semantics { disabled() }),
                content = content,
            )
            if (!enabled) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                        .changes
                                        .forEach { it.consume() }
                                }
                            }
                        },
                )
            }
        }
    }
}

/** Material's disabled-content opacity, which nothing in the theme exposes as a token. */
private const val DisabledAlpha = 0.38f
