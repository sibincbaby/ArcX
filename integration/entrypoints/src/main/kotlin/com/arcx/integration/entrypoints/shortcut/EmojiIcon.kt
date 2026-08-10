package com.arcx.integration.entrypoints.shortcut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

/**
 * Turns a workflow's emoji into a launcher icon at runtime.
 *
 * The alternative — a drawable per workflow — is impossible here: workflows are user-created, so
 * there is no build-time set to ship. Rasterising the emoji the user already picked keeps the
 * shortcut recognisably theirs at the cost of one small bitmap per publish.
 */
internal object EmojiIcon {

    /** Adaptive icons are authored on a 108dp canvas of which the outer quarter can be masked off. */
    private const val CANVAS_DP = 108
    private const val SAFE_ZONE = 0.66f

    /**
     * A fixed tint rather than the Material You accent: shortcut icons are baked into the
     * launcher's database at publish time and would not follow a wallpaper change anyway, so a
     * stable colour is more honest than one that drifts out of date.
     */
    private const val BACKGROUND = 0xFFE5DEFF.toInt()

    fun adaptive(emoji: String, density: Float): IconCompat =
        IconCompat.createWithAdaptiveBitmap(render(emoji, density))

    private fun render(emoji: String, density: Float): Bitmap {
        val size = (CANVAS_DP * density).roundToInt().coerceIn(108, 512)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        // The background must reach every edge: whatever the launcher's mask crops is still drawn.
        canvas.drawColor(BACKGROUND)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = size * SAFE_ZONE * 0.62f
        }
        // drawText positions by baseline, not by centre; this shifts it so the glyph's own box is
        // centred, which for emoji (which sit high in the em square) is visibly different.
        val metrics = paint.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji.ifBlank { "✨" }, size / 2f, baseline, paint)
        return bitmap
    }
}
