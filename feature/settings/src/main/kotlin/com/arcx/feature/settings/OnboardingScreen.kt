package com.arcx.feature.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.ErrorCard
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/**
 * Four calm screens rather than a wizard: what this is, what BYOK means, one key to paste, done.
 * Nothing here asks anyone to sign in, because there is nothing to sign in to.
 */
@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    fun goTo(page: Int) = scope.launch { pager.animateScrollToPage(page) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ByokPage()
                    2 -> ConnectPage(
                        state = state,
                        onApiKeyChange = viewModel::onApiKeyChange,
                        onToggleReveal = viewModel::onToggleReveal,
                        onTest = viewModel::onTest,
                    )

                    else -> DonePage(connected = state.connected)
                }
            }

            PageIndicator(current = pager.currentPage, count = PAGE_COUNT)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (pager.currentPage) {
                    2 -> {
                        // Skipping is a first-class path: the library, the builder and the
                        // settings all work without a key. Only running a workflow does not.
                        TextButton(onClick = { goTo(3) }) { Text("Skip for now") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { goTo(3) }, enabled = state.connected) { Text("Continue") }
                    }

                    3 -> {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { viewModel.onFinish(onDone) },
                            enabled = !state.finishing,
                        ) { Text("Start using ArcX") }
                    }

                    else -> {
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { goTo(pager.currentPage + 1) }) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun WelcomePage() = PageColumn {
    Text("✨", fontSize = 64.sp)
    Spacer(Modifier.height(24.dp))
    Text("ArcX", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Your workflows. One tap away.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "Build an AI action once — rewrite this, summarise that, translate the other — then " +
            "fire it from wherever you already are. Share sheet, selected text, a shortcut.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ByokPage() = PageColumn {
    Text("🔑", fontSize = 56.sp)
    Spacer(Modifier.height(24.dp))
    Text("Your key. Your data.", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(20.dp))
    Text(
        "ArcX has no account and no server. You bring an API key from an AI provider you " +
            "already trust, and it stays encrypted on this device.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "When you run a workflow, your text goes from this phone straight to that provider and " +
            "the answer comes straight back. Nothing passes through us, so there is no bill " +
            "from us and nothing of yours for us to lose.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConnectPage(
    state: OnboardingUiState,
    onApiKeyChange: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onTest: () -> Unit,
) = PageColumn {
    val uriHandler = LocalUriHandler.current

    Text("Connect a provider", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Gemini has a free tier and is the one ArcX supports today. More are on the way.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◆", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName(state.type), style = MaterialTheme.typography.titleSmall)
                Text(
                    defaultModel(state.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.connected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Connected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    apiKeyUrl(state.type)?.let { url ->
        TextButton(onClick = { uriHandler.openUri(url) }) {
            Text("Get a free key")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("API key") },
        singleLine = true,
        visualTransformation =
            if (state.revealKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleReveal) {
                Icon(
                    imageVector = if (state.revealKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (state.revealKey) "Hide key" else "Show key",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onTest, enabled = state.canTest) { Text("Test") }
        if (state.test is TestState.Running) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

    when (val test = state.test) {
        is TestState.Success -> {
            Spacer(Modifier.height(12.dp))
            Text(
                "Connected. ${test.modelCount} model${if (test.modelCount == 1) "" else "s"} available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        is TestState.Failure -> {
            Spacer(Modifier.height(12.dp))
            ErrorCard(title = "Could not connect", message = test.message)
        }

        else -> Unit
    }
}

@Composable
private fun DonePage(connected: Boolean) = PageColumn {
    Text(if (connected) "🎉" else "👋", fontSize = 56.sp)
    Spacer(Modifier.height(24.dp))
    Text(
        if (connected) "You're set up" else "Have a look around",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        if (connected) {
            "A handful of ready-made workflows are waiting on your home screen. Run one, then " +
                "make it yours — or build your own from scratch."
        } else {
            "Ready-made workflows are waiting on your home screen. You can browse and edit " +
                "them now; add a provider key in Settings whenever you want to run one."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PageIndicator(current: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 22.dp else 8.dp, label = "dot$index")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 8.dp)
                    .background(
                        color = if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
