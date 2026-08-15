package com.arcx.integration.entrypoints.shortcut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.arcx.core.designsystem.component.workflowIconFor
import kotlin.math.roundToInt
import android.graphics.Path as AndroidPath

/**
 * Rasterises a workflow's icon for the two surfaces that cannot compose: the launcher shortcut
 * and the home screen widget.
 *
 * A drawable per workflow is impossible here — workflows are user-created, so there is no
 * build-time set to ship. Instead the same [ImageVector] the app draws is walked and filled onto
 * a Canvas. Workflows made before the icon set existed still hold an emoji, and those are drawn
 * as text exactly as they always were.
 */
internal object WorkflowIconBitmap {

    /** Adaptive icons are authored on a 108dp canvas of which the outer quarter can be masked off. */
    private const val CANVAS_DP = 108
    private const val SAFE_ZONE = 0.66f

    /**
     * A fixed tint rather than the Material You accent: shortcut icons are baked into the
     * launcher's database at publish time and would not follow a wallpaper change anyway, so a
     * stable colour is more honest than one that drifts out of date.
     */
    private const val BACKGROUND = 0xFFE5DEFF.toInt()
    private const val FOREGROUND = 0xFF2C2185.toInt()

    fun adaptive(icon: String, density: Float): IconCompat {
        val size = (CANVAS_DP * density).roundToInt().coerceIn(108, 512)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        // The background must reach every edge: whatever the launcher's mask crops is still drawn.
        canvas.drawColor(BACKGROUND)
        draw(canvas, icon, size.toFloat(), size * SAFE_ZONE * 0.62f, FOREGROUND)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    /**
     * A bare glyph on transparent, for the widget — which tints it to the theme itself, so the
     * colour baked in here only has to be opaque.
     */
    fun glyph(icon: String, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        draw(Canvas(bitmap), icon, sizePx.toFloat(), sizePx * 0.86f, Color.BLACK)
        return bitmap
    }

    private fun draw(canvas: Canvas, icon: String, canvasSize: Float, glyphSize: Float, color: Int) {
        val vector = workflowIconFor(icon)
        if (vector != null) {
            drawVector(canvas, vector, canvasSize, glyphSize, color)
        } else {
            drawEmoji(canvas, icon, canvasSize, glyphSize)
        }
    }

    private fun drawVector(
        canvas: Canvas,
        vector: ImageVector,
        canvasSize: Float,
        glyphSize: Float,
        color: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val scale = glyphSize / vector.viewportWidth
        val offset = (canvasSize - glyphSize) / 2f
        val transform = Matrix().apply {
            setScale(scale, scale)
            postTranslate(offset, offset)
        }
        // ponytail: group transforms in the vector are ignored — every icon in WorkflowIcons is
        // flat paths in a 24x24 viewport. Walk VectorGroup.transform here if that ever changes.
        vector.root.paths().forEach { path ->
            path.transform(transform)
            canvas.drawPath(path, paint)
        }
    }

    /** Each sub-path is its own [AndroidPath] so it keeps its own fill rule. */
    private fun VectorGroup.paths(): List<AndroidPath> = buildList {
        this@paths.forEach { node ->
            when (node) {
                is VectorPath -> add(
                    PathParser().addPathNodes(node.pathData).toPath().asAndroidPath().apply {
                        fillType = when (node.pathFillType) {
                            androidx.compose.ui.graphics.PathFillType.EvenOdd ->
                                AndroidPath.FillType.EVEN_ODD

                            else -> AndroidPath.FillType.WINDING
                        }
                    },
                )

                is VectorGroup -> addAll(node.paths())
            }
        }
    }

    private fun drawEmoji(canvas: Canvas, emoji: String, canvasSize: Float, glyphSize: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = glyphSize
        }
        // drawText positions by baseline, not by centre; this shifts it so the glyph's own box is
        // centred, which for emoji (which sit high in the em square) is visibly different.
        val metrics = paint.fontMetrics
        val baseline = canvasSize / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji.ifBlank { "✨" }, canvasSize / 2f, baseline, paint)
    }
}
