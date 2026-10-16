package com.savie.precut.ui.editor.tools.sticker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.sticker.DEFAULT_DRAW_SIZE
import com.savie.precut.model.sticker.Drawing
import com.savie.precut.model.sticker.MAX_DRAW_SIZE
import com.savie.precut.model.sticker.MIN_DRAW_SIZE
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.editor.tools.ColorSwatchRow
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.TextSecondary
import kotlin.math.roundToInt

/** What the Draw panel can do. */
interface DrawActions {
    fun onUndo()
    fun onRedo()
    fun onEraser(eraser: Boolean)
    fun onSize(size: Float)
    fun onColor(argb: Int)
    fun onDiscard()
    fun onDone()
}

/** S-02: Brush or Eraser, size and colour; the strokes are drawn on the preview. */
@Composable
fun DrawTool(drawing: Drawing, note: String, actions: DrawActions) {
    var size by remember(drawing.size) { mutableFloatStateOf(drawing.size) }
    ToolPanel(
        stringResource(R.string.draw_title),
        onDone = actions::onDone,
        onBack = actions::onDiscard,
        backIcon = R.drawable.ic_close,
        backLabel = R.string.draw_discard,
        actions = {
            IconButton(onClick = actions::onUndo, enabled = drawing.canUndo) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.draw_undo))
            }
            IconButton(onClick = actions::onRedo, enabled = drawing.canRedo) {
                Icon(painterResource(R.drawable.ic_redo), contentDescription = stringResource(R.string.draw_redo))
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreCutChip(stringResource(R.string.draw_brush), selected = !drawing.eraser, onClick = { actions.onEraser(false) }, icon = R.drawable.ic_brush)
            PreCutChip(stringResource(R.string.draw_eraser), selected = drawing.eraser, onClick = { actions.onEraser(true) }, icon = R.drawable.ic_ink_eraser)
        }
        SliderRow(value = size.roundToInt().toString(), label = stringResource(R.string.draw_size)) { sliderModifier ->
            PreCutSlider(
                value = size,
                onValueChange = { size = it },
                onValueChangeFinished = { actions.onSize(size) },
                valueRange = MIN_DRAW_SIZE..MAX_DRAW_SIZE,
                onReset = { actions.onSize(DEFAULT_DRAW_SIZE) },
                modifier = sliderModifier,
            )
        }
        ColorSwatchRow(drawing.colorArgb.takeIf { !drawing.eraser }, emptyList(), actions::onColor)
        Text(note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
