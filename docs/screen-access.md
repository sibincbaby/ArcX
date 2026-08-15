# Screen access and permissions

Everything found while chasing "screenshot workflows don't run even after granting permission",
written up so the next session can start from the conclusions rather than rediscover them.

Status of this document: the bug is diagnosed and fixed, the permission model is mapped, and one
design decision is still open — see [Open decision](#open-decision).

Updated after `689cd83`, which removed the biggest limitation described here: screen capture is no
longer bubble-only. §3a carried a wrong conclusion for a while and now says so; if you are reading
this to decide something, read that section before the others.

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
| ~~`replaceFocusedText`~~ — **deleted, see below** | — | (would need it) | — |
| `{{current_app}}` | — | — | ✅ from `AccessibilityEvent.getPackageName()` |
| **Replace selected text from the selection menu** | — | — | ✅ `ACTION_PROCESS_TEXT` + `setResult` |

### Per entry point

The table above is per *feature*. This one is per *surface*, which is the question that actually
gets asked ("why does it work from the bubble and not the tile?").

| Entry point | Screen image | `{{screen_text}}` | Text input | Replace selection |
|---|---|---|---|---|
| Bubble | ✅ captures | ✅ fresh read at expand | clipboard → screen text | ❌ |
| Accessibility button | ✅ captures | ✅ cached snapshot | clipboard → screen text | ❌ |
| Quick Settings tile | ✅ captures | cached | clipboard → screen text | ❌ |
| Edge panel / shortcut / widget | ✅ captures | cached | clipboard → screen text | ❌ |
| Share sheet | ✅ uses the shared image if there is one, else captures | cached | ✅ shared text | ❌ |
| Text selection | ✅ captures | cached | ✅ the selection | ✅ **no permission** |
| "ArcX Actions" drawer icon | ⚠️ captures the **home screen** — see §3a | cached | clipboard → screen text | ❌ |

Every ✅ in the image column arrived with `689cd83`; before it, only the bubble had one. The text
snapshot and the image behave differently and it is worth knowing which is which: the text snapshot
is rewritten on **every** window change so it self-refreshes, while the image is taken only when a
vision workflow is actually run.

That last row of the feature table matters and is easy to get wrong. Replacing selected text **already works with no
permission at all**: Android sends `ACTION_PROCESS_TEXT`, `RunnerActivity` returns the answer via
`setResult(EXTRA_PROCESS_TEXT)`, and the *host app* performs the replacement. ArcX never touches
the field. (This is why `RunnerActivity` must stay on standard launch mode — `singleTask` breaks
the result channel. Already noted in CLAUDE.md.)

Accessibility is therefore needed for text-writing only in one case: **writing into a focused field
when the run did not start from the selection menu** — from the bubble, tile or a shortcut, where
there is no `setResult` channel. `replaceFocusedText` existed for that and was never called by
anything, so **it has been deleted** along with the service's `setFocusedText`, and the Play
declaration no longer claims ArcX can replace text in place.

Restoring it is a small job — `ACTION_SET_TEXT` on `findFocus(FOCUS_INPUT)`, guarded against
writing into ArcX's own window — but it is the *only* remaining reason to keep
`canRetrieveWindowContent` beyond `{{screen_text}}`, so weigh it against the section below rather
than adding it back reflexively.

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
frame, and `frame` was written nowhere else. So a `SCREENSHOT` workflow fired from a Home tile, the
Library, the picker, a shortcut, the widget or the share sheet had no frame and failed every time.

**This section previously called that a constraint — "only the bubble and the accessibility button
can possibly do this". That was wrong**, and it is worth saying so plainly, because it sent a whole
session looking at MediaProjection for a problem that did not need it.

The real rule is narrower: **ArcX's own pixels must not be in the picture.** The bubble satisfies it
by blanking a 56dp handle. What the runner has going for it is that its window is *translucent* —
the task underneath keeps drawing, so the app the user came from is still on the display, behind the
sheet. Verified by screenshotting the picker over Chrome and seeing the page through it. Blanking
our own content is therefore enough, wherever we are.

**Fix (`689cd83`):** the capture moved into `RunnerViewModel.run()`, the one choke point every entry
point already passes through:

- the host renders **nothing** while capturing — not a transparent sheet, nothing, since a scrim
  still on screen would be photographed
- it reports back once it has genuinely stopped drawing (two frames plus a settle), rather than the
  ViewModel guessing with a delay that fails silently when it is too short
- `ScreenContextProvider.captureScreenshotNow()` waits for the JPEG to be *encoded*, not merely
  grabbed; `screenshot()` still returns the held frame

Capture now happens **on tap, not on launch**, so a text run never grabs a picture of whatever
happened to be on screen. That also closed a hazard this document did not previously name: because
`frame` was only ever written by the bubble and had a 2-minute TTL, a tile run could succeed by
sending a two-minute-old picture of a *different app* — a confident answer about the wrong screen,
stored in History as if it were current.

`onClicked` in `ArcxAccessibilityService` also captures before launching, from the earlier attempt
at this. It is now redundant but harmless: the second grab hits the platform's minimum interval,
returns null, and the run falls back to the frame already held.

**What still cannot work, and never will:** whatever is behind the window is what gets photographed.
From the Quick Settings tile or an Edge panel over Chrome, that is Chrome. From the "ArcX Actions"
drawer icon it is your home screen, because reaching that icon meant leaving the app. No amount of
code recovers a screen the user has already navigated away from.

### 3b. The error blamed the wrong thing

`AiError.NoScreenshot` had a single message regardless of cause: *"Screen capture needs the screen
reading permission… Turn it on in Settings › Entry points."* With the permission granted, the app
still told the user to grant it. **This is exactly the reported symptom.**

**Fix:** `NoScreenshot(captureAvailable: Boolean)`. `ExecuteWorkflowUseCase` passes
`screen.canScreenshot()`, and the sheet says either "grant the permission" or *"There was nothing on
screen to photograph"*.

Since §3a, the second branch is far rarer — every entry point can capture now — so it means what it
says rather than "you used the wrong surface". Note it is still two-valued while the situation is
three: a capture that the platform **refuses** (a secure window — banking, DRM, refused outright on
API 34+) reports as "nothing to look at" even though the permission is granted and the surface was
fine. Outstanding.

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

Note the interaction with §3a: this used to mean that with the button off, the bubble was the only
surface that could capture. **That is no longer true** — every entry point captures for itself now,
so leaving the accessibility button off costs nothing but the button itself.

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

Tier 2's appeal dropped sharply after §3a. The reason to want `MediaProjection` was that
accessibility capture only worked from one surface; it now works from all of them, so the remaining
gap is narrower than it looked: capture without the accessibility grant at all.

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
  build a workflow that silently cannot run. Either hide them or mark them as requiring it. Narrower
  than it was — with accessibility granted, `SCREENSHOT` now works from every surface except the
  drawer icon — but the accessibility-off case is unchanged.
- `NoScreenshot` is two-valued where the situation is three: a platform refusal (secure window)
  reads as "nothing to look at". See §3b.
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

**Verified on device (Redmi 23049PCD8I, Android 15) for the §3a fix:**
- Reproduced first: "Explain This Screen" from the picker over Chrome → *"Nothing to look at"*.
- The premise, before writing any code: screenshotted the picker over Chrome and confirmed the page
  is fully composited behind the translucent window.
- After the fix, from the **real Quick Settings tile** over Chrome → a correct description of the
  page ("Google AI Studio in a mobile browser…").
- Pulled the stored JPEG out of app storage and looked at it: **pure Chrome, no sheet, no scrim, no
  ArcX pixels**. The blanking handshake works.
- A regression caught the same way: the first successful run stored *nothing*. `record()` still
  keyed History off the use case's own capture, so with the image arriving as an attachment every
  surface but the bubble ran fine and silently lost the picture. Fixed, with a test
  (`screenshot supplied by the caller is the one stored in history`), and re-verified by pulling the
  file again.

**Not verified — do this first next session:**
- **The bubble path after `689cd83`.** The overlay was not running on the Redmi (MIUI autostart
  after a reinstall), so the one path that always worked is the one left unconfirmed. Reasoning says
  it is fine — the second grab hits the platform interval, returns null, and falls back to the frame
  the bubble already took — but that is reasoning, not evidence.
- The accessibility-button capture path end to end. Never tested; the device dropped off USB before
  it could be. Less important now that it is redundant (§3a).
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
