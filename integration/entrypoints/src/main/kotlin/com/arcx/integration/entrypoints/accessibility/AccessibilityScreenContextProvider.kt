package com.arcx.integration.entrypoints.accessibility

import com.arcx.core.common.di.IoDispatcher
import com.arcx.core.domain.capture.ScreenContextProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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

    override suspend fun replaceFocusedText(text: String): Boolean = withContext(io) {
        AccessibilityServiceHolder.current()?.setFocusedText(text) ?: false
    }
}
