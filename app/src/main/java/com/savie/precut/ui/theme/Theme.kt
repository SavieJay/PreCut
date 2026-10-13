package com.savie.precut.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** PreCut is dark-only so footage stands out; no dynamic color to keep the brand consistent. */
private val PreCutColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainer,
    onPrimaryContainer = TextPrimary,
    secondary = TextPrimary,
    onSecondary = Ink0,
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
    outline = Ink3,
    outlineVariant = Ink2,
    error = ErrorRed,
)

@Composable
fun PreCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PreCutColors,
        typography = PreCutTypography,
        content = content,
    )
}
