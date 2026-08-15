package com.arcx.core.common.prompt

/**
 * A placeholder the workflow builder offers in its variable picker. [name] is what the user
 * types inside `{{ }}`, and it is also what the picker draws on the chip — the raw token is the
 * thing being inserted, so a prettier label would only hide what the user is about to get.
 */
data class PromptVariable(
    val name: String,
) {
    val token: String get() = "{{$name}}"

    companion object {
        val SELECTED_TEXT = PromptVariable(name = "selected_text")
        val SCREEN_TEXT = PromptVariable(name = "screen_text")
        val CLIPBOARD = PromptVariable(name = "clipboard")
        val INPUT = PromptVariable(name = "input")
        val SHARE_TEXT = PromptVariable(name = "share_text")
        val SHARE_SUBJECT = PromptVariable(name = "share_subject")
        val TODAY = PromptVariable(name = "today")
        val NOW = PromptVariable(name = "now")
        val CURRENT_APP = PromptVariable(name = "current_app")

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
