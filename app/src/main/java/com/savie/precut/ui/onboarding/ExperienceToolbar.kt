package com.savie.precut.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.glass
import com.savie.precut.ui.theme.TextSecondary

private val MiniShape = RoundedCornerShape(22.dp)

/** The Simple toolbar as the editor shows it (EditorToolbar), More last. */
private val SIMPLE_TOOLS = listOf(
    R.drawable.ic_auto_awesome to R.string.tool_ai_edit, R.drawable.ic_split to R.string.tool_split, R.drawable.ic_trim to R.string.tool_trim,
    R.drawable.ic_music to R.string.tool_audio, R.drawable.ic_text to R.string.tool_text, R.drawable.ic_filter to R.string.tool_filters,
    R.drawable.ic_apps to R.string.tool_more,
)

/** The full toolbar's first tools; the rest scroll in from the right. */
private val FULL_TOOLS = listOf(
    R.drawable.ic_auto_awesome to R.string.tool_ai_edit, R.drawable.ic_split to R.string.tool_split, R.drawable.ic_crop to R.string.tool_edit,
    R.drawable.ic_trim to R.string.tool_trim, R.drawable.ic_music to R.string.tool_audio, R.drawable.ic_speed to R.string.tool_speed,
    R.drawable.ic_animation to R.string.tool_animation, R.drawable.ic_canvas to R.string.tool_canvas,
)

/** A small copy of the toolbar this choice gives, with a line saying which. */
@Composable
internal fun ToolbarPreview(simple: Boolean) {
    val caption = stringResource(if (simple) R.string.onboarding_experience_simple_preview else R.string.onboarding_experience_full_preview)
    Column(Modifier.clearAndSetSemantics { contentDescription = caption }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Box(Modifier.fillMaxWidth().height(60.dp).glass(MiniShape)) {
            Row(Modifier.matchParentSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                (if (simple) SIMPLE_TOOLS else FULL_TOOLS).forEachIndexed { index, (icon, label) ->
                    MiniTool(icon, label, highlighted = simple && index == SIMPLE_TOOLS.lastIndex, modifier = Modifier.weight(1f))
                }
            }
            // The full toolbar runs on past the edge.
            if (!simple) {
                Box(Modifier.matchParentSize().clip(MiniShape).background(Brush.horizontalGradient(0.7f to Color.Transparent, 1f to Ink1)))
            }
        }
    }
}

@Composable
private fun MiniTool(@DrawableRes icon: Int, @StringRes label: Int, highlighted: Boolean, modifier: Modifier = Modifier) {
    // AI Edit keeps its lime (AI); the More tool the Simple toolbar adds is pink.
    val color = when {
        highlighted -> Accent
        icon == R.drawable.ic_auto_awesome -> AiAccent
        else -> TextSecondary
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
