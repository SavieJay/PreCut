package com.savie.precut.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextSecondary

/** Tiles of the welcome collage: their colours stand for clips on a timeline. */
private val TILE_COLOURS = listOf(Color(0xFFFF4D6D), Color(0xFF7C5CFF), Color(0xFF2EC4B6), Color(0xFFFFB703), Color(0xFF3A86FF), Color(0xFFFF7A3D))

/** Welcome (A1): a moving collage of clip-like tiles, the logo, the promise, Get started. */
@Composable
internal fun WelcomePage(onStart: () -> Unit, onHaveProjects: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ClipCollage(Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Accent, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            FilledButton(stringResource(R.string.onboarding_get_started), onStart, Modifier.fillMaxWidth().padding(top = 8.dp), large = true)
            AccentTextButton(stringResource(R.string.onboarding_have_projects), onHaveProjects, color = TextSecondary)
        }
    }
}

/** Rows of rounded tiles drifting sideways like timelines, fading into the page at the bottom. */
@Composable
private fun ClipCollage(modifier: Modifier = Modifier) {
    val drift by rememberInfiniteTransition(label = "collage").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    Canvas(modifier) {
        val rowHeight = size.height * 0.11f
        val gap = rowHeight * 0.18f
        val tileWidth = rowHeight * 1.6f
        repeat(6) { row ->
            val direction = if (row % 2 == 0) 1f else -1f
            val shift = (drift * (tileWidth + gap) * direction) - tileWidth
            var x = shift
            var index = row
            while (x < size.width + tileWidth) {
                drawRoundRect(
                    color = TILE_COLOURS[index % TILE_COLOURS.size].copy(alpha = 0.55f),
                    topLeft = Offset(x, row * (rowHeight + gap) + gap),
                    size = Size(tileWidth, rowHeight),
                    cornerRadius = CornerRadius(rowHeight * 0.2f),
                )
                x += tileWidth + gap
                index += 1
            }
        }
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Ink0.copy(alpha = 0.6f), 0.7f to Ink0))
    }
}
