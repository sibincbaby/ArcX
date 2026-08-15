package com.arcx.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icon a workflow gets when nothing else is chosen.
 *
 * The value belongs to `:core:model`, where `Workflow` and `WorkflowSpec` both default to it and
 * where the modules that cannot see Compose can reach it. Re-exported here under the name every
 * drawing-side caller already imports, so there is one literal rather than two that can drift.
 */
const val DEFAULT_WORKFLOW_ICON: String = com.arcx.core.model.DEFAULT_WORKFLOW_ICON

/** One entry in the picker: the stored key, what to draw, and a word to search it by. */
data class WorkflowIconOption(
    val key: String,
    val label: String,
    val group: String,
    val icon: ImageVector,
)

/**
 * The icon set a workflow can wear.
 *
 * Workflows used to carry an emoji, which meant every list rendered at the mercy of the system
 * emoji font — different metrics, different colours, different weight per vendor, and no way to
 * tint one to its category. These are line icons on the same grid as the rest of the interface,
 * so a row of workflows reads as one set instead of a sticker sheet.
 *
 * [Workflow.icon] still holds a plain string. A key that is not in this list falls back to being
 * drawn as text, which is what keeps the emoji of anyone who typed their own working untouched.
 */
val WorkflowIcons: List<WorkflowIconOption> = listOf(
    // General
    icon("auto_awesome", "Sparkle", "General", Icons.Outlined.AutoAwesome),
    icon("bolt", "Bolt", "General", Icons.Outlined.Bolt),
    icon("lightbulb", "Idea", "General", Icons.Outlined.Lightbulb),
    icon("rocket", "Rocket", "General", Icons.Outlined.RocketLaunch),
    icon("star", "Bookmark", "General", Icons.Outlined.BookmarkBorder),
    icon("pin", "Pin", "General", Icons.Outlined.PushPin),
    icon("palette", "Palette", "General", Icons.Outlined.Palette),
    icon("check_circle", "Tick", "General", Icons.Outlined.CheckCircle),

    // Writing
    icon("edit_note", "Rewrite", "Writing", Icons.Outlined.EditNote),
    icon("edit", "Edit", "Writing", Icons.Outlined.Edit),
    icon("spellcheck", "Spellcheck", "Writing", Icons.Outlined.Spellcheck),
    icon("content_cut", "Shorten", "Writing", Icons.Outlined.ContentCut),
    icon("expand", "Expand", "Writing", Icons.Outlined.OpenInFull),
    icon("format_list_bulleted", "Bullets", "Writing", Icons.AutoMirrored.Outlined.FormatListBulleted),
    icon("notes", "Note", "Writing", Icons.Outlined.StickyNote2),
    icon("description", "Document", "Writing", Icons.Outlined.Description),
    icon("article", "Article", "Writing", Icons.AutoMirrored.Outlined.Article),
    icon("tag", "Tag", "Writing", Icons.AutoMirrored.Outlined.Label),
    icon("translate", "Translate", "Writing", Icons.Outlined.Translate),
    icon("language", "Language", "Writing", Icons.Outlined.Language),

    // Development
    icon("code", "Code", "Development", Icons.Outlined.Code),
    icon("terminal", "Terminal", "Development", Icons.Outlined.Terminal),
    icon("bug_report", "Bug", "Development", Icons.Outlined.BugReport),
    icon("science", "Test", "Development", Icons.Outlined.Science),
    icon("build", "Build", "Development", Icons.Outlined.Build),
    icon("extension", "Extension", "Development", Icons.Outlined.Extension),
    icon("storage", "Database", "Development", Icons.Outlined.Storage),
    icon("merge", "Merge", "Development", Icons.Outlined.Merge),
    icon("data_object", "Data", "Development", Icons.Outlined.DataObject),

    // Research and study
    icon("search", "Search", "Research", Icons.Outlined.Search),
    icon("travel_explore", "Explore", "Research", Icons.Outlined.TravelExplore),
    icon("fact_check", "Fact check", "Research", Icons.Outlined.FactCheck),
    icon("key", "Key points", "Research", Icons.Outlined.Key),
    icon("insights", "Insights", "Research", Icons.Outlined.Insights),
    icon("bar_chart", "Chart", "Research", Icons.Outlined.BarChart),
    icon("psychology", "Thinking", "Research", Icons.Outlined.Psychology),
    icon("school", "School", "Research", Icons.Outlined.School),
    icon("menu_book", "Book", "Research", Icons.Outlined.MenuBook),
    icon("quiz", "Quiz", "Research", Icons.Outlined.Quiz),
    icon("help", "Question", "Research", Icons.Outlined.HelpOutline),

    // Work and life
    icon("work", "Work", "Work", Icons.Outlined.Work),
    icon("task_alt", "Task", "Work", Icons.Outlined.TaskAlt),
    icon("checklist", "Checklist", "Work", Icons.Outlined.Checklist),
    icon("groups", "People", "Work", Icons.Outlined.Groups),
    icon("mail", "Mail", "Work", Icons.Outlined.MailOutline),
    icon("forum", "Conversation", "Work", Icons.Outlined.Forum),
    icon("campaign", "Announce", "Work", Icons.Outlined.Campaign),
    icon("event", "Calendar", "Work", Icons.Outlined.Event),
    icon("schedule", "Time", "Work", Icons.Outlined.Schedule),
    icon("gavel", "Legal", "Work", Icons.Outlined.Gavel),
    icon("visibility", "Screen", "Work", Icons.Outlined.Visibility),
    icon("smart_toy", "Robot", "Work", Icons.Outlined.SmartToy),
    icon("restaurant", "Food", "Work", Icons.Outlined.Restaurant),
    icon("shopping_cart", "Shopping", "Work", Icons.Outlined.ShoppingCart),
    icon("photo_camera", "Camera", "Work", Icons.Outlined.PhotoCamera),
    icon("music_note", "Music", "Work", Icons.Outlined.MusicNote),
    icon("movie", "Video", "Work", Icons.Outlined.Movie),
)

private val byKey: Map<String, ImageVector> = WorkflowIcons.associate { it.key to it.icon }

/**
 * The vector for a stored icon string, or null when it is not one of ours — which is the signal
 * to draw it as text instead. Do not make this fall back to the default: a workflow carrying an
 * emoji has to keep its emoji, not silently become a sparkle.
 */
fun workflowIconFor(key: String): ImageVector? = byKey[key]

private fun icon(key: String, label: String, group: String, icon: ImageVector) =
    WorkflowIconOption(key, label, group, icon)
