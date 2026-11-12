package com.savie.precut.ui.editor.transcript

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.savie.precut.ui.components.AiChip
import com.savie.precut.ui.components.AiTag
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.editor.TranscriptUi
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
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
    ToolPanel(stringResource(R.string.transcript_title), actions::onDone, modifier, actions = { AiTag(stringResource(R.string.rd_media_ai_tag)) }) {
        if (ui.words.isEmpty()) {
            Text(stringResource(R.string.transcript_needs_captions), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            FilledButton(stringResource(R.string.transcript_make_captions), actions::onMakeCaptions, Modifier.fillMaxWidth(), large = true)
            return@ToolPanel
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiChip(
                pluralStringResource(R.plurals.transcript_fillers, ui.fillers.size, ui.fillers.size),
                onClick = actions::onFillers,
                selected = ui.fillers.isNotEmpty() && ui.fillers.all { it in ui.marked },
            )
            AiChip(
                pluralStringResource(R.plurals.transcript_silences, ui.silences.size, ui.silences.size),
                onClick = actions::onSilences,
                selected = ui.silencesMarked,
                icon = R.drawable.ic_volume_off,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ui.words.forEachIndexed { index, word ->
                val marked = index in ui.marked
                val filler = index in ui.fillers
                Text(
                    word.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        marked -> TextDisabled
                        filler -> AiAccent
                        else -> TextPrimary
                    },
                    textDecoration = if (marked) TextDecoration.LineThrough else null,
                    modifier = Modifier
                        .background(if (filler) AiAccent.copy(alpha = 0.14f) else Color.Transparent, WordShape)
                        .clickable { actions.onWord(index) }
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AccentTextButton(
                stringResource(if (ui.previewing) R.string.transcript_show_original else R.string.transcript_preview),
                actions::onPreview,
                icon = if (ui.previewing) R.drawable.ic_undo else R.drawable.ic_play,
                color = TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            FilledButton(
                pluralStringResource(R.plurals.transcript_apply, ui.cutCount, ui.cutCount),
                actions::onApply,
                enabled = ui.cutCount > 0,
            )
        }
    }
}

private val WordShape = RoundedCornerShape(6.dp)
