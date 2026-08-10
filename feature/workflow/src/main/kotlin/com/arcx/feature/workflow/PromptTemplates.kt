package com.arcx.feature.workflow

/**
 * Starting points for the prompt field. A blank multi-line box is the single hardest moment in
 * the builder, so every template here is a prompt worth shipping as-is — not a stub to fill in.
 *
 * They all read `{{input}}` rather than a specific source so a template works whichever input
 * the user picked, and they all say what *not* to return, because the commonest disappointment
 * with a one-shot AI action is a wall of preamble around the answer.
 */
internal data class PromptTemplateOption(
    val emoji: String,
    val title: String,
    val summary: String,
    val prompt: String,
    val systemPrompt: String? = null,
)

internal val PROMPT_TEMPLATES: List<PromptTemplateOption> = listOf(
    PromptTemplateOption(
        emoji = "💡",
        title = "Explain",
        summary = "Break something down for someone seeing it for the first time",
        systemPrompt = "You explain things to a smart person who simply has not met this topic before. You never pad and never condescend.",
        prompt = """
            Explain what follows.

            Start with one sentence saying what it is. Then walk through the parts that actually matter, in the order someone would need to meet them. Define every term the moment you use it. Where an example makes the idea concrete, give one.

            Skip anything obvious from the text itself, and stay under 250 words.

            {{input}}
        """.trimIndent(),
    ),
    PromptTemplateOption(
        emoji = "✍️",
        title = "Rewrite",
        summary = "Say the same thing, better",
        systemPrompt = "You are an editor. You rewrite without inflating, and you return only the rewritten text.",
        prompt = """
            Rewrite the text below so it reads clearly and naturally.

            Keep the meaning, every fact, the author's voice and roughly the original length. Write in the same language as the input. Cut hedging, repetition and throat-clearing. Do not add a greeting or sign-off that was not already there.

            Return only the rewritten text: no preamble, no explanation, no surrounding quotes.

            {{input}}
        """.trimIndent(),
    ),
    PromptTemplateOption(
        emoji = "📄",
        title = "Summarize",
        summary = "The substance, in a fraction of the words",
        prompt = """
            Summarise the text below.

            Lead with one sentence capturing the whole thing, then 4 to 6 bullets covering the points the author actually makes, in their order. Keep names, numbers, dates and conditions exactly as written.

            Do not add conclusions of your own and do not include anything the text only implies.

            {{input}}
        """.trimIndent(),
    ),
    PromptTemplateOption(
        emoji = "✨",
        title = "Improve",
        summary = "Critique, then a stronger version",
        systemPrompt = "You are a candid editor. You name real weaknesses rather than praising work you were asked to improve.",
        prompt = """
            Improve the text below.

            First, **what is weak** — three specific problems, each with the line that shows it. Be honest; do not manufacture praise.
            Then, **the stronger version** — the full rewritten text, fixing exactly those problems and nothing else.

            Keep the author's intent, voice and language throughout.

            {{input}}
        """.trimIndent(),
    ),
    PromptTemplateOption(
        emoji = "🌍",
        title = "Translate",
        summary = "Into another language, idiomatically",
        systemPrompt = "You are a translator who produces text a native speaker would actually write, not a word-for-word gloss.",
        prompt = """
            Translate the text below into English.

            Translate the meaning, not the words: use the phrasing a native speaker would choose, and keep the register of the original — casual stays casual, formal stays formal. Preserve formatting, line breaks and any names, code or numbers exactly.

            Where a phrase has no clean equivalent, translate the sense and add the literal version in brackets.

            Return only the translation.

            {{input}}
        """.trimIndent(),
    ),
    PromptTemplateOption(
        emoji = "🧱",
        title = "Convert to JSON",
        summary = "Turn loose text into structured data",
        systemPrompt = "You are a data extractor. You return valid JSON and nothing else.",
        prompt = """
            Convert the text below into JSON.

            Infer a sensible schema from the content: one object per record, `snake_case` keys, dates as `YYYY-MM-DD`, numbers as numbers rather than strings. Use `null` for anything the text does not state — never invent a value to fill a field.

            Return only the JSON, in a single code block. No commentary before or after.

            {{input}}
        """.trimIndent(),
    ),
    PromptTemplateOption(
        emoji = "❓",
        title = "Generate Questions",
        summary = "Questions that test whether you understood it",
        prompt = """
            Write 8 questions about the material below.

            Test understanding rather than recall of wording: ask why something follows, what would happen if a condition changed, how two ideas relate. Every question must be answerable from the material alone.

            After the questions, give a short model answer for each.

            {{input}}
        """.trimIndent(),
    ),
    PromptTemplateOption(
        emoji = "🔍",
        title = "Review Code",
        summary = "Real problems, worst first",
        systemPrompt = "You are a careful code reviewer. You report real problems, ranked by consequence, and you do not pad a review with nitpicks.",
        prompt = """
            Review the code below.

            Report findings most serious first: correctness bugs, crashes, data loss, race conditions, security holes, resource leaks, then error handling, then readability. For each one give the offending line, why it is wrong, and a concrete fix.

            If the code has no serious problems, say so plainly instead of inventing issues. Note any assumption you had to make about code you cannot see.

            {{input}}
        """.trimIndent(),
    ),
)
