# CLAUDE.md — Android Video Editor

This file is read at the start of every session. Follow it strictly.

## Project
A native Android video editor (similar in scope to a lightweight InShot/CapCut).
The full specification, architecture, and phased task list live in `docs/PLAN.md`.
**Always read `docs/PLAN.md` before starting work.**

## Stack
- Kotlin, Jetpack Compose, Material 3
- MVVM + unidirectional data flow (StateFlow), Hilt for DI
- AndroidX Media3: ExoPlayer, Transformer, Effects, CompositionPlayer
- Room for persistence, kotlinx.serialization for project JSON
- Coil for images/thumbnails
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)
- minSdk 26, targetSdk/compileSdk = latest stable

## Workflow rules
1. Work on **one phase at a time**, in the order given in `docs/PLAN.md`.
2. Before coding a phase, write a short plan (files to create/change, approach) and wait for approval.
3. After every meaningful change, run `./gradlew assembleDebug` and fix all errors and warnings you introduced.
4. Run `./gradlew testDebugUnitTest` before declaring a task done.
5. When a task is complete, tick its checkbox in `docs/PLAN.md`.
6. At the end of each phase, STOP. Summarize what was built and list the manual test steps from the phase's acceptance criteria so the user can verify on a device. Do not start the next phase until the user confirms.
7. Suggest a git commit message at the end of each completed task.
8. If something in the plan is technically wrong or a Media3 API differs from what the plan assumes, say so and propose a fix instead of silently deviating.

## Architecture rules
- `model/` contains pure Kotlin data classes only. No Android, Compose, or Media3 imports.
- Only `media/` may import Media3 editing classes (Transformer, Composition, Effects).
- Preview and export MUST both use `CompositionBuilder` to produce the same `Composition`. Never build a separate code path for preview.
- The editor state is an immutable `Project`. Every edit produces a new `Project` via a pure function in `model/edit/`. This enables undo/redo.
- Composables contain no business logic. They render state and send events to the ViewModel.
- Keep composables small (prefer < 150 lines per file).

## Media3 rules
- Use the latest **stable** Media3 version from Google Maven. Verify API signatures against that version's sources rather than relying on memory; Media3 APIs change between releases.
- Mark usage of `@UnstableApi` with `@OptIn(UnstableApi::class)` at the narrowest scope.
- Transformer must be built and started on a thread with a Looper (main thread is fine); it does its heavy work on its own threads.
- Release players and cancel transformers in the appropriate lifecycle callbacks. No leaks.

## Code quality
- No deprecated APIs.
- No `!!` except where provably safe, with a comment explaining why.
- Use `Result`/sealed classes for operations that can fail (import, export, load).
- Unit-test all `model/edit/` functions and `CompositionBuilder` mapping logic.
- User-facing strings go in `strings.xml`.
