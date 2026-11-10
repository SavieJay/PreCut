package com.savie.precut.ui.onboarding

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Stand-in footage for onboarding (the design canvas's scenes): no media exists on a first launch,
 * so clips are drawn as the colours of a beach, a city at night, a park and so on. Equal stop
 * positions make the hard horizons.
 */
internal enum class ClipScene(val brush: Brush) {
    BEACH(
        Brush.verticalGradient(
            0f to Color(0xFFF6B26B), 0.30f to Color(0xFFF08A5D), 0.46f to Color(0xFFC8576B), 0.46f to Color(0xFF2B6F8F),
            0.66f to Color(0xFF1D4E6B), 0.66f to Color(0xFFD9B98C), 1f to Color(0xFFC9A274),
        ),
    ),
    CITY(
        Brush.verticalGradient(
            0f to Color(0xFF0B1A3A), 0.42f to Color(0xFF25386B), 0.62f to Color(0xFF6A3F66), 0.62f to Color(0xFF14121C), 1f to Color(0xFF221E2C),
        ),
    ),
    PARK(Brush.verticalGradient(0f to Color(0xFFA9CDEB), 0.42f to Color(0xFFA9CDEB), 0.42f to Color(0xFF6FA34A), 1f to Color(0xFF6FA34A))),
    FOOD(Brush.linearGradient(0f to Color(0xFF6B4226), 0.5f to Color(0xFF8A5A35), 1f to Color(0xFF5A3620))),
    HIKE(Brush.verticalGradient(0f to Color(0xFFBCD7EF), 0.6f to Color(0xFFF1E3C8), 0.6f to Color(0xFF3B5640), 1f to Color(0xFF3B5640))),
    PORTRAIT(Brush.linearGradient(0f to Color(0xFFE9D8B4), 1f to Color(0xFFC7AE82))),
    CAFE(Brush.linearGradient(0f to Color(0xFF2E2A27), 1f to Color(0xFF4A403A))),
    MARKET(
        Brush.horizontalGradient(
            0f to Color(0xFFE6A03B), 0.33f to Color(0xFFE6A03B), 0.33f to Color(0xFFC2452F),
            0.66f to Color(0xFFC2452F), 0.66f to Color(0xFF2F7D6D), 1f to Color(0xFF2F7D6D),
        ),
    ),
    GREEN(Brush.verticalGradient(0f to Color(0xFF19C24A), 1f to Color(0xFF12A83E))),
}
