package com.arcx.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class ArcxApplication : Application() {

    @Inject lateinit var systemSurfaces: SystemSurfaceCoordinator

    /** Process-lifetime scope: these collectors should outlive any single Activity. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        systemSurfaces.start(appScope)
    }
}
