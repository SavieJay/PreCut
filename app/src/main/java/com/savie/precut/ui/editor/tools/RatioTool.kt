package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.savie.precut.ui.components.TileFrame
import androidx.compose.foundation.layout.padding
import com.savie.precut.ui.theme.TextPrimary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.AspectRatio

/** Project frame shape; the preview letterboxes to match. "Original" follows the first clip. */
@Composable
fun RatioTool(
    selected: AspectRatio,
    /** Width:height that [AspectRatio.ORIGINAL] resolves to, for its glyph. */
    originalRatio: Pair<Int, Int>,
    onSelected: (AspectRatio) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(stringResource(R.string.ratio_title), onDone, modifier) {
        // RD-T10: 68 dp tiles, each with the frame's shape drawn as an outline.
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AspectRatio.entries.forEach { ratio ->
                val shape = if (ratio == AspectRatio.ORIGINAL) originalRatio else ratio.widthRatio to ratio.heightRatio
                TileFrame(label = ratio.label(), selected = ratio == selected, onClick = { onSelected(ratio) }) {
                    RatioGlyph(shape.first, shape.second, TextPrimary)
                }
            }
        }
    }
}

@Composable
fun AspectRatio.label(): String = when (this) {
    AspectRatio.ORIGINAL -> stringResource(R.string.ratio_original)
    AspectRatio.RATIO_235_100 -> "2.35:1"
    AspectRatio.RATIO_185_100 -> "1.85:1"
    AspectRatio.RATIO_9_19_5 -> "9:19.5"
    else -> "$widthRatio:$heightRatio"
}

@Composable
internal fun RatioGlyph(width: Int, height: Int, color: Color, longSide: Float = 34f) {
    val scale = longSide / maxOf(width, height, 1)
    Box(
        Modifier
            .size((width * scale).dp, (height * scale).dp)
            .border(2.dp, color, RoundedCornerShape(3.dp)),
    )
}
