package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.look.Adjustment
import com.savie.precut.model.look.ClipLook
import kotlin.math.roundToInt

/** What the Adjust panel can do. */
interface AdjustActions {
    fun onAdjust(adjustment: Adjustment, value: Float)
    fun onReset()
    fun onHsl()
    fun onCurves()
}

/**
 * CapCut's Adjust: pick a property, drag its slider (applied on release); HSL and Curves open
 * their own panels. Changed properties show their value on the chip.
 */
@Composable
fun AdjustTool(
    look: ClipLook,
    actions: AdjustActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onApplyAll: (() -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(Adjustment.BRIGHTNESS) }
    var value by remember(selected, look.adjust[selected]) { mutableFloatStateOf(look.adjust[selected]) }
    ToolPanel(
        stringResource(R.string.adjust_title),
        onDone,
        modifier,
        actions = {
            TextButton(onClick = actions::onReset) { Text(stringResource(R.string.adjust_reset)) }
            onApplyAll?.let { ApplyToAllButton(it) }
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Adjustment.entries) { adjustment ->
                val current = look.adjust[adjustment]
                FilterChip(
                    selected = adjustment == selected,
                    onClick = { selected = adjustment },
                    label = {
                        val name = stringResource(adjustment.labelRes())
                        Text(if (current == 0f) name else "$name ${current.roundToInt()}")
                    },
                )
            }
            item {
                FilterChip(selected = look.hsl.any { !it.isIdentity }, onClick = actions::onHsl, label = { Text(stringResource(R.string.adjust_hsl)) })
            }
            item {
                FilterChip(selected = !look.curves.isIdentity, onClick = actions::onCurves, label = { Text(stringResource(R.string.adjust_curves)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { actions.onAdjust(selected, value) },
                valueRange = selected.min..selected.max,
                modifier = Modifier.weight(1f),
            )
            Text("${value.roundToInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        }
    }
}
