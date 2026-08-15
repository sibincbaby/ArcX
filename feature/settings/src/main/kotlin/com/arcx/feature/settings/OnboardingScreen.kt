package com.arcx.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UnfoldMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.StepBar
import com.arcx.core.designsystem.component.TintedIcon
import com.arcx.core.designsystem.theme.MetaTextStyle
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
            // Progress at the top rather than dots at the bottom: this is a short sequence with
            // an end, not a carousel to browse.
            StepBar(
                current = pager.currentPage,
                total = PAGE_COUNT,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )

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
                        ForwardButton(
                            label = "Continue",
                            enabled = state.connected,
                            onClick = { goTo(3) },
                        )
                    }

                    3 -> {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { viewModel.onFinish(onDone) },
                            enabled = !state.finishing,
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Start using ArcX") }
                    }

                    else -> {
                        Spacer(Modifier.weight(1f))
                        ForwardButton(
                            label = "Continue",
                            enabled = true,
                            onClick = { goTo(pager.currentPage + 1) },
                        )
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

/**
 * BYOK stated as a feature, not as fine print. Where the text goes and where the key lives are
 * the two questions a stranger's AI app has to answer before anyone pastes a credential into
 * it, so both are on this screen rather than buried in a privacy policy nobody opens.
 */
@Composable
private fun ConnectPage(
    state: OnboardingUiState,
    onApiKeyChange: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onTest: () -> Unit,
) = PageColumn {
    val uriHandler = LocalUriHandler.current

    TintedIcon(
        icon = Icons.Outlined.Key,
        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        content = MaterialTheme.colorScheme.primary,
        size = 52.dp,
    )
    Spacer(Modifier.height(18.dp))
    Text("Bring your own key", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(10.dp))
    Text(
        "ArcX has no account and no server of its own. You connect a provider directly, so " +
            "there is nothing to subscribe to.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))
    OnboardingLabel("Provider")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TintedIcon(
                icon = Icons.Outlined.CheckCircle.takeIf { state.connected }
                    ?: Icons.Outlined.Key,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 34.dp,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName(state.type), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = defaultModel(state.type),
                    style = MetaTextStyle,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            // Gemini is the only provider with an implementation behind it today, so this is
            // an honest affordance, not a dropdown that would open onto seven dead ends.
            Icon(
                imageVector = Icons.Outlined.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier = Modifier.size(19.dp),
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    OnboardingLabel("API key")
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = onApiKeyChange,
        placeholder = { Text("Paste it here") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation =
            if (state.revealKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.connected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Connected",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        imageVector = if (state.revealKey) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = if (state.revealKey) "Hide key" else "Show key",
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(10.dp))
    NoteRow(
        icon = Icons.Outlined.Lock,
        text = "Stored in the Android Keystore, AES-256-GCM. Never written to the database, " +
            "never logged, excluded from backup.",
    )

    apiKeyUrl(state.type)?.let { url ->
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable { uriHandler.openUri(url) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Get a free key at ${url.removePrefix("https://").substringBefore('/')}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onTest, enabled = state.canTest, shape = RoundedCornerShape(14.dp)) {
            Text("Test")
        }
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

    Spacer(Modifier.height(14.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        NoteRow(
            icon = Icons.Outlined.Shield,
            text = "Your workflows, history and key stay on this device. Text goes only to the " +
                "provider you just chose.",
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun OnboardingLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun NoteRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(top = 1.dp, end = 8.dp)
                .size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun ForwardButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(50.dp),
    ) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
        )
    }
}
