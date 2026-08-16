package com.arcx.core.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Migrations are the only code in ArcX that runs exactly once per user, on data that already
 * exists, with no way to try again if it is wrong. This runs them against a real SQLite engine on
 * a table populated the way the previous version left it, and reads the rows back afterwards.
 *
 * Asserting the SQL string instead would have been cheaper and would have proved almost nothing:
 * it passes on a statement SQLite rejects outright, and it cannot show what became of the rows.
 */
class MigrationsTest {

    /**
     * The `workflows` table exactly as schema version 3 created it — copied from
     * `schemas/…ArcxDatabase/3.json`, which is the file Room itself validates against.
     */
    private val workflowsAtVersion3 = """
        CREATE TABLE workflows (
            id TEXT NOT NULL, name TEXT NOT NULL, icon TEXT NOT NULL, category TEXT NOT NULL,
            input TEXT NOT NULL, prompt TEXT NOT NULL, systemPrompt TEXT, providerId TEXT,
            model TEXT, output TEXT NOT NULL, temperature REAL, maxTokens INTEGER,
            isPinned INTEGER NOT NULL, isFavorite INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL,
            sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            lastRunAt INTEGER, PRIMARY KEY(id)
        )
    """.trimIndent()

    /** The `runs` table before `MIGRATION_1_2` added `screenshotPath`. */
    private val runsAtVersion2 = """
        CREATE TABLE runs (
            id TEXT NOT NULL, workflowId TEXT NOT NULL, workflowName TEXT NOT NULL,
            workflowIcon TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL,
            providerLabel TEXT NOT NULL, model TEXT NOT NULL, status TEXT NOT NULL,
            inputPreview TEXT NOT NULL, outputPreview TEXT, error TEXT, PRIMARY KEY(id)
        )
    """.trimIndent()

    /**
     * The one thing this migration must not get wrong.
     *
     * A default of 0 would take every workflow the user has out of the picker, the sidebar, the
     * widget and the launcher's shortcut menu on an ordinary app update — leaving the Library the
     * only place anything still appeared, with no event to connect it to and no undo but flipping
     * every row back by hand.
     */
    @Test
    fun `every workflow that already existed comes out switched on`() {
        sqlite { db ->
            db.exec(workflowsAtVersion3)
            db.insertWorkflow(id = "starter", name = "Rewrite Professionally")
            db.insertWorkflow(id = "mine", name = "Fix Grammar")

            MIGRATION_3_4.migrate(db.asRoomConnection())

            assertEquals(listOf(1, 1), db.query("SELECT enabled FROM workflows ORDER BY id"))
        }
    }

    /** The column has to be writable both ways, or the switch would only ever turn things off. */
    @Test
    fun `a workflow can be switched off after the migration`() {
        sqlite { db ->
            db.exec(workflowsAtVersion3)
            db.insertWorkflow(id = "mine", name = "Fix Grammar")

            MIGRATION_3_4.migrate(db.asRoomConnection())
            db.exec("UPDATE workflows SET enabled = 0 WHERE id = 'mine'")

            assertEquals(listOf(0), db.query("SELECT enabled FROM workflows"))
        }
    }

    /** Nothing else in the row is touched: this is a switch, not a rewrite. */
    @Test
    fun `the migration leaves every other column alone`() {
        sqlite { db ->
            db.exec(workflowsAtVersion3)
            db.insertWorkflow(id = "mine", name = "Fix Grammar", isPinned = 1, sortOrder = 7)

            MIGRATION_3_4.migrate(db.asRoomConnection())

            assertEquals(listOf(1), db.query("SELECT isPinned FROM workflows"))
            assertEquals(listOf(7), db.query("SELECT sortOrder FROM workflows"))
            assertEquals(listOf(1), db.query("SELECT COUNT(*) FROM workflows"))
        }
    }

    @Test
    fun `the migration is registered for the step it claims`() {
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)
    }

    /** Guards the older step the same way, since this harness now exists. */
    @Test
    fun `the screenshot column arrives null on runs that predate it`() {
        sqlite { db ->
            db.exec(runsAtVersion2)
            db.exec(
                "INSERT INTO runs VALUES ('r1','w1','Fix Grammar','spellcheck',1,2,'Gemini'," +
                    "'gemini-2.0-flash','SUCCESS','in','out',NULL)",
            )

            MIGRATION_1_2.migrate(db.asRoomConnection())

            assertEquals(listOf(1), db.query("SELECT screenshotPath IS NULL FROM runs"))
        }
    }

    /**
     * The emoji rewrite still only touches the glyphs ArcX itself shipped.
     *
     * It needs both tables: history rows carry their own copy of the icon so a deleted workflow's
     * runs still render, and the migration rewrites those too.
     */
    @Test
    fun `the icon migration leaves an emoji the user chose alone`() {
        sqlite { db ->
            db.exec(workflowsAtVersion3)
            db.exec(runsAtVersion2)
            db.insertWorkflow(id = "shipped", name = "Fix Grammar", icon = "✅")
            db.insertWorkflow(id = "mine", name = "Feed The Cat", icon = "🐈")

            MIGRATION_2_3.migrate(db.asRoomConnection())

            assertEquals("spellcheck", db.queryText("SELECT icon FROM workflows WHERE id = 'shipped'"))
            assertEquals("🐈", db.queryText("SELECT icon FROM workflows WHERE id = 'mine'"))
        }
    }

    // -- Harness ------------------------------------------------------------------------------

    private fun sqlite(block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use(block)
    }

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.query(sql: String): List<Int> = createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            buildList { while (rows.next()) add(rows.getInt(1)) }
        }
    }

    private fun Connection.queryText(sql: String): String? = createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
    }

    private fun Connection.insertWorkflow(
        id: String,
        name: String,
        icon: String = "auto_awesome",
        isPinned: Int = 0,
        sortOrder: Int = 0,
    ) = exec(
        "INSERT INTO workflows VALUES ('$id','$name','$icon','CUSTOM','SELECTED_TEXT','P'," +
            "NULL,NULL,NULL,'BOTTOM_SHEET',NULL,NULL,$isPinned,0,0,$sortOrder,1,1,NULL)",
    )

    /**
     * The one adapter this needs: Room hands a migration an `SQLiteConnection`, and
     * `androidx.sqlite.execSQL` is `prepare(sql).use { it.step() }`. A migration issues DDL and
     * nothing else, so `step` is the only method with anything to do — every getter and binder
     * below fails loudly rather than returning a plausible zero, because a migration reaching for
     * one means it is doing something this harness is not actually testing.
     */
    private fun Connection.asRoomConnection(): SQLiteConnection {
        val jdbc = this
        return object : SQLiteConnection {
            override fun prepare(sql: String): SQLiteStatement = object : SQLiteStatement {
                override fun step(): Boolean {
                    jdbc.exec(sql)
                    return false
                }

                override fun close() = Unit
                override fun reset() = Unit
                override fun clearBindings() = Unit

                private fun unused(): Nothing =
                    error("A migration should only ever execute statements, not read or bind")

                override fun bindBlob(index: Int, value: ByteArray) = unused()
                override fun bindDouble(index: Int, value: Double) = unused()
                override fun bindLong(index: Int, value: Long) = unused()
                override fun bindText(index: Int, value: String) = unused()
                override fun bindNull(index: Int) = unused()
                override fun getBlob(index: Int) = unused()
                override fun getDouble(index: Int) = unused()
                override fun getLong(index: Int) = unused()
                override fun getText(index: Int) = unused()
                override fun isNull(index: Int) = unused()
                override fun getColumnCount() = unused()
                override fun getColumnName(index: Int) = unused()
                override fun getColumnType(index: Int) = unused()
            }

            override fun close() = Unit
        }
    }

    init {
        // Fail with something readable if the JDBC driver is missing, rather than an
        // "No suitable driver" from six frames down inside DriverManager.
        assertTrue(
            "org.xerial:sqlite-jdbc is not on the test classpath",
            runCatching { Class.forName("org.sqlite.JDBC") }.isSuccess,
        )
    }
}
