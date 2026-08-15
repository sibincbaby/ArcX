package com.arcx.integration.entrypoints.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.arcx.core.model.Workflow
import com.arcx.integration.entrypoints.ArcxDeepLinks
import com.arcx.integration.entrypoints.R
import com.arcx.integration.entrypoints.di.entrypointsGraph
import com.arcx.integration.entrypoints.shortcut.WorkflowIconBitmap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A home screen cell is small; past six rows the widget scrolls instead of helping. */
private const val MAX_WIDGET_WORKFLOWS = 6

/**
 * Fixed pixels, not dp: this bitmap crosses into the launcher's process through a RemoteViews,
 * where our density does not apply. Big enough for any launcher's scaling, small enough that six
 * of them stay well under the transaction limit.
 */
private const val GLYPH_PX = 72

/**
 * The home screen surface: the user's favourites, one tap from anywhere they already are.
 *
 * Every cell launches the same `arcx://run/{id}` deep link the bubble and the shortcuts use, via
 * `actionStartActivity`, which wraps it in the PendingIntent the launcher process needs to fire it
 * on ArcX's behalf.
 */
class FavoritesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val workflows = loadWorkflows(context)
        provideContent {
            GlanceTheme {
                WidgetContent(context, workflows)
            }
        }
    }

    /**
     * Bounded, because this runs inside the launcher's binder call: if the database is slow to open
     * — first launch after a reboot, direct-boot storage still locked — an empty widget that says
     * so is better than a placeholder that never resolves.
     */
    private suspend fun loadWorkflows(context: Context): List<Workflow> = runCatching {
        withTimeoutOrNull(WIDGET_LOAD_TIMEOUT_MS) {
            val repository = context.entrypointsGraph().workflowRepository()
            combine(
                repository.observePinned(),
                repository.observeFavorites(),
            ) { pinned, favorites ->
                (pinned + favorites).distinctBy { it.id }.take(MAX_WIDGET_WORKFLOWS)
            }.first()
        }
    }.getOrNull().orEmpty()

    private companion object {
        const val WIDGET_LOAD_TIMEOUT_MS = 3_000L
    }
}

@Composable
private fun WidgetContent(context: Context, workflows: List<Workflow>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            // appWidgetBackground is what lets the launcher round and theme the widget's own
            // surface on Android 12+; without it the corners stay square.
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(ArcxDeepLinks.intent(context)))
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✦ ArcX",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "⌄",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
        }

        if (workflows.isEmpty()) {
            Text(
                text = context.getString(R.string.arcx_widget_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn {
                items(workflows, itemId = { it.id.hashCode().toLong() }) { workflow ->
                    WorkflowCell(context, workflow)
                }
            }
        }
    }
}

@Composable
private fun WorkflowCell(context: Context, workflow: Workflow) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(ArcxDeepLinks.intent(context, workflow.id)))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rasterised rather than drawn as text: a workflow's icon is a vector now, and Glance
        // cannot compose one. Tinted here so it still follows the widget's own theme.
        Image(
            provider = ImageProvider(WorkflowIconBitmap.glyph(workflow.icon, GLYPH_PX)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = workflow.name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface),
        )
    }
}

/**
 * Declared in this module's manifest. The receiver is the only part of a Glance widget the system
 * knows about; it forwards every widget broadcast to the [FavoritesWidget] above.
 */
class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
