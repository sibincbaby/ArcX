package com.arcx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.app.navigation.ArcxNavHost
import com.arcx.app.runner.RunnerActivity
import com.arcx.core.designsystem.theme.ArcXTheme
import com.arcx.core.designsystem.theme.ThemeMode
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings
import com.arcx.feature.settings.OnboardingRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            val settings = (state as? AppUiState.Ready)?.settings
            ArcXTheme(
                themeMode = settings?.theme.toThemeMode(),
                dynamicColor = settings?.dynamicColor ?: true,
                // Nothing in the app's own screens is glass, but the Appearance screen previews it
                // and both need the same value to be telling the truth.
                popupTransparency = settings?.popupTransparency ?: UserSettings().popupTransparency,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        settings == null -> Unit // Still loading; the window stays blank briefly.

                        !settings.hasOnboarded -> OnboardingRoute(onDone = { /* settings flow re-emits */ })

                        else -> ArcxNavHost(
                            onRunWorkflow = { workflowId ->
                                startActivity(RunnerActivity.intent(this@MainActivity, workflowId))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun ThemePreference?.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
    ThemePreference.SYSTEM, null -> ThemeMode.SYSTEM
}
