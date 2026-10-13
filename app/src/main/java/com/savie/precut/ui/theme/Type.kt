package com.savie.precut.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Tighter, bolder titles; tabular-ish small labels for timecodes and tool names. */
val PreCutTypography = Base.copy(
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 10.sp),
)
