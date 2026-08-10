# ArcX

**Your workflows. One tap away.**

An Android *personal AI workflow launcher*. Build a reusable AI action once — name, input,
prompt, provider, output — then run it from wherever you already are: the share sheet, the
text-selection menu, a floating bubble, a home-screen shortcut, a widget, or a Quick Settings
tile.

Not a chatbot, and not an automation platform. The whole point is that a useful AI action
should take one tap and about two seconds, with no chat window and no prompt writing at the
moment you need it.

## BYOK

ArcX never owns your AI. You connect your own provider with your own key, so there is no ArcX
subscription and no ArcX server. There is no account at all — your workflows, history and keys
stay on the device, and your text goes only to the provider you configured.

Gemini is implemented today. The provider layer is a Hilt multibinding behind a single
interface, so OpenAI, Anthropic, OpenRouter, Groq, Ollama and LM Studio are each one
implementation and one `@Binds` line.

## Building

Requires JDK 17 and the Android SDK (compileSdk 37).

```bash
./gradlew assembleDebug        # build
./gradlew installDebug         # install on a connected device
./gradlew testDebugUnitTest    # unit tests
```

The Gradle wrapper is mandatory — the toolchain is AGP 9.3.1 / Gradle 9.7 / Kotlin 2.2.10.
Note that AGP 9 ships Kotlin itself, so the `kotlin-android` plugin must **not** be applied,
and the Kotlin version is pinned by AGP rather than chosen independently.

Testing the bubble or screen reading on Xiaomi/MIUI: **reinstalling revokes both the overlay
permission and the accessibility service every time**, silently. After each `installDebug`:

```bash
adb shell appops set com.arcx.app SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services \
  com.arcx.app/com.arcx.integration.entrypoints.accessibility.ArcxAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

On first launch, onboarding asks for a provider key. Get a free Gemini key at
[aistudio.google.com/apikey](https://aistudio.google.com/apikey) — `gemini-flash-lite-latest`
is the default because it has the most generous free tier.

## Architecture

Multi-module Clean Architecture + MVVM, Jetpack Compose with Material 3 and dynamic colour.

```
:app                      Application, navigation, RunnerActivity (every entry point lands here)
:core:model               Pure Kotlin domain types — no Android dependencies
:core:common              Prompt variable engine, dispatchers, time source
:core:designsystem        Theme, shared components, Markdown rendering
:core:data                Room, DataStore, the Keystore-backed API key vault
:core:ai                  Provider abstraction, Gemini SSE streaming
:core:domain              Repository interfaces and use cases
:feature:*                home · workflow · runner · history · settings · discover
:integration:entrypoints  Accessibility service, bubble, shortcuts, widget, QS tile
```

Two things hold the shape together:

- **`ExecuteWorkflowUseCase` is the only execution path.** Share sheet, text selection, bubble,
  shortcut, widget and tile all funnel through it, so provider resolution, variable expansion,
  history and error mapping exist exactly once.
- **`:core:domain` owns every contract.** Data, AI and platform integrations implement
  interfaces defined there; features depend on domain and never on each other.

API keys live only in an AES-256-GCM key held in the Android Keystore, with the ciphertext in
its own DataStore file. They are never written to Room, never logged (the auth header is
redacted in the debug HTTP log), and excluded from backup.

## Status

v1 feature-complete and verified end to end on a physical device. See
[`docs/play-store-readiness.md`](docs/play-store-readiness.md) for what remains before a Play
release — chiefly a signing config, a hosted privacy policy, and the two manual-review
declarations for the accessibility service and the `specialUse` foreground service.
