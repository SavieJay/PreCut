package com.savie.precut.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens: see docs/design/DESIGN.md (from the Claude Design canvas).

// Surfaces and text
val Ink0 = Color(0xFF0E0E10) // app background, top bar, timeline
val Ink1 = Color(0xFF17171A) // toolbar, panels, sheets
val Ink2 = Color(0xFF222226) // chips, tiles, dialogs, menus
val Ink3 = Color(0xFF2E2E33) // dividers, disabled fills, slider track, Done button
val Ink4 = Color(0xFF3A3A40) // pressed chip, selected tab
val Outline = Color(0xFF5A5A62) // outlined buttons, switch border, idle field
val TextPrimary = Color(0xFFF2F2F5)
val TextSecondary = Color(0xFF9A9AA3)
val TextDisabled = Color(0x61F2F2F5) // TextPrimary at 38%
val InverseSurface = Color(0xFFE6E6EA) // snackbar, toast
val Scrim = Color(0x99000000) // 60%

// Accent and status
val Accent = Color(0xFFFF4D6D) // slider fill, selection, accent text
val AccentFill = Color(0xFFD92B50) // filled buttons with white labels (white on Accent fails contrast)
val AccentFillPressed = Color(0xFFC21F44)
val AccentContainer = Color(0xFF5C1426) // selected chip/tile/segment fill
val OnAccentContainer = Color(0xFFFFD9DF)
val OnAccent = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFFF6B5E)
val ErrorContainer = Color(0xFF4A1712)
val OnErrorContainer = Color(0xFFFFDAD5)
val InverseAccent = Color(0xFFB3123A) // snackbar action
val SnapGuide = Color(0xFF38D9A9)

// Timeline
val Playhead = Color(0xFFFFFFFF)
val ClipSelection = Color(0xFFFFFFFF)
val ClipBackground = Color(0xFF34343A)
val RulerTick = Color(0xFF6B6B74)
val BeatDot = Color(0xFFFFD43B)
val TextLaneBar = Color(0xFFB8860B) // amber, Ink0 label
val MusicBar = Color(0xFF137D6F) // teal, white label
val VoiceoverBar = Color(0xFF7B5BD6) // violet, white label
val ExtractedAudioBar = Color(0xFF2F6FB8) // blue, white label
val LookLayerBar = Color(0xFFB0457A) // plum, white label
val OverlayBar = Color(0xFF8A93A6) // slate, Ink0 label (Phase 9)
val StickerBar = Color(0xFFE07A2E) // orange, Ink0 label (Phase 10)
val EffectBar = Color(0xFFA3C93A) // lime, Ink0 label (Phase 11)
val TransitionMarker = Color(0xFFE6E6EA) // (8C)
