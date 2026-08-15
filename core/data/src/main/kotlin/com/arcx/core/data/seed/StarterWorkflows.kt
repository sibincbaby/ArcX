package com.arcx.core.data.seed

import android.content.Context
import com.arcx.core.data.bundle.parseWorkflowBundle
import com.arcx.core.model.WorkflowSpec

const val STARTER_WORKFLOWS_ASSET = "starter_workflows.json"

/**
 * The workflows ArcX ships with, in the same envelope as the gallery and as anything a user
 * exports — see `com.arcx.core.data.bundle.WorkflowBundle`.
 *
 * Blocking asset read; callers are responsible for being off the main thread.
 */
fun readStarterWorkflows(context: Context): List<WorkflowSpec> =
    context.assets.open(STARTER_WORKFLOWS_ASSET)
        .bufferedReader()
        .use { parseWorkflowBundle(it.readText()).workflows }
