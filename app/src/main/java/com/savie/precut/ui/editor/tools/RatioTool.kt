package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
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
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatio.entries.forEach { ratio ->
                val shape = if (ratio == AspectRatio.ORIGINAL) originalRatio else ratio.widthRatio to ratio.heightRatio
                FilterChip(
                    selected = ratio == selected,
                    onClick = { onSelected(ratio) },
                    label = { Text(ratio.label()) },
                    leadingIcon = { RatioGlyph(shape.first, shape.second) },
                )
            }
        }
    }
}

@Composable
private fun AspectRatio.label(): String = when (this) {
    AspectRatio.ORIGINAL -> stringResource(R.string.ratio_original)
    AspectRatio.RATIO_235_100 -> "2.35:1"
    AspectRatio.RATIO_185_100 -> "1.85:1"
    AspectRatio.RATIO_9_19_5 -> "9:19.5"
    else -> "$widthRatio:$heightRatio"
}

@Composable
private fun RatioGlyph(width: Int, height: Int) {
    val longSide = 16f
    val scale = longSide / maxOf(width, height, 1)
    Box(
        Modifier
            .size((width * scale).dp, (height * scale).dp)
            .border(1.5.dp, LocalContentColor.current, RoundedCornerShape(2.dp)),
    )
}
