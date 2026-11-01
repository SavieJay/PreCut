package com.savie.precut.ui.editor.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.editor.TranscriptUi
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextSecondary

/** What the transcript panel can do. */
interface TranscriptActions {
    fun onWord(index: Int)
    fun onFillers()
    fun onSilences()
    fun onPreview()
    fun onApply()
    fun onMakeCaptions()
    fun onDone()
}

/**
 * Edit by transcript (E1): the words, fillers tinted, struck-through ones to be cut; Remove filler
 * words and Remove silences mark them all; Preview plays the result; Apply cuts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptPanel(ui: TranscriptUi, actions: TranscriptActions, modifier: Modifier = Modifier) {
    ToolPanel(stringResource(R.string.transcript_title), actions::onDone, modifier) {
        if (ui.words.isEmpty()) {
            Text(stringResource(R.string.transcript_needs_captions), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            FilledButton(stringResource(R.string.transcript_make_captions), actions::onMakeCaptions, Modifier.fillMaxWidth())
            return@ToolPanel
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreCutChip(
                pluralStringResource(R.plurals.transcript_fillers, ui.fillers.size, ui.fillers.size),
                selected = ui.fillers.isNotEmpty() && ui.fillers.all { it in ui.marked },
                onClick = actions::onFillers,
            )
            PreCutChip(
                pluralStringResource(R.plurals.transcript_silences, ui.silences.size, ui.silences.size),
                selected = ui.silencesMarked,
                onClick = actions::onSilences,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ui.words.forEachIndexed { index, word ->
                val marked = index in ui.marked
                Text(
                    word.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (marked) TextDisabled else MaterialTheme.typography.bodyLarge.color,
                    textDecoration = if (marked) TextDecoration.LineThrough else null,
                    modifier = Modifier
                        .background(if (index in ui.fillers) AccentContainer.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable { actions.onWord(index) }
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AccentTextButton(stringResource(if (ui.previewing) R.string.transcript_show_original else R.string.transcript_preview), actions::onPreview)
            Spacer(Modifier.weight(1f))
            FilledButton(
                pluralStringResource(R.plurals.transcript_apply, ui.cutCount, ui.cutCount),
                actions::onApply,
                enabled = ui.cutCount > 0,
            )
        }
    }
}
