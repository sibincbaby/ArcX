package com.arcx.core.domain.usecase

import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.capture.ScreenshotStore
import com.arcx.core.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Expires screenshots the user's retention setting says are old enough to go. Meant to run at
 * app start: these images stop being useful long before they stop being sensitive, so they have
 * to age out on their own rather than wait for someone to remember them.
 */
class PurgeExpiredScreenshotsUseCase @Inject constructor(
    private val screenshots: ScreenshotStore,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {
    suspend operator fun invoke() {
        // FOREVER has no cutoff — the user asked to keep them, so nothing is removed.
        val days = settings.current().screenshotRetention.days ?: return
        screenshots.purgeOlderThan(time.nowMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
    }
}

/**
 * The "delete screenshots" button in Settings: every image, now, whatever the retention says.
 * Run history survives — the rows are text the user may still want, and one whose image is gone
 * simply shows none.
 */
class DeleteAllScreenshotsUseCase @Inject constructor(
    private val screenshots: ScreenshotStore,
) {
    suspend operator fun invoke() = screenshots.deleteAll()
}
