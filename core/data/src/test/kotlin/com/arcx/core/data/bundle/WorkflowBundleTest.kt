package com.arcx.core.data.bundle

import com.arcx.core.model.DEFAULT_WORKFLOW_ICON
import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory
import com.arcx.core.model.WorkflowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundle is the one format ArcX asks people to hand to each other, and until now nothing
 * checked it. These tests pin the two promises the format makes — that a file from a newer build
 * still imports, and that the files ArcX itself ships are valid — because both are the kind of
 * thing that breaks silently and is only noticed by a user with a file that will not open.
 */
class WorkflowBundleTest {

    // -- A newer file still imports on an older build -----------------------------------------

    /**
     * The regression this suite was written for. `ignoreUnknownKeys` never covered unknown enum
     * values, so one input source added in a later release threw and the caller — which decodes
     * the whole file in one call — reported the entire file as unreadable.
     */
    @Test
    fun `an input source this build does not know falls back to the default`() {
        val bundle = parseWorkflowBundle(
            """
            {
              "version": 1,
              "workflows": [
                { "name": "From the future", "prompt": "Do the thing", "input": "VIDEO" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(InputSource.SELECTED_TEXT, bundle.workflows.single().input)
    }

    @Test
    fun `an output target this build does not know falls back to the default`() {
        val bundle = parseWorkflowBundle(
            """
            {"workflows":[{"name":"N","prompt":"P","output":"HOLOGRAM"}]}
            """.trimIndent(),
        )

        assertEquals(OutputTarget.BOTTOM_SHEET, bundle.workflows.single().output)
    }

    @Test
    fun `a category this build does not know falls back to the default`() {
        val bundle = parseWorkflowBundle(
            """
            {"workflows":[{"name":"N","prompt":"P","category":"COOKING"}]}
            """.trimIndent(),
        )

        assertEquals(WorkflowCategory.CUSTOM, bundle.workflows.single().category)
    }

    /**
     * The point of coercion is that one unrecognised value costs one field, not the file. A
     * bundle is decoded in a single call, so before this the good workflow below was lost too.
     */
    @Test
    fun `one unreadable field does not cost the other workflows in the file`() {
        val bundle = parseWorkflowBundle(
            """
            {
              "workflows": [
                { "name": "Future", "prompt": "P", "input": "VIDEO" },
                { "name": "Ordinary", "prompt": "Q", "input": "CLIPBOARD" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, bundle.workflows.size)
        assertEquals(InputSource.SELECTED_TEXT, bundle.workflows[0].input)
        assertEquals(InputSource.CLIPBOARD, bundle.workflows[1].input)
    }

    @Test
    fun `an unknown key on the envelope is ignored`() {
        val bundle = parseWorkflowBundle(
            """
            {
              "kind": "arcx.workflows",
              "exportedAt": 1700000000,
              "version": 7,
              "workflows": [{ "name": "N", "prompt": "P" }]
            }
            """.trimIndent(),
        )

        assertEquals(7, bundle.version)
        assertEquals("N", bundle.workflows.single().name)
    }

    @Test
    fun `an unknown key on a workflow is ignored`() {
        val bundle = parseWorkflowBundle(
            """
            {"workflows":[{"name":"N","prompt":"P","description":"added later","retries":3}]}
            """.trimIndent(),
        )

        assertEquals("N", bundle.workflows.single().name)
    }

    // -- What still has to fail ----------------------------------------------------------------

    /**
     * Coercion needs a default to fall back to. `name` and `prompt` have none, on purpose — a
     * workflow without either is not a workflow, and silently inventing one would be worse than
     * refusing the file.
     */
    @Test
    fun `a workflow with no prompt is still a parse failure`() {
        assertThrows(Exception::class.java) {
            parseWorkflowBundle("""{"workflows":[{"name":"N"}]}""")
        }
    }

    @Test
    fun `something that is not a bundle at all is still a parse failure`() {
        assertThrows(Exception::class.java) {
            parseWorkflowBundle("""{"items":[{"title":"N"}]}""")
        }
    }

    // -- Defaults ------------------------------------------------------------------------------

    /**
     * `icon` is an icon key drawn as a Material vector, not an emoji. A bundle that omits it must
     * land on the key, not on a glyph — the whole point of MIGRATION_2_3 was getting emoji out of
     * this column, and that migration is a one-shot 2-to-3 step that will never run again.
     */
    @Test
    fun `an omitted icon defaults to the icon key, not an emoji`() {
        val spec = parseWorkflowBundle("""{"workflows":[{"name":"N","prompt":"P"}]}""")
            .workflows
            .single()

        assertEquals(DEFAULT_WORKFLOW_ICON, spec.icon)
        assertEquals(WorkflowCategory.CUSTOM, spec.category)
        assertEquals(InputSource.SELECTED_TEXT, spec.input)
        assertEquals(OutputTarget.BOTTOM_SHEET, spec.output)
        assertNull(spec.providerId)
        assertNull(spec.model)
    }

    @Test
    fun `an omitted version reads as 1`() {
        assertEquals(1, parseWorkflowBundle("""{"workflows":[]}""").version)
    }

    // -- What a file is not allowed to bring with it ---------------------------------------------

    /**
     * Built-ins are read-only in the library — the row menu offers "Duplicate to edit" instead of
     * Edit — so a file asserting this would both pose as first-party ArcX content and be
     * uneditable. It is the single most important thing on this list.
     */
    @Test
    fun `a bundle cannot claim ArcX built it`() {
        val spec = importedSpec("""{"name":"N","prompt":"P","isBuiltIn":true}""")

        assertFalse(spec.isBuiltIn)
        assertFalse(importedWorkflow("""{"name":"N","prompt":"P","isBuiltIn":true}""").isBuiltIn)
    }

    /** Pinned workflows appear on Home and in the widget. A file does not get to put itself there. */
    @Test
    fun `a bundle cannot pin or favourite itself`() {
        val spec = importedSpec("""{"name":"N","prompt":"P","isPinned":true,"isFavorite":true}""")

        assertFalse(spec.isPinned)
        assertFalse(spec.isFavorite)
    }

    /**
     * Every library query is `ORDER BY sortOrder ASC`, and nothing sanitised this before: a file
     * saying `-999999` sorted itself above everything the user had made. Zero is what the editor
     * gives a workflow the user creates, which is what an imported one becomes.
     */
    @Test
    fun `a bundle cannot order itself above the user's own workflows`() {
        assertEquals(0, importedSpec("""{"name":"N","prompt":"P","sortOrder":-999999}""").sortOrder)
        assertEquals(0, importedWorkflow("""{"name":"N","prompt":"P","sortOrder":-999999}""").sortOrder)
    }

    /**
     * Provider ids are random UUIDs minted per device, so an imported one names nothing. Left in,
     * it resolves silently to the importer's default while the editor shows a provider that does
     * not exist.
     */
    @Test
    fun `an imported provider id is dropped`() {
        assertNull(importedSpec("""{"name":"N","prompt":"P","providerId":"not-on-this-device"}""").providerId)
    }

    /**
     * Blank, not missing: kotlinx only applies a default to an absent key, so `"icon": ""` used to
     * survive all the way into Room and render as the ✨ glyph — the emoji MIGRATION_2_3 exists to
     * remove, in a one-shot 2→3 step that will never run again.
     */
    @Test
    fun `a blank icon becomes the icon key`() {
        assertEquals(DEFAULT_WORKFLOW_ICON, importedSpec("""{"name":"N","prompt":"P","icon":"   "}""").icon)
    }

    /** A user's own emoji is not a mistake to be corrected. Only blank is. */
    @Test
    fun `an emoji icon survives an import untouched`() {
        assertEquals("🍳", importedSpec("""{"name":"N","prompt":"P","icon":"🍳"}""").icon)
    }

    /** Blank id, so `SaveWorkflowUseCase` mints a fresh one rather than overwriting anything. */
    @Test
    fun `an imported workflow arrives without an id`() {
        assertEquals("", importedWorkflow("""{"name":"N","prompt":"P"}""").id)
    }

    /**
     * The other half of the reason there are two mappers. The starter path must keep everything the
     * import path strips — this is how a starter arrives pinned and read-only — so a single shared
     * `toWorkflow` would silently reopen the hole the tests above close.
     */
    @Test
    fun `the starter mapper keeps what the import mapper strips`() {
        val spec = parseWorkflowBundle(
            """
            {"workflows":[{"name":"N","prompt":"P","isBuiltIn":true,"isPinned":true,
              "isFavorite":true,"sortOrder":7,"providerId":"local"}]}
            """.trimIndent(),
        ).workflows.single()

        val starter = spec.toWorkflowAsStarter(now = 1_000L, id = "fixed-id")

        assertEquals("fixed-id", starter.id)
        assertEquals(1_000L, starter.createdAt)
        assertEquals(1_000L, starter.updatedAt)
        assertTrue(starter.isBuiltIn)
        assertTrue(starter.isPinned)
        assertTrue(starter.isFavorite)
        assertEquals(7, starter.sortOrder)
        assertEquals("local", starter.providerId)
    }

    /**
     * Export has always dropped the provider id and the built-in flag; the mapper moved modules, so
     * this pins it where it now lives. Pinning and ordering still travel — they are the author's
     * arrangement of their own library, and this file is also their backup of it — which is safe
     * only because the import side clears them again.
     */
    @Test
    fun `an export drops the provider id and the built-in flag`() {
        val spec = Workflow(
            id = "local-id",
            name = "Mine",
            prompt = "P",
            providerId = "local-uuid",
            isBuiltIn = true,
            isPinned = true,
            sortOrder = 4,
        ).toSpec()

        assertNull(spec.providerId)
        assertFalse(spec.isBuiltIn)
        assertTrue(spec.isPinned)
        assertEquals(4, spec.sortOrder)
    }

    /** A round trip through a file cannot smuggle back what export was willing to write. */
    @Test
    fun `pinning does not survive the round trip back in`() {
        val exported = Workflow(id = "x", name = "Mine", prompt = "P", isPinned = true, sortOrder = 4).toSpec()
        val text = encodeWorkflowBundle(WorkflowBundle(workflows = listOf(exported)))

        val reimported = parseWorkflowBundle(text).workflows.single().sanitisedForImport()

        assertFalse(reimported.isPinned)
        assertEquals(0, reimported.sortOrder)
    }

    private fun importedSpec(workflowJson: String): WorkflowSpec =
        parseWorkflowBundle("""{"workflows":[$workflowJson]}""").workflows.single().sanitisedForImport()

    private fun importedWorkflow(workflowJson: String): Workflow =
        parseWorkflowBundle("""{"workflows":[$workflowJson]}""").workflows.single().toWorkflowAsImport()

    // -- The files ArcX itself ships -----------------------------------------------------------

    @Test
    fun `the bundled starter workflows parse`() {
        val bundle = parseWorkflowBundle(asset("core/data/src/main/assets/starter_workflows.json"))

        assertEquals(1, bundle.version)
        assertTrue("starter_workflows.json is empty", bundle.workflows.isNotEmpty())
        bundle.workflows.forEach { spec ->
            assertTrue("a starter has a blank name", spec.name.isNotBlank())
            assertTrue("${spec.name} has a blank prompt", spec.prompt.isNotBlank())
            // A starter naming a provider would name a row that only exists on one device.
            assertNull("${spec.name} pins a providerId", spec.providerId)
        }
    }

    /**
     * Read as a file rather than through a module dependency: `:core:data` must not know about
     * `:feature:discover` (see CLAUDE.md), but the gallery and the starters are the same envelope,
     * and this is the only place that fact is checked at all. A path, not a dependency, keeps the
     * check without the coupling.
     */
    @Test
    fun `the bundled gallery parses as the same envelope`() {
        val bundle = parseWorkflowBundle(asset("feature/discover/src/main/assets/gallery.json"))

        assertEquals(1, bundle.version)
        assertTrue("gallery.json is empty", bundle.workflows.isNotEmpty())
        bundle.workflows.forEach { spec ->
            assertTrue("a gallery entry has a blank name", spec.name.isNotBlank())
            assertTrue("${spec.name} has a blank prompt", spec.prompt.isNotBlank())
            assertNull("${spec.name} pins a providerId", spec.providerId)
            // Nothing shipped in the gallery may claim ArcX built it — an installed copy belongs
            // to the user and has to stay editable.
            assertTrue("${spec.name} claims isBuiltIn", !spec.isBuiltIn)
        }
    }

    /** Both asset files draw a real icon rather than falling through to being drawn as text. */
    @Test
    fun `every shipped workflow names an icon`() {
        val shipped = parseWorkflowBundle(asset("core/data/src/main/assets/starter_workflows.json")).workflows +
            parseWorkflowBundle(asset("feature/discover/src/main/assets/gallery.json")).workflows

        shipped.forEach { spec ->
            assertTrue("${spec.name} has a blank icon", spec.icon.isNotBlank())
        }
    }

    private fun asset(path: String): String {
        val file = File(repoRoot(), path)
        assertTrue("Missing asset: ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    /**
     * Walks up from the working directory rather than assuming it. Gradle points a `Test` task at
     * the module directory today, but that is a default rather than a promise, and a test that
     * cannot find its own fixtures is a confusing way to learn it changed.
     */
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return checkNotNull(dir) {
            "No settings.gradle.kts above ${File("").absolutePath}"
        }
    }
}
