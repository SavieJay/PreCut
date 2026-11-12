package com.savie.precut.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The 2026 corner steps (RD-TOK "Shape & space"); pills use [Radius.pill]. */
object Radius {
    val clip = RoundedCornerShape(10.dp)
    val tile = RoundedCornerShape(18.dp)
    val dock = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(24.dp)
    val panel = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(50)
}

/**
 * Material's slots on the same steps: menus and snackbars 12, small surfaces 18, cards 24,
 * panels 28, dialogs and sheets 32.
 */
internal val PreCutShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = Radius.tile,
    medium = Radius.card,
    large = Radius.panel,
    extraLarge = Radius.sheet,
)
