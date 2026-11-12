package com.savie.precut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * PreCut is dark-only so footage stands out; no dynamic colour, to keep the brand consistent.
 * `primary` is PreCut pink (main actions, selection, sliders, progress) with dark labels; lime
 * ([AiAccent]) marks AI and is used explicitly through PreCut components, never as a Material role.
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

@Composable
fun PreCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PreCutColors,
        typography = PreCutTypography,
        shapes = PreCutShapes,
        content = content,
    )
}
