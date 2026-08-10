package com.arcx.core.common.prompt

/**
 * A placeholder the workflow builder offers in its variable picker. [name] is what the user
 * types inside `{{ }}`; the rest is copy for the picker sheet.
 */
data class PromptVariable(
    val name: String,
    val label: String,
    val description: String,
) {
    val token: String get() = "{{$name}}"

    companion object {
        val SELECTED_TEXT = PromptVariable(
            name = "selected_text",
            label = "Selected text",
            description = "Text the user highlighted in another app",
        )
        val SCREEN_TEXT = PromptVariable(
            name = "screen_text",
            label = "Screen text",
            description = "Everything readable on screen. Needs the accessibility service",
        )
        val CLIPBOARD = PromptVariable(
            name = "clipboard",
            label = "Clipboard",
            description = "Current clipboard contents",
        )
        val INPUT = PromptVariable(
            name = "input",
            label = "Input",
            description = "Whatever the workflow was fired with, whichever entry point was used",
        )
        val SHARE_TEXT = PromptVariable(
            name = "share_text",
            label = "Shared text",
            description = "Text sent to ArcX through the share sheet",
        )
        val SHARE_SUBJECT = PromptVariable(
            name = "share_subject",
            label = "Shared subject",
            description = "Subject line of a shared item, such as an email or article title",
        )
        val TODAY = PromptVariable(
            name = "today",
            label = "Today's date",
            description = "Current date, e.g. 2026-08-10",
        )
        val NOW = PromptVariable(
            name = "now",
            label = "Current time",
            description = "Current time of day, e.g. 14:05",
        )
        val CURRENT_APP = PromptVariable(
            name = "current_app",
            label = "Current app",
            description = "Package name of the app the workflow was fired from",
        )

        val ALL: List<PromptVariable> = listOf(
            SELECTED_TEXT,
            SCREEN_TEXT,
            CLIPBOARD,
            INPUT,
            SHARE_TEXT,
            SHARE_SUBJECT,
            TODAY,
            NOW,
            CURRENT_APP,
        )
    }
}

/** Renders `{{variable}}` placeholders in a workflow's prompt. */
object PromptTemplate {

    private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}""")

    /**
     * Substitutes every placeholder, dropping the ones with no value. A prompt on its way to a
     * model must never contain a literal `{{foo}}`, and a typo in a template is not worth
     * failing a run over, so unknown names render as an empty string instead of throwing.
     */
    fun render(template: String, vars: Map<String, String>): String =
        PLACEHOLDER.replace(template) { match -> vars[match.groupValues[1]].orEmpty() }

    /** Names used by [template], distinct, in order of first appearance. */
    fun variablesIn(template: String): List<String> =
        PLACEHOLDER.findAll(template)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
}
