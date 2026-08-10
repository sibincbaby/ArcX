package com.arcx.app

import android.content.Context
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.integration.entrypoints.ArcxEntrypoints
import com.arcx.integration.entrypoints.shortcut.ArcxShortcuts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the surfaces outside the app — the bubble, launcher shortcuts and the widget — in
 * step with the data they mirror.
 *
 * This lives in :app rather than in a feature because no single screen owns it: the bubble
 * setting is toggled in Settings, favourites change from Home, the list and the runner, and
 * the widget has to reflect all of them. Driving it from repository flows in one place means
 * no screen has to remember to call it.
 */
@Singleton
class SystemSurfaceCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val workflowRepository: WorkflowRepository,
    private val entrypoints: ArcxEntrypoints,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            settingsRepository.settings
                .map { it.bubbleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    // Never start the service without the permission — it would crash on
                    // addView. The settings UI is responsible for asking; we just obey.
                    entrypoints.setBubbleRunning(enabled && entrypoints.isOverlayGranted())
                }
        }

        scope.launch {
            workflowRepository.observeFavorites()
                .distinctUntilChanged()
                .collect { favorites ->
                    ArcxShortcuts.publishFavorites(context, favorites)
                    entrypoints.refreshFavoritesWidget()
                }
        }
    }
}
