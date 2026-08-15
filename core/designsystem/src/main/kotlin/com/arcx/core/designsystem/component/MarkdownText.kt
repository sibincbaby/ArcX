package com.arcx.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Minimal Markdown rendering for model output: fenced code blocks, headings, bullet and
 * numbered lists, and inline bold / italic / code.
 *
 * Deliberately hand-rolled rather than pulling in a Markdown library — model answers only
 * ever use this handful of constructs, and a parser dependency would be far larger than
 * the feature. Anything unrecognised falls through as plain text, so output is never lost.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }

    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Code -> CodeBlock(block.code)

                is Block.Heading -> Text(
                    text = inline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )

                is Block.Bullet -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text(block.marker, style = style)
                    Text(
                        text = inline(block.text),
                        style = style,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                is Block.Paragraph -> Text(
                    text = inline(block.text),
                    style = style,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        // Code must scroll rather than wrap; wrapped code is unreadable.
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private sealed interface Block {
    data class Code(val code: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val marker: String, val text: String) : Block
    data class Paragraph(val text: String) : Block
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val UNORDERED = Regex("""^\s*[-*+]\s+(.*)$""")
private val ORDERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")

private fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
        paragraph.setLength(0)
    }

    markdown.lines().forEach { line ->
        when {
            line.trimStart().startsWith("```") -> {
                if (inCode) {
                    blocks += Block.Code(code.toString().trimEnd('\n'))
                    code.setLength(0)
                } else {
                    flushParagraph()
                }
                inCode = !inCode
            }

            inCode -> code.append(line).append('\n')

            line.isBlank() -> flushParagraph()

            HEADING.matches(line) -> {
                flushParagraph()
                val (hashes, text) = HEADING.find(line)!!.destructured
                blocks += Block.Heading(hashes.length, text)
            }

            UNORDERED.matches(line) -> {
                flushParagraph()
                blocks += Block.Bullet("•", UNORDERED.find(line)!!.groupValues[1])
            }

            ORDERED.matches(line) -> {
                flushParagraph()
                val (number, text) = ORDERED.find(line)!!.destructured
                blocks += Block.Bullet("$number.", text)
            }

            else -> paragraph.append(line).append(' ')
        }
    }

    // An unterminated fence still has to render — models get cut off mid-block.
    if (inCode && code.isNotBlank()) blocks += Block.Code(code.toString().trimEnd('\n'))
    flushParagraph()
    return blocks
}

private val INLINE = Regex("""\*\*(.+?)\*\*|`([^`]+)`|(?<![*\w])\*([^*]+)\*(?![*\w])""")

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    INLINE.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val (bold, code, italic) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

private inline fun AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit,
) {
    val index = pushStyle(style)
    block()
    pop(index)
}
