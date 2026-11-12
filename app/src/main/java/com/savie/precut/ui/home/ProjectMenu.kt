package com.savie.precut.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.GlassLine
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

private val MenuShape = RoundedCornerShape(20.dp)
private val MenuRowShape = RoundedCornerShape(14.dp)

/** RD-H03: Rename, Duplicate, Delete (ErrorRed) as 52 dp rows on a raised glass menu (20 dp corners). */
@Composable
internal fun ProjectMenu(expanded: Boolean, onDismiss: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MenuShape,
        containerColor = GlassSolid,
        border = BorderStroke(1.dp, GlassLine),
        shadowElevation = 20.dp,
        modifier = Modifier.widthIn(min = 220.dp),
    ) {
        MenuRow(R.drawable.ic_edit, stringResource(R.string.action_rename), TextPrimary) { onDismiss(); onRename() }
        MenuRow(R.drawable.ic_duplicate, stringResource(R.string.action_duplicate), TextPrimary) { onDismiss(); onDuplicate() }
        MenuRow(R.drawable.ic_delete, stringResource(R.string.action_delete), ErrorRed) { onDismiss(); onDelete() }
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = color, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = if (color == TextPrimary) TextSecondary else color) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.padding(horizontal = 8.dp).height(52.dp).clip(MenuRowShape),
    )
}
