package com.arcx.feature.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
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
import com.arcx.core.designsystem.component.ArcxGlassSurface
import com.arcx.core.designsystem.component.glassSheen
import com.arcx.core.designsystem.theme.ArcXCorner
import com.arcx.core.designsystem.theme.PanelScrim
import com.arcx.core.designsystem.theme.glassColor
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
                // Offered whenever the source was editable. For a REPLACE_SELECTION workflow
                // the applier has already done it and closed, so this is really for the ones
                // that show a sheet: the answer can still go back where the text came from.
                onReplace = if (request.input.isReplaceable && text.isNotEmpty()) {
                    { close(RunnerOutcome(replacementText = text)) }
                } else {
                    null
                },
            )
        }
    }

    if (state.capturing) {
        // Draw nothing at all — not a transparent sheet, nothing. takeScreenshot photographs the
        // composited display, so a scrim or a card still on screen would be in the picture the
        // model is asked about. The window itself is already transparent with no dim, so what is
        // left is the app the user came from.
        LaunchedEffect(Unit) {
            // Two frames, then a settle: the first is the recomposition that removes the sheet,
            // the second is the one that reaches the compositor. The delay covers the display
            // pipeline, the same reason the bubble waits after hiding its handle.
            withFrameNanos {}
            withFrameNanos {}
            delay(CAPTURE_SETTLE_MS)
            viewModel.onWindowBlank()
        }
    } else if (compact == null) {
        // Waiting on the preference. One empty frame, and the host Activity is transparent.
    } else if (asDialog) {
        // The dialog surfaces had no dim at all until this: the sheet below gets one from
        // ModalBottomSheet and the bubble paints its own, so the compact picker was the one
        // surface of the three whose backdrop was bit-identical with and without it.
        //
        // Two ways of getting one are wrong here, both for the same reason. `backgroundDimEnabled`
        // on Theme.ArcX.Translucent must stay false, and DialogProperties(decorFitsSystemWindows =
        // false) quietly reintroduces it — it swaps the window onto Compose's
        // FloatingDialogWindowTheme, which sets `android:backgroundDimEnabled` back to true.
        // Measured on device when tried: FLAG_DIM_BEHIND set, dimAmount=0.6, backdrop at 0.27 of
        // its brightness rather than the intended 0.68. A window dim is composited by the *system*,
        // so it survives the capturing branch above, where ArcX has to contribute nothing at all
        // to the frame takeScreenshot photographs — every vision workflow would send a dimmed
        // picture to the model.
        //
        // So it is Compose, and it is in this branch, which means `state.capturing` removes it in
        // the same recomposition that removes the card. It goes in the runner's own window rather
        // than inside the Dialog because a dialog window is laid out *inside* the system bars —
        // filling it left an undimmed strip under the status bar (measured: ratio 1.0 above y=85,
        // 0.68 below it), and setting decorFitsSystemWindows on that window at runtime does not
        // move its frame. This Activity is edge to edge, so here the dim covers the display.
        Box(Modifier.fillMaxSize().background(PanelScrim))

        Dialog(
            onDismissRequest = { onClose(RunnerOutcome()) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            if (workflow == null && compact) {
                // The compact picker draws its own panel card; wrapping it in the surface below
                // would put a card inside a card.
                content(true)
            } else {
                // The same glass as the panel, not a second translucent card of its own — this is
                // the surface an answer comes back on, and the two are seen one after the other in
                // a single run.
                ArcxGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Column(Modifier.padding(top = 20.dp)) { content(true) }
                }
            }
        }
    } else {
        // The sheet is the surface most answers arrive on, so it wears the same glass as the panel
        // and the popup — assembled by hand because ModalBottomSheet draws its own container and
        // takes no Surface. containerColor and contentColor both have to be given: Material infers
        // the second from the first, and an exact scheme colour carrying an alpha is no longer a
        // match, so inferring would fall through to LocalContentColor and put black text on a dark
        // sheet.
        //
        // No rim on this one. Material puts its drag handle in a slot *above* this content lambda,
        // so a stroke drawn here would cross the sheet under the handle instead of tracing its top
        // edge, and reaching the real edge means replacing the handle — which is what carries the
        // sheet's dismiss semantics. A sheet is anchored to the bottom of the screen rather than
        // floating in the middle of it, so it has less edge to catch the light anyway.
        ModalBottomSheet(
            onDismissRequest = { onClose(RunnerOutcome()) },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = ArcXCorner.Panel, topEnd = ArcXCorner.Panel),
            containerColor = glassColor(MaterialTheme.colorScheme.surfaceContainerLow),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.glassSheen().navigationBarsPadding()) { content(false) }
        }
    }
}

/** Time for a blanked window to actually leave the display before the shutter. */
private const val CAPTURE_SETTLE_MS = 90L
