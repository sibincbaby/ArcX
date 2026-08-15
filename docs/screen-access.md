# Screen access and permissions

Everything found while chasing "screenshot workflows don't run even after granting permission",
written up so the next session can start from the conclusions rather than rediscover them.

Status of this document: the bug is diagnosed and fixed, the permission model is mapped, and one
design decision is still open — see [Open decision](#open-decision).

---

## 1. There is only one permission, and ArcX calls it two things

"Screen reading" is **ArcX's own name** for the Android accessibility service. There is no second
permission. `SystemSurfaces.isScreenReadingEnabled()` reads
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, and the Enable button opens
`ACTION_ACCESSIBILITY_SETTINGS`. One switch, one grant.

Entry points shows two rows — "Screen reading" and "Accessibility button" — which reads like two
permissions. It is one permission plus an optional *assignment* of an already-granted service to a
system button. **This naming is a real defect and is most of why the permission model was
confusing.** Renaming it to say "accessibility" plainly is outstanding work.

## 2. What actually needs what

The two capabilities in `accessibility_service_config.xml` are independent. Taking a picture does
**not** require reading the view tree.

| Feature | `canTakeScreenshot` | `canRetrieveWindowContent` | Neither |
|---|---|---|---|
| Vision workflows (`InputSource.SCREENSHOT`) | ✅ required | — | — |
| `{{screen_text}}` | — | ✅ required | — |
| `replaceFocusedText` (`findFocus` + `ACTION_SET_TEXT`) | — | ✅ required | — |
| `{{current_app}}` | — | — | ✅ from `AccessibilityEvent.getPackageName()` |
| **Replace selected text from the selection menu** | — | — | ✅ `ACTION_PROCESS_TEXT` + `setResult` |

That last row matters and is easy to get wrong. Replacing selected text **already works with no
permission at all**: Android sends `ACTION_PROCESS_TEXT`, `RunnerActivity` returns the answer via
`setResult(EXTRA_PROCESS_TEXT)`, and the *host app* performs the replacement. ArcX never touches
the field. (This is why `RunnerActivity` must stay on standard launch mode — `singleTask` breaks
the result channel. Already noted in CLAUDE.md.)

Accessibility is therefore needed for text-writing only in one case: **writing into a focused field
when the run did not start from the selection menu** — from the bubble, tile or a shortcut, where
there is no `setResult` channel. That is what `replaceFocusedText` exists for, and it is currently
**implemented but called by nothing**.

### Cost of dropping `canRetrieveWindowContent`

Measured against what ships today:

- Workflows using `{{screen_text}}` or `InputSource.SCREEN_TEXT`: **zero** — none in
  `starter_workflows.json`, none in `gallery.json`.
- Workflows using `InputSource.SCREENSHOT`: one starter ("Explain This Screen"), plus user-made.

So dropping it costs nothing that ships, keeps vision workflows working, keeps `{{current_app}}`,
keeps replace-from-selection — and removes ArcX's ability to read the contents of other apps
entirely. It would forfeit `{{screen_text}}` and any future insert-from-bubble.

---

## 3. The bug

**Two defects, both fixed.**

### 3a. Capture only ever happened from the bubble

`captureScreenImage()` had exactly one caller in the codebase: `OverlayService`.
`AccessibilityScreenContextProvider.screenshot()` only returns `latestScreenImage()`, the cached
frame, and `frame` is written nowhere else. So a `SCREENSHOT` workflow fired from a Home tile, the
Library, the picker, a shortcut, the widget or the share sheet had no frame and failed every time.

This is not an oversight so much as a constraint: to photograph *the user's* screen, ArcX must not
be on it. The bubble works because it hides itself, grabs, then opens. Only two surfaces can
possibly do this — **the bubble and the accessibility button** — because they are the only ones
that fire while the user's app is still the only thing on the display.

**Fix:** `onClicked` in `ArcxAccessibilityService` now captures before launching the picker.

### 3b. The error blamed the wrong thing

`AiError.NoScreenshot` had a single message regardless of cause: *"Screen capture needs the screen
reading permission… Turn it on in Settings › Entry points."* With the permission granted, the app
still told the user to grant it. **This is exactly the reported symptom.**

**Fix:** `NoScreenshot(captureAvailable: Boolean)`. `ExecuteWorkflowUseCase` passes
`screen.canScreenshot()`, and the sheet says either "grant the permission" or *"Nothing to look at
— ArcX can only photograph the screen from the floating bubble or the accessibility button."*

### 3c. Related: "switched on but not running"

`isScreenReadingEnabled()` reads the settings string. Several OEMs kill the service process
without clearing that setting, so Android keeps reporting it as enabled while nothing is bound —
screen reading and screenshots fail while every screen insists the permission is granted. Another
"I granted it and it still says grant it".

**Fix:** `SystemSurfaces.isScreenReadingRunning()` reports the bound instance
(`AccessibilityServiceHolder.isConnected`). Entry points now says *"Switched on, but not running —
Android stopped it. Turn it off and on again"*, the Home chip agrees, and the "N of 6 are live"
count no longer counts a service nobody is hosting.

---

## 4. The accessibility shortcut is now opt-in

`flagRequestAccessibilityButton` has been **removed from the manifest**. It was putting ArcX into
the accessibility-button and volume-key target list for everyone who granted the permission — a
system control the user may already have pointed at something they depend on.

It is now applied at runtime from `UserSettings.accessibilityButtonOffered` (default **off**) via
`AccessibilityServiceInfo.flags` + `setServiceInfo`, with a switch in Entry points. Granting
accessibility and volunteering for a system shortcut are now two separate decisions.

Note the interaction with §3a: with the button off, **the bubble is the only surface that can
capture the screen**.

---

## 5. Getting a screenshot without accessibility

There are three tiers. All are real.

| Tier | Route | Grant | Per-run friction | Ongoing cost |
|---|---|---|---|---|
| 1 | User screenshots manually, shares to ArcX | **none** | share sheet each time | none |
| 2 | `MediaProjection` | consent dialog | none *if the session is held* | **permanent screen-recording indicator** |
| 3 | Accessibility `takeScreenshot` | Settings toggle | none | appears in Accessibility list; Play declaration |

**Tier 1 works today** — ArcX already accepts `IMAGE` input and share-sheet attachments. It is the
natural fallback for a user who has removed accessibility.

**Tier 2 notes.** `MediaProjection` needs a foreground service of type `mediaProjection`, started
before projection (required from API 34). Each `MediaProjection` instance is one-shot: once
stopped, the next capture needs fresh consent. A long-lived session avoids repeated dialogs, at
the price of Android showing the screen-recording privacy indicator continuously. So the trade is
**a dialog per run** versus **a permanent recording indicator** — not "a dialog per run" full stop,
which an earlier reading of this got wrong.

**Assist API** (`VoiceInteractionService.onHandleScreenshot`) also avoids accessibility, but ArcX
would have to become the default assistant, replacing Gemini/Bixby. Rejected as more intrusive
than either alternative.

---

## 6. The stated design principle

> Accessibility should be **optional**. With it, features work great. Without it, the app should
> still work, so a user can remove it if they do not need it. The accessibility shortcut must also
> be optional.

The shortcut half is done (§4). The rest resolves to a fork:

- **A — usable without it.** Everything except screen-aware features works: share sheet, selection
  menu, clipboard, typed input, bubble, all outputs. Screen-aware workflows are unavailable and say
  so. Nearly true today; the remaining work is honesty (§7).
- **B — every feature works without it.** Requires tier 2 above.

## Open decision

1. **Build tier 2 (MediaProjection)?** If yes: hold the session open (permanent indicator, no
   dialogs) or re-consent per run (dialog each time, no indicator)?
2. **Keep `canRetrieveWindowContent`?** Keeping it is only justified by `{{screen_text}}` (zero
   shipped workflows) and future insert-from-bubble. Dropping it makes the accessibility ask far
   smaller and the Play declaration far simpler.

---

## 7. Outstanding work

- The builder still offers `SCREENSHOT` and `SCREEN_TEXT` when accessibility is off, so a user can
  build a workflow that silently cannot run. Either hide them or mark them as requiring it.
- Entry points should stop calling one grant "Screen reading" (§1) and state plainly what works
  with it and what still works without.
- The sideload caveat below is not mentioned anywhere in the UI.

## 8. Verification status

**Verified on device (SM-S938B, Android 16):**
- The bug reproduced: screenshot workflow fired from Home with accessibility granted →
  *"Couldn't capture the screen."*
- Both branches of the corrected error: accessibility off → "grant the permission"; accessibility
  on, fired from Home → *"Nothing to look at…"*.
- Build green, 67 unit tests pass, app installs and runs.

**Not verified — do this first next session:**
- The accessibility-button capture path end to end (§3a fix). Never tested; the device dropped off
  USB before it could be.
- The "switched on but not running" message (§3c). Hard to induce deliberately — `force-stop` on
  this phone removes the service from the setting entirely rather than leaving it stranded.
- The `accessibilityButtonOffered` runtime flag (§4). Only exercises once the service is bound.

## 9. Device gotchas that cost real time

- **Never `am force-stop com.arcx.app` while testing accessibility.** On this Samsung it strips
  ArcX out of `enabled_accessibility_services` entirely. Several hours of "the permission is
  granted but nothing works" traced back to this. CLAUDE.md warns about it; heed it.
- **Sideloads are blocked from accessibility by Android 13+ restricted settings.** `installDebug`
  produces `installerPackageName=null`, so the Accessibility toggle silently refuses until
  App info → ⋮ → **Allow restricted settings**. **This resets on every reinstall.** Check with
  `adb shell appops get com.arcx.app ACCESS_RESTRICTED_SETTINGS`.
- **`adb exec-out screencap` returns an all-black PNG if the display has slept**, even while
  `dumpsys power` reports `mWakefulness=Awake`. Not an app bug. `uiautomator dump` still works and
  is the more reliable check.
- `./gradlew :benchmark:connectedBenchmarkAndroidTest` **uninstalls the app**, taking the database,
  preferences and the Keystore-backed API key with it. See `docs/benchmarking.md`.
