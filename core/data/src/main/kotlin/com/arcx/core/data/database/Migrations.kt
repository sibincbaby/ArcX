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
