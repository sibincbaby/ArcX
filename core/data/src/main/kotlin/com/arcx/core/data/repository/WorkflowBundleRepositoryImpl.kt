package com.arcx.core.data.repository

import android.content.Context
import android.net.Uri
import com.arcx.core.common.di.IoDispatcher
import com.arcx.core.data.bundle.WorkflowBundle
import com.arcx.core.data.bundle.encodeWorkflowBundle
import com.arcx.core.data.bundle.parseWorkflowBundle
import com.arcx.core.data.bundle.sanitisedForImport
import com.arcx.core.data.bundle.toSpec
import com.arcx.core.data.bundle.toWorkflowAsImport
import com.arcx.core.domain.repository.WorkflowBundleRepository
import com.arcx.core.domain.usecase.SaveWorkflowUseCase
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The gallery lives in `:feature:discover`'s assets, not this module's. That is fine and not an
 * accident: Android merges every module's `assets/` into one directory in the APK, so the file is
 * open-able by name from anywhere at run time, and leaving it where it is keeps the gallery's
 * content next to the screen that shows it. The reading of it belongs here, with the parsing.
 */
private const val GALLERY_ASSET = "gallery.json"

internal class WorkflowBundleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveWorkflow: SaveWorkflowUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
) : WorkflowBundleRepository {

    override suspend fun readGallery(): List<WorkflowSpec> = withContext(io) {
        context.assets.open(GALLERY_ASSET)
            .bufferedReader()
            .use { parseWorkflowBundle(it.readText()).workflows }
            .usable()
    }

    override suspend fun read(uri: Uri): List<WorkflowSpec> = withContext(io) {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("That file could not be opened")
        parseWorkflowBundle(text).workflows.usable()
    }

    /**
     * Goes through [SaveWorkflowUseCase] rather than straight to the DAO so an imported workflow is
     * stamped exactly like one the user built: the blank id from [toWorkflowAsImport] is what makes
     * it mint a fresh one.
     */
    override suspend fun install(specs: List<WorkflowSpec>): List<Workflow> =
        specs.map { saveWorkflow(it.toWorkflowAsImport()) }

    override suspend fun write(uri: Uri, workflows: List<Workflow>) {
        val bundle = WorkflowBundle(workflows = workflows.map { it.toSpec() })
        val text = encodeWorkflowBundle(bundle)
        withContext(io) {
            context.contentResolver.openOutputStream(uri)
                ?.bufferedWriter()
                ?.use { it.write(text) }
                ?: error("That file could not be written")
        }
    }

    /**
     * Sanitised, and without the entries that are not workflows at all.
     *
     * Coercion can rescue an unrecognised enum value but has nothing to fall back to for `name` or
     * `prompt`, so a blank one is dropped here rather than being written as an unusable row — and
     * dropping one entry keeps the rest of the file, which is the whole point.
     */
    private fun List<WorkflowSpec>.usable(): List<WorkflowSpec> =
        filter { it.name.isNotBlank() && it.prompt.isNotBlank() }
            .map { it.sanitisedForImport() }
}
