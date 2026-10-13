# PreCut: complete mobile screen designs (Android video editor)

## 1. What you're designing
PreCut is a native **Android video editor**, similar in scope to **CapCut** (including CapCut Pro tools) and InShot. It runs entirely on the phone: no AI features, no online libraries, no accounts. People import their own videos and photos, edit them on a multi-track timeline, and export an MP4 to the gallery.

Design **every screen, panel, sheet, dialog and state** listed in section 6, plus a component library (section 7). The result will be built by hand in **Jetpack Compose with Material 3**, so designs must be buildable with standard Compose layouts. No effects that need custom rendering engines.

**Audience:** everyday creators making short social videos (TikTok, Reels, Shorts) who expect CapCut-level power with less clutter.

**Tone:** professional, calm and fast. The footage is the hero; the interface recedes.

## 2. Platform and format
- **Android phone, portrait.** Design at **412 × 915 dp** (Pixel 8). Check the main editor, toolbar and panels also fit **360 × 800 dp**: no clipping, toolbars scroll horizontally.
- **Full-screen preview** is the only screen that also needs a **landscape** frame.
- Use **edge-to-edge**: content draws behind transparent status and navigation bars, with insets respected. Status bar icons are light.
- **Dark only.** There is no light theme and no dynamic (wallpaper) colour.
- Touch targets are at least **48 dp**; icon buttons are 48 dp with 24 dp icons.
- Units: **dp** for sizes and spacing, **sp** for type. Name colours by the tokens in section 3 (and propose new tokens if needed).
- Use **realistic content**: video thumbnails of people, travel, food and pets; real-looking times such as 0:07 / 0:42; filter names such as "Teal & Orange". No lorem ipsum.

## 3. Brand and design system (keep; refine if you can justify it)
The app already uses this system, so evolve it rather than replacing it. If you change a token, say so explicitly and give the new hex.

**Colour tokens:**

| Token | Hex | Use |
|---|---|---|
| Ink0 | #0E0E10 | App background, top bar, timeline background |
| Ink1 | #17171A | Surfaces: bottom toolbar, tool panels |
| Ink2 | #222226 | Raised surfaces, chips, timeline track |
| Ink3 | #2E2E33 | Outlines, dividers, disabled fills |
| TextPrimary | #F2F2F5 | Primary text and icons |
| TextSecondary | #9A9AA3 | Secondary text, timecodes, ruler labels |
| Accent | #FF4D6D | Primary actions (Export, New project), slider active tracks, selected chips/tiles |
| AccentContainer | #5C1426 | Tonal accent backgrounds |
| OnAccent | #FFFFFF | Text/icons on Accent |
| ErrorRed | #FF6B5E | Errors, missing media |
| Playhead / ClipSelection | #FFFFFF | Centre playhead; selected-clip border and trim handles |
| ClipBackground | #34343A | Clip placeholder while thumbnails load |
| RulerTick | #6B6B74 | Ruler ticks |
| TextLaneBar | #B8860B | Text bars (amber) |
| MusicBar | #16897A | Music bars (teal) |
| VoiceoverBar | #7B5BD6 | Voiceover bars (violet) |
| ExtractedAudioBar | #2F6FB8 | Extracted-audio bars (blue) |
| LookLayerBar | #B0457A | Filter/Adjust layer bars (plum) |

New lanes coming later need their own distinguishable colours: overlay/PiP, stickers, effects, and transition markers. Propose them, keeping enough contrast against Ink0 and from each other, including for colour-blind users.

**Type:** Material 3 type scale (Roboto or Google Sans–like) with semibold titles. Tool labels and timecodes use 11 sp (`labelMedium`); ruler labels and badges use 10 sp (`labelSmall`). Timecodes use tabular figures.

**Shape:**
- clips: 6 dp corners
- timeline bars: 4 dp
- chips: full pill
- tool tiles: 8 dp
- sheets: 16 dp top corners
- the "add media" button is a 40 dp white square with 8 dp corners

**Spacing:** 16 dp screen gutters; 8–16 dp panel padding; 4 dp grid.

**Icons:** Material Symbols, rounded or outlined. Pick one family and use it everywhere. Section 7 lists the icons needed.

## 4. Editor anatomy (the core screen; keep this structure)
From top to bottom:
1. **Top bar** (Ink0, 56 dp): Back · project name (tap to rename) · Undo · Redo · **Export** (accent pill). Undo and Redo appear dimmed when unavailable.
2. **Preview** (black; takes all remaining height). The video is letterboxed to the project aspect ratio. Things that can appear over it:
   - the selected text's transform box (dashed outline, plus pinch and rotate)
   - snap guide lines (thin mint, #38D9A9) at the centre lines
   - a small "Optimizing preview 40%" chip at the top-left while preview copies are made
   - later: transform handles for clips, overlays, crop and masks
3. **Playback row** (48 dp): `0:07 / 0:42` at the left (current time in TextPrimary, total in TextSecondary), Play/Pause centred, Full screen at the right.
4. **Timeline:** a horizontally scrolling multi-track timeline under a **fixed white centre playhead** (a 2 dp line with a small knob at the top). Dragging the timeline scrubs, and pinch zooms down to single frames. Lanes, top to bottom:
   - **Ruler** (18 dp): labelled seconds (0:00, 0:02…), and frame labels ("5f", "10f") at maximum zoom.
   - **Main clip strip** (56 dp): thumbnail tiles at 3:4. Each clip has 6 dp corners and a 2 dp gap.
     - A selected clip has a white 2 dp border, two white **trim handles** (14 dp wide with a dark grip line) and a duration badge ("3.2s") at the top-left.
     - Small badges at the top-left show speed ("2x") and muted (speaker-off).
     - A **missing-media** clip is tinted ErrorRed with a "!".
     - Pinned outside the scroll: a round **"Mute clip audio"** button at the far left of the strip, and a white **"+" add-media** button at the far right.
     - Phase 8C adds a small **transition button** (a square diamond icon) centred on each boundary between clips.
   - **Filter/Adjust layer rows** (22 dp each): only when layers exist, in LookLayerBar.
   - **Text rows** (22 dp each): amber bars with the text as the label. Overlapping texts stack into extra rows.
   - **Audio rows** (24 dp each): Music, Voiceover or Extracted bars, with a translucent white waveform and yellow **beat dots**. With no audio, one row shows "+ Add audio".
   - All bars: the label is left-aligned. Selected bars get a white border and start/end handles. While a bar is dragged, its label shows the precise time ("0:03.4").
   - Later lanes: **Overlay/PiP** rows (thumbnail bars, above the main strip, as in CapCut), **Sticker** rows, **Effect** rows, and **keyframe diamonds** on selected items.
5. **Bottom area** (Ink1): either a **toolbar** (a horizontally scrolling row of 64 dp-wide icon-over-label buttons) or a **tool panel**, which replaces the toolbar while open. Panel header: title at the left, optional actions (Reset, Apply to all, icons), then **Done** at the right.

## 5. Interaction rules (show them where they matter)
- **Gestures:**
  - Tap a clip to select it; tap empty space to deselect.
  - **Long-press** a clip to reorder: all clips collapse into equal square tiles in a row. Drag, and a haptic tick marks each slot change.
  - Drag a selected item's handles to trim. Edges **snap** to the playhead, clip edges and beats, with a haptic tick.
  - Dragging near the screen edge auto-scrolls the timeline.
  - Sliders apply on release; chips and tiles apply on tap.
- **Toolbar context:**
  - **Clip tools** act on the selected clip, or on the clip under the playhead when nothing is selected.
  - **Filters/Adjust with nothing selected** add a 3-second layer at the playhead.
- **Multi-select:** the **Select** tool lets you tap several clips, texts and audio clips (each gets a check badge). The bottom bar becomes the selection toolbar.
- **Errors and confirmations:** errors show as snackbars above the toolbar. Destructive actions on projects ask for confirmation; editor actions don't, because Undo exists.

## 6. Screens to design (use these IDs as frame names)

### A. App level
- **A-01 App icon:** adaptive Android icon (foreground plus background layers, plus a monochrome themed-icon version). The concept is "pre-cut": a clip being trimmed or scissors over a film frame, in Accent on Ink0.
- **A-02 Splash screen:** Android 12+ splash, with the icon on Ink0.
- **A-03 Notification-permission explainer** (Android 13+): shown before the first background export. "Get notified when your export finishes."

### H. Projects (Home)
- **H-01 Projects, empty:** app name in the top bar, a camera icon button, an empty-state illustration with the text "Start your first video", and a large accent **New project** button.
- **H-02 Projects, drafts grid:**
  - a 2-column grid of draft cards
  - each card shows: cover image at 9:16 or 1:1, a duration badge ("0:42"), the name (or "Project Oct 13, 14:05" as a fallback), and "Edited 5 min ago"
  - each card has a ⋮ overflow menu
  - an accent "+ New project" extended FAB at the bottom
- **H-03 Card overflow menu:** Rename, Duplicate, Delete.
- **H-04 Rename dialog:** a text field.
- **H-05 Delete confirmation:** "Delete 'Trip to Lagos'? This can't be undone." Delete is shown in ErrorRed.
- **H-06 Camera menu:** a dropdown from the top bar with Record video and Take photo.
- **H-07 Importing:** a full-screen progress state shown while picked or shared media is prepared ("Preparing 3 videos…").
- **H-08 Opened via Share to PreCut:** the same importing state, with the source shown ("From Photos").
- **H-09 Settings** (from a gear icon in the top bar):
  - default photo duration
  - default aspect ratio for new projects
  - default export settings
  - storage: preview cache (1.2 GB) with Clear, imported LUTs
  - about and licences

Note: media picking uses Android's **system Photo Picker**, so don't design a custom gallery. Design only the **H-06 / E-02 entry points**.

### E. Editor: base states
- **E-01 Editor, empty project:** a black preview with "Tap + to add media", a disabled Export button, and the timeline showing only the + button.
- **E-02 Add-media menu:** from the timeline "+": Photos & videos, Record video, Take photo.
- **E-03 Editor, default:** three clips, one text, two music clips on two rows, nothing selected, main toolbar visible. The main toolbar holds Split, Trim, Audio, Speed, Volume, Fade, Extract, Filters, Adjust, Rotate, Replace, Text, Ratio, Cover, Select, Duplicate and Delete; show the first 6 or 7 and a scroll affordance.
- **E-04 Clip selected:** handles, the duration badge and the toolbar (same as the main toolbar). For 8B+, the toolbar adds: Edit (crop, mirror, rotate), Canvas, Opacity, Animation, Freeze, Reverse, Mask, Chroma key and Keyframe.
- **E-05 Reordering:** long-press state with collapsed equal tiles; one tile is lifted and follows the finger, with a slot indicator.
- **E-06 Missing media:** a red banner under the top bar ("2 files can't be found", with Remove), and red-tinted clips. Export is disabled.
- **E-07 Multi-select:** check badges on the picked items. The selection toolbar shows Done, "3 selected", Delete, Duplicate, Speed, Filters and Volume.
- **E-08 Optimizing preview:** the chip on the preview, then the same preview without it.
- **E-09 Full-screen preview:** portrait and landscape, with controls shown (close, scrub bar, play/pause, time) and hidden.
- **E-10 Rename project:** tap the title in the top bar.

### T. Editor: clip tool panels
- **T-01 Trim:** a range slider over the clip, with the duration in the title ("Trim · 4.2s").
- **T-02 Speed:**
  - Normal | Curve tabs (Curve arrives in 8C)
  - preset chips: 0.1x, 0.5x, 1x, 2x, 5x, 10x, 100x
  - a logarithmic slider with tick labels
  - "3.2s → 1.6s"
  - a Keep pitch switch
- **T-03 Speed curves (8C):**
  - preset tiles: Custom, Montage, Hero, Bullet, Jump cut, Flash in, Flash out
  - a curve editor graph: speed on the y axis with a 1x line marked, time on the x axis, and draggable beat points
  - the resulting duration
- **T-04 Volume:** 0–200% slider with a value label, a Mute switch, and Apply to all.
- **T-05 Fade:** Fade in and Fade out sliders in seconds.
- **T-06 Filters:**
  - category chips: Basic, Life, Film, Retro, Mono, Food, Nature, My LUTs
  - a row of 60 dp tiles showing the clip's frame graded by each filter, with the name underneath; the first tile is "None"
  - the selected tile has an accent border
  - an Intensity slider with its value
  - Apply to all
  - My LUTs shows an "Import .cube" tile with a + icon
- **T-07 Adjust:**
  - a row of chips: Brightness, Contrast, Saturation, Exposure, Sharpen, Highlights, Shadows, Temperature, Tint, Hue, Fade, Vignette, Grain, then HSL and Curves
  - a changed chip shows its value ("Exposure 40")
  - one slider for the selected chip, with 0 marked and the value shown
  - Reset and Apply to all
- **T-08 HSL:** 8 colour dots (Red, Orange, Yellow, Green, Cyan, Blue, Purple, Magenta) and Hue, Saturation and Lightness sliders (−100 to 100).
- **T-09 Curves:** RGB, R, G and B chips; a square curve editor with a grid, diagonal reference, curve and point handles; a hint line ("Drag points · tap to add · long-press to remove"); and Reset.
- **T-10 Ratio:** a scrolling row of chips, each with a shape glyph: Original, 9:16, 16:9, 1:1, 4:5, 4:3, 3:4, 2:1, 2.35:1, 1.85:1, 9:19.5.
- **T-11 Cover:**
  - "Cover: frame at 0:03.2"
  - "Use frame 0:05.0" and "From gallery" buttons
  - Reset
  - an "Add cover as the video's first frame" switch
- **T-12 Edit (8B):**
  - Crop, Mirror and Rotate as sub-buttons
  - a **crop mode** on the preview: dimmed outside, a rule-of-thirds grid, corner handles, and ratio chips (Free, Original, 9:16, 16:9, 1:1, 4:5)
- **T-13 Canvas (8B):**
  - Colour | Blur | Image tabs
  - swatches (including a custom-colour swatch)
  - 4 blur strength tiles
  - image tiles plus "From gallery"
  - Apply to all
- **T-14 Opacity (8B):** a 0–100% slider.
- **T-15 Transform on preview (8B):** the selected clip shows a bounding box. Drag moves it, pinch scales it, two fingers rotate it. A rotation readout ("15°") appears, centre guides snap, and Fit | Fill chips sit in the panel.
- **T-16 Transitions (8C):**
  - opened from the diamond between two clips
  - tiles: None, Fade black, Fade white, Flash, Zoom in, Zoom out, Blur, Glitch, Spin, then in Phase 9 also Dissolve, Slide, Push and Wipe
  - a duration slider (0.1–2.0s)
  - Apply to all
- **T-17 Animations (8C):** In | Out | Combo tabs; tiles for Fade, Zoom, Slide (4 directions), Spin, Bounce and Shake; a duration slider.
- **T-18 Freeze frame (8C):** an inline action plus a snackbar ("Freeze frame added · 3s").
- **T-19 Reverse (8C):** a progress dialog ("Reversing clip… 45%", with Cancel) and the done state.
- **T-20 Rotate:** an instant 90° action (no panel); show the toast or animation.

### X. Text
- **X-01 Text panel:**
  - a text field with up to 3 lines
  - Size and Opacity sliders
  - a colour row: a custom rainbow swatch plus 8 swatches
  - a Background switch
  - header icons: Split, Duplicate, Delete
- **X-02 Text on preview:** a dashed selection box around the text, a pinch and rotate gesture hint, and the centre snap guides shown.
- **X-03 Colour picker sheet:** a saturation/brightness square, a hue bar, a Hex field, a preview swatch, a "Used in this project" row, and Done.
- **X-04 Text styles (Phase 10):** tabs for Font, Style, Effects, Animation, Templates.
  - **Font:** a list of fonts, plus "Import font".
  - **Style:** bold, italic, underline, alignment, letter spacing, line spacing.
  - **Effects:**
    - stroke (colour and width)
    - shadow (colour, blur, distance, angle)
    - glow
    - background (colour, opacity, corner radius)
    - curved text (an amount slider)
  - **Animation:** In, Out and Loop tabs, with tiles for Typewriter, Fade, Slide, Pop, Bounce and Wave, plus a duration.
  - **Templates:** a preset grid.
- **X-05 Rich text (Phase 10):** select part of the text and give it its own colour or size.
- **X-06 Text-to-speech (Phase 10):** voice list (device voices), speed, pitch, "Add as voiceover".

### U. Audio
- **U-01 Audio menu:** a sub-toolbar with Back, Music, Extract, Record.
- **U-02 Audio clip selected:** the toolbar shows Volume, Fade, Split, Speed, Voice, Beats, Replace, Duplicate, Delete.
- **U-03 Voice effects:** tiles for None, Chipmunk, High, Deep, Monster.
- **U-04 Beats:** an "Add beat / Remove beat" button at the playhead, the beat count, Clear, and a hint.
- **U-05 Voiceover recording:**
  - a big round record button that becomes a stop button
  - an elapsed timer ("0:04.2")
  - "Recording starts at 0:03"
  - a preview muted indicator
- **U-06 Microphone permission rationale.**
- **U-07 Audio speed:** like T-02, with 0.25–4x and no pitch switch.

### L. Filter/Adjust layers
- **L-01 Layer selected:** a plum bar with handles; the toolbar shows Filter, Adjust, Split, Duplicate, Delete.
- **L-02 Filters and Adjust opened from a layer:** the same panels as T-06 and T-07, without Apply to all.

### P. Overlays, masks, keyframes (Phase 9)
- **P-01 Overlay (picture-in-picture):** "Overlay" in the main toolbar, then the media picker. The overlay sits in a row above the main strip, and the preview shows the PiP with transform handles.
- **P-02 Overlay selected:** the toolbar shows Animation, Opacity, Mask, Chroma key, Filters, Adjust, Switch to main track, Split, Duplicate, Delete.
- **P-03 Mask:**
  - shape tiles: None, Linear, Mirror, Circle, Rectangle, Heart, Star
  - preview handles for position, size, rotation and feather
  - Invert and a Feather slider
- **P-04 Chroma key:** an eyedropper on the preview with a loupe, the picked colour swatch, Intensity and Shadow sliders.
- **P-05 Keyframes:**
  - a keyframe (diamond) button by the playhead (add or remove a keyframe here)
  - diamonds on the selected item's bar
  - an easing panel: Linear, Ease in, Ease out, Ease in-out, and a Custom bezier graph with two handles
- **P-06 Overlapping transition (Dissolve, Slide):** the two clips overlap on the timeline with a hatched overlap region.

### S. Stickers, drawing, effects (Phases 10–11)
- **S-01 Stickers sheet:** Emoji | My images tabs, a search field for emoji, and a grid. A sticker then appears on the preview with handles, and in a sticker lane.
- **S-02 Draw:** a full-preview canvas with a brush-size slider, colours, eraser, undo, Done; the result becomes a sticker.
- **S-03 Effects:**
  - category chips: Basic, Retro, Glitch, Light, Lens, Split
  - animated tiles, for example Shake, Zoom lens, Glitch, RGB split, VHS, Film grain, Light leak, Flash, Strobe, Blur, Pixelate, Mirror, Kaleidoscope, Split ×4, Fisheye, Bokeh, Neon
  - Intensity and Speed sliders
  - an effect lane bar
- **S-04 Effect applied to a clip vs a time range:** a choice sheet ("Apply to: This clip / Everything (new layer)").

### X2. Export (Phase 12)
- **EX-01 Export settings sheet** (opened from Export):
  - resolution segmented control: 480p, 720p, 1080p, 2K, 4K (unsupported ones disabled with "Not supported on this device")
  - frame-rate control: 24, 25, 30, 50, 60
  - bitrate: Lower, Recommended, Higher
  - codec: H.264 | HEVC
  - an HDR switch, where supported
  - "Estimated size: 86 MB"
  - an "Export video" button, and secondary actions: Export audio only, Save current frame
- **EX-02 Exporting:** a big circular progress with a percentage, the cover thumbnail, "Keep PreCut open or continue in the background", and Cancel.
- **EX-03 Export notification:** an Android notification with progress and Cancel, then "Video saved" with Open and Share.
- **EX-04 Done:** a playable result thumbnail, "Saved to Movies/PreCut", Open, Share (and share targets row), "Back to edit".
- **EX-05 Failed:** the reason ("Your phone couldn't encode 4K"), "Try 1080p" (primary) and Retry; also a variant for "Not enough storage".

## 7. Component library (one sheet)
Show each component in all of its states: default, pressed, selected, disabled, and error where relevant.

- **Buttons:**
  - tool button (icon over label, 64 dp)
  - pill accent button (Export)
  - extended FAB
  - text button
  - outlined button
  - icon button
  - the white "+" square
  - round Mute clip audio button
- **Chips:**
  - filter chip
  - ratio chip with glyph
  - adjust chip with value
- **Tiles:**
  - filter tile (60 dp, with label)
  - icon tile ("Import .cube")
  - preset tile (transitions/animations, with a small motion hint)
- **Sliders:**
  - value slider with a zero mark
  - range slider (Trim)
  - logarithmic speed slider with ticks
- **Toggles:** switch rows with a label.
- **Panel:** the tool panel shell (header with title, actions, Done) in 1-row, 2-row and tall variants.
- **Sheets and dialogs:**
  - bottom sheet
  - dialog
  - snackbar
  - banner (error)
  - dropdown menu
- **Timeline parts:**
  - ruler (seconds and frame modes)
  - playhead
  - clip (normal, selected, multi-selected, missing, with badges)
  - trim handle
  - every bar type with label, waveform and beat dots
  - transition diamond
  - keyframe diamond
- **Badges:** duration, speed, muted, "Optimizing preview".
- **Empty states and loading:** the empty projects illustration, a spinner, a progress ring.
- **Icon set:**
  - editing: split, trim, audio, speed, volume, fade, extract, filters, adjust, rotate, replace, text, ratio, cover
  - selection and edit tools: select, duplicate, delete, crop, mirror, canvas, opacity, animation, transition, freeze, reverse, mask, chroma key, keyframe
  - overlays and creative: overlay, sticker, effect, draw, voice, beats, mic
  - media: music, camera, videocam, gallery
  - playback and app: full screen, exit full screen, undo, redo, play, pause, add, more, settings, share, open, import

## 8. Deliverables I need back
1. One frame per ID in section 6, named exactly by its ID and title (e.g. "T-06 Filters"). Where a state matters (empty, selected, error, loading), show it as an extra frame with a suffix (e.g. "T-06b Filters – My LUTs").
2. The component sheet from section 7.
3. A **design-tokens table**: final colours (hex), type scale (sp, weight, line height), corner radii, spacing, elevation, and the new lane colours.
4. **Redlines** on E-03, T-06, T-07 and EX-01: dp sizes, paddings, gaps, and the token names used.
5. Short **interaction notes** on frames whose behaviour isn't obvious: gestures, what applies on release vs tap, and snapping.
6. Keep it **buildable in Compose and Material 3**:
   - no blur-behind glass effects on video
   - no custom fonts beyond one open-licence family (state which)
   - no layouts that need absolute positioning outside the timeline and preview overlays
