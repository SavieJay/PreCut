package com.savie.precut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentPill
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** Top bar actions. */
interface EditorTopBarActions {
    fun onBack()
    fun onStartRename()
    fun onRename(name: String)
    fun onCancelRename()
    fun onUndo()
    fun onRedo()
    fun onExport()
}

/**
 * 56 dp: Back · project name (tap to rename in place, E-10) · Undo · Redo · Export pill. While
 * [renaming], the bar becomes Cancel · name field · Save.
 */
@Composable
fun EditorTopBar(
    title: String,
    canExport: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    renaming: Boolean,
    actions: EditorTopBarActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink0)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (renaming) 4.dp else 0.dp),
    ) {
        if (renaming) {
            RenameRow(title, actions)
            return@Row
        }
        BarIcon(R.drawable.ic_back, stringResource(R.string.action_back), onClick = actions::onBack)
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_rename), onClick = actions::onStartRename)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        BarIcon(R.drawable.ic_undo, stringResource(R.string.action_undo), enabled = canUndo, onClick = actions::onUndo)
        BarIcon(R.drawable.ic_redo, stringResource(R.string.action_redo), enabled = canRedo, onClick = actions::onRedo)
        AccentPill(stringResource(R.string.action_export), enabled = canExport, onClick = actions::onExport, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun RowScope.RenameRow(title: String, actions: EditorTopBarActions) {
    var field by remember { mutableStateOf(TextFieldValue(title, TextRange(0, title.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BarIcon(R.drawable.ic_close, stringResource(R.string.action_cancel), onClick = actions::onCancelRename)
    Box(
        Modifier
            .weight(1f)
            .height(44.dp)
            .border(2.dp, Accent, CircleShape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = field,
            onValueChange = { field = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onRename(field.text) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
    BarIcon(R.drawable.ic_check, stringResource(R.string.action_save), tint = Accent) { actions.onRename(field.text) }
}

@Composable
private fun BarIcon(icon: Int, description: String, enabled: Boolean = true, tint: Color = TextPrimary, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = TextDisabled),
    ) {
        Icon(painterResource(icon), contentDescription = description)
    }
}
