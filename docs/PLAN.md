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

### CapCut parity (added 2026-10-13 at the user's request — see §6 gap analysis)
Phases 7–12 below bring the editor to CapCut level, **including CapCut Pro features**, for
everything that runs on-device without AI or online content:
- Phase 7 — core editing gaps found by auditing Phases 1–4: insert at playhead, camera, share-to,
  full-screen preview, snapping, multi-select, cover, 0.1–100x speed, more ratios, text rows and transform
- Phase 8 — look and frame: LUT filter library, Adjust incl. HSL and Curves, filter/adjust layers,
  transform, crop, mirror, canvas, opacity, transitions, animations, speed curves, freeze, reverse
- Phase 9 — overlays/PiP, masks, chroma key, overlapping transitions, keyframes with easing graphs
- Phase 10 — rich text, templates, text-to-speech, drawing, stickers
- Phase 11 — video effects pack
- Phase 12 — export up to 4K/60 fps with HEVC/HDR, background export, settings, release

### Out of scope (needs AI, online content, or Media3 can't do it)
Auto captions/lyrics, background removal, AI effects/beauty/retouch, upscaling, smooth slow-mo
(optical flow), auto reframe, stabilization, AutoCut/templates from the cloud, online
stock/sticker/music/effect libraries, cloud sync, blend modes other than Normal (Media3's video
compositor isn't pluggable — see Decisions log).

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
- [x] All model classes from section 2 with serialization
- [x] `TimelineMath` (total duration, clip at timeline time, timeline↔source time with speed) + unit tests
- [x] `MediaProbe` to read duration/type of a Uri
- [x] Photo Picker (`PickMultipleVisualMedia`) for videos + images; take persistable URI permission
- [x] Create a new `Project` from picked media (in memory for now) and open Editor
- [x] Editor shows ExoPlayer preview of the first clip with play/pause and a seek bar

**Acceptance:** pick 3 videos → editor opens → first video plays, pauses, and seeks.

### Phase 2 — End-to-end export (single clip)
- [x] `CompositionBuilder` v1: one clip with trim → `Composition`
- [x] Preview switched to `PreviewController` (CompositionPlayer) — moved here from Phase 3, see Decisions log
- [x] Trim tool: range slider over the clip, preview reflects trim
- [x] `ExportManager`: Transformer export to app cache, progress as `Flow<ExportState>` (polling `getProgress`)
- [x] `MediaStoreSaver`: save to `Movies/PreCut`
- [x] Export screen with progress, success (open/share), and error states

**Acceptance:** trim a clip, export, and the result appears in the gallery with correct length and audio.

### Phase 3 — Multi-clip timeline
- [x] `EditAction` + `EditReducer`: AddClips, DeleteClip, SplitClip(atTimelineMs), MoveClip(from,to), DuplicateClip, TrimClip — all unit-tested
- [x] `CompositionBuilder` v2: all clips in one sequence, images supported
- [x] Preview rebuilt when the project changes (debounced) — CompositionPlayer switch already done in Phase 2
- [x] `ThumbnailLoader`: lazy frame extraction at low resolution with LRU memory cache
- [x] Timeline UI: horizontal scroll, thumbnail strip per clip proportional to duration, time ruler, fixed center playhead synced to playback in both directions (scroll seeks, playback scrolls)
- [x] Pinch to zoom timeline
- [x] Tap to select clip; selected clip highlighted with trim handles
- [x] Long-press drag to reorder
- [x] Bottom toolbar: Split, Delete, Duplicate, Trim
- [x] Export uses the full composition

**Acceptance:** 3+ clips (including an image) can be split, reordered, trimmed, and deleted; preview plays them seamlessly; export matches preview.

UI for all phases follows `docs/design/DESIGN.md`.

### Phase 4 — Editing tools
- [x] Speed tool (0.25x–4x presets + slider); timeline width updates
- [x] Filter tool: horizontal list of filter previews; `EffectsFactory` mapping
- [x] Rotate tool (90° steps)
- [x] Aspect ratio picker (project level), preview letterboxes accordingly
- [x] Text tool: add/edit/delete text, color, size, background; drag to position on preview; set start/end on a text track lane in the timeline
- [x] All effects routed through `CompositionBuilder` so preview == export
- [x] Unit tests for overlay timing (composition-level, timeline time — see Decisions log), clamping across clip/speed changes, NDC mapping

**Acceptance:** a project using speed, filter, rotation, 9:16 ratio, and a text overlay spanning two clips exports identically to the preview.

### Phase 5 — Audio (CapCut-style; rebuilt per user request)
- [x] Multi-track audio: any number of clips, auto-stacked into rows when overlapping; Music / Voiceover / Extracted kinds
- [x] Add music from device (`OpenDocument` audio/* — persistable), inserted at the playhead; Replace keeps slot and length
- [x] Per audio clip: move, trim, split, duplicate, delete, volume (0–200%), fade in/out, speed (pitch kept), voice effects (pitch presets), beats
- [x] Waveforms on audio clips (on-device decode, memory + disk cache)
- [x] Voiceover recording at the playhead (RECORD_AUDIO), preview muted while recording
- [x] Extract audio from a video clip (same timing; clip muted)
- [x] Video clip sound: volume, mute, fade in/out; "Mute clip audio" toggle for all original sound
- [x] Snapping of text/audio bars to beats, playhead and edges (haptic tick)
- [x] One audio-only sequence per row in the `Composition`; audio past the video end is cut
- [x] Unit tests: lanes, placement with speed, every audio action, spec mapping, drag/snap geometry, peak bucketing

Out of scope (AI or online content): noise reduction, AI voice changers/vocal isolation, auto beat detection, sound libraries.

**Acceptance:** exported video contains original audio and music mixed at the chosen levels, with correct offset.

### Phase 6 — Persistence, drafts and undo/redo
- [x] Room: `ProjectEntity(id, name, json, thumbnailPath, durationMs, updatedAt)` + DAO
- [x] `ProjectRepository`: save, load, observe list, delete, rename, duplicate
- [x] Auto-save on every edit (debounced ~500 ms) and on leaving the editor
- [x] Draft cover thumbnails (first frame of the first clip) saved with the project
- [x] Home screen (CapCut "Projects"): grid of drafts with cover, name, duration, last edited; rename, duplicate and delete
- [x] `History`: undo/redo stack (cap 50, rapid edits to the same target coalesced) wired into ViewModel; top-bar buttons
- [x] Missing-source handling: unreadable media is marked on the timeline; remove all missing, or replace a missing clip
- [x] Process-death safety: editor restores from repository
- [x] Voiceover file cleanup when projects or voiceovers are deleted

**Acceptance:** edit, kill the app, reopen → project restored exactly; undo/redo works across all edit types.

### Phase 7 — Core editing parity (gaps found by auditing Phases 1–4 against CapCut)
Import and preview (Phase 1 gaps)
- [x] New media is inserted at the playhead (after the clip under it), not always at the end
- [x] Camera: record a video or take a photo from Home and from the editor's add button (system camera via `CaptureVideo` / `TakePicture` into app storage)
- [x] "Share to PreCut": accept `ACTION_SEND` / `ACTION_SEND_MULTIPLE` videos and photos from other apps and open them as a new project
- [x] Full-screen preview with its own scrub bar and play/pause (moved here from Phase 12)

Timeline (Phase 2–3 gaps)
- [x] Trim handles and bar moves snap to the playhead and to neighbouring edges (haptic tick); auto-scroll when a drag nears the screen edge (moved here from Phase 12)
- [x] Frame-level zoom: ruler shows frame ticks at max zoom; the selected clip shows its duration; clip badges for speed, filter and muted
- [x] Multi-select mode: select several clips, texts or audio clips → delete, duplicate, or set speed/filter/volume at once (one undo step)
- [x] "Apply to all" for filter and volume (and for every later per-clip look tool)
- [x] Project cover: pick a frame of the video or a gallery photo (CapCut "Cover"); shown on the drafts grid, optionally exported as the first frame

Speed and ratio (Phase 4 gaps)
- [x] Normal speed range 0.1x–100x like CapCut, with a "Keep pitch" toggle and the new length shown; verify Media3 behaviour at the extremes
- [x] More ratios: Original (first clip), 4:3, 3:4, 2:1, 2.35:1, 1.85:1, 9:19.5

Text (Phase 4 gaps)
- [x] Overlapping texts auto-stack into rows like audio
- [x] Pinch to scale and two-finger rotate text on the preview, snapping to the centre lines and to 0°/90°; `TextOverlay.rotationDegrees`
- [x] Full colour picker (hue, saturation, hex, recent colours) and text opacity
- [x] Split text at the playhead, duplicate text
- [x] Unit tests: insert-at-playhead index, batch actions, text lanes, transform clamping, speed range, ratio sizes

**Acceptance:** media shared from the gallery opens as a project; a camera clip inserts at the playhead; three overlapping texts sit on three rows and can be scaled/rotated on the preview; a multi-selected batch delete undoes in one step.

### Phase 8 — Clip look and frame (CapCut "Edit", "Filters", "Adjust")
- [ ] **Filter library:** about 40 bundled LUT filters in categories (Life, Film, Retro, Mono, Food, Nature, Style) via `SingleColorLut`; intensity 0–100%; apply to all; import your own `.cube` LUT (CapCut Pro)
- [ ] **Adjust:** brightness, contrast, saturation, exposure, sharpen, highlights, shadows, temperature, tint, hue, fade, vignette, grain (custom GL shaders where Media3 has no built-in effect)
- [ ] **HSL** per colour (8 hues: hue/saturation/lightness) and **Curves** (RGB + per channel) — CapCut Pro tools
- [ ] **Filter and Adjust layers:** time-ranged bars on their own lane that affect everything underneath (composition-level, time-gated, like text)
- [ ] **Transform main clips:** drag, pinch-scale and rotate any angle on the preview; Fit / Fill; snap to centre and 0°/90° (`MatrixTransformation`)
- [ ] **Crop:** free and fixed ratios on the preview; **Mirror** horizontal / vertical
- [ ] **Canvas / background:** black, colour, blur (4 strengths, `GaussianBlurWithFrameOverlaid`), or an image/pattern; apply to all
- [ ] **Opacity** per clip
- [ ] **Edge transitions** between clips (no overlap, total length unchanged): fade through black/white, flash, zoom in/out, blur, glitch, spin; adjustable duration; apply to all. Overlapping transitions come in Phase 9
- [ ] **Clip animations** in/out/combo with duration: fade, zoom, slide, spin, bounce, shake
- [ ] **Speed curves:** presets (Montage, Hero, Bullet, Jump cut, Flash in/out) and a custom curve editor with draggable points; variable `SpeedProvider`; timeline math handles variable speed
- [ ] **Freeze frame:** insert a still (photo clip) of the frame under the playhead, splitting the clip
- [ ] **Reverse:** pre-render a reversed copy (decode GOP by GOP, re-encode in reverse order, audio reversed) with a progress dialog, cached in app storage; undo restores the original
- [ ] **Motion blur** (CapCut Pro): frame blending with `FrameCache` — stretch goal, verify performance
- [x] **Replace clip** (keeps position, length and settings) — built in Phase 6 for missing media
- [ ] Unit tests for every new action and spec mapping (curve integration, transition edge timing, LUT parsing, layer time gating)

**Acceptance:** a project with a LUT filter at 60%, HSL + curves, a filter layer, a transformed and cropped clip, blurred canvas, 2 transitions, an animation, a speed curve, a freeze frame and a reversed clip exports identically to the preview.

### Phase 9 — Overlays (PiP), masks and keyframes
- [ ] Overlay track(s): add video/photo as picture-in-picture; overlays auto-stack into rows like audio
- [ ] Composition gets extra video sequences; preview uses `MultipleInputVideoGraph`; per-input placement through `VideoCompositorSettings.getOverlaySettings(inputId, timeUs)`
- [ ] Transform overlays directly on the preview: drag, pinch-scale, two-finger rotate; snap to centre lines
- [ ] Overlay opacity, rotation, crop/adjust/filter reuse, animations, "Switch to main track" / "Switch to overlay"
- [ ] **Masks** for main clips and overlays: linear, mirror, circle, rectangle (rounded corners), heart, star; feather, invert; move/scale/rotate on the preview (alpha-mask shader)
- [ ] **Chroma key** (colour picker + intensity/shadow) as a custom GL shader
- [ ] **Overlapping transitions** (CapCut style): dissolve, slide, push, wipe, zoom, spin. Neighbouring clips go on alternating A/B sequences that overlap by the transition duration, driven by time-dependent compositor settings plus masks; total length shrinks by the overlap like CapCut
- [ ] **Keyframes** for position, scale, rotation, opacity, mask, chroma, filter intensity, adjust values and volume, on main clips, overlays and text: add/remove at the playhead, keyframe dots on the timeline
- [ ] Keyframe easing: linear, ease in/out presets and a custom bezier graph (CapCut Pro "graphs")
- [ ] Unit tests: keyframe interpolation and easing, overlay placement/lanes, compositor settings mapping, A/B transition layout

**Acceptance:** a PiP overlay with keyframed motion, a heart mask and chroma key over a main clip, plus a dissolve between two main clips, exports identically to the preview.

### Phase 10 — Text, stickers and drawing
- [ ] Fonts (bundled open-licence set, plus import `.ttf`/`.otf` from the device), bold/italic/underline, alignment, letter/line spacing
- [ ] Stroke (outline), shadow (colour, blur, distance, angle), glow, text box background with corner radius and padding
- [ ] Curved text; rich text (different colour/size per selected characters)
- [ ] Text animations in/out/loop with duration (fade, typewriter, slide, pop, bounce, wave)
- [ ] Text style presets and bundled text templates (one-tap combinations of style + animation)
- [ ] **Text-to-speech:** turn a text into a voiceover clip with the device's `TextToSpeech` engine (offline voices, speed/pitch)
- [ ] Batch edit: change the style of all texts at once
- [ ] Stickers: emoji picker and device images (PNG/WebP, animated GIF/WebP via `ImageDecoder` frames) on a sticker lane; transform on preview; animations; keyframes (reuse Phase 9)
- [ ] **Draw:** freehand brush (colour, size, eraser) on the frame, saved as a sticker
- [ ] Unit tests: style → render spec mapping, animation curves, rich-text spans, sticker lanes, TTS timing

**Acceptance:** styled, curved, animated text with a TTS voiceover, an animated sticker and a drawing export identically to the preview.

### Phase 11 — Video effects (CapCut "Effects", non-AI)
- [ ] Effect lane: time-ranged effects applied to everything underneath; also per-clip effects
- [ ] Bundled GLSL pack (~25): shake, zoom lens, glitch, RGB split, VHS, old film, film grain, light leak, flash, strobe, blur, pixelate, mirror, kaleidoscope, split screen (2/3/4/9), fisheye, vignette pulse, soft glow, bokeh, sparkle, black-and-white flash, wobble, edge, posterize, neon
- [ ] Parameters per effect (intensity, speed), favourites, apply to all
- [ ] Unit tests: effect time gating and parameter mapping

Out of scope: body/face effects (need AI segmentation), online effect store.

**Acceptance:** a project with two effect layers and a per-clip effect exports identically to the preview.

### Phase 12 — Export settings, polish and release prep
- [ ] Move export into `ExportService` (foreground, `dataSync`/`mediaProcessing` type as appropriate for targetSdk) with progress notification and cancel action
- [ ] Notification permission request (Android 13+)
- [ ] Export settings (CapCut-style sheet): resolution 480p/720p/1080p/2K/4K; frame rate 24/25/30/50/60 (`FrameDropEffect` lowers it; can't exceed the source); bitrate presets (`VideoEncoderSettings`); codec H.264/HEVC where supported; estimated file size; capability check per device
- [ ] HDR: keep HDR10/HLG when the device can encode it (`Composition.HDR_MODE_KEEP_HDR`), otherwise tone-map to SDR; Ultra HDR photos
- [ ] Export audio only (M4A); save the current frame as a photo; GIF export (stretch — Media3 has no GIF encoder)
- [ ] Settings screen: default photo duration, default ratio, default export settings, cache size with clear
- [ ] Pinch-zoom preview
- [ ] Error handling: unsupported codec, low storage, export failure with retry
- [ ] Loading/empty states everywhere; haptics on split/delete
- [ ] Performance pass: thumbnail memory, recomposition counts, player rebuild debounce, effect shader cost
- [ ] App icon, splash screen, dark theme review
- [ ] R8 enabled for release, keep rules verified, release build tested
- [ ] Basic UI tests for the main flow (create → edit → export)

**Acceptance:** release build runs smoothly on a low-end device; a 4K/60 (where supported) export while the app is backgrounded completes; no crashes in the main flow.

---

## 4. Manual test matrix (use at the end of phases 3–12)
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

## 6. CapCut gap analysis (2026-10-13)
✅ have · ⚠️ partial · ❌ missing → planned phase · ⛔ out of scope (AI / online / Media3 limit).
"Pro" marks CapCut Pro (paid) features. Audit of each built phase against CapCut, then the overall picture.

### 6.1 Phase 1 — Import and playback
| CapCut | PreCut | Plan |
|---|---|---|
| Pick videos + photos, multi-select, selection order, preview | ✅ system Photo Picker | — |
| New clips inserted at the playhead | ❌ always appended at the end | 7 |
| Camera (record / photo) inside the app | ❌ | 7 |
| Share to CapCut / open with | ❌ | 7 |
| Play/pause, time readout, scrub | ✅ | — |
| Full-screen preview | ❌ | 7 |
| HDR / Ultra HDR media | ❌ shown as SDR | 12 |
| Stock library, AutoCut, cloud templates | ⛔ online/AI | — |

### 6.2 Phase 2 — Trim and export
| CapCut | PreCut | Plan |
|---|---|---|
| Trim by dragging clip edges, precise trim | ✅ (handles + Trim tool) | — |
| Trim snaps to playhead/edges with haptic | ❌ | 7 |
| Export to gallery, progress, cancel, open/share | ✅ | — |
| No watermark / ending clip (Pro) | ✅ never added | — |
| Resolution 480p–4K | ⚠️ fixed 1080p | 12 |
| Frame rate 24–60, bitrate, size estimate | ❌ | 12 |
| HEVC, HDR export (Pro) | ❌ | 12 |
| Export in background | ❌ | 12 |
| Export audio only, save frame, GIF | ❌ | 12 (GIF stretch) |

### 6.3 Phase 3 — Timeline
| CapCut | PreCut | Plan |
|---|---|---|
| Thumbnails, ruler, centre playhead, pinch zoom | ✅ | — |
| Select, split, delete, duplicate, long-press reorder | ✅ | — |
| Replace clip | ✅ (Phase 6) | — |
| Undo / redo, drafts | ✅ (Phase 6) | — |
| Frame-level zoom, duration and speed badges on clips | ⚠️ muted badge only | 7 |
| Auto-scroll when dragging near the edge | ❌ | 7 |
| Multi-select / batch edit | ❌ | 7 |
| Apply to all | ❌ | 7 (+ every look tool) |
| Cover (frame or photo) | ⚠️ automatic first frame only | 7 |
| Freeze frame, reverse | ❌ | 8 |

### 6.4 Phase 4 — Editing tools
| CapCut | PreCut | Plan |
|---|---|---|
| Speed 0.1x–100x, keep-pitch toggle | ⚠️ 0.25x–4x, pitch always kept | 7 |
| Speed curves + custom curve editor | ❌ | 8 |
| Smooth slow-mo (Pro) | ⛔ AI (optical flow) | — |
| Filters: ~100 in categories + intensity | ⚠️ 7 filters, no intensity | 8 (LUT pack) |
| Custom LUT import (Pro) | ❌ | 8 |
| Filter / Adjust as timeline layers | ❌ | 8 |
| Adjust incl. sharpen, highlights, shadows, grain, vignette | ❌ | 8 |
| HSL, Curves (Pro) | ❌ | 8 |
| Rotate 90° | ✅ | — |
| Transform (move, scale, any-angle rotate), Fit/Fill | ❌ | 8 |
| Crop, mirror | ❌ | 8 |
| Ratios (11 incl. Original, 4:3, 2.35:1) | ⚠️ 4 ratios | 7 |
| Canvas colour / blur / image | ❌ black only | 8 |
| Opacity | ❌ | 8 |
| Mask (Pro shapes) | ❌ | 9 |
| Transitions, clip animations | ❌ | 8 (edge), 9 (overlapping) |
| Keyframes + easing graphs (Pro) | ❌ | 9 |
| Motion blur (Pro) | ❌ | 8 (stretch) |
| Video effects | ❌ | 11 |
| Text: add, size, colour, background, position, timing | ✅ | — |
| Text rows when overlapping, pinch/rotate on preview | ❌ all on one row | 7 |
| Text full colour picker, opacity, split, duplicate | ❌ | 7 |
| Fonts, stroke, shadow, glow, curved, rich text | ❌ | 10 |
| Text animations, templates, presets | ❌ | 10 |
| Text-to-speech | ❌ | 10 (device TTS) |
| Stickers, draw | ❌ | 10 (emoji + own images; online store ⛔) |
| Auto captions, remove background, retouch, upscale, stabilize | ⛔ AI | — |

### 6.5 Overall
| Area | PreCut after Phase 6 | Plan |
|---|---|---|
| Projects, undo/redo | ✅ | — |
| Main track editing | ✅ core · ❌ batch, cover, insert at playhead | 7 |
| Speed | ✅ normal | 7 (range), 8 (curves) |
| Look | ✅ basic filters | 8 |
| Frame | ✅ rotate/ratio | 7 (ratios), 8 (transform, crop, canvas) |
| Motion | ❌ | 8, 9 |
| Overlays, masks, chroma key | ❌ | 9 (blend modes ⛔) |
| Text and stickers | ✅ basic | 7, 10 |
| Audio | ✅ (Phase 5; AI voice/noise ⛔) | — |
| Effects | ❌ | 11 (body effects ⛔) |
| Export | ✅ 1080p | 12 |

## 7. Decisions log
Claude Code: append significant technical decisions or deviations from this plan here, with a one-line reason.

- **2026-10-12 — Package `com.savie.precut`, app name "PreCut".** `com.example.*` is rejected by Google Play.
- **2026-10-12 — Application class is `PreCutApp`** (instead of `VideoEditorApp`), to match the app name.
- **2026-10-12 — Versions (latest stable on 2026-10-12):** AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Navigation 2.10.2, Hilt 2.60.1 (androidx.hilt 1.4.0), Room 2.8.5, kotlinx.serialization 1.11.0, coroutines 1.11.0, Coil 3.6.3, Media3 1.11.1, Turbine 1.2.1, JUnit 4.13.2. compileSdk/targetSdk 37 (Android 17), minSdk 26.
- **2026-10-12 — AGP 9 built-in Kotlin:** the `kotlin-android` plugin is not applied in `app/`; it is declared `apply false` at the root only to pin the KGP version.
- **2026-10-12 — Java/Kotlin target 17 via `compileOptions` + `jvmTarget`** rather than `jvmToolchain(17)`, to avoid deprecated toolchain auto-provisioning (builds run on the installed JDK 21).
- **2026-10-12 — `hiltViewModel()` comes from `androidx.hilt:hilt-lifecycle-viewmodel-compose`**; the older `hilt-navigation-compose` location is deprecated.
- **2026-10-12 — Known build warning:** `Configuration.setVisible` deprecation is emitted by AGP itself, not by project scripts.
- **2026-10-12 — `Clip` also stores `sourceWidth`, `sourceHeight`, `sourceRotationDegrees`** (probed at import), needed later for mixed portrait/landscape layout without re-probing.
- **2026-10-12 — Preview UI uses `media3-ui-compose` (`ContentFrame`) instead of View-based `media3-ui` (`PlayerView`).** Compose-native, handles aspect-fit and shutter; `media3-ui` dropped as unused.
- **2026-10-12 — Import goes through `MediaImporter`** (persistable permission + `MediaProbe`) so `HomeViewModel` is unit-testable with a fake.
- **2026-10-12 — `di/AppModule` added** (not in the original layout) for Hilt bindings, `Clock` and the IO dispatcher.
- **2026-10-12 — Projects start with a blank `name`;** naming/rename UI arrives in Phase 6. Until then the in-memory repository means projects don't survive process death (Editor shows "no longer available").
- **2026-10-12 — CompositionPlayer switch moved from Phase 3 into Phase 2.** The trim preview would otherwise need a second, non-`CompositionBuilder` code path, which CLAUDE.md forbids.
- **2026-10-12 — `CompositionPlayer` is `@ExperimentalApi` (stricter than `@UnstableApi`) in Media3 1.11.1.** It's the only way to preview a `Composition`; the opt-in is confined to `PreviewController`. Every `EditedMediaItem` must have `setDurationUs` (source duration, pre-clipping) for it.
- **2026-10-12 — `CompositionBuilder` is split into a pure `compositionSpecOf(Project)` (JVM unit-tested) and a thin Media3 conversion,** because Media3 `MediaItem` needs `android.net.Uri`, which doesn't work in JVM tests.
- **2026-10-12 — Sequences use `EditedMediaItemSequence.Builder(setOf(AUDIO, VIDEO))`;** the item-list constructors are deprecated in 1.11.
- **2026-10-12 — ViewModels depend on `VideoExporter` / `MediaSaver` interfaces,** never on `Composition`; `ExportManager` builds the composition itself.
- **2026-10-12 — Route args are read with `SavedStateHandle.requireProjectId()`** instead of `toRoute()`, which needs Android's Bundle and breaks JVM ViewModel tests. `RoutesTest` guards the argument name.
- **2026-10-12 — Android 8–9 export saving uses the deprecated `getExternalStoragePublicDirectory` + `WRITE_EXTERNAL_STORAGE` (maxSdk 28),** suppressed at the narrowest scope; there is no non-deprecated alternative before API 29.
- **2026-10-12 — Design system defined in-house** (`docs/design/DESIGN.md`) instead of waiting for Sleek designs: dark-only, accent `#FF4D6D`, no dynamic colour.
- **2026-10-12 — Every composition item gets a `Presentation` (fit into the project frame, 1080 short side)** from Phase 3, not Phase 4: without a fixed output size, mixed portrait/landscape clips and full-resolution photos export at the wrong size or fail. Only the aspect-ratio *picker* remains in Phase 4.
- **2026-10-12 — Photo clips: `sourceDurationMs` = 60 s max, trim range = display duration (default 3 s),** so a photo can be lengthened with its trim handles, not just shortened.
- **2026-10-12 — Clip tools act on the selected clip, else the clip under the playhead.** Split always acts at the playhead.
- **2026-10-12 — Timeline is a custom `horizontalScroll` with manual thumbnail culling,** not a LazyRow: clip widths are time-proportional and the content must stay aligned with the ruler and playhead. Zoom is capped so content stays under ~120k px (Compose's layout limit is ~262k).
- **2026-10-12 — Tap and long-press-to-reorder are detected on the timeline container,** not per clip, so the gesture survives the switch to reorder tiles mid-gesture. Long-presses within 24dp of the selected clip's edges are left to the trim handles.
- **2026-10-13 — Text overlays are one composition-level `OverlayEffect` in timeline time,** not per-item effects mapped into each clip's local time. Both CompositionPlayer and Transformer apply `Composition.effects` (single sequence → SingleInputVideoGraph). Media3 doesn't document composition-level timestamps; **verify on device** that text timing matches in preview and export. Fallback: the per-item mapping from the original plan.
- **2026-10-13 — Text sizing convention:** `textSizeSp` = sp on a 360dp-wide screen, scaled to the output's short side (`textSizePx`). The preview drag ghost and `TextOverlayRenderer` share the padding/corner ratios.
- **2026-10-13 — Speed uses `EditedMediaItem.setSpeed(SpeedParameters(constant, shouldMaintainPitch = true))`** — supported by both CompositionPlayer and Transformer; `SpeedParameters` is `@ExperimentalApi` (opt-in in `CompositionBuilder`). Sped-up clips are capped at 60 fps.
- **2026-10-13 — Rotation is clockwise in the model;** `EffectsFactory` negates it because Media3 rotates counter-clockwise.
- **2026-10-13 — Filters are pure `FilterEffect` specs mapped to Media3 in `EffectsFactory`;** picker thumbnails use Compose colour-matrix approximations (`FilterPreviewMatrix`). Vintage = 80% sepia matrix + slight contrast reduction.
- **2026-10-13 — The reducer clamps text overlays after every edit** (timeline shrink, speed change, delete); with no clips, texts are dropped. A text left blank when its panel closes is deleted.
- **2026-10-13 — Text bars move in time by dragging:** a selected bar's body drags horizontally; an unselected bar needs a long-press first so normal drags still scrub. Moves keep the length and stay inside the timeline (`TimelineGeometry.moveRange`).
- **2026-10-13 — Music is picked with `OpenDocument`, not `GetContent`:** only OpenDocument Uris can be persisted, which Phase 6 drafts need.
- **2026-10-13 — Phase 5 rebuilt CapCut-style (user request):** multi-track audio replaces the single music track. Rows are derived (`Project.audioLanes`, greedy stacking), not stored. Each row is an audio-only `EditedMediaItemSequence` with `addGap` before each clip; placement (`placementWithin`, speed-aware) cuts audio at the video end, so output length never depends on Media3's multi-sequence rules. Audio isn't destructively trimmed when the video shrinks. **Verify on device** that leading gaps export correctly; fallback is a silent audio item.
- **2026-10-13 — Audio processing chain per item:** constant `GainProcessor` (volume) → separate fade `GainProcessor` (a fade's shape replaces, not scales, the provider gain) → `SonicAudioProcessor` (voice pitch). Fade positions are in timeline (post-speed) time; **verify on device** for sped-up clips.
- **2026-10-13 — Audio actions are a sealed `EditAction.AudioEdit` subtype** handled in `AudioEdits.kt`, keeping both `when`s exhaustive. Editor audio logic lives in `EditorAudio` (via `EditorHost`) to keep the ViewModel manageable.
- **2026-10-13 — Audio bars draw only their audible part (up to the video end)** so widths stay bounded by the video's zoom cap; the end handle trims from the audible end.
- **2026-10-13 — Voiceovers are app-private files** (`filesDir/voiceovers`); deleting a voiceover clip doesn't delete its file yet (cleanup belongs with Phase 6 persistence). No timeline vertical scrolling: the timeline grows with the number of audio rows.
- **2026-10-13 — Volume is a `GainProcessor` (constant `DefaultGainProvider`) per item, 0–200%;** omitted at 100%. Clips have a separate `muted` flag so unmuting restores the volume.
- **2026-10-13 — Text and audio bars share `TimelineBar`** (drag body to move, handles to trim, commit on release, optional snapping). The audio start handle trims the source and moves the start together so the remaining audio stays in place.
- **2026-10-13 — CapCut gap analysis added (§6) and the plan extended with Phases 7–10** at the user's request. Persistence/undo (Phase 6) stays first: every later feature needs undo, and drafts are core CapCut behaviour.
- **2026-10-13 — Projects are stored in Room as JSON** (`ProjectEntity.json`) plus list columns (name, cover, duration, dates). `ProjectSerializer` ignores unknown keys and omits defaults, so drafts survive model changes without migrations for additive fields.
- **2026-10-13 — Draft covers** are JPEGs of the first clip's first frame (via `ThumbnailLoader`), regenerated only when the first clip changes, with a new file name per change so Coil never shows a stale image.
- **2026-10-13 — Undo/redo is a pure `History`** (cap 50); text typing on the same overlay within 1 s coalesces into one step. `ProjectSession` owns history and debounced (500 ms) auto-save; saves run in an application-scoped `CoroutineScope` so the final flush on leaving the editor completes.
- **2026-10-13 — Missing media** is detected on open (`MediaProbe.isReadable`); the preview plays the rest, export is disabled until the media is replaced (`ReplaceClip`, audio Replace) or removed (`RemoveItems`, one undo step).
- **2026-10-13 — Unused voiceover files are swept on the Projects screen only,** never while an editor is open (its undo history may still need them).
- **2026-10-13 — Second CapCut audit (Phases 1–4, including Pro features), at the user's request.** Gaps went into a new Phase 7 (core editing parity); the old Phases 7–10 became 8–12, expanded, and a video-effects phase was added (11). Full tables in §6.
- **2026-10-13 — Reverse moved from out of scope to Phase 8** as a pre-rendered reversed copy (CapCut also renders it). Real-time backwards decoding is still impossible with Media3.
- **2026-10-13 — Real (overlapping) transitions are feasible** with A/B video sequences: `VideoCompositorSettings.getOverlaySettings(inputId, presentationTimeUs)` is time-dependent, so alpha/position can animate across the overlap. Edge transitions (Phase 8) need only one sequence.
- **2026-10-13 — Blend modes other than Normal are out of scope:** in Media3 1.11.1 `MultipleInputVideoGraph.Factory` has no compositor parameter and `DefaultVideoCompositor` only alpha-blends. Revisit if Media3 makes the compositor pluggable.
- **2026-10-13 — Export frame rate can only be lowered** (`FrameDropEffect`); the settings sheet caps choices at the source frame rate. HDR export uses `Composition.HDR_MODE_KEEP_HDR` behind a device capability check.
- **2026-10-13 — Phase 7: camera captures and shared media live in app storage** (`files/captures`, `files/imports`). Shares only carry temporary read grants, so they're copied in before import; captures use a FileProvider Uri for the camera app and a `file://` Uri in the project. `AppMediaJanitor` (was `VoiceoverJanitor`) sweeps all three app media folders.
- **2026-10-13 — "Cover as first frame" is a full-frame overlay for the first 1/30 s** in the composition-level `OverlayEffect`, not an extra item, so audio/text timing stays identical between preview and export. Cover frames are extracted exactly (`OPTION_CLOSEST`) and kept out of the thumbnail cache.
- **2026-10-13 — Multi-select and "Apply to all" use `EditAction.Batch`,** folded by the reducer, so a batch is one undo step. `BatchEdits` builds batches and skips items an edit doesn't apply to (speed on photos, filters on audio).
- **2026-10-13 — Text transform on the preview:** the gesture layer covers the whole preview (two-finger pinch/rotate anywhere, one-finger move only when started on the text). Rotation is `StaticOverlaySettings.setRotationDegrees(-deg)`. **Verify on device** that rotated text lines up between preview and export on non-square frames.
- **2026-10-13 — Clip speed range is 0.1x–100x** (audio clips stay 0.25x–4x) on a logarithmic slider, with a per-clip keep-pitch flag (`SpeedParameters.shouldMaintainPitch`). **Verify on device:** preview at very high speeds is decoder-bound and may stutter; export should be correct.
- **2026-10-13 — Trim handles snap (playhead, edges, beats) and the start handle follows the finger** while the clip keeps its slot; neighbours only move when the trim is committed. Edge auto-scroll moves the playhead with the scroll, like a scrub.
- **2026-10-13 — Full-screen preview reuses the editor's player;** the editor's own surface is removed while full screen is open, because one player renders to one surface.
- **2026-10-13 — Share-to-PreCut navigation uses a non-replayed `ShareInbox.arrivals` signal,** separate from the pending Uris, so the Projects screen consuming the share can't race the navigation back to it.
- **2026-10-13 — Scrubbing uses `CompositionPlayer.setScrubbingModeEnabled`** (Media3 1.11.1) between drag start and the end of its fling (also for edge auto-scroll and the full-screen scrub bar). Its ExoPlayers keep decoders warm and drop stale seeks instead of queuing an exact seek per scroll pixel; turning it off lands exactly on the final position.
- **2026-10-13 — Preview proxies (CapCut-style) for smooth scrubbing.** `ProxyManager` transcodes each video larger than 540p (short side) once, in the background, to a 540p H.264 copy with a keyframe every 0.2 s (`cacheDir/proxies/<sha1>.mp4`). The preview plays proxies via `Project.withMediaSubstitutes`, which only swaps video Uris, so the same `CompositionBuilder` path is used. Export always uses originals and pauses proxying. Proxies swap in once the preview is idle; failures fall back to the original. The janitor trims orphans and caps the cache at 2 GB. Plan said "skip ≤ 720p"; lowered to ≤ 540p because long keyframe intervals, not resolution, are what make 720p sources scrub badly. **Verify on device** that encoders honour the 0.2 s keyframe interval.
- **2026-10-13 — Preview renders at a 720 short side** (`PREVIEW_SHORT_SIDE_PX`), export at 1080. The spec differs only in output size and text pixel size; texts scale with the short side.
- **2026-10-13 — The playhead is passed as a lambda/State through the editor UI,** so only the time label, the timeline scroll and a few derived flags (`rememberCanSplit`, beat-at-playhead) react to it. Nothing recomposes the whole editor 60 times a second while playing or scrubbing.
