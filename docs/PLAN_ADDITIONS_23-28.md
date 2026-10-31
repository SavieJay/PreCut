# PreCut — Plan additions: Phases 23–28

Instructions for Claude Code: merge these phases into `docs/PLAN.md` after Phase 22, and add them
to the roadmap. Follow all rules in `/CLAUDE.md`, including stopping after each phase for a phone check.

**Start condition:** do not begin Phase 23 until Phases 9–20 are confirmed on a phone
(see `docs/DEVICE_CHECKLIST.md`) and Phases 21–22 are finished or explicitly paused by the user.

## Order and why
| Phase | What | Why this order |
|---|---|---|
| 23 | Forced update system | Must be in the first public release; old versions without it can never be forced to update |
| 24 | Onboarding | Needed for launch; sets up personalization used by later phases |
| 25 | Subscriptions and Pro foundation | Pro features need entitlements and a paywall first |
| 26 | Free unlocks and Pro features | Builds on 25 |
| 27 | Agentic editing | Builds on 22C (prompt-to-edit) and 25 (Pro gating) |
| 28 | Growth and sharing (optional) | After launch |

New screens for 23–27 are designed separately (see `docs/design/NEW_SCREENS_DESIGN_PROMPT.md`).
Implement them to match the exported designs in `docs/design/` and the existing design system.

---

## Phase 23 — Forced update system

Goal: when a newer version is released, users see a full-screen overlay and cannot use the app until they update.

### Remote config (Supabase)
- [ ] Table `app_config` (single row per platform): `min_supported_version_code`, `latest_version_code`,
      `force_message`, `whats_new` (JSON list of short bullet strings), `updated_at`. Public read via RLS, no writes from the app.
- [ ] The user forces an update by raising `min_supported_version_code` to the new release's versionCode.
      Document this in `docs/RELEASING.md` (release checklist: publish on Play → wait until it's live → raise the minimum).

### App
- [ ] `UpdateChecker`: fetch config on cold start and on every return to foreground (throttle: at most once per 15 min). No background polling.
- [ ] Cache the last config in DataStore.
- [ ] If `versionCode < min_supported_version_code` → **blocking update overlay**:
  - full screen, not dismissable, back button and gestures do nothing, no navigation possible behind it
  - "Update now" starts the Google Play In-App Updates **immediate** flow; fallback: open the Play listing
  - if the app was not installed from Play (installer package check), open the website download page instead
- [ ] If `versionCode < latest_version_code` but not below the minimum → non-blocking banner on Home using the In-App Updates **flexible** flow.
- [ ] After an update, show a one-time "What's new" bottom sheet from `whats_new`.

### Safeguards (required)
- [ ] **Fail open when offline:** if the fetch fails, use the cached config; if there is none, do not block.
- [ ] **Never interrupt an export.** If an export is running, show the overlay only after it finishes.
- [ ] Force an autosave of the open project before showing the overlay.
- [ ] Draft compatibility: every new version must open drafts saved by older versions (project JSON has a `schemaVersion`; add migrations + tests).

### Tests
- [ ] Unit tests for the decision logic (block / banner / nothing / offline / export running) using `FakeAppUpdateManager`.
- [ ] Document how to test real updates via Play internal app sharing or the internal testing track.

**Acceptance:** with `min_supported_version_code` raised above the installed version, the app shows the overlay and nothing else is usable; in airplane mode the app opens normally; during an export the overlay waits until the export finishes.

---

## Phase 24 — Onboarding

Goal: a short, skippable first-run flow (target: under 45 seconds) that personalizes the app.

- [ ] Screens (see design prompt): Welcome → "What will you create?" (multi-select) → Editing experience (New / Some / Pro) → Notifications priming → Pro trial offer (skippable, "Continue free" always visible) → Home with first-run coach marks.
- [ ] Personalization stored in DataStore:
  - creation goals → default aspect ratio and which templates show first
  - experience level → **Simple mode** (fewer tools in the bottom toolbar, advanced ones under "More") vs **Full mode**; switchable any time in Settings
- [ ] Coach marks on first editor open: max 3 tooltips (timeline, tools, export). Tap anywhere to dismiss.
- [ ] "Skip" on every onboarding screen; onboarding shown once; "Replay intro" in Settings.
- [ ] Respect existing permission approach (Photo Picker needs no permission; notifications asked only via the priming screen).
- [ ] Until Phase 25 exists, the Pro trial screen is hidden behind a flag.

**Acceptance:** fresh install shows onboarding once; choices change the default aspect ratio, template order and toolbar mode; skipping at any step lands on Home with sensible defaults.

---

## Phase 25 — Subscriptions and Pro foundation

Goal: PreCut Pro as a Google Play subscription, verified server-side.

- [ ] **Decision needed from the user before coding:** (a) RevenueCat (faster, handles receipts and renewals, free tier) or (b) own verification in a Supabase Edge Function using the Google Play Developer API + Real-time Developer Notifications. Present the trade-offs and wait.
- [ ] Google Play Billing (latest stable library): monthly and yearly plans, a free-trial offer. Prices are set by the user in Play Console.
- [ ] `EntitlementRepository` exposing `isPro: StateFlow<Boolean>`; cached for offline use with a sensible expiry.
- [ ] Backend checks the entitlement for every Pro cloud request (never trust a flag sent by the app). Cloud AI quotas become per plan.
- [ ] Paywall screen (from design), restore purchases, manage subscription (deep link to Play subscriptions), grace period and account-hold messaging.
- [ ] Gating UX rules:
  - Pro tools are visible with a small "Pro" badge; tapping one opens the paywall
  - never show a paywall during an export or in the middle of an edit gesture
  - each Pro AI feature has a small free taste where cost allows (e.g. a few uses per month), shown as "2 free uses left"
- [ ] Settings → PreCut Pro: plan, renewal date, manage, restore.
- [ ] Play policy: digital features in the Play build must be sold through Play Billing only.

**Acceptance:** on a test account (license tester), buying, cancelling, restoring and trial expiry all update `isPro` correctly, and Pro cloud requests are refused by the backend for non-Pro users.

---

## Phase 26 — Free unlocks and Pro features

### 26A — Free (rule: every creative tool that runs on the phone is free)
- [ ] Audit: compare PreCut against the current CapCut free vs Pro split (check the live app; do not rely on memory). List any tool competitors charge for that PreCut can offer free on-device, and add the missing ones to this phase.
- [ ] Confirm these are free and never watermarked: no watermark on any export, up to 4K/60 fps export, all built-in filters, effects, transitions, fonts, stickers and text templates, keyframes, masks, chroma key, on-device background removal (22A), on-device noise reduction and auto reframe (22B), templates, AutoCut, auto captions within the monthly free minutes.

### 26B — Pro features (AI and time-saving workflows)
Each one must be usable in 3 taps or fewer from where the user needs it, with a one-line explanation and a preview before applying.
- [ ] **Edit by transcript:** edit the video by editing its text (delete words → those parts are cut). One-tap "Remove filler words" and "Remove silences" with a list to review before applying.
- [ ] **Long video to shorts:** pick a long video → PreCut finds the best moments (transcript + audio energy + scene changes) → creates several 9:16 clips with captions and auto reframe, each saved as its own draft.
- [ ] **One-tap multi-format export:** export 9:16, 1:1 and 16:9 versions at once, each auto-reframed, with per-format preview.
- [ ] **Brand kit:** logo, colors, fonts, intro and outro saved once and applied with one tap (and to templates).
- [ ] **Translate and dub captions:** translate captions into other languages; optional AI voice dub (cloud, consent first; only the user's own voice or provided stock voices, never cloning other people).
- [ ] **Hook coach:** opt-in check of the first 3 seconds and overall pacing with 2–4 concrete suggestions, each with an "Apply" button.
- [ ] **Smart B-roll from your gallery:** on-device search of the user's own photos and videos by description ("beach", "dog") to drop B-roll at the playhead. (Runs on the phone; Pro because it is a power workflow.)
- [ ] More cloud caption minutes and agent runs (quotas from Phase 25).

Do not build features not listed here without asking the user.

**Acceptance:** every 26B feature shows a preview or review list before changing the project, is a single undo step, shows the Pro badge for free users, and works for Pro users on a phone.

---

## Phase 27 — Agentic editing

Goal: the user types what they want ("Make a 30-second upbeat TikTok from these clips with captions, cut to the beat") and an AI agent builds the edit. Extends 22C (prompt-to-edit) from single-shot changes to a multi-step agent.

### Media understanding (on the phone, before any upload)
- [ ] Build a compact **Media Brief** JSON per project: clip list and durations, shot/scene boundaries, transcript with word timings (if captions consent exists), beat grid of the music, on-device image labels for a few frames per shot (ML Kit), face/subject positions from reframe, loudness per second.
- [ ] Send only the Media Brief and the project JSON to the backend. Sending low-resolution frames is optional and needs its own consent prompt.

### Agent (backend, Supabase Edge Function)
- [ ] Claude via the Anthropic API with tool use. Tools are generated from the existing `EditAction` sealed class (one tool per action, JSON schema from the model). Keep the API key on the backend only. Check docs.claude.com for current model names and pick a cost-appropriate model; make it configurable.
- [ ] Agent loop on the server with limits: max steps, max tokens, timeout, cost logged per run.
- [ ] Every proposed action is validated by the same reducer rules as the app; invalid actions are returned to the agent as errors to fix.
- [ ] Stream progress to the app ("Picking the best shots…", "Cutting to the beat…").

### App
- [ ] Entry points: an "AI Edit" button on Home (new project from selected media + prompt) and in the editor (edit the current project).
- [ ] Prompt screen with suggestion chips (e.g. "30s TikTok, upbeat", "Calm travel recap", "Remove pauses and add captions").
- [ ] Results are applied to a **copy** of the project. Review screen: preview player + list of changes grouped by step, each with a toggle; "Apply all", "Apply selected", "Refine" (follow-up prompt), "Discard".
- [ ] Applying is a single undo step. The agent can never delete source media or anything outside the project.
- [ ] Pro feature with a small free taste (quota from Phase 25). Clear consent screen the first time.

### Evaluation
- [ ] `docs/AGENT_EVALS.md`: 20 test prompts with sample projects and pass criteria (duration within ±10%, captions present when asked, cuts on beats when asked, no invalid actions). Run before each release.

**Acceptance:** the 20 eval prompts pass at least 17/20; a full agent run on a phone finishes in under 60 seconds for a 2-minute project; the user can review, partially apply, refine and undo.

---

## Phase 28 — Growth and sharing (optional, after launch)

- [ ] Share a template as a link (`https://<website>/t/<id>`) that opens PreCut via Android App Links (needs `assetlinks.json` on the website) or the Play listing if not installed.
- [ ] Optional "Made with PreCut" end card, off by default, user's choice.
- [ ] Play In-App Review prompt after a successful export (max once per few weeks, never after an error).
- [ ] Play Install Referrer to measure installs coming from the website.
