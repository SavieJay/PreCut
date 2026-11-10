package com.savie.precut.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Playhead
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/** A clip on the welcome wall: its stand-in footage and, on some, a length badge. */
private class WallClip(val scene: ClipScene, val length: String? = null)

/** The wall's four columns, top to bottom, and how far up each starts (the stagger). */
private val WALL = listOf(
    0 to listOf(WallClip(ClipScene.BEACH, "0:12"), WallClip(ClipScene.FOOD, "0:07"), WallClip(ClipScene.HIKE)),
    -92 to listOf(WallClip(ClipScene.CITY), WallClip(ClipScene.PARK, "0:21"), WallClip(ClipScene.CAFE, "0:05")),
    -28 to listOf(WallClip(ClipScene.MARKET, "0:16"), WallClip(ClipScene.PORTRAIT), WallClip(ClipScene.GREEN, "0:09")),
    -128 to listOf(WallClip(ClipScene.HIKE, "0:31"), WallClip(ClipScene.BEACH), WallClip(ClipScene.CITY, "0:14")),
)

/**
 * Welcome (O-01): a tilted wall of clips with the editor's white playhead running through it,
 * fading into the page; then the logo, the promise, Get started and "I already have projects".
 */
@Composable
internal fun WelcomePage(onStart: () -> Unit, onHaveProjects: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wallHeight = maxHeight * 0.72f
        ClipWall(Modifier.fillMaxWidth().height(wallHeight))
        Box(
            Modifier.fillMaxWidth().height(wallHeight).background(
                Brush.verticalGradient(0f to Ink0.copy(alpha = 0.82f), 0.18f to Color.Transparent, 0.45f to Color.Transparent, 0.85f to Ink0),
            ),
        )
        WallPlayhead(Modifier.offset(x = maxWidth * 0.36f, y = maxHeight * 0.105f).height(maxHeight * 0.46f))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Color(0xFFE4E4E8), modifier = Modifier.size(36.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
            Text(
                stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
            FilledButton(stringResource(R.string.onboarding_get_started), onStart, Modifier.fillMaxWidth().padding(top = 32.dp), large = true)
            AccentTextButton(stringResource(R.string.onboarding_have_projects), onHaveProjects, Modifier.fillMaxWidth().padding(top = 8.dp), color = TextSecondary)
        }
    }
}

/** Four staggered columns of upright clips, wider than the screen and turned a little. */
@Composable
private fun ClipWall(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds()) {
        Row(
            Modifier.requiredWidth(maxWidth + 144.dp).offset(y = (-56).dp).graphicsLayer { rotationZ = -8f },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WALL.forEach { (stagger, clips) ->
                Column(Modifier.weight(1f).offset(y = stagger.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    clips.forEach { clip -> WallTile(clip) }
                }
            }
        }
    }
}

@Composable
private fun WallTile(clip: WallClip) {
    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(clip.scene.brush)) {
        clip.length?.let { length ->
            Text(
                length,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold).tabular(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Ink0.copy(alpha = 0.82f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
            )
        }
    }
}

/** The editor's playhead: a white line with its knob on top. */
@Composable
private fun WallPlayhead(modifier: Modifier = Modifier) {
    Box(modifier.width(12.dp)) {
        Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().background(Playhead, RoundedCornerShape(1.dp)))
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-6).dp).size(12.dp).background(Playhead, CircleShape))
    }
}
