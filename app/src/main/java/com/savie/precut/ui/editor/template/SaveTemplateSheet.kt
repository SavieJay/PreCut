package com.savie.precut.ui.editor.template

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Project
import com.savie.precut.model.template.TemplateCategory
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.PreCutSwitch
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.editor.timeline.ThumbnailTile
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.templates.label
import com.savie.precut.ui.theme.TextSecondary

/** Save as template: a title, a category, which clips take new media and which texts are meant to change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveTemplateSheet(
    project: Project,
    thumbnails: ThumbnailLoader,
    saving: Boolean,
    onSave: (title: String, category: TemplateCategory, slotIds: Set<String>, textIds: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val clips = remember(project) { project.clips.map { it to false } + project.overlays.map { it.clip to true } }
    var title by rememberSaveable { mutableStateOf(project.name) }
    var category by rememberSaveable { mutableStateOf(TemplateCategory.OTHER) }
    var slots by rememberSaveable { mutableStateOf(clips.map { it.first.id }) }
    var texts by rememberSaveable { mutableStateOf(project.textOverlays.filter { it.sticker == null }.map { it.id }) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink1,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.template_save_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true, shape = RoundedCornerShape(8.dp),
                label = { Text(stringResource(R.string.template_name)) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TemplateCategory.entries.forEach { PreCutChip(stringResource(it.label), selected = it == category, onClick = { category = it }) }
            }
            Text(stringResource(R.string.template_replaceable), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            val thumbHeight = with(LocalDensity.current) { 48.dp.roundToPx() }
            clips.forEachIndexed { index, (clip, overlay) ->
                ToggleRow(
                    label = stringResource(if (overlay) R.string.template_overlay_n else R.string.template_clip_n, index + 1, formatDurationMs(clip.timelineDurationMs())),
                    checked = clip.id in slots,
                    onChecked = { on -> slots = if (on) slots + clip.id else slots - clip.id },
                ) {
                    ThumbnailTile(thumbnails, ThumbnailKey(clip.uri, clip.type, clip.trimStartMs, thumbHeight, clip.sourceRotationDegrees), Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                }
            }
            val editable = project.textOverlays.filter { it.sticker == null }
            if (editable.isNotEmpty()) Text(stringResource(R.string.template_texts), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            editable.forEach { text ->
                ToggleRow(text.text.ifBlank { " " }, checked = text.id in texts, onChecked = { on -> texts = if (on) texts + text.id else texts - text.id })
            }
            Text(stringResource(R.string.template_save_hint), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            FilledButton(
                stringResource(if (saving) R.string.template_saving else R.string.template_save),
                onClick = { onSave(title.trim(), category, slots.toSet(), texts.toSet()) },
                enabled = !saving && slots.isNotEmpty() && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, leading: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading?.invoke()
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        PreCutSwitch(checked, onChecked)
    }
}
