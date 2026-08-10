package com.arcx.core.data.screenshot

import com.arcx.core.common.di.IoDispatcher
import com.arcx.core.data.di.ScreenshotDirectory
import com.arcx.core.domain.capture.ScreenshotStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One JPEG per run under `filesDir/screenshots`, which is app-internal: no other app can read it,
 * and `allowBackup=false` plus the data-extraction rules keep it out of cloud and device-transfer
 * backups. External storage and content providers are deliberately not options here — a screen
 * capture holds whatever happened to be visible, so it never leaves the app sandbox.
 */
@Singleton
internal class ScreenshotStoreImpl @Inject constructor(
    @ScreenshotDirectory private val directory: File,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ScreenshotStore {

    /**
     * A failed write is not worth failing the run over — the answer is already on its way and
     * History just shows no image — so the caller gets null instead of an exception.
     */
    override suspend fun save(runId: String, jpeg: ByteArray): String? = withContext(io) {
        try {
            directory.mkdirs()
            val file = File(directory, "$runId.jpg")
            file.writeBytes(jpeg)
            file.absolutePath
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun delete(paths: List<String>) {
        withContext(io) {
            // Paths arrive from database rows, so only files that really sit in our directory are
            // touched; a stale or corrupted row must not be able to aim delete() somewhere else.
            val root = directory.absoluteFile
            paths.map { File(it).absoluteFile }
                .filter { it.parentFile == root }
                .forEach { it.delete() }
        }
    }

    override suspend fun deleteAll() {
        withContext(io) { directory.deleteRecursively() }
    }

    /** Last-modified is the capture time: nothing rewrites these files after [save]. */
    override suspend fun purgeOlderThan(cutoffMillis: Long) {
        withContext(io) {
            directory.listFiles()?.forEach { if (it.lastModified() < cutoffMillis) it.delete() }
        }
    }
}
