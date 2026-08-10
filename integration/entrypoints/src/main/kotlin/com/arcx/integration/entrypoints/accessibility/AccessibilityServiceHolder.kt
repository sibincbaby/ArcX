package com.arcx.integration.entrypoints.accessibility

/**
 * Process-wide handle to the bound accessibility service.
 *
 * The platform constructs and destroys [ArcxAccessibilityService]; Hilt never sees the instance,
 * so there is no graph to inject it into. Instead the service pushes itself in here the moment the
 * system binds it, and the injectable [AccessibilityScreenContextProvider] reads it back out.
 *
 * A null field is the normal state, not a failure: most users will never turn the service on.
 */
internal object AccessibilityServiceHolder {

    @Volatile
    private var service: ArcxAccessibilityService? = null

    val isConnected: Boolean get() = service != null

    fun attach(instance: ArcxAccessibilityService) {
        service = instance
    }

    /**
     * Identity-checked. Toggling the service off and on quickly can have the outgoing instance's
     * onDestroy run *after* the replacement has already connected; an unchecked clear would then
     * null out the live service and leave screen reading silently broken until the next reboot.
     */
    fun detach(instance: ArcxAccessibilityService) {
        if (service === instance) service = null
    }

    fun current(): ArcxAccessibilityService? = service
}
