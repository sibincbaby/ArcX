package com.arcx.app

import android.app.Application
import com.arcx.core.domain.usecase.PurgeExpiredScreenshotsUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ArcxApplication : Application() {

    @Inject lateinit var systemSurfaces: SystemSurfaceCoordinator

    @Inject lateinit var purgeExpiredScreenshots: PurgeExpiredScreenshotsUseCase

    /** Process-lifetime scope: these collectors should outlive any single Activity. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        systemSurfaces.start(appScope)

        // Screen captures expire on their own rather than accumulating. Doing it at process
        // start means the sweep happens even for a user who only ever fires workflows from the
        // bubble and never opens the app, which is most of them.
        appScope.launch { runCatching { purgeExpiredScreenshots() } }
    }
}
