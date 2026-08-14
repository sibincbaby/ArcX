package com.arcx.feature.runner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.WorkflowInput
import com.arcx.feature.runner.output.copyText
import com.arcx.feature.runner.output.rememberOutputApplier
import com.arcx.feature.runner.output.shareText
import com.arcx.feature.runner.output.toast
import com.arcx.feature.runner.ui.RunContent
import com.arcx.feature.runner.ui.WaitingRow
import com.arcx.feature.runner.ui.WorkflowPicker
import kotlinx.coroutines.launch

/** What an entry point asks the runner to do. */
data class RunRequest(
    /** Null → show the picker first. Share sheet and bubble arrive this way. */
    val workflowId: String? = null,
    val input: WorkflowInput,
)

data class RunnerOutcome(
    /** Non-null when the user chose "Replace selection" — the host Activity returns it via setResult. */
    val replacementText: String? = null,
)

/**
 * The surface that appears over whatever app the user was in. Hosted by a transparent
 * Activity, so it is always a sheet (or, for [OutputTarget.POPUP], a small centred card) over
 * a scrim — never an opaque screen that would read as leaving the app they were using.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunnerHost(
    request: RunRequest,
    onClose: (RunnerOutcome) -> Unit,
    viewModel: RunnerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.start(request) }

    val workflow = state.workflow
    val popup = workflow?.output == OutputTarget.POPUP

    // A compact run is hosted as a floating card for its whole life, not just while the picker is
    // up: swapping host the moment a workflow is tapped would slide a sheet in over the card the
    // user just tapped. Null means the preference has not been read yet, so nothing is drawn.
    val compact = state.compactPicker
    val asDialog = popup || compact == true

    // The sheet has to finish sliding out before the transparent host Activity goes away,
    // otherwise the surface blinks out with the window instead of leaving.
    val close: (RunnerOutcome) -> Unit = { outcome ->
        if (asDialog) onClose(outcome)
        else scope.launch { sheetState.hide() }.invokeOnCompletion { onClose(outcome) }
    }

    val applyOutput = rememberOutputApplier(
        isReplaceable = request.input.isReplaceable,
        onNotice = { notice = it },
        onClose = close,
    )

    val success = state.execution as? ExecutionState.Success
    LaunchedEffect(success, state.outputApplied) {
        if (workflow != null && success != null && !state.outputApplied) {
            // Latched in the ViewModel first: a rotation re-enters this effect, and copying
            // or saving the same answer twice is worse than not doing it at all.
            viewModel.onOutputApplied()
            applyOutput(workflow, success.text)
        }
    }

    val text = when (val execution = state.execution) {
        is ExecutionState.Streaming -> execution.text
        is ExecutionState.Success -> execution.text
        else -> ""
    }

    val content: @Composable (Boolean) -> Unit = { compact ->
        if (workflow == null) {
            if (state.resolving) {
                WaitingRow("Starting…")
            } else {
                WorkflowPicker(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onPick = { viewModel.run(it) },
                    compact = compact == true,
                )
            }
        } else {
            RunContent(
                workflow = workflow,
                execution = state.execution,
                stopped = state.stopped,
                notice = notice,
                compact = compact,
                onStop = viewModel::stop,
                onRetry = {
                    notice = null
                    viewModel.retry()
                },
                onCopy = {
                    context.copyText(workflow.name, text)
                    context.toast("Copied to clipboard")
                },
                onShare = { context.shareText(workflow.name, text) },
                onDone = { close(RunnerOutcome()) },
            )
        }
    }

    if (compact == null) {
        // Waiting on the preference. One empty frame, and the host Activity is transparent.
    } else if (asDialog) {
        Dialog(
            onDismissRequest = { onClose(RunnerOutcome()) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            if (workflow == null && compact) {
                // The compact picker draws its own panel card; wrapping it in the Surface below
                // would put a card inside a card.
                content(true)
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Column(Modifier.padding(top = 20.dp)) { content(true) }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = { onClose(RunnerOutcome()) },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.navigationBarsPadding()) { content(false) }
        }
    }
}
