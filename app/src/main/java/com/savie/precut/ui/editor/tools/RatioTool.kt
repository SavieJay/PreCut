package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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

/** Project frame shape; the preview letterboxes to match. */
@Composable
fun RatioTool(
    selected: AspectRatio,
    onSelected: (AspectRatio) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolPanel(stringResource(R.string.ratio_title), onDone, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = ratio == selected,
                    onClick = { onSelected(ratio) },
                    label = { Text("${ratio.widthRatio}:${ratio.heightRatio}") },
                    leadingIcon = { RatioGlyph(ratio) },
                )
            }
        }
    }
}

@Composable
private fun RatioGlyph(ratio: AspectRatio) {
    val longSide = 16f
    val scale = longSide / maxOf(ratio.widthRatio, ratio.heightRatio)
    Box(
        Modifier
            .size((ratio.widthRatio * scale).dp, (ratio.heightRatio * scale).dp)
            .border(1.5.dp, LocalContentColor.current, RoundedCornerShape(2.dp)),
    )
}
