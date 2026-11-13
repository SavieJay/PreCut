# PreCut cloud API (Phase 20)

The `cloud` Supabase Edge Function (`supabase/functions/cloud/`) is the only server PreCut talks to. It holds the ElevenLabs and Anthropic keys, checks with Google Play Integrity that each caller is the genuine app on a genuine phone, and keeps each install's free minutes and everyone's monthly budget in Postgres (`supabase/migrations/*_cloud.sql`). There are no user accounts: an install registers once and gets the backend's own install token.

Base URL: `https://auekkmbwfpqofjqugjsr.supabase.co/functions/v1/cloud`

## Headers
| Header | Sent on | Meaning |
|---|---|---|
| `X-PreCut-Api: 1` | every request | API version; older clients get `upgrade_required` |
| `x-region: eu-west-1` | every request | runs the function next to the database (Ireland, the project's region) |
| `X-Install-Token` | status, transcribe, edit, purchase, me | the token from `register` |
| `X-Integrity-Token` | register, transcribe, edit, purchase | a Play Integrity standard token whose request hash is below |
| `X-Audio-Duration-Ms` | transcribe | the audio's length as the app measured it |
| `Content-Type` | transcribe | `audio/mp4` (m4a), `audio/wav`, `audio/mpeg`, `audio/ogg`, `audio/webm` |

## Request hashes (Play Integrity `requestHash`)
Each verdict is bound to one request: the server recomputes the hash and refuses a verdict made for anything else. Hashes are SHA-256, URL-safe base64 without padding (43 characters).
- Register: `sha256("precut-register:" + installId)`
- Transcribe: `sha256("precut-transcribe:" + installId + ":" + hex(sha256(audio bytes)))`
- Edit: `sha256("precut-edit:" + installId + ":" + hex(sha256(request body bytes)))`
- Template: `sha256("precut-template:" + installId + ":" + hex(sha256(request body bytes)))`
- Translate: `sha256("precut-translate:" + installId + ":" + hex(sha256(request body bytes)))`
- Purchase: `sha256("precut-purchase:" + installId + ":" + hex(sha256(purchase token)))`

`installId` is a random UUID the app makes once and keeps (lower case). Verdicts must be under 5 minutes old, for `com.savie.precut`, signed with one of the certificates in `PLAY_CERT_SHA256`, on a device meeting `MEETS_DEVICE_INTEGRITY`.

## Endpoints
**`POST /register`** — body `{"installId": "<uuid>"}`, with `X-Integrity-Token`.
Returns `{"installToken": "...", "status": Status}`. The install token is valid 180 days; any later response may carry a fresh `installToken` when the old one has under 30 days left, and the app keeps the newest.

**`GET /status`** — `X-Install-Token` only (no Play Integrity check).
Returns `{"status": Status}` and maybe `installToken`.

**`POST /transcribe?lang=auto|<2-3 letter code>`** — the audio as the body (at most 10 MB and the config's `max_seconds_per_upload`, 10 minutes by default), with `X-Install-Token`, `X-Integrity-Token`, `X-Audio-Duration-Ms`, `Content-Type`.
Returns `{"transcript": {"language": "en", "durationMs": 2400, "words": [{"text": "Hello", "startMs": 100, "endMs": 600}]}, "secondsLeft": 1797}` and maybe `installToken`. Charged: the longer of the stated and the transcribed length, in whole seconds; nothing when it fails.

**`POST /edit`** (Phase 22C) — body `{"runId": "<uuid>", "messages": [...]}` (at most 512 KB, 200 messages), with `X-Install-Token` and `X-Integrity-Token`. One step of prompt-to-edit: the app keeps the conversation with Claude and runs every tool itself, on a copy of the project through its own `EditReducer` (so Claude's changes follow exactly the editor's rules); the server adds the system prompt and the tools (`supabase/functions/_shared/edit_tools.json`), asks Claude, and returns `{"content": [...], "stopReason": "tool_use|end_turn|max_tokens|refusal", "stepsLeft": int, "editRunsLeft": int}` and maybe `installToken`. The first user message's brief includes a `media` section from Phase 27 (loudness per second, shots with image labels, spoken lines; see `docs/AGENT_EVALS.md`), and the tools number 19 (`remove_time_ranges` and `add_captions` added). `messages` alternate user and assistant, starting with the user; user messages hold only `text` and `tool_result` blocks. `content` is Claude's reply to append as it is (thinking blocks included; after a server-side fallback to another model, only what may be sent back). A run is one request: its first step uses one of the month's runs, its later steps use the run's steps (`max_edit_steps`). Nothing of the conversation is stored or logged, only token counts and cost per run (`cloud_edit_runs`). Model: `ANTHROPIC_MODEL` (default `claude-opus-5-5`), effort `ANTHROPIC_EFFORT` (default `medium`); without `ANTHROPIC_API_KEY` the route answers `not_configured`.

**`POST /translate`** (Phase 26B) — body `{"lines": [{"id", "text"}], "target": "fr"}` (at most 400 lines, 64 KB), with `X-Install-Token` and `X-Integrity-Token` (hash `precut-translate:` + install + body hash, as for edit). Claude translates each line (JSON-schema output, same model and effort as AI edit); returns `{"lines": [{"id", "text"}], "editRunsLeft"}`. Counts as one AI edit request (`runs_used`, `budget_reached` apply); nothing stored but the token counts.

**`POST /templates`** (Phase 28) — body `{"template": {...}}` (a template's JSON with no media of the maker's own, plus a `summary` the website's page draws: `{"lengthMs", "parts": [{"ms", "slot"}], "slots", "texts"}`; at most 256 KB), with `X-Install-Token` and `X-Integrity-Token` (hash `precut-template:` + install + body hash). Stores it under a new 8-character id and returns `{"id"}`; at most `templates_per_day` (20) per install a day (`templates_used`). Deleted with the install's cloud data.

**`GET /templates/<id>`** (Phase 28) — public, no API version (the website's `/t/<id>` page uses it too), CORS open, cacheable a minute: `{"template": {...}}` or `not_found`.

**`POST /purchase`** (Phase 25) — body `{"purchaseToken": "..."}`, with `X-Install-Token` and `X-Integrity-Token`. The server looks the token up with the Google Play Developer API (`purchases.subscriptionsv2.get`), refuses one Google doesn't know or for another product (`bad_purchase`), saves its state and end date, links it to this install, acknowledges it, and returns `{"status": Status}`. Works with cloud AI switched off. See `docs/BILLING.md`.

**`POST /release`** — the publish workflow announces a release it published; no `X-PreCut-Api`. `Authorization: Bearer <RELEASE_SECRET>` (`forbidden` otherwise, and always while the secret isn't set) and `{"versionCode": 72, "versionName": "1.1.1", "whatsNew": ["…"]}` (a name of 1–32 characters, up to 8 lines of 1–120; `bad_request` otherwise). Sets the latest version, its name and "What's new" unless a newer version is already set, and never changes the minimum. Answers `{"applied": true|false, "config": <as GET /config>}`.

**`POST /rtdn?secret=...`** (Phase 25) — Google Play's Real-time developer notifications through a Pub/Sub push subscription; no `X-PreCut-Api`. The secret must match `RTDN_SECRET` (`forbidden` otherwise). A subscription notification makes the server look that purchase up again, if an app ever sent it; anything else is acknowledged and ignored. Answers 2xx except when Google can't be asked (`billing_unavailable`, so Pub/Sub retries).

**`GET /config?platform=android`** (Phase 23) — public: no `X-PreCut-Api` check, no install token, no Play Integrity, so any app version learns when it must update. Returns `{"minSupportedVersionCode": int, "latestVersionCode": int, "latestVersionName": string|null (from 2026-11-02), "forceMessage": string|null, "whatsNew": [string], "downloadUrl": string|null, "updatedAt": string|null}` from the `app_config` table (zeros when unset), with `Cache-Control: public, max-age=300`. How to use it: `docs/RELEASING.md`.

**`DELETE /me`** — `X-Install-Token`. Deletes everything kept about the install. Returns `{"deleted": true|false}`.

`Status` is `{"cloudEnabled": bool, "secondsLeft": int, "secondsPerMonth": int, "resetsAt": "<ISO 8601>", "maxUploadSeconds": int, "editEnabled": bool, "editRunsLeft": int, "editRunsPerMonth": int, "maxEditSteps": int, "pro": Plan}`; `Plan` is `{"pro": bool, "state": "active|grace|on_hold|paused|canceled|expired|pending"|null, "productId", "basePlanId", "expiresAt", "autoRenewing", "trial"}` for the install's newest purchase (Pro while active, in grace, or cancelled before its end). Quotas follow the plan. `cloudEnabled` is false while cloud AI is paused or the month's budget is used up; `editEnabled` likewise for AI edits (`edit_paused`, the edit budget).

## Accounts and cloud projects (Phase 30)
These routes need `X-PreCut-Api` and the signed-in user's Supabase Auth access token, `Authorization: Bearer <access token>` (`signed_out` without a good one; `not_configured` while accounts aren't set up). The function checks the token against the project's signing keys (`/auth/v1/.well-known/jwks.json`, ES256), its issuer and the `authenticated` audience; anonymous users don't count. The app gets the token from Supabase Auth itself (email code or Google ID token; `docs/ACCOUNTS.md`). A project's JSON names its files as `cloud:<sha256>`; files live in the private `media` bucket at `<user id>/<sha256>`.

**`GET /projects?since=<ISO 8601>&after=<uuid>`** — projects and deletions the server took after that point, oldest first, 50 a page: `{"projects": [CloudProject], "next": {"since", "after"}|null, "more": bool}`. Start without a cursor; follow `next` while `more`; next time start again a minute before the last `next.since` (seeing a project twice is harmless). `CloudProject` is `{"id", "revision", "name", "project": {...}|null, "coverSha256", "durationMs", "editedAt", "updatedAt", "deleted": bool, "media": ["<sha256>"]}`.

**`PUT /projects/<uuid>`** — body `{"baseRevision": int|null, "name", "project": {...}, "media": ["<sha256>"], "coverSha256": string|null, "durationMs", "editedAt"}`. `baseRevision` is the revision the phone last had (null for a project the server never had). Saves and answers `{"revision", "updatedAt"}`, or `conflict` with the server's copy as `project` (null when the server has none), `missing_media` with `missing` (upload those first), `too_large` (over `max_project_bytes`).

**`DELETE /projects/<uuid>?baseRevision=<int>`** — leaves a deletion other phones read; `{"revision"}`, or `conflict` with `project` when it changed since. Deleting a project the server doesn't have is fine.

**`POST /media/grant`** — body `{"sha256", "size", "mime"}`. `{"exists": true}` when the file is already up; otherwise the phone may upload it: `{"exists": false, "upload": {"url", "bucket": "media", "path": "<user id>/<sha256>"}}`, a resumable (TUS) upload with the access token, within a day. `storage_full` (`storageUsed`, `storageLimit`) or `too_large` (`maxUploadBytes`).

**`POST /media/confirm`** — body `{"sha256"}` after an upload: `{}` once the stored object has the granted size; `not_found` while it isn't there; `size_mismatch` (the object is removed; upload it again).

**`GET /media/<sha256>`** — `{"url", "expiresIn": 3600}`: a signed link that downloads the file for an hour; `not_found` when it isn't up.

**`GET /account`** — `{"email", "pro", "storageUsed", "storageLimit", "maxFileBytes", "projects"}`. Pro bought on any install signed in to the account counts for all of them.

**`DELETE /account`** — removes every stored file, then the Auth user, with their projects, files and install links. `{"deleted": true}`.

**`POST /account/link`** — also with `X-Install-Token`: signs this install in to the account (out of any other), so Pro follows the account. `{"linked": true, "status": Status}` (and maybe `installToken`).

**`POST /cleanup`** — the daily clean-up (`.github/workflows/cloud-cleanup.yml`); no `X-PreCut-Api`, `Authorization: Bearer <RELEASE_SECRET>`. Drops grants never uploaded within a day, files no project has used for 30 days, and deletions older than 90 days; removes those files from Storage. `{"removed": int}`.

## Errors
Every refusal is `{"error": {"code": "<code>", ...details}}`:

| Code | HTTP | Details | The app tells the user |
|---|---|---|---|
| `upgrade_required` | 426 | | update PreCut |
| `not_registered` | 401 | | (registers again, once) |
| `app_not_verified` | 403 | | this copy of PreCut couldn't be verified |
| `device_not_verified` | 403 | | this phone couldn't be verified |
| `integrity_unavailable` | 503 | | try again shortly |
| `bad_request` | 400 | | (a bug) |
| `unsupported_audio` | 415 | | (a bug) |
| `bad_audio` | 422 | | the sound couldn't be read |
| `too_large` | 413 | `maxUploadBytes` | (the app splits audio first) |
| `too_long` | 413 | `maxUploadSeconds` | (the app splits audio first) |
| `quota_used` | 429 | `secondsLeft`, `resetsAt` | free minutes used; when they come back |
| `budget_reached` | 503 | `resetsAt` | cloud AI is busy this month |
| `runs_used` | 429 | `editRunsLeft`, `resetsAt` | AI edit requests used; when they come back |
| `steps_used` | 429 | `maxEditSteps` | (the run ends; its changes stay for review) |
| `bad_purchase` | 400 | | Google Play couldn't confirm this purchase |
| `billing_unavailable` | 503 | | try again shortly |
| `forbidden` | 403 | | (`/rtdn`, `/release` or `/cleanup` with a wrong secret) |
| `signed_out` | 401 | | sign in again |
| `conflict` | 409 | `project` | (keeps both copies) |
| `missing_media` | 409 | `missing` | (uploads those files first) |
| `size_mismatch` | 409 | | (uploads the file again) |
| `storage_full` | 507 | `storageUsed`, `storageLimit` | cloud storage full; Pro has more |
| `paused` | 503 | | cloud AI is paused |
| `provider_failed` | 502 | | try again |
| `not_found` | 404 | | (a bug) |
| `not_configured`, `server_error` | 500 | | try again later (on `/edit`: AI edits aren't switched on yet) |

## Limits and settings
Edit the single row of `public.cloud_config` in the Supabase dashboard (Table Editor); it applies at once, with no redeploy and no app update:
- `free_seconds_per_month`: 1800 (30 minutes per install)
- `budget_seconds_per_month`: 324000 (5,400 minutes, about $20 at $0.0037/min)
- `max_seconds_per_upload`: 600
- `paused`: false
- `free_edit_runs_per_month`: 10 (AI edit requests per install)
- `max_edit_steps`: 8 (Claude steps in one request)
- `edit_budget_microusd_per_month`: 20000000 ($20 of Claude for everyone)
- `edit_paused`: false
- `pro_seconds_per_month`: 18000 (300 caption minutes for Pro installs)
- `pro_edit_runs_per_month`: 100 (AI edit requests for Pro installs)
- `free_storage_bytes`: 2147483648 (2 GB of project files per account)
- `pro_storage_bytes`: 53687091200 (50 GB for Pro accounts)
- `max_media_bytes`: 4294967296 (4 GB a file; the `media` bucket's and the project's upload limits must allow it)
- `max_project_bytes`: 2097152 (2 MB of project JSON)

## Secrets (`supabase/.env.secrets`, from `.env.secrets.example`)
`ELEVENLABS_API_KEY`, `ELEVENLABS_ZERO_RETENTION`, `ANTHROPIC_API_KEY` (and optionally `ANTHROPIC_MODEL`, `ANTHROPIC_EFFORT`), `PRO_PRODUCT_IDS`, `RTDN_SECRET`, `RELEASE_SECRET` (32+ characters; the same value as the GitHub secret `PRECUT_RELEASE_SECRET`), `GOOGLE_SERVICE_ACCOUNT_JSON`, `INSTALL_TOKEN_SECRET`, `PLAY_CERT_SHA256`. Supabase provides `SUPABASE_URL` and the project's secret key itself. `REQUIRE_INTEGRITY=false`, `TRANSCRIBER=fake`, `EDIT_AGENT=fake` and `BILLING=fake` exist for a local stack only; a deployed function refuses to start with them.

## Setup (on your own accounts)
1. **Supabase:** PreCut's project is `auekkmbwfpqofjqugjsr`, in `eu-west-1` (Ireland); a region can't be changed after creation. Free while developing, Pro before launch (Free pauses a project after a week with no database activity). On this machine, once: `npx supabase login` (opens your browser; the CLI keeps the token, so it never appears in a command).
2. **Google Cloud:** a project with the Play Integrity API turned on (the one Play Console links under App integrity, once the app is there). Note its project number for the app. Create a service account in it and download a JSON key.
3. **ElevenLabs:** an API key on a plan whose terms allow commercial use; switch off "Improve the models for everyone"; set a usage limit.
4. **Secrets file:** `tools/cloud_secrets.py` fills `supabase/.env.secrets` (owner-only; created from the example) without ever printing a value:
   ```
   python3 -I tools/cloud_secrets.py --service-account ~/Downloads/<key>.json \
     --apk app/build/outputs/apk/debug/app-debug.apk --apk app/build/outputs/apk/release/app-release.apk
   ```
   It writes the service-account JSON as one line, generates `INSTALL_TOKEN_SECRET` (only if empty: replacing it signs every install out) and adds the APKs' signing certificates to `PLAY_CERT_SHA256` (`--cert <sha256>` adds Play's app signing key from Play Console → App integrity). Paste the ElevenLabs key into the file yourself.
5. **Deploy** (Claude runs these on your logged-in CLI):
   ```
   npx supabase link --project-ref auekkmbwfpqofjqugjsr
   npx supabase db push --linked
   python3 -I tools/cloud_secrets.py --push
   npx supabase functions deploy cloud --use-api --no-verify-jwt
   ```

**Hosted Supabase waits for the whole upload.** It doesn't pass the function's reply back while the request body is still arriving, so a reply sent before an upload was read hangs the caller (a deployed project answered 1 KB but never 100 KB). The function therefore reads any upload to the end (up to 10 MB) before every reply, refusals included. With that, 100 KB to 10 MB all reach the function (10 MB took 21 s to upload from Lagos at about 0.5 MB/s).

## Logs
The function logs one JSON line per request (method, path, status, time) and per event (register, transcribe, edit, forget, integrity refusals) with a 10-character hash of the install ID (and of an edit's run ID), lengths, token counts, cost and outcomes. Never audio, words, prompts, Claude's replies, tokens or install IDs.

## App side (Phase 20B)
- **Config:** the gitignored `cloud.properties` (`cloudUrl`, `cloudRegion`, `playCloudProjectNumber`, and from Phase 28 `webHost`, the website's host for template links and App Links) becomes `BuildConfig` fields; without it the app builds and says cloud AI isn't set up.
- **`ai/cloud/CloudClient`:** the only code that talks to the function. It sends nothing while cloud AI is off in Settings, in a build without a backend, or offline; registers on the first upload only; registers again once on `not_registered`; keeps renewed install tokens; maps every error code above to an `AiFailure` (`model/ai`).
- **`ai/UploadConsent`:** given only by the consent dialog's Send (`ui/cloud/UploadConsentDialog`), for the audio sizes it showed, and spent upload by upload: one job's parts in order, each of exactly its size, and no more (Phase 21A). The client refuses to upload without one.
- **Captions (Phase 21A, `ai/CaptionJob`):** the timeline's sound without music, exported as 64 kbps AAC (`audio/mp4`); sound over the upload limit (`maxUploadSeconds` from `status`, less 2 s) is cut at the quietest moment in the 30 s before each limit and sent part by part under one consent. When `status` shows fewer free minutes than the whole timeline needs, only the leading parts they cover are prepared and sent.
- **`ai/cloud/PlayIntegrityTokens`:** Play Integrity standard requests (prepared once, prepared again on error -19, transient errors retried after 1, 2 and 4 s).
- **`ai/cloud/RequestHashes`:** the request hashes above; its unit test uses values produced by the server's own code.
- **Settings → Cloud AI** (`ui/settings/CloudAiSection`): the switch, free minutes, Check cloud AI, Delete my cloud data, What is sent.
- The install ID and token live in the private `cloud` preferences, which the backup rules leave out.
- **AI edit (Phase 22C, `model/ai/edit`, `ui/editor/EditorAiEdit`):** the editor's AI edit button opens the request sheet, which says what is sent (the project's description and the request; never media) and agrees to it on the first Send. `EditSession` sends the project brief (`editBrief`: items under short aliases like `c1`, times in seconds, catalogs of this app's filters, effects, transitions, animations and text templates) and the request, then loops: each `tool_use` is run by `EditTools` on the working copy through `EditReducer`, and every result (new ids, the updated clip timeline, or a reason with `is_error`) goes back in one user message. The review panel replaces the timeline while the preview plays the proposal; each change has a switch, and Apply re-runs the kept ones (with the ids they made) as one `Batch`, one undo step. A follow-up (Refine) keeps only the switched-on changes and tells Claude the project as it now is.

## Tests
`cd supabase/functions/cloud && deno task test` (handler, Play Integrity rules, install tokens, ElevenLabs mapping, config, and the SQL under PGlite), and `deno task check` (types, lint, formatting). App: `./gradlew testDebugUnitTest --tests '*CloudClientTest' --tests '*RequestHashesTest' --tests 'com.savie.precut.model.ai.edit.*'` (the tool test reads `edit_tools.json`, so a tool added on the server without a phone side fails it). Live: `python3 -I tools/cloud_smoke.py https://auekkmbwfpqofjqugjsr.supabase.co/functions/v1/cloud`.
