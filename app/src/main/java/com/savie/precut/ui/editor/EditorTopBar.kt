package com.savie.precut.ui.editor

import com.savie.precut.ui.editor.coach.CoachTarget
import com.savie.precut.ui.editor.coach.LocalCoachTargets
import com.savie.precut.ui.editor.coach.coachTarget
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentPill
import com.savie.precut.ui.components.IconButtonStyle
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.GlassLine
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.TextMuted
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
    fun onSaveAsTemplate()
}

/**
 * The 2026 editor bar (RD-E03, redline 1): 56 dp, Back · project name over a muted [subtitle]
 * (tap to rename in place, E-10) · Undo · Redo · the small pink Export pill · More. While
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
    /** "9:16 · 0:18" under the name; [subtitleColor] is ErrorRed while media is missing (E-06). */
    subtitle: String? = null,
    subtitleColor: Color = TextMuted,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink0)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (renaming) 8.dp else 0.dp),
    ) {
        if (renaming) {
            RenameRow(title, actions)
            return@Row
        }
        PreCutIconButton(R.drawable.ic_back, stringResource(R.string.action_back), actions::onBack)
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_rename), onClick = actions::onStartRename)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PreCutIconButton(R.drawable.ic_undo, stringResource(R.string.action_undo), actions::onUndo, enabled = canUndo)
        PreCutIconButton(R.drawable.ic_redo, stringResource(R.string.action_redo), actions::onRedo, enabled = canRedo)
        AccentPill(
            stringResource(R.string.action_export),
            enabled = canExport,
            onClick = actions::onExport,
            modifier = Modifier.padding(start = 4.dp).coachTarget(LocalCoachTargets.current, CoachTarget.EXPORT),
        )
        MoreMenu(canExport, actions)
    }
}

/** ⋮: Save as template (needs a playable project, like Export). Solid: it drops over the preview. */
@Composable
private fun MoreMenu(enabled: Boolean, actions: EditorTopBarActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        PreCutIconButton(R.drawable.ic_more_vert, stringResource(R.string.action_more), { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = Radius.dock,
            containerColor = Ink2,
            border = BorderStroke(1.dp, GlassLine),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.template_save_title), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_template), contentDescription = null, tint = TextSecondary) },
                enabled = enabled,
                onClick = {
                    open = false
                    actions.onSaveAsTemplate()
                },
                modifier = Modifier.height(52.dp),
            )
        }
    }
}

/** E-10: a 48 dp field with a 2 dp pink ring between Cancel and the pink round Save. */
@Composable
private fun RowScope.RenameRow(title: String, actions: EditorTopBarActions) {
    var field by remember { mutableStateOf(TextFieldValue(title, TextRange(0, title.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    PreCutIconButton(R.drawable.ic_close, stringResource(R.string.action_cancel), actions::onCancelRename)
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .clip(CircleShape)
            .background(RowFill)
            .border(2.dp, Accent, CircleShape)
            .padding(horizontal = 18.dp),
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
    PreCutIconButton(R.drawable.ic_check, stringResource(R.string.action_save), { actions.onRename(field.text) }, style = IconButtonStyle.Pink)
}
