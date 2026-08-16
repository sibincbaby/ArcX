package com.arcx.app.runner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.theme.ArcXTheme
import com.arcx.core.designsystem.theme.ThemeMode
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.Attachment
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings
import com.arcx.core.model.WorkflowInput
import com.arcx.feature.runner.RunRequest
import com.arcx.feature.runner.RunnerHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Every way of firing a workflow lands here: the share sheet, the text-selection menu, the
 * floating bubble, pinned shortcuts, the widget and the Quick Settings tile. Keeping one
 * entry Activity means the execution path is identical no matter where the user came from.
 *
 * It is translucent and excluded from recents so it reads as an overlay on whatever app the
 * user was already in, not as a context switch.
 */
@AndroidEntryPoint
class RunnerActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Standard launch mode is required for the PROCESS_TEXT result to reach the caller,
        // but it also means every launch stacks a new instance. Nobody wants a pile of old
        // answers behind the current one, so retire the previous sheet explicitly.
        current?.get()?.takeIf { it !== this && !it.isFinishing }?.finish()
        current = WeakReference(this)

        val workflowId = resolveWorkflowId(intent)
        val isProcessText = intent?.action == Intent.ACTION_PROCESS_TEXT

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

            // Attachment bytes are read off the main thread; text-only launches resolve instantly.
            val input by produceState<WorkflowInput?>(initialValue = null) {
                value = withContext(Dispatchers.IO) { buildInput(intent) }
            }

            ArcXTheme(
                themeMode = when (settings?.theme) {
                    ThemePreference.LIGHT -> ThemeMode.LIGHT
                    ThemePreference.DARK -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                },
                dynamicColor = settings?.dynamicColor ?: true,
                // Every surface this Activity draws is a popup over someone else's app, so this is
                // the host the setting exists for.
                popupTransparency = settings?.popupTransparency ?: UserSettings().popupTransparency,
            ) {
                val resolved = input
                if (resolved != null) {
                    RunnerHost(
                        request = RunRequest(workflowId = workflowId, input = resolved),
                        onClose = { outcome ->
                            val replacement = outcome.replacementText
                            if (isProcessText && replacement != null) {
                                setResult(
                                    Activity.RESULT_OK,
                                    Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, replacement),
                                )
                            }
                            finish()
                            // No exit animation: this is an overlay, not a screen.
                            @Suppress("DEPRECATION")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                            } else {
                                overridePendingTransition(0, 0)
                            }
                        },
                    )
                }
            }
        }
    }

    private fun resolveWorkflowId(intent: Intent?): String? = when {
        intent == null -> null
        intent.hasExtra(EXTRA_WORKFLOW_ID) -> intent.getStringExtra(EXTRA_WORKFLOW_ID)
        // arcx://run/{workflowId} — used by shortcuts, the widget, the tile and the bubble.
        intent.action == Intent.ACTION_VIEW -> intent.data?.lastPathSegment
        else -> null
    }

    private fun buildInput(intent: Intent?): WorkflowInput {
        if (intent == null) return WorkflowInput()

        return when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> WorkflowInput(
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
                sourcePackage = callingPackageOrNull(),
                // Only an editable field can accept a replacement; read-only selections cannot.
                isReplaceable = !intent.getBooleanExtra(
                    Intent.EXTRA_PROCESS_TEXT_READONLY,
                    false,
                ),
            )

            Intent.ACTION_SEND -> WorkflowInput(
                text = intent.getStringExtra(Intent.EXTRA_TEXT),
                shareSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                attachments = listOfNotNull(
                    intent.streamExtra()?.let { readAttachment(it) },
                ),
                sourcePackage = callingPackageOrNull(),
            )

            Intent.ACTION_SEND_MULTIPLE -> WorkflowInput(
                text = intent.getStringExtra(Intent.EXTRA_TEXT),
                shareSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                attachments = intent.streamExtras().mapNotNull { readAttachment(it) },
                sourcePackage = callingPackageOrNull(),
            )

            else -> WorkflowInput(sourcePackage = callingPackageOrNull())
        }
    }

    private fun callingPackageOrNull(): String? = callingPackage ?: referrer?.host

    private fun readAttachment(uri: Uri): Attachment? = try {
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            // Guard against someone sharing a 200 MB video into a prompt.
            if (bytes.size > MAX_ATTACHMENT_BYTES) null else Attachment(mime, bytes)
        }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtras(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        /** Weak so a finished sheet is never held alive by this reference. */
        private var current: WeakReference<RunnerActivity>? = null

        const val EXTRA_WORKFLOW_ID = "com.arcx.app.WORKFLOW_ID"

        /** 20 MB — comfortably above a screenshot, well below anything that would OOM. */
        private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024

        fun intent(context: Context, workflowId: String? = null): Intent =
            Intent(context, RunnerActivity::class.java).apply {
                if (workflowId != null) putExtra(EXTRA_WORKFLOW_ID, workflowId)
            }
    }
}
