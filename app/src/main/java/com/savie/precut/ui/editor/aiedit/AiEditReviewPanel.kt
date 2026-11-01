package com.savie.precut.ui.editor.aiedit

import androidx.compose.foundation.background
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
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.TextSecondary

/** What the review panel can do. */
interface AiEditReviewActions {
    fun onToggle(index: Int)
    fun onApply()
    fun onRefine()
    fun onDiscard()
}

/**
 * The review (Phase 22C), in place of the timeline while the preview plays the changes: what
 * Claude said, each change with a switch, then Discard, Refine and Apply.
 */
@Composable
fun AiEditReviewPanel(review: AiEditUi.Review, actions: AiEditReviewActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Ink1).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ai_edit_review_title), style = MaterialTheme.typography.titleMedium)
        if (review.message.isNotBlank()) {
            Text(review.message, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        review.note?.let { Text(noteText(it), style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            itemsIndexed(review.changes) { index, label ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.onToggle(index) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(aiChangeText(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    PreCutSwitch(index in review.kept, onCheckedChange = { actions.onToggle(index) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentTextButton(stringResource(R.string.ai_edit_discard), actions::onDiscard)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TonalButton(stringResource(R.string.ai_edit_refine), actions::onRefine)
            }
            FilledButton(
                pluralStringResource(R.plurals.ai_edit_apply, review.kept.size, review.kept.size),
                actions::onApply,
                enabled = review.kept.isNotEmpty(),
            )
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
