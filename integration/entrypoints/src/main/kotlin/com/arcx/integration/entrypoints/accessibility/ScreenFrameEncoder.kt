package com.arcx.integration.entrypoints.accessibility

import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Long edge of the kept frame, in pixels.
 *
 * This device captures at 1080x2400, and that bitmap is ~10MB raw. The frame is base64'd into a
 * prompt and written to disk for history, so the native resolution is paid for three times over for
 * detail a vision model does not use — 1568 is the point past which Gemini's image tiling stops
 * resolving anything new, and it still leaves screen text comfortably legible.
 */
private const val MAX_SCREENSHOT_EDGE = 1568

/** Screens are flat colour and glyph edges; 80 is where JPEG stops visibly smearing the text. */
private const val SCREENSHOT_JPEG_QUALITY = 80

/** The same idea as [ScreenSnapshot], in pixels. Never written to disk from here. */
internal class ScreenFrame(
    val jpeg: ByteArray,
    val takenAt: Long,
)

/**
 * Turns a [ScreenshotResult] into the JPEG that is kept. Runs on the screenshot executor.
 *
 * The [android.hardware.HardwareBuffer] is a graphics allocation whose ownership the platform
 * hands over, and closing it is not tidiness: a leaked one is several megabytes of memory the
 * JVM heap has no idea about, so nothing will ever collect it, on every single capture. Hence
 * the `finally` — the wrap, the copy and the encode below all have their own ways to fail.
 *
 * The copy out of the buffer is unavoidable. `wrapHardwareBuffer` yields a `Config.HARDWARE`
 * bitmap, which cannot be read back or drawn onto a software canvas, so nothing can be scaled
 * or compressed until the pixels are in the heap.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal fun decodeFrame(screenshot: ScreenshotResult): ByteArray? {
    val buffer = screenshot.hardwareBuffer
    return try {
        val wrapped = runCatching {
            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
        }.getOrNull()
        val software = wrapped
            ?.runCatching { copy(Bitmap.Config.ARGB_8888, false) }
            ?.getOrNull()
        wrapped?.recycle()
        software?.let { encodeFrame(it) }
    } finally {
        buffer.close()
    }
}

/**
 * Downscales to [MAX_SCREENSHOT_EDGE] and JPEG-encodes, recycling [source] and anything it makes
 * along the way. Returns null if the encoder refuses.
 *
 * Call this off the main thread. Scaling a full-screen bitmap and compressing it are each tens of
 * milliseconds of pure CPU — several dropped frames if it ran while the bubble's panel was opening.
 */
private fun encodeFrame(source: Bitmap): ByteArray? {
    val longEdge = maxOf(source.width, source.height)
    val scaled = if (longEdge <= MAX_SCREENSHOT_EDGE) {
        source
    } else {
        val ratio = MAX_SCREENSHOT_EDGE.toDouble() / longEdge
        runCatching {
            source.scale(
                (source.width * ratio).roundToInt().coerceAtLeast(1),
                (source.height * ratio).roundToInt().coerceAtLeast(1),
                // Filtered: nearest-neighbour on a downscale this large turns body text into
                // speckle, and text is most of what a vision workflow is asked to read.
                filter = true,
            )
        }.getOrNull() ?: source
    }

    val stream = ByteArrayOutputStream(DEFAULT_JPEG_BUFFER)
    val encoded = runCatching {
        scaled.compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_JPEG_QUALITY, stream)
    }.getOrDefault(false)

    if (scaled !== source) scaled.recycle()
    source.recycle()
    return if (encoded) stream.toByteArray() else null
}

/** Roughly what a 1568px-long-edge screen encodes to, so the stream almost never has to regrow. */
private const val DEFAULT_JPEG_BUFFER = 256 * 1024
