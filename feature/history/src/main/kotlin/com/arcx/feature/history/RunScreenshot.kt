package com.arcx.feature.history

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PreviewShape = RoundedCornerShape(14.dp)
private val PreviewMaxHeight = 320.dp

/**
 * Wider than any phone-sized sheet, so the decode never has to be redone for a bigger window.
 * Exactness does not matter: [sampleSizeFor] only ever halves, so anything in the right
 * neighbourhood picks the same divisor.
 */
private val PreviewTargetWidth = 480.dp

private sealed interface ScreenshotState {
    data object Loading : ScreenshotState
    data class Ready(val image: ImageBitmap) : ScreenshotState

    /** The row outlived its file — the retention sweep deletes images without touching runs. */
    data object Missing : ScreenshotState
}

/**
 * The screen capture a run was given. Only the detail sheet uses this: a screenshot is several
 * megabytes once decoded, and a history list that decoded one per row would run out of memory
 * long before the user ran out of scrolling.
 */
@Composable
internal fun RunScreenshot(path: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val targetWidthPx = with(density) { PreviewTargetWidth.roundToPx() }
    val targetHeightPx = with(density) { PreviewMaxHeight.roundToPx() }

    val state by produceState<ScreenshotState>(
        initialValue = ScreenshotState.Loading,
        path,
        targetWidthPx,
        targetHeightPx,
    ) {
        value = withContext(Dispatchers.IO) {
            val bitmap = decodeSampled(path, targetWidthPx, targetHeightPx)
            if (bitmap == null) ScreenshotState.Missing else ScreenshotState.Ready(bitmap.asImageBitmap())
        }
    }

    when (val current = state) {
        ScreenshotState.Loading -> Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        ScreenshotState.Missing -> Row(
            modifier = modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ImageNotSupported,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "This screenshot is no longer on the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is ScreenshotState.Ready -> Image(
            bitmap = current.image,
            contentDescription = "The screenshot this run was given",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = PreviewMaxHeight)
                .clip(PreviewShape),
        )
    }
}

/**
 * Decodes at the smallest size that still fills the preview. Null covers every way this can go
 * wrong at once — file swept by the retention job, storage cleared, JPEG truncated by a crash
 * mid-write — because the UI treats all of them the same way.
 *
 * Must not run on the main thread.
 */
private fun decodeSampled(path: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
    if (!File(path).isFile) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetWidthPx, targetHeightPx)
    }
    // runCatching rather than a null check alone: a decode can still fail on OOM, and a preview
    // is never worth taking the process down for.
    return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
}

/**
 * Largest power-of-two divisor that leaves the image at least as big as the target on both
 * axes, so the preview is downscaled but never upscaled into blur.
 */
internal fun sampleSizeFor(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    if (targetWidth <= 0 || targetHeight <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
        sample *= 2
    }
    return sample
}
