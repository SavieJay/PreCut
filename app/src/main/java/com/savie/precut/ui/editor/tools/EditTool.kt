package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.PresetTile

/** Edit (T-12): open crop mode, mirror left–right, or turn 90° clockwise. */
@Composable
fun EditTool(onCrop: () -> Unit, onMirror: () -> Unit, onRotate: () -> Unit, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.tool_edit), onDone) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetTile(stringResource(R.string.edit_crop), R.drawable.ic_crop, selected = false, onClick = onCrop)
            PresetTile(stringResource(R.string.edit_mirror), R.drawable.ic_mirror, selected = false, onClick = onMirror)
            PresetTile(stringResource(R.string.tool_rotate), R.drawable.ic_rotate, selected = false, onClick = onRotate)
        }
    }
}
