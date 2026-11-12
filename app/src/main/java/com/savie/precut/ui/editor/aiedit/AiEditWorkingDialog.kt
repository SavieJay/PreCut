package com.savie.precut.ui.editor.aiedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savie.precut.R
import com.savie.precut.model.ai.edit.EditProgress
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.PreCutProgressBar
import com.savie.precut.ui.components.shimmer
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.AiContainer
import com.savie.precut.ui.theme.GlassLevel
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glass

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
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .glass(Radius.sheet, GlassLevel.Raised, solid = true)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // AI at work: lime (RD-AI4).
            Box(Modifier.size(48.dp).background(AiContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_auto_awesome), contentDescription = null, tint = AiAccent)
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
            // Indeterminate: a lime bar with the AI shimmer sweeping across it.
            PreCutProgressBar(1f, Modifier.shimmer(ai = true), ai = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AccentTextButton(stringResource(R.string.ai_edit_stop), onClick = onStop, color = TextSecondary)
            }
        }
    }
}
