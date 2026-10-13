# PreCut design system

The source of truth is the Claude Design canvas "PreCut – Android screens" (private claude.ai link). A
copy is versioned in `docs/design/claude-design/`:
- one `.dc.html` per screen, named by screen ID
- `precut.css`, the shared component styles with exact sizes and colours
- `Tokens.dc.html`
- `C-01`…`C-07`, the component sheets
- `R-*`, the redlines
- `Checklist.dc.html`
- `canvas.json`, which includes the interaction notes

Open any `.dc.html` file in a browser to view it.

The look is dark only, with no dynamic colour. It is CapCut-like: near-black chrome so footage stands out, one hot accent, and white for "what's active" on the timeline. Tokens live in `ui/theme/Color.kt`, `Type.kt` and `Theme.kt`; keep them in sync with this file.

## Colour tokens

### Surfaces and text
| Token | Hex | Use |
|---|---|---|
| Ink0 | `#0E0E10` | App background, top bar, timeline |
| Ink1 | `#17171A` | Toolbar, panels, sheets |
| Ink2 | `#222226` | Chips, tiles, dialogs, menus, Mute button |
| Ink3 | `#2E2E33` | Dividers, disabled fills, slider track, Done button |
| Ink4 (new) | `#3A3A40` | Pressed chip, selected tab |
| Outline (new) | `#5A5A62` | Outlined buttons, switch border, idle field, sheet grab handle |
| TextPrimary | `#F2F2F5` | Text, icons |
| TextSecondary | `#9A9AA3` | Secondary text, timecode totals |
| TextDisabled (new) | `#F2F2F5` at 38% | Dimmed Undo/Redo, disabled tools |
| InverseSurface (new) | `#E6E6EA` | Snackbar, toast |
| Scrim (new) | `#000000` at 60% | Behind dialogs and sheets |

### Accent, status, overlays
| Token | Hex | Use |
|---|---|---|
| Accent | `#FF4D6D` | Slider fill, selected borders, accent text and icons |
| AccentFill (new) | `#D92B50` | **Filled buttons with white labels**: Export, New project, FAB, switch on. Accent with white is only 3.2:1. |
| AccentFillPressed (new) | `#C21F44` | Pressed filled button |
| AccentContainer | `#5C1426` | Selected chip/tile/segment fill |
| OnAccentContainer (new) | `#FFD9DF` | Text on AccentContainer |
| OnAccent / Playhead / ClipSelection | `#FFFFFF` | Labels on AccentFill, playhead, selection, handles |
| ErrorRed | `#FF6B5E` | Errors, missing media, destructive text |
| ErrorContainer (new) | `#4A1712` | Error banner fill |
| OnErrorContainer (new) | `#FFDAD5` | Banner text |
| InverseAccent (new) | `#B3123A` | Snackbar action |
| SnapGuide (new) | `#38D9A9` | Centre snap lines on the preview |
| ClipBackground | `#34343A` | Clip placeholder while thumbnails load |
| RulerTick | `#6B6B74` | Ruler ticks |

### Timeline lanes
Every bar starts with a 14 dp glyph, so lanes never rely on hue alone. Lanes keep a fixed order: overlay, main, looks, stickers, effects, text, audio.

| Token | Hex | Label | Glyph |
|---|---|---|---|
| TextLaneBar | `#B8860B` | **Ink0** (changed from white) | `title` |
| MusicBar (changed) | `#137D6F` (was `#16897A`) | white | `music_note` |
| VoiceoverBar | `#7B5BD6` | white | `mic` |
| ExtractedAudioBar | `#2F6FB8` | white | `audio_file` |
| LookLayerBar | `#B0457A` | white | `filter` / `tune` |
| OverlayBar (new, Phase 9) | `#8A93A6` | Ink0; rows show thumbnails, with this as a 2 dp outline and badge | `picture_in_picture` |
| StickerBar (new, Phase 10) | `#E07A2E` | Ink0 | `add_reaction` |
| EffectBar (new, Phase 11) | `#A3C93A` | Ink0 | `flare` |
| TransitionMarker (new, 8C) | `#E6E6EA` | — | a diamond inside a 24 dp Ink0 rounded square with a 1.5 dp border; selected = Accent fill |
| Keyframe (new, Phase 9) | `#FFFFFF` with a 1.5 dp Ink0 stroke; Accent at the playhead | — | a 10 dp diamond |
| BeatDot (new) | `#FFD43B` | — | 4 dp dots, 1 dp from the bar bottom |

## Type: Roboto Flex
Roboto Flex is variable and SIL OFL-licensed; it's bundled. Timecodes use tabular figures.

| Style | sp / line | Weight | Use |
|---|---|---|---|
| headlineSmall | 24 / 32 | 600 | Empty states, sheet titles |
| titleLarge | 22 / 28 | 700 / 600 | App name, dialog titles (Export sheet title: 20/28 600) |
| titleMedium | 16 / 24 | 600 | Project title, panel titles |
| bodyLarge | 16 / 22 | 400 | List rows, text fields |
| bodyMedium | 14 / 20 | 400 | Body, menus, snackbars |
| labelLarge | 14 / 20 | 600 | Buttons, Done, Export |
| labelChip (new) | 13 / 18 | 500 | Chips, tabs, slider labels and values |
| timecode (new) | 13 / 16 | 500, tnum | Playback row only |
| bodySmall | 12 / 16 | 400 | Hints, metadata; section labels at 600, caps, +0.4 tracking |
| labelMedium | 11 / 14 | 500 | Tool labels, tile labels, bar labels, in-panel timecodes |
| labelSmall | 10 / 12 | 500–600, tnum | Ruler labels, badges |

## Icons
**Material Symbols Rounded** (Apache 2.0) everywhere: weight 400, fill 0. Fill 1 is used only for Play/Pause and checked states. Icons are 24 dp in 48 dp targets, 18 dp in chips, and 14 dp in bar glyphs. They're imported as vector drawables, one per symbol; the component sheet C-07 lists the names.

## Shape (radius in dp)
- 4: badges, timeline bars
- 6: clips, crop handles
- 8: tool, filter and preset tiles; the add-media button; snackbars
- 12: menus, card covers
- 16: sheets and panels (top corners only), FAB
- 28: dialogs
- full: chips, pills, buttons, switches, the Mute button

## Spacing and sizes (dp)
- **Layout:** 4 grid · screen gutter 16 · panel padding 16 horizontal, 2–12 vertical · chip gap 8 · tile gap 10.
- **Top bar 56:** padding 4 start, 12 end · icon buttons 48 with 24 icons · title titleMedium plus an 18 `edit` glyph · Export pill 36 high, 16 padding.
- **Playback row 48:** a 3-column grid · timecode at 16 from the start · Play is a 32 filled icon in a 48 target · Full screen 4 from the end.
- **Timeline:**
  - ruler 18, labelled every 2 s, with 1 × 4 ticks every 0.5 s
  - main strip 56 with 6 above and below · thumbnail tiles 42 × 56 (3:4) · 2 gap between clips
  - look and text rows 22, audio rows 24, 4 gaps · overlay rows 40 (Phase 9)
  - **default scale 32 dp per second**
- **Pinned:**
  - Mute: 40 round, Ink2, 8 from the start
  - "+": 40 white, radius 8, 8 from the end
  - each sits in a 64 dp Ink0 fade zone, so clips slide underneath
- **Playhead:** 2 wide, the full timeline height, white with a 1 dp Ink0 35% halo; a 10 × 12 radius-3 knob at the top; fixed at W/2.
- **Selection:**
  - selected clip: 2 white border (radius 7, extended 2 outside) and **trim handles 14 × 60** with a 2 × 20 Ink0 grip
  - selected bar: a 2 white inset border and **bar handles 10 × row height** with a 2 × 10 grip
  - multi-selected items: a 2 Accent outline and an 18 round Accent check badge at the top-right
- **Badges:** 16 high, padding 5, radius 4, Ink0 at 82%, 10/600 tnum, 12 icon, at 4,4.
- **Toolbar 72:** Ink1, padding 4 · tools 64 × 64, radius 8, 24 icon, 4 gap, labelMedium · a 48 right fade as a scroll cue · shows 6 tools plus a cut-off one at 412 wide, 5 at 360.
- **Panels:**
  - Ink1 with a 16 top radius
  - **header 52**: padding 16 start, 8 end, then title, actions, Done
  - **Done**: a 36 high Ink3 pill with a 20 check icon and labelLarge
  - text actions (Reset, Apply to all): 40 high, padding 10, 18 icon. Apply to all is Accent; Reset is TextSecondary, or TextDisabled when nothing has changed.
  - body padding 16 horizontal, 2 top, 12 bottom, with 10 between rows
- **Panel heights:**
  - 1-row panels (Volume, Opacity): about 152; the timeline stays
  - 2-row panels (Filters, Adjust, Speed): about 232–264; the timeline stays
  - **tall panels** (Curves, HSL, Speed curve, Text, Canvas): about 400. The **timeline collapses to the main strip**, so the preview keeps at least 40% of the height.
- **Chips:** 32 high, padding 12, full radius, labelChip, Ink2. Selected chips are AccentContainer with a 1 dp Accent inner border and OnAccentContainer text. A changed value is shown inline in Accent tnum (white when selected); an edited channel gets a 6 dp Accent dot.
- **Tabs:** pill segments, Ink2 track with 3 padding, 30 high, selected in Ink4. Underline tabs are 44 high with a 3 Accent indicator.
- **Segmented control:** 40 high, full radius, 1 Outline border and dividers; selected is AccentContainer.
- **Tiles:**
  - filter: 60 × 60, radius 8, label 6 below in labelMedium 11/13 (2 lines max). Selected: a 2 Accent outer ring and TextPrimary label; default label TextSecondary. "None" is an Ink2 tile with a 24 `block` icon.
  - preset (transitions, animations): 64 × 64; selected is AccentContainer with an Accent ring
- **Sliders:** row 40 · label 64–72 wide in TextSecondary · track 4 Ink3 with Accent fill · knob 20 white with a 0 1 3 shadow · value 44 wide, right-aligned, labelChip tnum.
  - **bipolar sliders**: a 2 × 14 zero mark at 50%, filled from 50% · snap to 0 within 4 dp · double-tap the knob to reset
  - **value bubble** while dragging (and 1 s after): TextPrimary fill, Ink0 11/600, 18 above the track
- **Switch rows:** 48 high · switch 52 × 32 · on is AccentFill with a 24 white thumb.
- **Buttons:**
  - filled: AccentFill, 40 high (large 56 / radius 28)
  - outlined: 1 Outline border
  - text: Accent
  - tonal: Ink3
  - FAB: 56 high, radius 16, AccentFill, shadow 0 6 16 at 45%
- **Overlays:**
  - sheet: Ink1, radius 16, grab handle 32 × 4 in Outline
  - dialog: Ink2, radius 28, 24 padding, 28 side margins
  - menu: Ink2, radius 12, 48 rows, shadow 0 8 24 at 55%
  - snackbar: InverseSurface, radius 8, 48 minimum, with the action in InverseAccent
  - error banner: ErrorContainer
  - toast: a 36 InverseSurface pill
- **Elevation:** surfaces step by colour, with no shadows except menus, the FAB, and a lifted reorder tile (×1.1, shadow 0 8 20 at 70%).

## Interaction (from the canvas notes)
- **Scrubbing:** drag anywhere on the timeline to scrub; playback pauses and the preview follows.
- **Zoom:** pinch to zoom; the default is 32 dp per second, and maximum zoom shows single frames.
- **Selection:** tap to select; tap an empty lane to deselect.
- **Bar labels** stick to the visible left edge as the bar scrolls off.
- **Trimming:** the clip resizes live and its badge shows the duration. Edges snap to the playhead, clip edges and beats within 8 dp, with one haptic tick. Within 48 dp of the screen edge, the timeline auto-scrolls at up to one screen per second.
- **Reorder:** long-press (400 ms) collapses the clips to 48 dp squares centred on the finger. The lifted tile is ×1.1 with a shadow, a 3 dp white slot bar shows where it will land, and each slot change gives a haptic tick.
- **Multi-select:** taps toggle items. Done or system Back exits, keeping the last item as a single selection.
- **When edits apply:** sliders apply on release; chips and tiles on tap. Done closes a panel, and **system Back acts as Done**.
- **Filters:** tapping the selected tile again toggles a before/after peek.
- **Speed slider:** logarithmic, snapping to the preset values within 4 dp with a haptic tick.
- **Transforms:** centre lines snap within 6 dp; rotation snaps at right angles within 3°. The rotation readout shows only while rotating.
- **Text box:** a ✕ delete handle and a ↻ one-finger rotate/scale handle; double-tap opens the keyboard.
- **Optimizing chip:** updates every 1%, and fades 600 ms after reaching 100%.
- **Voiceover:** the timeline scrolls live and the violet bar grows; Stop selects the new clip.
- **Projects grid:**
  - a long-press on a card opens the same menu as ⋮
  - covers keep their own 9:16 or 1:1 ratio
  - the meta block under each cover is 48 high
  - the FAB hides when scrolling down

## Screen map: design IDs to code

| ID | Screen | Code |
|---|---|---|
| A-01 / A-02 | App icon, splash | `res/mipmap*`, `res/drawable/ic_launcher_*`, theme splash (Phase 12) |
| A-03 | Notification permission | Phase 12 |
| H-01 / H-02 | Projects, empty / grid | `ui/home/HomeContent.kt`, `ProjectCard.kt` |
| H-03 – H-05 | Card menu, rename, delete | `ProjectCard.kt`, `ProjectDialogs.kt` |
| H-06 | Camera menu | `ui/components/CaptureMenu.kt` |
| H-07 / H-08 | Importing, from Share | `HomeContent.kt` |
| H-09 | Settings | Phase 12 |
| E-01 – E-03 | Editor empty, add menu, default | `EditorScreen.kt`, `EditorContent.kt`, `EditorTopBar.kt`, `TimelineSection.kt`, `timeline/*`, `tools/EditorToolbar.kt` |
| E-04 / E-04b | Clip selected (8B toolbar) | `timeline/ClipView.kt`, `ClipBadges.kt`, `TrimHandle.kt`, `EditorToolbar.kt` |
| E-05 | Reordering | `timeline/ReorderOverlay.kt` |
| E-06 | Missing media | `MissingMediaBanner.kt`, `ClipView.kt` |
| E-07 | Multi-select | `tools/SelectionToolbar.kt`, `ClipView.kt`, `TimelineBar.kt` |
| E-08 | Optimizing preview | `preview/ProxyStatusChip.kt` |
| E-09 | Full screen | `preview/FullScreenPreview.kt` |
| E-10 | Rename project | new: top-bar title (`EditorTopBar.kt`) |
| T-01, T-02, T-04, T-05 | Trim, Speed, Volume, Fade | `tools/TrimTool.kt`, `SpeedTool.kt`, `ClipVolumeTool.kt`, `FadeTool.kt` |
| T-06 / T-06b | Filters, My LUTs | `tools/FilterTool.kt`, `FilterTiles.kt` |
| T-07 – T-09 | Adjust, HSL, Curves | `tools/AdjustTool.kt`, `HslPanel.kt`, `CurvesPanel.kt`, `CurveEditor.kt` |
| T-10 / T-11 | Ratio, Cover | `tools/RatioTool.kt`, `CoverTool.kt` |
| T-12 – T-15 | Edit/crop, Canvas, Opacity, Transform | Phase 8B |
| T-03, T-16 – T-19 | Speed curves, transitions, animations, freeze, reverse | Phase 8C |
| T-20 | Rotate (toast) | `EditorViewModel.onRotate` |
| X-01 – X-03 | Text panel, text on preview, colour picker | `tools/TextTool.kt`, `TextStyleRow.kt`, `preview/TextTransformOverlay.kt`, `tools/ColorPickerSheet.kt` |
| X-04 – X-06 | Text styles, rich text, TTS | Phase 10 |
| U-01 – U-07 | Audio menu, clip toolbar, voice, beats, voiceover, mic permission, speed | `tools/AudioToolbars.kt`, `VoiceEffectTool.kt`, `BeatsTool.kt`, `VoiceoverTool.kt`, `AudioPanels.kt`, `timeline/AudioRows.kt` |
| L-01 / L-02 | Layer selected, panels from a layer | `tools/LayerToolbar.kt`, `LookPanels.kt`, `timeline/LookLayerRows.kt` |
| P-01 – P-06 | Overlays, masks, chroma key, keyframes, overlapping transitions | Phase 9 |
| S-01 – S-04 | Stickers, draw, effects | Phases 10–11 |
| EX-01, EX-03, EX-05 | Export settings, notification, failures | Phase 12 (EX-02/EX-04 progress and done: `ui/export/ExportContent.kt`) |
