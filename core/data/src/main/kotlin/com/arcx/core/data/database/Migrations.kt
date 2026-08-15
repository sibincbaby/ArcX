package com.arcx.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Adds [RunEntity.screenshotPath].
 *
 * Written out rather than left to a destructive fallback: recreating the table would throw away
 * the user's history on an ordinary app update, and every screenshot on disk would survive with
 * no row left pointing at it — files nothing in the app could ever offer to delete again.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE runs ADD COLUMN screenshotPath TEXT")
    }
}

/**
 * Rewrites the emoji every shipped workflow used to carry into a key from the design system's
 * icon set.
 *
 * Done in SQL rather than by re-seeding, because a starter the user has renamed, pinned or run
 * is still theirs — only its glyph is being replaced. Matching is on the exact emoji ArcX itself
 * shipped: anything else in this column was typed by the user and is left alone, which is also
 * what makes running this on a library full of hand-picked emoji safe.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {

    /** Exactly the emoji from starter_workflows.json and gallery.json, in that order. */
    private val icons = listOf(
        "💼" to "edit_note",
        "✅" to "spellcheck",
        "✂️" to "content_cut",
        "📝" to "expand",
        "💻" to "code",
        "🔍" to "search",
        "🧪" to "science",
        "🐞" to "bug_report",
        "📚" to "menu_book",
        "🃏" to "school",
        "❓" to "quiz",
        "🗒️" to "notes",
        "📌" to "format_list_bulleted",
        "🕵️" to "fact_check",
        "🔑" to "key",
        "👀" to "visibility",
        "🌍" to "translate",
        "🐦" to "tag",
        "🗄️" to "storage",
        "🔤" to "data_object",
        "🔀" to "merge",
        "🍳" to "restaurant",
        "✉️" to "mail",
        "📅" to "event",
        "⚖️" to "gavel",
        "🧩" to "extension",
        "🎤" to "forum",
        "✨" to "auto_awesome",
    )

    override fun migrate(connection: SQLiteConnection) {
        icons.forEach { (emoji, key) ->
            connection.execSQL("UPDATE workflows SET icon = '$key' WHERE icon = '$emoji'")
        }
        // History rows keep their own copy of the icon so a deleted workflow's runs still render.
        icons.forEach { (emoji, key) ->
            connection.execSQL("UPDATE runs SET workflowIcon = '$key' WHERE workflowIcon = '$emoji'")
        }
    }
}
