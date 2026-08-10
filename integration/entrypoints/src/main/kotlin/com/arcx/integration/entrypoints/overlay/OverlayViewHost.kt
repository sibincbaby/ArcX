package com.arcx.integration.entrypoints.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The owners a Compose view needs, for a window that has no Activity behind it.
 *
 * This is the single most common way a "Compose in an overlay" feature fails. A [ComposeView] added
 * straight to the [android.view.WindowManager] throws on attach ("ViewTreeLifecycleOwner not
 * found") because Compose looks up the lifecycle, view-model store and saved-state registry from
 * the view tree, and in an Activity those are installed by the Activity itself. Here nothing
 * installs them, so the service owns this object, drives it by hand, and hangs it on the view
 * before adding it to the window.
 *
 * Driving it properly matters as much as having it: the recomposer Compose creates for a window is
 * lifecycle-aware and stays paused below STARTED, so a host stuck at CREATED produces a view that
 * attaches without crashing and then never draws anything.
 */
internal class OverlayViewHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /**
     * Restores from null: an overlay has no saved instance state to come back to, but the registry
     * still has to be attached and restored before the lifecycle moves off INITIALIZED or it will
     * refuse reads later.
     */
    fun create() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
