# PreCut design system

Dark-only, CapCut/InShot-like: near-black chrome so footage stands out, one hot accent, white for
"what's active" on the timeline. Tokens live in `ui/theme/Color.kt`; if real designs arrive (e.g.
from Sleek), update the tokens there and this file together.

## Colour tokens
| Token | Hex | Use |
|---|---|---|
| `Ink0` | `#0E0E10` | App background, top bar, timeline background, window background |
| `Ink1` | `#17171A` | Surfaces: toolbar, tool panels (`surfaceContainer`) |
| `Ink2` | `#222226` | Raised surfaces, chips (`surfaceContainerHigh`, `surfaceVariant`) |
| `Ink3` | `#2E2E33` | Outlines, disabled fills |
| `TextPrimary` | `#F2F2F5` | Primary text and icons; "add media" button fill |
| `TextSecondary` | `#9A9AA3` | Secondary text, total duration, ruler labels |
| `Accent` | `#FF4D6D` | Primary actions: Export button, New project FAB, sliders |
| `AccentContainer` | `#5C1426` | Tonal accent backgrounds |
| `Playhead` | `#FFFFFF` | Centre playhead line and knob |
| `ClipSelection` | `#FFFFFF` | Selected clip border and trim handles |
| `ClipBackground` | `#34343A` | Clip tile placeholder while thumbnails load |
| `RulerTick` | `#6B6B74` | Ruler ticks |

No dynamic colour: the brand look is the same on every device.

## Type
Material 3 defaults with semibold titles; `labelMedium` 11sp for tool labels and timecodes,
`labelSmall` 10sp for ruler labels.

## Shape and spacing
- Clips and reorder tiles: 6dp corners; trim handles 14dp wide, 4dp corners, with a dark 2dp grip.
- Buttons: Material defaults; the "add media" button is a 40dp white square with 8dp corners.
- Screen gutters 16dp; panel padding 8–16dp.

## Editor layout (top to bottom)
1. Top bar (Ink0): back · accent **Export** pill. Undo/redo join it in Phase 6.
2. Preview, black, letterboxed; takes the remaining height. Empty project shows a "Tap + to add" hint.
3. Playback row, 48dp: `current / total` time at the left, play/pause centred. The timeline is the scrubber.
4. Timeline, 104dp: ruler (18dp) + clip strip (56dp clips, thumbnails at 3:4) under a fixed white
   centre playhead; fixed white "+" button at the right edge. Text and audio lanes are added under the
   clip strip in Phases 4–5.
5. Bottom tool row (Ink1): icon + label buttons, 64dp wide each — Split, Trim, Duplicate, Delete
   (Speed, Filters, Text, Audio, Rotate join in Phases 4–5). A tool panel replaces the row while open.

## Interaction
- Drag the timeline to scrub (pauses playback); playback auto-scrolls it.
- Pinch to zoom (6–480 dp per second, lowered automatically for very long projects).
- Tap a clip to select (white border + handles); tap empty space to deselect.
- Drag a handle to trim; applied on release.
- Long-press a clip to reorder: clips collapse into equal tiles, drag, release.
- Clip tools act on the selected clip, or the clip under the playhead when none is selected.
