package com.arcx.integration.entrypoints.accessibility

import com.arcx.core.common.di.IoDispatcher
import com.arcx.core.domain.capture.ScreenContextProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The injectable half of the accessibility feature. Everything it does resolves to "ask the holder
 * whether a service is bound"; when none is, each method returns the empty answer instead of
 * throwing, because ArcX must stay fully usable for the majority of users who never grant this.
 */
@Singleton
internal class AccessibilityScreenContextProvider @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : ScreenContextProvider {

    override fun isAvailable(): Boolean = AccessibilityServiceHolder.isConnected

    /**
     * The last app the user had in front, never ArcX itself. Deliberately answered from the two
     * values the service already keeps rather than by inspecting windows: this is not a suspending
     * call, so it has to stay free of binder traffic.
     */
    override fun currentPackage(): String? = AccessibilityServiceHolder.current()?.let {
        it.foregroundPackage ?: it.snapshotPackage()
    }

    /**
     * Off the main thread: reading a node tree is synchronous IPC into the inspected app, and on a
     * dense screen that is tens of milliseconds — several dropped frames if it ran on the UI thread
     * while the bubble's panel is animating.
     */
    override suspend fun screenText(): String? = withContext(io) {
        AccessibilityServiceHolder.current()?.readScreenText()
    }

    /**
     * False on anything below Android 11, which is where `takeScreenshot` arrived, and false
     * whenever the capability has not actually been granted. Callers use this to decide whether to
     * offer a vision workflow at all, so it must not be optimistic.
     */
    override fun canScreenshot(): Boolean =
        AccessibilityServiceHolder.current()?.canScreenshot() == true

    /**
     * Unlike [screenText] this is a volatile field read: no binder traffic, no decoding, nothing
     * worth a dispatcher hop. The expensive half — the grab and the encode — already happened, back
     * when the bubble had itself hidden and the user's app was still the only thing on screen.
     */
    override suspend fun screenshot(): ByteArray? =
        AccessibilityServiceHolder.current()?.latestScreenImage()

    /**
     * Suspends until the frame has been encoded, not merely grabbed. [captureScreenImage] reports
     * those separately because the bubble wants its panel back the instant the pixels are off the
     * display — but a caller that is about to *send* the picture has to wait for the JPEG, or it
     * reads the previous frame out from under itself.
     */
    override suspend fun captureScreenshotNow(): ByteArray? {
        val service = AccessibilityServiceHolder.current() ?: return null
        if (!service.canScreenshot()) return null
        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            service.captureScreenImage(
                onGrabbed = {},
                onEncoded = { jpeg ->
                    // The platform makes no promise about calling back exactly once, and resuming
                    // a continuation twice throws.
                    if (!resumed) {
                        resumed = true
                        continuation.resume(jpeg)
                    }
                },
            )
        }
    }
}
