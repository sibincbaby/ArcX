# ArcX: from the idea to what exists

Written for whoever — human or agent — picks this up next. `CLAUDE.md` is the short operational
version; this is the reasoning behind it, including the parts that went wrong.

Everything here was true at commit `6853f1c`. Where a claim was verified on hardware, it says so.
Where it was not, it says that too.

---

## 1. The idea

A **personal AI workflow launcher**. The user defines an action once:

> name · icon · input source · prompt template · provider · model · output target

and then fires it from wherever they already are. The prompt is a template with variables
(`{{selected_text}}`, `{{screen_text}}`, `{{clipboard}}`, `{{current_app}}`, `{{today}}`, …) filled
in at run time from whatever the entry point could see.

**BYOK is the product, not a setting.** The user supplies their own provider key, so ArcX never pays
for inference, never needs an account, and never has a server to leak. Every design decision defers
to that: no backend, no analytics, no telemetry, local-only history.

### The competitor that shaped it

`com.rethink.arc` was installed on the test device and its APK was decoded. It validated the
technical bets — native Kotlin/Compose, a `specialUse` foreground service for a floating bubble, a
Quick Settings tile, boot receivers — and it shipped all of that on Play at targetSdk 35, so none of
it is a hack.

It also showed the opening:

| Arc | ArcX |
|---|---|
| Account + subscription, owns the AI | BYOK, no account |
| No share target, no `ACTION_PROCESS_TEXT` at all | Both, and they are permission-free |
| Fixed built-in actions via custom intents | User-defined workflows via `arcx://run/{id}` |
| No widget | Glance widget |

Arc requests `QUERY_ALL_PACKAGES`; ArcX deliberately uses a `<queries>` element instead, because the
broad permission triggers a Play declaration and buys nothing.

---

## 2. What exists now

16 commits. 14 modules, ~125 Kotlin files, 63 unit tests. Verified throughout on a Galaxy S25 Ultra
(SM-S938B, Android 16) and earlier on a Xiaomi device running MIUI.

### Entry points, all landing in one place

| Surface | Mechanism | Needs |
|---|---|---|
| Share sheet | `ACTION_SEND` / `SEND_MULTIPLE` | nothing |
| Text selection | `ACTION_PROCESS_TEXT` | nothing |
| Quick Settings tile | `TileService` → `arcx://run/` | user adds it |
| Launcher icon "ArcX Actions" | `<activity-alias>` MAIN/LAUNCHER | nothing |
| Long-press "Actions" | static shortcut | nothing |
| Home-screen shortcuts | `ShortcutManagerCompat` pinned | launcher support |
| Widget | Glance | nothing |
| Floating bubble | `TYPE_APPLICATION_OVERLAY` + `specialUse` FGS | `SYSTEM_ALERT_WINDOW` |
| Accessibility button | `FLAG_REQUEST_ACCESSIBILITY_BUTTON` | user assigns it |

The alias, the shortcut and the tile all exist because **a component only reaches Samsung's Edge
panel, Bixby Routines or a gesture binding if it is launcher-visible**. The picker was always an
Activity; nothing pointed at it.

### The execution path

```
entry point → RunnerActivity (or bubble panel) → ExecuteWorkflowUseCase → AiProvider → output
```

`ExecuteWorkflowUseCase` is the only path. It resolves the provider and key, resolves the input text,
captures a screenshot when the workflow's input is `SCREENSHOT`, renders the prompt, streams, and
records history. Adding a second path is how the error handling and the history rules start to
disagree with themselves.

Input resolution is worth knowing: a bubble/shortcut/tile launch carries **no text**, so for
selection- and share-sourced workflows it falls back to the clipboard, then to screen text. Without
that, one-tap launching would send an empty prompt.

### Storage

| What | Where |
|---|---|
| Workflows, providers, runs | Room (`ArcxDatabase`) |
| Preferences | DataStore (`arcx_settings.preferences_pb`) |
| API keys | `KeystoreVault` — AndroidKeystore AES-256-GCM, ciphertext in DataStore |
| Screenshots | app-internal files, expiring on a user-set retention |

Keys never enter Room. Confirmed empirically by grepping every file in app storage for the plaintext
key: only base64 ciphertext was present, and the auth header logs redacted.

---

## 3. Deviations from the original plan

Recorded so nobody "restores" something that was dropped on purpose, or assumes something exists
because the plan mentioned it.

| Planned | Actual | Why |
|---|---|---|
| AGP 8.13.2 on JDK 17 | **AGP 9.3.1** | Hilt 2.59 and the 2026 androidx libraries require AGP 9 / compileSdk 37 |
| MediaProjection or clipboard for screen capture | **AccessibilityService `takeScreenshot`** | No per-session consent dialog, so a vision workflow stays one tap |
| Screenshot input deprioritised to v2 | **Built in v1** | Requested during development |
| Discover with community browse/search/rate | **Local gallery + file import/export** | Needs a backend, which conflicts with "no server". Deferred, not cancelled |
| Several providers | **Gemini only** | The registry seam exists; only Gemini is implemented |

`ProviderType` lists 8 values. **Seven of them have no implementation.** Adding one is a class plus a
`@Binds @IntoMap` entry in `AiModule` — the seam works — but until then they are not features.

---

## 4. Decisions with evidence

These were all settled by measurement on a real device. They are the ones most likely to be
"cleaned up" by someone who has not read this.

**The bubble panel and the sheet picker are different on purpose.** The overlay window must keep
`FLAG_NOT_FOCUSABLE` even when expanded. Dropping it — the obvious move when a panel appears — makes
Android stop exposing the app underneath to accessibility: `getWindows()` went from listing Chrome to
listing two system bars and the overlay, and the workflow summarised a toolbar. A non-focusable
window cannot host a text field, so the panel cannot have search. Everything else opens a focused
Activity and has no such limit. Both are now user-switchable, and the card itself is shared code in
`:core:designsystem` so the two cannot drift apart again.

**Taps on the bubble were being swallowed** because a `ComposeView` is a `ViewGroup`, and a ViewGroup
only consults its `OnTouchListener` when no child consumed the event — `AndroidComposeView` always
consumes. Proven with MIUIInput logs showing ACTION_DOWN/UP reaching `ViewRootImpl` while
instrumentation produced nothing. Gestures moved to a `FrameLayout` host.

**Clearing ArcX from Recents on MIUI kills the bubble permanently.** The process was killed with
`stopped=false` (not a force-stop), `START_STICKY` returned, but the `ServiceRecord` was removed with
no restart scheduled — identical after a plain SIGKILL, so Recents was never the trigger. ArcX was
simply absent from MIUI's Autostart whitelist. With Autostart enabled: `restartCount=1` and the
bubble returned by itself. **Battery-optimisation exemption does not fix this** — Doze is Android's
mechanism, Autostart is the OEM's. Both are linked from Settings, and the in-app copy says so.

**Screenshot runs record no input text.** They once stored the screen's text alongside the image; the
justification was that it made history searchable. There is no search in History and no DAO method
for one, so that was speculative — and it made a text dump of the screen the one thing ArcX stored
that was never sent to the provider. Removed. A test asserts the preview stays empty.

**The accessibility button routes only in nav-bar mode** on this Samsung device. In floating-menu
mode the platform reports the button unavailable to ArcX and taps never arrive. Cause undetermined —
this is a known gap, not a solved problem.

---

## 5. Mistakes made here, so they are not repeated

Every one of these was a confident wrong answer. They are the reason `CLAUDE.md` says to verify
before asserting.

- **A diagnosis invented from a plausible symptom.** The bubble was declared "positioned off-screen"
  and a `windowContext` fix was built for it. It crashed twice. The phone was in landscape, where the
  coordinate was correct. The whole change was reverted.
- **Two fixes declared broken by bad test setups.** Once the accessibility service had been silently
  revoked by a force-stop; once a test intent raised an app chooser so no app was ever in front. Both
  times ArcX was behaving correctly.
- **An over-escaped Kotlin template** shipped `${'}{context.packageName}`, so MIUI's battery screen
  displayed the literal string as the app name. Caught only by looking at the actual screen.
- **A commit message that claimed more than the code did** — "compact mode matches the bubble's
  panel" when it had only removed the search box. The user caught it from a screenshot.

The pattern: the failure was never the code being hard. It was reporting an expectation as an
observation.

---

## 6. Known gaps

- Release builds are **unsigned**; no signing config exists.
- No privacy policy URL, which Play requires before upload. See `play-store-readiness.md`.
- No lint/ktlint/detekt task is wired up.
- No instrumented tests. `KeystoreVault` needs a device to test properly and currently has no test.
- History has no search — worth knowing before writing a feature that assumes one.
- Only the Gemini provider exists.
- Accessibility button unreliable in Samsung's floating-menu mode (above).
- The `BOOT_COMPLETED` receiver has **never been tested**, because that means rebooting the test
  device. On an OEM that blocks autostart it is likely blocked too.
