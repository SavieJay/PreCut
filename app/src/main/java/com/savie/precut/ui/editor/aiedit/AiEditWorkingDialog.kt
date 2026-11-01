package com.savie.precut.ui.editor.aiedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savie.precut.R
import com.savie.precut.model.ai.edit.EditProgress
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextSecondary

/** What Claude is doing, from the last tool it used. */
private fun activity(tool: String?): Int = when (tool) {
    null -> R.string.ai_edit_activity_thinking
    "trim_clip", "split_clip", "delete_clips", "move_clip", "duplicate_clip", "remove_time_ranges" -> R.string.ai_edit_activity_cutting
    "set_clip_speed" -> R.string.ai_edit_activity_pace
    "set_clip_sound", "set_audio_track" -> R.string.ai_edit_activity_sound
    "set_clip_look", "add_effect" -> R.string.ai_edit_activity_look
    "set_transition", "set_clip_animation" -> R.string.ai_edit_activity_motion
    "add_text", "edit_text", "delete_texts" -> R.string.ai_edit_activity_text
    "set_aspect_ratio" -> R.string.ai_edit_activity_shape
    "cut_to_beats" -> R.string.ai_edit_activity_beats
    "add_captions" -> R.string.ai_edit_activity_captions
    else -> R.string.ai_edit_activity_thinking
}

/**
 * AI Edit at work (D2): first reading the media on the phone ([understanding] %), then Claude's
 * steps with what it's doing and the changes so far. Stop keeps them for review.
 */
@Composable
fun AiEditWorkingDialog(progress: EditProgress, understanding: Int?, onStop: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.fillMaxWidth().background(Ink2, RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(48.dp).background(AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_auto_awesome), contentDescription = null, tint = Accent)
            }
            Text(
                if (understanding != null) stringResource(R.string.ai_edit_understanding, understanding) else stringResource(activity(progress.lastTool)),
                style = MaterialTheme.typography.titleLarge,
            )
            if (understanding == null) {
                Text(
                    listOf(
                        stringResource(R.string.ai_edit_step, progress.step),
                        pluralStringResource(R.plurals.ai_edit_changes_so_far, progress.changes, progress.changes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                Text(stringResource(R.string.ai_edit_understanding_body), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Accent,
                trackColor = Ink3,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AccentTextButton(stringResource(R.string.ai_edit_stop), onClick = onStop)
            }
        }
    }
}
