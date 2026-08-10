package com.arcx.core.common.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateTest {

    @Test
    fun `substitutes a known variable`() {
        val out = PromptTemplate.render(
            "Summarise: {{selected_text}}",
            mapOf("selected_text" to "hello world"),
        )
        assertEquals("Summarise: hello world", out)
    }

    @Test
    fun `substitutes every occurrence of a repeated variable`() {
        val out = PromptTemplate.render(
            "{{input}} then {{input}} again",
            mapOf("input" to "x"),
        )
        assertEquals("x then x again", out)
    }

    @Test
    fun `unknown variable renders as empty string`() {
        val out = PromptTemplate.render("a{{nope}}b", mapOf("input" to "x"))
        assertEquals("ab", out)
        assertTrue(!out.contains("{{"))
    }

    @Test
    fun `tolerates whitespace inside the braces`() {
        val out = PromptTemplate.render("[{{ name }}]", mapOf("name" to "Ada"))
        assertEquals("[Ada]", out)
    }

    @Test
    fun `template without variables is returned unchanged`() {
        val template = "Just a plain prompt with { braces } and }} stray"
        assertEquals(template, PromptTemplate.render(template, mapOf("input" to "x")))
        assertEquals(emptyList<String>(), PromptTemplate.variablesIn(template))
    }

    @Test
    fun `replacement value is not itself expanded`() {
        val out = PromptTemplate.render("{{input}}", mapOf("input" to "{{clipboard}}", "clipboard" to "leak"))
        assertEquals("{{clipboard}}", out)
    }

    @Test
    fun `replacement value with backslashes and dollars survives`() {
        val out = PromptTemplate.render("{{input}}", mapOf("input" to "C:\\tmp $1"))
        assertEquals("C:\\tmp $1", out)
    }

    @Test
    fun `variablesIn keeps first-appearance order and drops duplicates`() {
        val vars = PromptTemplate.variablesIn("{{now}} {{ today }} {{now}} {{clipboard}}")
        assertEquals(listOf("now", "today", "clipboard"), vars)
    }

    @Test
    fun `variablesIn ignores malformed placeholders`() {
        assertEquals(emptyList<String>(), PromptTemplate.variablesIn("{{1bad}} {{ }} {{-}}"))
    }

    @Test
    fun `every declared variable round-trips through render`() {
        PromptVariable.ALL.forEach { variable ->
            assertEquals("v", PromptTemplate.render(variable.token, mapOf(variable.name to "v")))
        }
        assertEquals(PromptVariable.ALL.size, PromptVariable.ALL.map { it.name }.distinct().size)
    }
}
