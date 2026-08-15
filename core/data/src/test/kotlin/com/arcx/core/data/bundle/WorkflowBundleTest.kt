package com.arcx.core.data.seed

import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.WorkflowCategory
import org.junit.Assert.assertEquals
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

        assertEquals("auto_awesome", spec.icon)
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
