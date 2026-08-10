# Play Store readiness

ArcX declares two things Google reviews by hand: an **AccessibilityService** and a
**`specialUse` foreground service**. Both will hold up a release if the justifications are
vague, and both are easier to write now than six weeks from now. This file is the source of
truth for what goes into the Play Console.

Nothing here is legal advice — the privacy policy in particular should be read by whoever is
publishing the app before it goes live.

---

## 1. Accessibility API declaration

Play Console → Policy → App content → **Accessibility API usage**.

**Which feature uses the API**

> Screen reading for user-invoked AI workflows.

**What the app does with it**

> ArcX lets a user build a reusable AI action once and run it from anywhere on the device. One
> of the input sources a user can choose for an action is "Text on screen". When — and only
> when — the user runs an action configured that way, ArcX reads the visible text of the
> current window through `AccessibilityNodeInfo` and puts that text into the prompt that is
> sent to the AI provider the user has connected with their own API key.
>
> The service also performs `ACTION_SET_TEXT` on the focused editable field, so an action such
> as "Fix grammar" can replace the text the user is editing in place rather than making them
> copy and paste the result.

**Why no other API is sufficient**

> `ACTION_PROCESS_TEXT` and the share sheet only deliver text the user has already selected or
> shared, and ArcX supports both — they are the primary entry points. Neither can read a screen
> the user has not selected text on, which is what the "Text on screen" input source and the
> floating bubble exist to do. `MediaProjection` plus OCR would capture the same content less
> accurately, with a per-session consent dialog, and would still require reading the screen.

**Data handling**

> Screen text is read on demand, held in memory for the duration of the request, and sent only
> to the AI provider the user configured. ArcX has no account and no server of its own. A
> truncated preview is written to the local run history, which the user can disable or clear.
> Password fields (`isPassword`) are skipped and never read.

### Prominent disclosure (already implemented in-app)

Shown in Settings → Entry points, **above** the control that opens the system accessibility
screen, so the user reads it before granting. It must stay above the control — a disclosure
below the button it describes does not satisfy the policy.

### Battery / correctness notes for the reviewer

The service subscribes to `typeWindowStateChanged` only and does not walk the view hierarchy on
content changes; the tree is read solely when a workflow asks for it.

---

## 2. Foreground service — `specialUse`

Play Console → App content → **Foreground service types**.

**Type:** `specialUse`
**Subtype property:** declared in the manifest as `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.

**Justification**

> The service hosts a user-enabled floating bubble drawn with `TYPE_APPLICATION_OVERLAY`, which
> gives one-tap access to the user's workflows over any app. It must stay running while the
> user is in other apps — that is the entire feature — so it cannot be a bound or background
> service. No other declared foreground service type describes an on-screen launcher: it is not
> media, location, data sync, camera, microphone, or a short service.
>
> It is off by default, started only when the user turns the bubble on in Settings, stopped as
> soon as they turn it off, and it does no network or location work.

---

## 3. Data safety form

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **No data is collected by the developer.** ArcX has no backend and no analytics. |
| Is data transferred off the device? | Yes — to the **AI provider the user chooses and configures with their own API key**. This is a user-directed transfer to a third party, not collection by ArcX. |
| Is data encrypted in transit? | Yes — HTTPS to the provider. |
| Can users request deletion? | Yes — Settings → Privacy → Delete all local data removes workflows, history and stored API keys. No server-side data exists to delete. |
| Data types transferred | Whatever the user runs a workflow on: selected text, clipboard contents, screen text, or shared files/images. Named explicitly rather than generically, since the user is the one choosing it each time. |

**API keys:** stored only in an AES-256-GCM key from the Android Keystore, never transmitted
anywhere except as the auth header to the user's own provider, never written to logs, never
included in backups (`allowBackup=false`, with explicit `dataExtractionRules` exclusions).

---

## 4. Permissions rationale

| Permission | Why | Degrades to |
|---|---|---|
| `INTERNET` | Reach the user's AI provider | — (required) |
| `SYSTEM_ALERT_WINDOW` | Floating bubble | Bubble off; every other entry point still works |
| `FOREGROUND_SERVICE` + `_SPECIAL_USE` | Keep the bubble alive | as above |
| `POST_NOTIFICATIONS` | Notification output target; bubble's ongoing notification | Results shown in the sheet instead |
| `RECEIVE_BOOT_COMPLETED` | Restore the bubble after reboot | User re-enables manually |
| `BIND_ACCESSIBILITY_SERVICE` | `{{screen_text}}`, `{{current_app}}`, in-place text replacement | Those inputs render empty; selection and share entry points unaffected |

`QUERY_ALL_PACKAGES` is deliberately **not** requested — a `<queries>` element covers what
ArcX needs, and the broad permission would trigger a further Play declaration for no benefit.

---

## 5. Before the first upload

- [ ] Signing config — release currently builds unsigned (`app-release-unsigned.apk`)
- [ ] Privacy policy URL, publicly hosted, linked in Settings → About and in the Console
- [ ] Confirm `com.arcx.app` is available as an application ID, or change it
- [ ] Store listing screenshots — the share sheet and text-selection flows are the differentiators worth showing
- [ ] Decide whether the accessibility service ships in v1 at all: the selection and share entry points work without it, so it can be held back to keep the first review simple
