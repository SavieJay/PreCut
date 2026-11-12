package com.savie.precut.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens: "Afterglow", the Redesign 2026 page of the Claude Design canvas (RD-TOK, RD-CMP).
// Names from the first design are kept so every screen picks up the new values; the 2026 names
// are noted beside them. Contrast ratios are on Base 0 unless said; ColorContrastTest checks them.

// Surfaces: Base 0–3
val Ink0 = Color(0xFF0B0B0F) // Base 0: screens, top bar, timeline
val Ink1 = Color(0xFF121218) // Base 1: sunk areas, toolbar, panels without glass
val Ink2 = Color(0xFF1A1A22) // Base 2: chips, tiles, dialogs, menus; glass fallback
val Ink3 = Color(0xFF24242E) // Base 3: dividers, disabled fills, slider track
val Ink4 = Color(0xFF2E2E3A) // pressed chip, selected tab
val Outline = Color(0xFF5A5A66) // outlined buttons, switch border, idle field
val Scrim = Color(0x8C000000) // 55%, behind sheets and dialogs

// Text
val TextPrimary = Color(0xFFF4F4F7) // titles, values · 17.9 : 1
val TextSecondary = Color(0xFFB8B8C4) // body, tool labels · 10.0 : 1
val TextMuted = Color(0xFF8A8A97) // small labels, captions · 5.77 : 1
val TextFaint = Color(0xFF6E6E7A) // ticks and disabled icons only, never text · 3.9 : 1
val TextDisabled = Color(0x61F4F4F7) // TextPrimary at 38%
val InverseSurface = Color(0xFFE6E6EA) // light toast
val InverseAccent = Color(0xFFB3123A) // pink text on InverseSurface

// PreCut pink: the one main action per screen, selection, sliders, progress
val Accent = Color(0xFFFF4D6D)
val AccentFill = Accent // main pills are pink with dark labels (OnAccent 6.1 : 1)
val AccentFillPressed = Color(0xFFE8365A)
val AccentContainer = Color(0xFF3A0F1C) // selected chip/tile/segment fill
val OnAccentContainer = Color(0xFFFFD9DF)
val SelectedSurface = Color(0xFF1F1418) // a picked card: Base 1 tinted toward pink
val OnAccent = Color(0xFF0B0B0F)

// Lime: AI only (chips, tags, glows, generated regions). Never a main action.
val AiAccent = Color(0xFFC4F04A) // 14.9 : 1
val AiContainer = Color(0xFF1C2509)
val OnAiAccent = Color(0xFF0B0B0F)

// Status
val Success = Color(0xFF38D9A9) // "included" ticks
val ErrorRed = Color(0xFFFF6B5E) // 7.0 : 1; also the destructive main action
val ErrorContainer = Color(0xFF4A1712)
val OnErrorContainer = Color(0xFFFFDAD5)
val SnapGuide = Color(0xFF38D9A9)

// Glass (Glass.Panel and Glass.Raised); never over the video preview or the full-screen player.
val GlassFill = Color(0x9E14141B) // rgba(20,20,27,.62)
val GlassRaisedFill = Color(0xBD1C1C25) // rgba(28,28,37,.74)
val GlassSolid = Color(0xF516161D) // #16161D at 96%: where nothing shows through
val GlassLine = Color(0x1FFFFFFF) // white 12%, 1 dp
val GlassHighlight = Color(0x29FFFFFF) // white 16%, the 1 dp inner top line
val GlassSheenTop = Color(0x1CFFFFFF) // white 11%
val GlassSheenBottom = Color(0x05FFFFFF) // white 2%
val GlassShadow = Color(0x73000000) // black 45%
val RowFill = Color(0x0FFFFFFF) // white 6%: rows and quiet icon buttons on glass
val RowLine = Color(0x1AFFFFFF) // white 10%

// On the video: solid, no blur
val OnVideoChip = Color(0xC70B0B0F) // Base 0 at 78%
val BadgeInk = Color(0xD10B0B0F) // Base 0 at 82%
val PreviewScrimTop = Color(0x8C000000)
val PreviewScrimBottom = Color(0xB3000000)
val PreviewButton = Color(0x990B0B0F)
val CoachDim = Color(0xBD08080A) // 74%, around a tip's cut-out
val SwitchThumbOff = Color(0xFF8A8A97)

// Timeline
val Playhead = Color(0xFFFFFFFF)
val PlayheadHalo = Color(0x590B0B0F)
val ClipSelection = Accent // selected clip outline and its trim handles
val ClipBackground = Color(0xFF24242E)
val RulerTick = Color(0xFF6E6E7A)
val BeatDot = Color(0xFFFFD43B)
val TransitionMarker = Color(0xFFE6E6EA)

// Track colours: every bar takes a dark label (TrackLabel), all at least 6 : 1.
val VideoBar = Color(0xFFFF4D6D)
val MusicBar = Color(0xFF3DD9C1)
val VoiceoverBar = Color(0xFF7CC4FF)
val ExtractedAudioBar = Color(0xFF9AE6DA) // music's family, lighter
val TextLaneBar = Color(0xFFFFC14D)
val OverlayBar = Color(0xFF8EA2FF)
val StickerBar = Color(0xFFFF9157)
val EffectBar = Color(0xFFD6A8FF) // the design's lime is kept for AI (PLAN.md, 2026-11-12)
val LookLayerBar = Color(0xFFF7A1C4)
val TrackLabel = Color(0xFF0B0B0F)
