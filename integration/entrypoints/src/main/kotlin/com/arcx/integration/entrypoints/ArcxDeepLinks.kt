package com.arcx.integration.entrypoints

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/**
 * The single contract every launch surface in this module shares with the app.
 *
 * Nothing here names the runner Activity. The bubble, the widget, the tile and the launcher
 * shortcuts all live in :integration:entrypoints while the runner lives in :app, and a class
 * reference would invert that dependency and drag the whole app graph in behind it. A URI is a
 * seam the manifest merger resolves at install time instead.
 */
object ArcxDeepLinks {

    const val SCHEME: String = "arcx"
    const val HOST: String = "run"

    /**
     * The launcher alias in :app that opens the picker. A name rather than a class reference for
     * the same reason as [uri] — this module must not depend on :app — and it is only ever used to
     * toggle the icon, so a rename would cost a dead switch rather than a crash.
     */
    const val ACTIONS_ALIAS: String = "com.arcx.app.runner.ActionsAlias"

    /** `arcx://run/{id}`, or `arcx://run/` when [workflowId] is null — that opens the picker. */
    fun uri(workflowId: String? = null): Uri = "$SCHEME://$HOST/${workflowId.orEmpty()}".toUri()

    /**
     * NEW_TASK is not optional: every caller in this module is a service, a widget host or the
     * Quick Settings panel, none of which own an Activity task to launch into. Pinning the package
     * keeps an implicit VIEW from ever reaching a chooser, or another app that claims the scheme.
     */
    fun intent(context: Context, workflowId: String? = null): Intent =
        Intent(Intent.ACTION_VIEW, uri(workflowId)).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
