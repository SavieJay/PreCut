# PreCut cloud API (Phase 20)

The `cloud` Supabase Edge Function (`supabase/functions/cloud/`) is the only server PreCut talks to. It holds the ElevenLabs key, checks with Google Play Integrity that each caller is the genuine app on a genuine phone, and keeps each install's free minutes and everyone's monthly budget in Postgres (`supabase/migrations/*_cloud.sql`). There are no user accounts: an install registers once and gets the backend's own install token.

Base URL: `https://auekkmbwfpqofjqugjsr.supabase.co/functions/v1/cloud`

## Headers
| Header | Sent on | Meaning |
|---|---|---|
| `X-PreCut-Api: 1` | every request | API version; older clients get `upgrade_required` |
| `x-region: eu-west-1` | every request | runs the function next to the database (Ireland, the project's region) |
| `X-Install-Token` | status, transcribe, me | the token from `register` |
| `X-Integrity-Token` | register, transcribe | a Play Integrity standard token whose request hash is below |
| `X-Audio-Duration-Ms` | transcribe | the audio's length as the app measured it |
| `Content-Type` | transcribe | `audio/mp4` (m4a), `audio/wav`, `audio/mpeg`, `audio/ogg`, `audio/webm` |

## Request hashes (Play Integrity `requestHash`)
Each verdict is bound to one request: the server recomputes the hash and refuses a verdict made for anything else. Hashes are SHA-256, URL-safe base64 without padding (43 characters).
- Register: `sha256("precut-register:" + installId)`
- Transcribe: `sha256("precut-transcribe:" + installId + ":" + hex(sha256(audio bytes)))`

`installId` is a random UUID the app makes once and keeps (lower case). Verdicts must be under 5 minutes old, for `com.savie.precut`, signed with one of the certificates in `PLAY_CERT_SHA256`, on a device meeting `MEETS_DEVICE_INTEGRITY`.

## Endpoints
**`POST /register`** — body `{"installId": "<uuid>"}`, with `X-Integrity-Token`.
Returns `{"installToken": "...", "status": Status}`. The install token is valid 180 days; any later response may carry a fresh `installToken` when the old one has under 30 days left, and the app keeps the newest.

**`GET /status`** — `X-Install-Token` only (no Play Integrity check).
Returns `{"status": Status}` and maybe `installToken`.

**`POST /transcribe?lang=auto|<2-3 letter code>`** — the audio as the body (at most 10 MB and the config's `max_seconds_per_upload`, 10 minutes by default), with `X-Install-Token`, `X-Integrity-Token`, `X-Audio-Duration-Ms`, `Content-Type`.
Returns `{"transcript": {"language": "en", "durationMs": 2400, "words": [{"text": "Hello", "startMs": 100, "endMs": 600}]}, "secondsLeft": 1797}` and maybe `installToken`. Charged: the longer of the stated and the transcribed length, in whole seconds; nothing when it fails.

**`DELETE /me`** — `X-Install-Token`. Deletes everything kept about the install. Returns `{"deleted": true|false}`.

`Status` is `{"cloudEnabled": bool, "secondsLeft": int, "secondsPerMonth": int, "resetsAt": "<ISO 8601>", "maxUploadSeconds": int}`. `cloudEnabled` is false while cloud AI is paused or the month's budget is used up.

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
| `paused` | 503 | | cloud AI is paused |
| `provider_failed` | 502 | | try again |
| `not_found` | 404 | | (a bug) |
| `not_configured`, `server_error` | 500 | | try again later |

## Limits and settings
Edit the single row of `public.cloud_config` in the Supabase dashboard (Table Editor); it applies at once, with no redeploy and no app update:
- `free_seconds_per_month`: 1800 (30 minutes per install)
- `budget_seconds_per_month`: 324000 (5,400 minutes, about $20 at $0.0037/min)
- `max_seconds_per_upload`: 600
- `paused`: false

## Secrets (`supabase/.env.secrets`, from `.env.secrets.example`)
`ELEVENLABS_API_KEY`, `ELEVENLABS_ZERO_RETENTION`, `GOOGLE_SERVICE_ACCOUNT_JSON`, `INSTALL_TOKEN_SECRET`, `PLAY_CERT_SHA256`. Supabase provides `SUPABASE_URL` and the project's secret key itself. `REQUIRE_INTEGRITY=false` and `TRANSCRIBER=fake` exist for a local stack only; a deployed function refuses to start with them.

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
The function logs one JSON line per request (method, path, status, time) and per event (register, transcribe, forget, integrity refusals) with a 10-character hash of the install ID, lengths and outcomes. Never audio, words, tokens or install IDs.

## App side (Phase 20B)
- **Config:** the gitignored `cloud.properties` (`cloudUrl`, `cloudRegion`, `playCloudProjectNumber`) becomes `BuildConfig` fields; without it the app builds and says cloud AI isn't set up.
- **`ai/cloud/CloudClient`:** the only code that talks to the function. It sends nothing while cloud AI is off in Settings, in a build without a backend, or offline; registers on the first upload only; registers again once on `not_registered`; keeps renewed install tokens; maps every error code above to an `AiFailure` (`model/ai`).
- **`ai/UploadConsent`:** given only by the consent dialog's Send (`ui/cloud/UploadConsentDialog`), for the audio size it showed, and spent by that upload. The client refuses to upload without one.
- **`ai/cloud/PlayIntegrityTokens`:** Play Integrity standard requests (prepared once, prepared again on error -19, transient errors retried after 1, 2 and 4 s).
- **`ai/cloud/RequestHashes`:** the request hashes above; its unit test uses values produced by the server's own code.
- **Settings → Cloud AI** (`ui/settings/CloudAiSection`): the switch, free minutes, Check cloud AI, Delete my cloud data, What is sent.
- The install ID and token live in the private `cloud` preferences, which the backup rules leave out.

## Tests
`cd supabase/functions/cloud && deno task test` (handler, Play Integrity rules, install tokens, ElevenLabs mapping, config, and the SQL under PGlite), and `deno task check` (types, lint, formatting). App: `./gradlew testDebugUnitTest --tests '*CloudClientTest' --tests '*RequestHashesTest'`. Live: `python3 -I tools/cloud_smoke.py https://auekkmbwfpqofjqugjsr.supabase.co/functions/v1/cloud`.
