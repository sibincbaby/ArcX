# ArcX

Android **personal AI workflow launcher**. A user builds a reusable AI action once — name, input
source, prompt, provider, output target — and fires it from anywhere: share sheet, text-selection
menu, floating bubble, Quick Settings tile, launcher icon, shortcut, widget, accessibility button.

**BYOK.** The user brings their own provider key. ArcX has no account, no backend, and no server-side
state. Nothing about the product may quietly break that.

Deeper background — the original PRD, what was built vs. dropped, and every non-obvious decision with
its evidence — is in `docs/architecture.md`. Read it before any structural change.

- `docs/screen-access.md` — the accessibility/screenshot permission model, the capability matrix,
  and an **open design decision**. Read it before touching anything to do with screen capture,
  `{{screen_text}}`, or the accessibility service.
- `docs/benchmarking.md` — how to measure startup and frame timing, and the current baseline.
- `docs/workflow-sharing.md` — the `.json` bundle format, what breaks when a file leaves the
  device it was made on, and the trust rules for importing someone else's prompt. Read it before
  touching `WorkflowBundle`/`WorkflowSpec`, import/export, or the bundled asset files. **Partly
  built** — its §7 items 0–2 shipped; 3–6 (review sheet, versioning, share sheet) are still design.

---

## Commands

```bash
./gradlew testDebugUnitTest          # 101 unit tests, all modules
./gradlew installDebug               # build + install on the attached device
./gradlew :core:domain:testDebugUnitTest
adb logcat -d | grep -E "arcx|AndroidRuntime"
```

There is **no lint or ktlint task wired up**. Do not claim one was run.

`KeystoreVault` **is** tested — four cases in `core/data/src/androidTest`. They are instrumented,
because the vault needs a real AndroidKeystore, so `testDebugUnitTest` does not run them and a
count of that task will always look as though the vault is untested. It is not.

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest   # startup + frame timing, on device
```

**That task uninstalls ArcX when it finishes**, taking the database, the preferences and the
Keystore-backed API key with it. `docs/benchmarking.md` has the way to run it without that, and
the baseline numbers to compare against. Do not trust a `dumpsys gfxinfo` number over a
macrobenchmark one — gfxinfo has no warm-up and measured ±25% run to run on an identical build.

## Verifying on a device

This project is verified on real hardware, not emulators, because most of its hard problems are
OEM behaviour. The useful probes:

```bash
adb shell am start -n com.arcx.app/.MainActivity
adb shell am start -a android.intent.action.VIEW -d "arcx://run/"      # picker
adb shell am start -a android.intent.action.VIEW -d "arcx://run/<id>"  # one workflow
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "hi"
adb shell dumpsys activity activities | grep -m1 topResumedActivity     # what is actually in front
adb shell dumpsys window windows | grep -oE "com\.arcx\.app, frame=\[Rect\([^)]*\)\]"
adb shell settings get secure enabled_accessibility_services
adb shell cmd accessibility call-system-action 11                       # fire the a11y button
adb shell run-as com.arcx.app cat /data/data/com.arcx.app/files/datastore/arcx_settings.preferences_pb | strings
```

**`adb shell input tap` is unreliable here.** ArcX's own bubble and the system accessibility button
are floating windows that intercept taps. Always read the real frame from `dumpsys window windows`
first, and confirm the outcome with `topResumedActivity` rather than assuming the tap landed.

**`am force-stop com.arcx.app` revokes the accessibility service** on Samsung and Xiaomi. If a test
suddenly shows screen reading as broken, check `enabled_accessibility_services` before debugging code.
This is not a small trap — it cost hours in one session, presenting as "the permission is granted
but nothing works". Do not force-stop while testing anything that touches the service.

**A sideload cannot be granted accessibility until restricted settings are allowed.** `installDebug`
leaves `installerPackageName=null`, so Android 13+ silently refuses the Accessibility toggle until
App info → ⋮ → Allow restricted settings — and that resets on **every reinstall**. Check with
`adb shell appops get com.arcx.app ACCESS_RESTRICTED_SETTINGS`.

---

## Build stack — read before touching Gradle

AGP 9 is not AGP 8, and most of the internet is still about AGP 8.

| | |
|---|---|
| AGP | 9.3.1 |
| Kotlin | 2.2.10 — **shipped by AGP**, not declared separately |
| KSP | 2.2.10-2.0.2 (must match Kotlin exactly) |
| Hilt | 2.59.2 (requires AGP 9) |
| compileSdk / target / min | 37 / 36 / 26 |
| Compose BOM | 2026.06.01 |

Traps that have already cost time:

- **Never add `org.jetbrains.kotlin.android`.** AGP 9 rejects it; Kotlin comes from AGP.
- **AGP 9 DSL interfaces dropped the Action-taking overloads.** Convention plugins use property
  access (`commonExtension.compileSdk = …`), not `defaultConfig { … }`. See
  `build-logic/convention/.../ProjectExtensions.kt`.
- `CommonExtension` is no longer generic — no type arguments.
- `android.disallowKotlinSourceSets=false` in `gradle.properties` is **load-bearing**; KSP fails
  without it.

---

## Modules

14 modules, ~156 Kotlin files. Dependencies point inward; nothing in `core/` knows about `feature/`.

```
:app                     Application, MainActivity, RunnerActivity, the merged manifest
:core:model              Pure Kotlin contracts, WorkflowSpec included. No Android imports.
:core:common             Dispatchers, PromptTemplate, TimeSource
:core:designsystem       Theme, Spacing/Shape tokens, shared components — see below
:core:data               Room, DataStore, KeystoreVault, bundle/ (envelope + Json), repository impls
:core:ai                 AiProvider abstraction, Gemini, SSE parsing, registry
:core:domain             Repository interfaces, ports, use cases (Execute/ResolveInput/RecordRun, …)
:feature:{home,workflow,runner,history,settings,discover}
:integration:entrypoints Accessibility service, bubble, widget, tile, shortcuts
```

**`:integration:entrypoints` must never depend on `:app`.** It reaches the runner through the
`arcx://run/{id}` URI and the component-name constant in `ArcxDeepLinks`, resolved by the manifest
merger at install time. Do not "fix" this into a class reference.

`:feature:settings` and `:feature:home` talk to system surfaces through the `SystemSurfaces` port in
`:core:domain`, implemented by `ArcxEntrypoints`. Add new system-permission state there, not by
adding a module dependency. Both re-read it on resume — none of it emits on change.

The bottom bar is **four tabs**: Home · Library · Discover · Activity. Settings is a full-screen
push off the Home header, not a tab; `arcx://` deep links go to `RunnerActivity`, never to a tab,
so tab routes are free to be renamed.

**Settings is six rows grouped by the question the user arrived with** — Providers · Entry points ·
Permissions · Appearance · Privacy & data · About — where does ArcX appear, what is ArcX allowed to
do, what does it look like, what does it keep. Put a new control on the screen that answers its
question, not on the screen belonging to the feature that introduced it: that is how Entry points
came to hold the accessibility disclosure, the notification grant, five appearance sliders and 45%
of every control in Settings. **Exactly one control in the app opens
`ACTION_ACCESSIBILITY_SETTINGS`** — Permissions → Screen reading, gated by the disclosure screen.
See `docs/play-store-readiness.md` §1 before adding a second.

**Motion lives in `Motion.kt`** (`:core:designsystem`), and `ArcxNavHost` picks between its two
kinds by asking whether both ends of the move are tabs. Do not fall back to navigation-compose's
defaults — they are a single 700ms crossfade for every destination, which measured ~500ms of
visible ghosting on device and made a push look identical to a tab switch.

## The design system has a middle layer — use it

Between the theme and the screens, because without one five screens had each rebuilt the same list
row and drifted. Feature-module `.dp` literals went 492 → 182 when every screen moved onto it.

- **Tokens:** `Spacing` (Xs…Xxxl on a 4dp grid, plus `Gutter` = 20dp) and `ArcXCorner`/`ArcXShapes`
  (Chip · Control · Card · Panel), wired into `MaterialTheme.shapes` by `ArcXTheme`.
- **Components:** `ArcxListRow`, `ArcxSearchField`, `ArcxPill`, `ArcxAction`, `NoticeCard`,
  `LoadingState`, `EmptyState`, `SectionHeader`, `WorkflowPanel*`, `WorkflowIcon`.

**Read `MaterialTheme.shapes` and the `Spacing` tokens instead of hardcoding a `.dp`, and reach for
the shared row/field/pill before writing a new one.** Minimum touch targets and `Role.Button` live in
the primitives, so a hand-rolled clickable loses both silently.

---

## History has to stay bounded

The runs table is the only thing in ArcX that grows with use, and two of the most-visited screens
read it. Measured on device, going from 13 to 5,000 runs tripled every frame-time percentile while
scrolling Activity and pushed the tab-switch 99th from 85ms to 150ms.

Three rules keep that from coming back, all of them easy to undo by accident:

1. **`HistoryRepository` has no "give me everything" method, on purpose.** Every read is bounded —
   by row count, by timestamp, or by being a SQL aggregate. Do not add `observeAll()`.
2. **Lists take `RunSummary`, not `RunRecord`.** The two preview columns are most of a row's bytes
   and no list draws them; the detail sheet fetches the full record by id.
3. **`RunRecord.HISTORY_LIMIT` is enforced on insert**, and pruning deletes the dropped rows'
   screenshots in the same breath — see `HistoryRepositoryImpl.prune`. Rows without their images is
   the leak `ScreenshotStore` warns about.

ViewModel folds over any of this belong on `flowOn(defaultDispatcher)`. `stateIn(viewModelScope)`
collects on `Main.immediate`, so without it a `combine {}` transform is UI-thread work — that is
what turned a row count into dropped frames rather than just memory.

## Compose stability

`:core:model` is pure Kotlin and is not compiled with the Compose plugin, so the compiler has no
stability information for anything in it. Left alone it assumes the worst, and that cascades:
`Workflow` unstable makes `HomeTile` unstable, which under strong skipping means every tile in the
grid is compared by *instance* against an object the ViewModel just reallocated — so every tile and
every history row recomposed on every keystroke and every completed run.

`compose-stability.conf` at the repo root declares those types stable, and both Compose convention
plugins wire it in. **It is a promise, not a hint** — everything in `:core:model` must stay a data
class of primitives, Strings and enums with `val` properties. Break that and Compose will silently
miss updates.

To check the effect of any change here:

```bash
./gradlew assembleDebug -Pcompose.reports --rerun-tasks
# then read <module>/build/compose-reports/<module>-composables.txt
```

A composable parameter that reads `unstable` there is one that recomposes whenever its owner
re-emits, whether or not the value changed.

## The one execution path

Every entry point ends in `ExecuteWorkflowUseCase`. Provider resolution, variable expansion,
history and error mapping exist exactly once. **Do not add a second path.**

`ResolveWorkflowInputUseCase` and `RecordRunUseCase` are **implementation details of
`ExecuteWorkflowUseCase`** — two of its jobs lifted out to keep it readable, not to be reused. Do
not inject either anywhere else: an entry point resolving its own input, or writing its own history
row, is precisely the second path the rule above exists to prevent.

`ExecuteWorkflowUseCase` resolves **only the placeholders the prompt actually names**. Two of them
cost real time — `{{clipboard}}` is an IPC, `{{screen_text}}` is an accessibility snapshot — and
resolving the full set on every run put both between the user's tap and the first token.

`RunnerActivity` is the single Activity entry point. It uses **standard launch mode on purpose** —
`ACTION_PROCESS_TEXT` returns its replacement via `setResult`, which only reaches the caller when the
Activity runs in the caller's task. `singleTask` breaks text replacement.

## What is actually built

- **One provider.** `ProviderType` declares 8 (GEMINI, OPENAI, ANTHROPIC, OPENROUTER, GROQ, OLLAMA,
  LMSTUDIO, OPENAI_COMPATIBLE) but **only GEMINI has an implementation and a `@Binds @IntoMap`
  entry** in `AiModule`. The enum is a seam, not a feature list. Do not tell a user ArcX supports
  OpenAI.
- **Gemini streaming needs `?alt=sse`.** Without it the endpoint returns a chunked JSON array, not
  SSE.
- **Discover is local only** — a bundled `gallery.json` plus `.arcx.json` import/export. There is no
  community backend, no ratings, no search. That was deferred, not forgotten.
- 16 starter workflows in `core/data/src/main/assets/starter_workflows.json`.
- **`Workflow.icon` is an icon key, not an emoji** — one of `WorkflowIcons` in `:core:designsystem`,
  drawn as a Material vector and tinted by category. Anything not in that list is still drawn as
  text, which is how a user's own emoji survives; `MIGRATION_2_3` rewrote only the emoji ArcX
  itself shipped. Shortcuts and the widget cannot compose, so `WorkflowIconBitmap` rasterises the
  same vector for them.
- Release builds are **unsigned**. No signing config exists.
- No monetization code of any kind.

## Secrets

API keys live only in `KeystoreVault` (AndroidKeystore AES-256-GCM; `androidx.security:security-crypto`
is deprecated and deliberately unused). Keys never enter Room, never reach logs — the OkHttp
interceptor redacts the auth header — and `allowBackup=false` with explicit `dataExtractionRules`.
Verified by searching app storage for the plaintext key and finding only ciphertext. Keep it that way.

---

## Constraints discovered on device — do not undo these

Each of these looks like a bug and is not. They are commented at the code site; this is the index.

1. **The bubble's overlay window must stay `FLAG_NOT_FOCUSABLE`, expanded as well as collapsed**
   (`BubbleOverlay.kt`, `EXPANDED_FLAGS`). The moment it takes focus, Android stops exposing the app
   underneath to accessibility entirely — `getWindows()` returned only system bars and the overlay,
   and workflows summarised a browser toolbar. This is why the bubble panel has no search box: a
   non-focusable window cannot host a text field.
2. **A `ComposeView` is a `ViewGroup`, so `setOnTouchListener` never fires** — `AndroidComposeView`
   always consumes. Bubble gestures live in a `FrameLayout` host that overrides
   `onInterceptTouchEvent`/`onTouchEvent`.
3. **Compose in a `WindowManager` view needs ViewTree owners set on the window root**, not on an
   inner view, or it crashes on attach.
4. **MIUI/Xiaomi will not restart a killed service** unless the app is on the vendor Autostart
   whitelist. Battery-optimisation exemption does **not** fix this — different mechanism. Settings →
   Entry points links to both.
5. **Screen text is read as a screen opens**, snapshot cached with a 2-minute TTL, because Android
   only exposes the frontmost window and ArcX's own UI covers it during a run. The service subscribes
   to `typeWindowStateChanged` only; subscribing to `typeWindowContentChanged` is the classic
   battery-destroying mistake.
6. **The QS tile `startActivityAndCollapse(Intent)` overload throws on API 34+.** Use the
   `PendingIntent` overload above 33.

## Conventions

- Comments explain **why**, especially where the code looks wrong but is not. Match that density;
  do not strip these comments as "noise".
- Prefer the stdlib/platform answer. An `<activity-alias>` beat a second Activity; a static shortcut
  beat runtime publishing.
- **Verify claims on the device before writing them down.** Several confident diagnoses in this
  project's history were wrong — an "off-screen" bubble that was simply in landscape, a "broken" fix
  that was a bad test setup. State what was observed, not what should happen.
- **Never** add `Co-Authored-By` or any AI attribution to commit messages.
- Git: personal account `sibincbaby`, remote `git@github-personal:sibincbaby/ArcX.git`.
- Commit only when asked.
