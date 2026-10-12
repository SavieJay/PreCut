# Video Editor — Build Plan

Instructions for Claude Code: this is the source of truth for what to build.
Work through phases in order. Tick checkboxes (`- [x]`) as tasks are completed.
Follow all rules in `/CLAUDE.md`.

---

## 1. Product scope

### v1 (build now)
- Import multiple videos and photos (Android Photo Picker)
- Multi-clip timeline: trim, split at playhead, delete, reorder, duplicate
- Speed control per clip (0.25x – 4x)
- Filters per clip (brightness, contrast, saturation, grayscale, warm, cool, vintage)
- Text overlays (text, font size, color, background, position, start/end time)
- Rotation per clip; project aspect ratio (16:9, 9:16, 1:1, 4:5)
- Background music track with trim, offset, volume; per-clip volume / mute
- Undo / redo
- Auto-saved project drafts
- Export to gallery: 720p / 1080p, H.264 + AAC MP4, with progress, running in a foreground service

### v2 (do NOT build unless asked)
Transitions, stickers, voiceover, keyframes, chroma key, multiple overlay tracks, 4K, cloud sync.

---

## 2. Architecture

```
Compose UI ──events──▶ ViewModel ──▶ EditReducer (pure) ──▶ new Project
     ▲                     │
     └──── StateFlow ◀─────┘
                           │
                           ▼
                   CompositionBuilder (Project → Media3 Composition)
                      │                      │
                      ▼                      ▼
             CompositionPlayer         Transformer (export)
               (preview)
```

### Package layout
```
com.savie.precut/
├── PreCutApp.kt                 Hilt application
├── MainActivity.kt
├── model/                       Pure Kotlin (no Android imports)
│   ├── Project.kt
│   ├── Clip.kt
│   ├── TextOverlay.kt
│   ├── AudioClip.kt
│   ├── Enums.kt                 AspectRatio, ClipType, FilterType, ExportQuality
│   ├── TimelineMath.kt          durations, clip-at-time lookup, time mapping with speed
│   └── edit/
│       ├── EditAction.kt        sealed class of all edits
│       ├── EditReducer.kt       (Project, EditAction) -> Project
│       └── History.kt           undo/redo stack
├── data/
│   ├── db/                      Room: AppDatabase, ProjectEntity, ProjectDao
│   ├── ProjectRepository.kt
│   └── ProjectSerializer.kt     kotlinx.serialization JSON
├── media/
│   ├── CompositionBuilder.kt
│   ├── EffectsFactory.kt        FilterType -> Media3 effects; TextOverlay -> OverlayEffect
│   ├── PreviewController.kt     wraps CompositionPlayer
│   ├── ExportManager.kt         wraps Transformer, exposes progress Flow
│   ├── MediaStoreSaver.kt       copies output to Movies/PreCut
│   ├── MediaProbe.kt            duration, size, rotation of a Uri
│   └── ThumbnailLoader.kt       frame extraction + LRU cache
├── service/
│   └── ExportService.kt         foreground service
└── ui/
    ├── navigation/
    ├── theme/
    ├── home/
    ├── editor/
    │   ├── EditorScreen.kt
    │   ├── EditorViewModel.kt
    │   ├── preview/
    │   ├── timeline/
    │   └── tools/               TrimTool, SpeedTool, FilterTool, TextTool, AudioTool
    ├── export/
    └── components/
```

### Core model (starting point; refine if needed)
```kotlin
@Serializable data class Project(
    val id: String,
    val name: String,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val clips: List<Clip> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable data class Clip(
    val id: String,
    val uri: String,
    val type: ClipType,             // VIDEO, IMAGE
    val sourceDurationMs: Long,     // for IMAGE: chosen display duration (default 3000)
    val trimStartMs: Long = 0,
    val trimEndMs: Long,            // exclusive
    val speed: Float = 1f,
    val volume: Float = 1f,         // 0f = muted
    val filter: FilterType = FilterType.NONE,
    val rotationDegrees: Int = 0,   // 0, 90, 180, 270
)

@Serializable data class TextOverlay(
    val id: String,
    val text: String,
    val startMs: Long,              // timeline time
    val endMs: Long,
    val xNorm: Float = 0.5f,        // 0..1 position in frame
    val yNorm: Float = 0.8f,
    val textSizeSp: Float = 32f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundArgb: Int? = null,
)

@Serializable data class AudioClip(
    val id: String,
    val uri: String,
    val sourceDurationMs: Long,
    val timelineStartMs: Long = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long,
    val volume: Float = 1f,
)
```

Timeline duration of a clip = `(trimEndMs - trimStartMs) / speed`.

### Media3 mapping (verify APIs against the installed version)
| Project concept | Media3 |
|---|---|
| Clip trim | `MediaItem.ClippingConfiguration` |
| Image clip | `EditedMediaItem` with image duration + frame rate set |
| Speed | Media3 speed-change support for video and audio (check current API: e.g. `SpeedChangeEffect` + audio speed processor, or `EditedMediaItem` speed API if available) |
| Filters | `Brightness`, `Contrast`, `HslAdjustment`, `RgbFilter`, `RgbMatrix` |
| Rotation | `ScaleAndRotateTransformation` |
| Aspect ratio | `Presentation.createForWidthAndHeight(..., LAYOUT_SCALE_TO_FIT)` |
| Text overlay | `OverlayEffect` with `TextOverlay` (time-gated to start/end) |
| Clip volume / mute | Audio processor for volume, or `setRemoveAudio(true)` when muted |
| Main clip list | `EditedMediaItemSequence` |
| Background music | Second `EditedMediaItemSequence` in the same `Composition` (mixed) |
| Preview | `CompositionPlayer` |
| Export | `Transformer` + `ExportResult` / `ExportException` |

Text overlay timing note: overlays are applied per `EditedMediaItem`, so CompositionBuilder must convert timeline times into each clip's local presentation time and attach the relevant overlays to every clip they span.

---

## 3. Phases

Each phase ends with acceptance criteria. Stop after each phase for user verification.

### Phase 0 — Project setup
- [x] Create Android project `com.savie.precut` (Kotlin DSL, version catalog)
- [x] Add dependencies: Compose BOM, Material 3, Navigation Compose, Hilt, Room (KSP), kotlinx.serialization, Coil, Media3 (exoplayer, transformer, effect, ui)
- [x] Hilt application + `MainActivity` with theme and navigation host
- [x] Empty `HomeScreen` and `EditorScreen` routes
- [x] `.gitignore`, unit-test setup (JUnit + kotlinx-coroutines-test + Turbine)

**Acceptance:** `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass; app launches to a Home screen.

### Phase 1 — Model, import, playback
- [ ] All model classes from section 2 with serialization
- [ ] `TimelineMath` (total duration, clip at timeline time, timeline↔source time with speed) + unit tests
- [ ] `MediaProbe` to read duration/type of a Uri
- [ ] Photo Picker (`PickMultipleVisualMedia`) for videos + images; take persistable URI permission
- [ ] Create a new `Project` from picked media (in memory for now) and open Editor
- [ ] Editor shows ExoPlayer preview of the first clip with play/pause and a seek bar

**Acceptance:** pick 3 videos → editor opens → first video plays, pauses, and seeks.

### Phase 2 — End-to-end export (single clip)
- [ ] `CompositionBuilder` v1: one clip with trim → `Composition`
- [ ] Trim tool: range slider over the clip, preview reflects trim
- [ ] `ExportManager`: Transformer export to app cache, progress as `Flow<ExportState>` (polling `getProgress`)
- [ ] `MediaStoreSaver`: save to `Movies/PreCut`
- [ ] Export screen with progress, success (open/share), and error states

**Acceptance:** trim a clip, export, and the result appears in the gallery with correct length and audio.

### Phase 3 — Multi-clip timeline
- [ ] `EditAction` + `EditReducer`: AddClips, DeleteClip, SplitClip(atTimelineMs), MoveClip(from,to), DuplicateClip, TrimClip — all unit-tested
- [ ] `CompositionBuilder` v2: all clips in one sequence, images supported
- [ ] Switch preview to `PreviewController` (CompositionPlayer), rebuilt when the project changes (debounced)
- [ ] `ThumbnailLoader`: lazy frame extraction at low resolution with LRU memory cache
- [ ] Timeline UI: horizontal scroll, thumbnail strip per clip proportional to duration, time ruler, fixed center playhead synced to playback in both directions (scroll seeks, playback scrolls)
- [ ] Pinch to zoom timeline
- [ ] Tap to select clip; selected clip highlighted with trim handles
- [ ] Long-press drag to reorder
- [ ] Bottom toolbar: Split, Delete, Duplicate, Trim
- [ ] Export uses the full composition

**Acceptance:** 3+ clips (including an image) can be split, reordered, trimmed, and deleted; preview plays them seamlessly; export matches preview.

### Phase 4 — Editing tools
- [ ] Speed tool (0.25x–4x presets + slider); timeline width updates
- [ ] Filter tool: horizontal list of filter previews; `EffectsFactory` mapping
- [ ] Rotate tool (90° steps)
- [ ] Aspect ratio picker (project level), preview letterboxes accordingly
- [ ] Text tool: add/edit/delete text, color, size, background; drag to position on preview; set start/end on a text track lane in the timeline
- [ ] All effects routed through `CompositionBuilder` so preview == export
- [ ] Unit tests for overlay time mapping across clip boundaries and speed changes

**Acceptance:** a project using speed, filter, rotation, 9:16 ratio, and a text overlay spanning two clips exports identically to the preview.

### Phase 5 — Audio
- [ ] Add music from device (`GetContent` audio/*)
- [ ] Audio lane in timeline: drag to offset, trim handles
- [ ] Volume sliders: per clip and per audio track; mute toggle per clip
- [ ] Second sequence in `Composition` for music; music shorter than video simply ends; music longer is cut at video end
- [ ] Unit tests for audio placement logic

**Acceptance:** exported video contains original audio and music mixed at the chosen levels, with correct offset.

### Phase 6 — Persistence and undo/redo
- [ ] Room: `ProjectEntity(id, name, json, thumbnailPath, updatedAt)` + DAO
- [ ] `ProjectRepository`: save, load, list, delete, rename
- [ ] Auto-save on every edit (debounced ~500 ms)
- [ ] Home screen: grid of drafts with thumbnail, name, duration; rename and delete
- [ ] `History`: undo/redo stack (cap 50) wired into ViewModel; toolbar buttons
- [ ] Missing-source handling: if a Uri can no longer be opened, mark the clip and offer to remove or replace it
- [ ] Process-death safety: editor restores from repository

**Acceptance:** edit, kill the app, reopen → project restored exactly; undo/redo works across all edit types.

### Phase 7 — Polish and release prep
- [ ] Move export into `ExportService` (foreground, `dataSync`/`mediaProcessing` type as appropriate for targetSdk) with progress notification and cancel action
- [ ] Notification permission request (Android 13+)
- [ ] Export settings: 720p/1080p, quality (bitrate) presets, estimated file size
- [ ] Error handling: unsupported codec, low storage, export failure with retry
- [ ] Loading/empty states everywhere; haptics on split/delete
- [ ] Performance pass: thumbnail memory, recomposition counts, player rebuild debounce
- [ ] App icon, splash screen, dark theme review
- [ ] R8 enabled for release, keep rules verified, release build tested
- [ ] Basic UI tests for the main flow (create → edit → export)

**Acceptance:** release build runs smoothly on a low-end device; exporting while the app is backgrounded completes; no crashes in the main flow.

---

## 4. Manual test matrix (use at the end of phases 3–7)
- Portrait and landscape videos mixed in one project
- Very short (< 1 s) and long (> 10 min) clips
- MP4 (H.264), HEVC, MOV from different phones
- Videos with no audio track
- Images mixed with videos
- Low-end device and a recent device

## 5. Known risks
- **Preview/export mismatch** → single `CompositionBuilder` path (enforced in CLAUDE.md).
- **Device codec limits** → use Transformer fallback/encoder settings; catch `ExportException` and offer a lower resolution.
- **Memory pressure from thumbnails** → low-res frames, LRU cache, cancel off-screen loads.
- **Media3 API changes** → verify against the installed version's sources; document workarounds here.

## 6. Decisions log
Claude Code: append significant technical decisions or deviations from this plan here, with a one-line reason.

- **2026-10-12 — Package `com.savie.precut`, app name "PreCut".** `com.example.*` is rejected by Google Play.
- **2026-10-12 — Application class is `PreCutApp`** (instead of `VideoEditorApp`), to match the app name.
- **2026-10-12 — Versions (latest stable on 2026-10-12):** AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Navigation 2.10.2, Hilt 2.60.1 (androidx.hilt 1.4.0), Room 2.8.5, kotlinx.serialization 1.11.0, coroutines 1.11.0, Coil 3.6.3, Media3 1.11.1, Turbine 1.2.1, JUnit 4.13.2. compileSdk/targetSdk 37 (Android 17), minSdk 26.
- **2026-10-12 — AGP 9 built-in Kotlin:** the `kotlin-android` plugin is not applied in `app/`; it is declared `apply false` at the root only to pin the KGP version.
- **2026-10-12 — Java/Kotlin target 17 via `compileOptions` + `jvmTarget`** rather than `jvmToolchain(17)`, to avoid deprecated toolchain auto-provisioning (builds run on the installed JDK 21).
- **2026-10-12 — `hiltViewModel()` comes from `androidx.hilt:hilt-lifecycle-viewmodel-compose`**; the older `hilt-navigation-compose` location is deprecated.
- **2026-10-12 — Known build warning:** `Configuration.setVisible` deprecation is emitted by AGP itself, not by project scripts.
