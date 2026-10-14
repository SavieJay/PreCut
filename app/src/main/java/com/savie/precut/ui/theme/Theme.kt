package com.savie.precut.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * PreCut is dark-only so footage stands out; no dynamic colour, to keep the brand consistent.
 * `primary` is the bright Accent (text buttons, sliders, progress, selection); filled surfaces
 * with white labels (Export, FAB, switches) use [AccentFill] explicitly through PreCut components.
 */
private val PreCutColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainer,
    onPrimaryContainer = OnAccentContainer,
    secondary = TextPrimary,
    onSecondary = Ink0,
    secondaryContainer = Ink4,
    onSecondaryContainer = TextPrimary,
    tertiary = Accent,
    background = Ink0,
    onBackground = TextPrimary,
    surface = Ink0,
    onSurface = TextPrimary,
    surfaceVariant = Ink2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink0,
    surfaceContainerLow = Ink1,
    surfaceContainer = Ink1,
    surfaceContainerHigh = Ink2,
    surfaceContainerHighest = Ink3,
    inverseSurface = InverseSurface,
    inverseOnSurface = Ink0,
    inversePrimary = InverseAccent,
    outline = Outline,
    outlineVariant = Ink3,
    error = ErrorRed,
    onError = Ink0,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    scrim = Color.Black,
)

/** DESIGN.md radii: 8 small surfaces/snackbar, 12 menus/cards, 16 sheets/FAB, 28 dialogs. */
private val PreCutShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PreCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PreCutColors,
        typography = PreCutTypography,
        shapes = PreCutShapes,
        content = content,
    )
}
