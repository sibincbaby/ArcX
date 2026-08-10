package com.arcx.core.domain.capture

/**
 * Persists the screenshots that history shows.
 *
 * These are the most sensitive bytes ArcX touches — a screen capture contains whatever happened
 * to be visible, not just what the user meant to act on. Implementations must keep them in
 * app-internal storage, out of any backup, and must actually delete the files when the runs that
 * reference them go away; an orphaned image is a privacy leak with no UI left to remove it.
 */
interface ScreenshotStore {
    /** Returns the stored path, or null if it could not be written. */
    suspend fun save(runId: String, jpeg: ByteArray): String?

    suspend fun delete(paths: List<String>)

    suspend fun deleteAll()

    /** Removes screenshots last modified before [cutoffMillis]; used to honour the retention setting. */
    suspend fun purgeOlderThan(cutoffMillis: Long)
}
