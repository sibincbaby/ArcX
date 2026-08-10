package com.arcx.feature.runner.output

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

private const val A4_WIDTH_PT = 595
private const val A4_HEIGHT_PT = 842
private const val MARGIN_PT = 48
private const val BODY_SIZE_PT = 11f
private const val TITLE_SIZE_PT = 16f

/**
 * Lays the answer out once and slices that single layout across pages, so a paragraph that
 * straddles a page break still breaks at the same place it would on screen.
 *
 * Deliberately plain: A4, one column, no Markdown styling. The PDF is an export of the text,
 * not a second rendering engine to keep in step with [com.arcx.core.designsystem.component.MarkdownText].
 */
internal fun renderPdf(title: String, text: String, out: OutputStream) {
    val body = TextPaint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = BODY_SIZE_PT
    }
    val heading = TextPaint(body).apply { textSize = TITLE_SIZE_PT }

    val width = A4_WIDTH_PT - MARGIN_PT * 2
    val titleLayout = layoutOf(title, heading, width)
    val bodyLayout = layoutOf(text.ifBlank { " " }, body, width)

    val document = PdfDocument()
    var line = 0
    var pageNumber = 1

    while (line < bodyLayout.lineCount) {
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageNumber).create(),
        )
        val canvas = page.canvas

        // The title only occupies the first page; every later page gets the full text column.
        var top = MARGIN_PT
        if (pageNumber == 1) {
            canvas.save()
            canvas.translate(MARGIN_PT.toFloat(), top.toFloat())
            titleLayout.draw(canvas)
            canvas.restore()
            top += titleLayout.height + 16
        }

        val available = A4_HEIGHT_PT - MARGIN_PT - top
        val firstLineTop = bodyLayout.getLineTop(line)
        var last = line
        while (
            last + 1 < bodyLayout.lineCount &&
            bodyLayout.getLineBottom(last + 1) - firstLineTop <= available
        ) {
            last++
        }

        canvas.save()
        // Shift the whole layout so this page's first line lands under the margin, then clip
        // to the page so the lines belonging to the next page are not painted over it.
        canvas.translate(MARGIN_PT.toFloat(), (top - firstLineTop).toFloat())
        canvas.clipRect(
            0f,
            firstLineTop.toFloat(),
            width.toFloat(),
            (firstLineTop + available).toFloat(),
        )
        bodyLayout.draw(canvas)
        canvas.restore()

        document.finishPage(page)
        // A line taller than a whole page still advances, so this can never spin.
        line = last + 1
        pageNumber++
    }

    document.writeTo(out)
    document.close()
}

private fun layoutOf(text: String, paint: TextPaint, width: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setLineSpacing(2f, 1f)
        .setIncludePad(false)
        .build()
