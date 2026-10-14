package com.savie.precut.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.savie.precut.R

/** Roboto Flex (OFL), bundled as a variable font cut to the weights the design uses (400–700). */
private fun flex(weight: Int) = Font(
    R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val RobotoFlex = FontFamily(flex(400), flex(500), flex(600), flex(700))

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = RobotoFlex,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

/** The design's type scale (DESIGN.md "Type"). */
val PreCutTypography = Typography(
    displaySmall = style(36, 44, 700),
    headlineMedium = style(28, 36, 600),
    headlineSmall = style(24, 32, 600),
    titleLarge = style(22, 28, 700),
    titleMedium = style(16, 24, 600),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 22, 400),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 16, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(11, 14, 500),
    labelSmall = style(10, 12, 600),
)

/** Styles the Material scale doesn't have. */
object PreCutType {
    /** Chips, tabs, slider labels and values. */
    val labelChip = style(13, 18, 500)

    /** The playback row's timecode; use with tabular figures. */
    val timecode = style(13, 16, 500).copy(fontFeatureSettings = "tnum")

    /** Section labels: bodySmall, semibold, caps, +0.4 tracking. */
    val section = style(12, 16, 600, 0.4)
}

/** Tabular figures, for any timecode or changing number. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
