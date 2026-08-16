package com.arcx.feature.runner.output

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.Workflow
import com.arcx.feature.runner.R
import com.arcx.feature.runner.RunnerOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CHANNEL_ID = "arcx_results"

/**
 * Must not collide with the bubble's foreground-service id in `OverlayService` — it was 4201 there
 * too, and because notification ids are per-app rather than per-channel, a workflow finishing in
 * the shade while the bubble was running silently replaced the service's ongoing notification.
 */
private const val NOTIFICATION_ID = 4202
private const val NOTIFICATION_PREVIEW = 4_000

/** Where a file save was headed, held while the user is off in the system picker. */
private data class PendingSave(val title: String, val text: String, val pdf: Boolean)

/**
 * Turns a workflow's [OutputTarget] into whatever the platform needs — a clip, a chooser, a
 * document, a notification — and reports back in the sheet when it cannot.
 *
 * Returned as a single lambda rather than an object because every launcher and every scrap of
 * pending state it needs has to be remembered in composition anyway.
 */
@Composable
internal fun rememberOutputApplier(
    isReplaceable: Boolean,
    onNotice: (String) -> Unit,
    onClose: (RunnerOutcome) -> Unit,
): (Workflow, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The Storage Access Framework hands the Uri back long after the run finished, so the
    // payload has to wait here for it.
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    var pendingNotification by remember { mutableStateOf<Pair<Workflow, String>?>(null) }

    val write: (Uri?) -> Unit = { uri ->
        val save = pendingSave
        pendingSave = null
        when {
            uri == null -> onNotice("Save cancelled. The answer is still here.")
            save == null -> Unit
            else -> scope.launch {
                val saved = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            if (save.pdf) renderPdf(save.title, save.text, stream)
                            else stream.write(save.text.toByteArray())
                        } ?: error("Could not open $uri")
                    }
                }.isSuccess
                onNotice(if (saved) "Saved." else "Couldn't write that file.")
            }
        }
    }

    val createMarkdown = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
        write,
    )
    val createPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
        write,
    )
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingNotification
        pendingNotification = null
        if (granted && pending != null) {
            context.postResult(pending.first, pending.second)
            onClose(RunnerOutcome())
        } else {
            // Denied is not a failure: the answer is already on screen, which is where the
            // notification would have sent them anyway.
            onNotice("Notifications are off, so the answer stays here.")
        }
    }

    return { workflow, text ->
        when (workflow.output) {
            OutputTarget.BOTTOM_SHEET, OutputTarget.POPUP -> Unit

            OutputTarget.CLIPBOARD -> {
                context.copyText(workflow.name, text)
                context.toast("Copied to clipboard")
                onClose(RunnerOutcome())
            }

            OutputTarget.REPLACE_SELECTION ->
                if (isReplaceable) {
                    onClose(RunnerOutcome(replacementText = text))
                } else {
                    context.copyText(workflow.name, text)
                    onNotice("That text wasn't editable, so the answer was copied instead.")
                }

            OutputTarget.SHARE -> context.shareText(workflow.name, text)

            OutputTarget.SAVE_MARKDOWN -> {
                pendingSave = PendingSave(workflow.name, text, pdf = false)
                createMarkdown.launch(fileName(workflow.name, "md"))
            }

            OutputTarget.SAVE_PDF -> {
                pendingSave = PendingSave(workflow.name, text, pdf = true)
                createPdf.launch(fileName(workflow.name, "pdf"))
            }

            OutputTarget.NOTIFICATION ->
                if (context.canNotify()) {
                    context.postResult(workflow, text)
                    onClose(RunnerOutcome())
                } else {
                    pendingNotification = workflow to text
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
        }
    }
}

internal fun Context.copyText(label: String, text: String) {
    getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun Context.shareText(subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    // The runner may be hosted by a transparent Activity started from another app's task, so
    // the chooser needs its own task to land in.
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * The one place in ArcX that still speaks in Toasts, and it has to.
 *
 * Everything else now shares the single SnackbarHostState hoisted into the Scaffold in
 * `ArcxNavHost` — but that host lives in MainActivity's composition, and the runner is
 * `RunnerActivity`: a separate, translucent Activity drawn over whatever app the user was in.
 * There is no shared composition to reach into, and `LocalSnackbarHostState` would fall through
 * to its unhosted default and say nothing at all.
 *
 * A Toast is also the only thing that still works after the case that needs it most. A CLIPBOARD
 * workflow copies and closes in the same breath; a snackbar inside a window that is finishing goes
 * with the window, while a Toast outlives it. That is the whole reason the confirmation is
 * believable.
 */
internal fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private fun Context.canNotify(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission") // Guarded by canNotify() and the permission launcher.
private fun Context.postResult(workflow: Workflow, text: String) {
    val manager = NotificationManagerCompat.from(this)
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Workflow results")
            .setDescription("Answers from workflows set to finish in the notification shade")
            .build(),
    )
    val body = text.take(NOTIFICATION_PREVIEW)
    manager.notify(
        NOTIFICATION_ID,
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_arcx_result)
            // The name alone: a workflow's icon is a key like "edit_note" now, not a glyph.
            .setContentTitle(workflow.name)
            .setContentText(body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build(),
    )
}

private val STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault())

/** Names the file after the workflow so a folder of exports is readable without opening them. */
private fun fileName(workflowName: String, extension: String): String {
    val slug = workflowName
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "arcx" }
        .take(40)
    return "$slug-${STAMP.format(Instant.now())}.$extension"
}
