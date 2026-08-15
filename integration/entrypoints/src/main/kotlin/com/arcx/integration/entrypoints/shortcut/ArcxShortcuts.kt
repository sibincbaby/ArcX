package com.arcx.integration.entrypoints.shortcut

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.arcx.core.model.Workflow
import com.arcx.integration.entrypoints.ArcxDeepLinks

/**
 * Launcher shortcuts: the long-press menu on the app icon, and workflows the user has dragged onto
 * the home screen themselves.
 *
 * Every call here is best-effort. Launchers are third-party apps and the shortcut APIs throw for
 * reasons ArcX cannot control — a rate limit, a launcher that does not implement pinning, a device
 * in direct-boot. None of that is worth surfacing to the user, so failures are swallowed and the
 * shortcut simply does not appear.
 */
object ArcxShortcuts {

    /**
     * The platform's own limit is higher, but four is what every launcher actually shows in a
     * long-press menu before it starts scrolling or truncating.
     */
    const val MAX_DYNAMIC: Int = 4

    private const val ID_PREFIX = "arcx_workflow_"

    /** Launchers ellipsise beyond roughly this; cutting it here avoids a mid-word "…". */
    private const val SHORT_LABEL_MAX = 20

    /** Replaces the published set wholesale — the caller passes the user's current favourites. */
    fun publishFavorites(context: Context, workflows: List<Workflow>) {
        val shortcuts = workflows.take(MAX_DYNAMIC).mapIndexed { index, workflow ->
            build(context, workflow, rank = index)
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    /**
     * Asks the launcher to show its "add to home screen" confirmation. Returns false when the
     * launcher does not support pinning at all (most third-party ones on pre-Oreo semantics), which
     * is the caller's cue to hide the option rather than offer something that will not happen.
     */
    /** Whether the current launcher implements pinning; the caller hides the option when false. */
    fun canPin(context: Context): Boolean =
        runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }
            .getOrDefault(false)

    fun requestPinShortcut(context: Context, workflow: Workflow): Boolean {
        if (!canPin(context)) return false
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, build(context, workflow, rank = 0), null)
        }.getOrDefault(false)
    }

    fun clearDynamic(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }

    private fun build(context: Context, workflow: Workflow, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID_PREFIX + workflow.id)
            .setShortLabel(workflow.name.take(SHORT_LABEL_MAX).ifBlank { "ArcX" })
            .setLongLabel(workflow.name.ifBlank { "ArcX" })
            .setRank(rank)
            .setIcon(WorkflowIconBitmap.adaptive(workflow.icon, context.resources.displayMetrics.density))
            // A pinned shortcut outlives the app that made it, so this has to be the same deep link
            // every other surface uses rather than an Activity reference that a refactor could move.
            .setIntent(ArcxDeepLinks.intent(context, workflow.id))
            .build()
}
