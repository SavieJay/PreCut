package com.savie.precut.ui.editor.tools

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.pressScale
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glassRow

/** Edit (RD-T12): open crop mode, mirror left–right, or turn 90° clockwise, as 96 dp quiet buttons. */
@Composable
fun EditTool(onCrop: () -> Unit, onMirror: () -> Unit, onRotate: () -> Unit, onDone: () -> Unit) {
    ToolPanel(stringResource(R.string.tool_edit), onDone) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigToolButton(stringResource(R.string.edit_crop), R.drawable.ic_crop, onCrop, Modifier.weight(1f))
            BigToolButton(stringResource(R.string.edit_mirror), R.drawable.ic_mirror, onMirror, Modifier.weight(1f))
            BigToolButton(stringResource(R.string.tool_rotate), R.drawable.ic_rotate, onRotate, Modifier.weight(1f))
        }
    }
}

/** The design's `.big`: a 96 dp row-glass button, 30 dp icon over a 13 sp label. */
@Composable
private fun BigToolButton(label: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .height(96.dp)
            .pressScale(interaction)
            .glassRow(BigShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(30.dp))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary, maxLines = 1)
    }
}

private val BigShape = RoundedCornerShape(22.dp)
