# AI Edit evals (Phase 27)

Twenty requests on sample projects, run against the real Claude with the app's own session (`EditSession`), tools (`EditTools`) and reducer, and the server's own system prompt and tool list (read from `supabase/functions/_shared/`). **Run them before each release**, and after any change to the system prompt, the tools or the model. Target: **at least 17 of 20 pass.**

## Running
```
PRECUT_RUN_EVALS=1 ANTHROPIC_API_KEY=sk-ant-... ./gradlew testDebugUnitTest --tests '*AgentEvalsTest'
```
- `ANTHROPIC_MODEL` tries another model (default `claude-opus-5-5`, as on the server).
- The report (pass or fail per case, why, changes made, rejected tool calls, token totals) is written to `app/build/reports/agent-evals.md`.
- Without both variables the test is skipped, so ordinary test runs cost nothing. A full run is 20 requests of a few steps each: roughly 0.5–1.5 million input tokens with caching, about $3–8 at Opus 5.5 prices; check the report's token totals.
- The harness (`app/src/test/.../evals/ClaudeEvalStep.kt`) calls Anthropic's API directly with the Java SDK, with the same settings as the cloud function (`medium` effort, prompt caching, strict tools), so no phone, Play Integrity or deployed server is needed. Claude's replies are sent back unchanged, thinking included.

## The cases
Sample projects are built in `EvalCases.kt`: 16:9 video clips of the given lengths in a 9:16 project, synthetic loudness and shots (labels "seashore" and "street sign") in the media brief, and, where named, music with beats every 0.5 s or captions every 4 s.

| # | Request | Sample project | Passes when |
|---|---|---|---|
| 1 | Make it 15 seconds | 4 × 10 s | length 15 s ± 10% |
| 2 | Make a 30-second TikTok from these clips, upbeat, cut to the beat | 5 × 12 s, music with beats | length 30 s ± 10%, cut to the beat used, still 9:16 |
| 3 | Remove pauses and add captions | 2 × 20 s, captions with pauses, quiet seconds | pauses cut, captions asked for |
| 4 | Add a title that says Lagos Trip at the start | 2 × 8 s | a "Lagos Trip" text starting in the first second |
| 5 | Make the colours warm and bright | 3 × 6 s | warmth, brightness or a filter on a clip |
| 6 | Add smooth transitions between all the clips | 4 × 5 s | a transition at every junction |
| 7 | Make it square for Instagram | 2 × 6 s | 1:1 |
| 8 | Slow down the second clip | 3 × 6 s | clip 2's speed below 1 |
| 9 | Mute the clips' own sound but keep the music | 2 × 6 s, music | clips silent, music not |
| 10 | Delete the last clip | 5, 6, 7 s | clips v1, v2 left |
| 11 | Move the last clip to the start | 5, 6, 7 s | v3 first |
| 12 | Fade the music out at the end | 2 × 10 s, music | music fade-out |
| 13 | Cut the quiet parts | 30 s with three quiet stretches | a time cut, under 28 s |
| 14 | Make a calm travel recap with a soft look | 4 × 8 s | a look change, not longer |
| 15 | A highlight reel of the loudest moments, about 20 seconds | 60 s, every third second quiet | length 20 s ± 10% |
| 16 | Add a snow effect over the whole video | 2 × 6 s | an effect over ≥ 90% of the video |
| 17 | Remove the text that says Draft | 2 × 6 s, texts "Draft" and "Hello" | only "Hello" left |
| 18 | Speed up the boring middle clip | 5, 20, 5 s, quiet middle | the middle clip faster or shorter |
| 19 | Make it 16:9 for YouTube and add a zoom-in at the start | 2 × 6 s | 16:9, a zoom-in (or keyframes) on clip 1 |
| 20 | Add my dog photos from last summer | 2 × 6 s | no change, and Claude says why (it can't reach the gallery) |

Every case also counts **rejected tool calls** (calls the reducer refused and sent back as reasons). They don't fail a case (Claude is meant to correct itself), but a rising count after a change means the tools or prompt got harder to use.

## Results
Keep the last few reports here, newest first, with the date, model and app commit.

| Date | Model | Commit | Passed | Notes |
|---|---|---|---|---|
| — | — | — | — | Not run yet: the Anthropic key arrives later (2026-11-01). |
