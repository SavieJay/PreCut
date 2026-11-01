# Releasing PreCut

## Version codes
`versionCode` is the number of commits (`git rev-list --count HEAD`), so a build from a newer commit always goes over the last one. Release from a full clone (a shallow clone counts fewer commits). Note the version code of every build you upload; the update rules below use it. `versionName` is set by hand in `app/build.gradle.kts`.

## Release checklist
1. Run the checks: `./gradlew assembleDebug testDebugUnitTest lintDebug`, and the phone checklist (`docs/DEVICE_CHECKLIST.md`) for what changed.
2. Build the bundle: `./gradlew bundleRelease` (signed with the upload key from the gitignored `keystore.properties`; see the Phase 15 notes in `docs/PLAN.md`). Upload `app/build/outputs/bundle/release/app-release.aab` in Play Console.
3. Test it through internal testing or internal app sharing (below) before production.
4. Publish on Play. **Wait until the release is live** for everyone in the rollout (Play Console shows the release as available; on a test phone, the Play listing offers the new version).
5. Update the update rules in Supabase (Table Editor → `app_config`, row `android`):
   - `latest_version_code` = the new version code. Older versions then show a banner on Home offering the update (Play's flexible update).
   - `whats_new` = a JSON list of short bullets, e.g. `["AI edit: describe a change and review it", "Faster exports"]`. Shown once to people who updated to this version.
   - Only to **force** the update: `min_supported_version_code` = the new version code. Every older version then shows the blocking "Update PreCut" screen and can't be used until it updates. Optionally `force_message` (the screen's words) and `download_url` (where copies not installed from Play get the APK; empty sends them to the website's `/download` page).
6. Changes apply within about 15 minutes for open apps (each app checks on start and when it comes back to the front, at most every 15 minutes; the server lets caches keep the answer 5 minutes).

**Never raise the minimum before the version is live everywhere:** a phone that can't get the new version from Play yet would be blocked with no way forward. To undo a mistake, lower `min_supported_version_code` again.

## What the app does (Phase 23)
- Reads the rules from the cloud function's public `GET /config` (no install token or Play Integrity, and independent of the cloud AI switch). It keeps the last answer: offline, it uses that; with none, it never blocks.
- Below the minimum: a full-screen overlay; Back and touches do nothing. "Update now" starts Play's immediate in-app update; when Play can't (the copy wasn't installed from Play, or Play doesn't offer the update yet), it opens the Play listing, or, for copies not from Play, `download_url` or else `https://<webHost>/download`.
- An export in progress is never interrupted: the overlay waits until it finishes. Open projects are saved before the overlay shows.
- Old drafts always open: project JSON carries `schemaVersion`, and `ProjectSerializer` migrates older versions step by step. Changing the project format means raising `SCHEMA_VERSION`, adding a step for the old version, and a test with a draft in the old format.

## Testing updates for real
- **Internal app sharing** (Play Console → Internal app sharing): upload two builds with different version codes, install the lower one from its link, then open the higher one's link once so Play knows it; In-App Updates then offer it. Set `latest_version_code` (banner) or `min_supported_version_code` (block) to the higher code.
- **Internal testing track:** the same with a testers' list; updates can take a while to reach Play's cache.
- In-App Updates only work for apps installed by Google Play. A debug build from Android Studio gets the store fallback instead.
- Unit tests cover the decisions (`UpdateRulesTest`); `PlayUpdatesTest` runs the Play flows against `FakeAppUpdateManager` on a phone or emulator (`./gradlew connectedDebugAndroidTest`).
