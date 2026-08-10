package com.arcx.core.domain.capture

/**
 * Screen access provided by the accessibility service in :integration:entrypoints.
 *
 * Every method must be safe to call when the service is disabled — ArcX has to keep working
 * without it, since the user may never grant it and Play may take a while to approve it.
 */
interface ScreenContextProvider {
    /** True only when the accessibility service is connected and bound. */
    fun isAvailable(): Boolean

    /** Visible text of the foreground window, or null when unavailable. */
    suspend fun screenText(): String?

    /** True when this device and permission state allow pixel capture at all. */
    fun canScreenshot(): Boolean

    /**
     * JPEG of the user's screen for vision workflows, or null when unavailable.
     *
     * Like [screenText] this returns the most recent capture rather than taking one now: ArcX's
     * own window covers the user's screen by the time a workflow runs, so the frame has to have
     * been taken at the moment the user chose the workflow, before anything of ours was drawn.
     */
    suspend fun screenshot(): ByteArray?

    /** Package name of the foreground app, or null when unavailable. */
    fun currentPackage(): String?

    /**
     * Replaces the text in the currently focused editable field.
     * Returns false when there is no focused editable node — callers must fall back to copy.
     */
    suspend fun replaceFocusedText(text: String): Boolean
}

interface ClipboardAccess {
    /** Null on Android 10+ when ArcX does not hold window focus. */
    fun read(): String?
    fun write(label: String, text: String)
}
