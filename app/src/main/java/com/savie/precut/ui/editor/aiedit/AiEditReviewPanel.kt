package com.savie.precut.ui.editor.aiedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutSwitch
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.editor.AiEditUi
import com.savie.precut.ui.editor.ReviewNote
import com.savie.precut.ui.components.AiTag
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glass

/** What the review panel can do. */
interface AiEditReviewActions {
    fun onToggle(index: Int)
    fun onApply()
    fun onApplyAll()
    fun onRefine()
    fun onDiscard()
}

/**
 * The review (Phase 22C), in place of the timeline while the preview plays the changes: what
 * Claude said, each change with a switch, then Discard, Refine and Apply.
 */
@Composable
fun AiEditReviewPanel(review: AiEditUi.Review, actions: AiEditReviewActions, modifier: Modifier = Modifier) {
    // RD panel: glass inset 8 dp with 28 dp corners, under the preview (never over it).
    Column(
        modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp).glass(Radius.panel).padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ai_edit_review_title), style = PreCutType.panelTitle, modifier = Modifier.weight(1f, fill = false))
            AiTag(stringResource(R.string.rd_editor_ai_tag))
        }
        if (review.message.isNotBlank()) {
            Text(review.message, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        review.note?.let { Text(noteText(it), style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            itemsIndexed(review.changes) { index, label ->
                val step = review.steps.getOrNull(index)
                if (step != null && step != review.steps.getOrNull(index - 1)) {
                    Text(stringResource(R.string.ai_edit_step, step).uppercase(), style = PreCutType.section, color = TextMuted, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { actions.onToggle(index) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(aiChangeText(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    PreCutSwitch(index in review.kept, onCheckedChange = { actions.onToggle(index) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentTextButton(stringResource(R.string.ai_edit_discard), actions::onDiscard, color = TextSecondary)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TonalButton(stringResource(R.string.ai_edit_refine), actions::onRefine)
            }
            if (review.kept.size == review.changes.size) {
                FilledButton(stringResource(R.string.ai_edit_apply_all), actions::onApplyAll)
            } else {
                FilledButton(
                    pluralStringResource(R.plurals.ai_edit_apply, review.kept.size, review.kept.size),
                    actions::onApply,
                    enabled = review.kept.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun noteText(note: ReviewNote): String = when (note) {
    ReviewNote.OutOfSteps -> stringResource(R.string.ai_edit_note_out_of_steps)
    ReviewNote.CutShort -> stringResource(R.string.ai_edit_note_cut_short)
    ReviewNote.Stopped -> stringResource(R.string.ai_edit_note_stopped)
    is ReviewNote.Failed -> stringResource(R.string.ai_edit_note_failed, aiFailureMessage(note.failure))
}
