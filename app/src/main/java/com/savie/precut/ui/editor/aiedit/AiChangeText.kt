package com.savie.precut.ui.editor.aiedit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.model.ai.edit.AiChangeLabel
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.components.formatSpeed

/** A change in the review list, in words. */
@Composable
fun aiChangeText(label: AiChangeLabel): String = when (label) {
    is AiChangeLabel.Trim -> stringResource(R.string.ai_change_trim, label.clip, formatSeconds(label.lengthMs))
    is AiChangeLabel.Split -> stringResource(R.string.ai_change_split, label.clip, formatSeconds(label.atMs))
    is AiChangeLabel.DeleteClips -> pluralStringResource(R.plurals.ai_change_delete_clips, label.count, label.count)
    is AiChangeLabel.Move -> stringResource(R.string.ai_change_move, label.clip, label.toPosition)
    is AiChangeLabel.Duplicate -> stringResource(R.string.ai_change_duplicate, label.clip)
    is AiChangeLabel.Speed -> stringResource(R.string.ai_change_speed, label.clip, formatSpeed(label.speed))
    is AiChangeLabel.Sound -> stringResource(R.string.ai_change_sound, label.clip)
    is AiChangeLabel.Look -> stringResource(R.string.ai_change_look, label.clip)
    is AiChangeLabel.Transition -> stringResource(if (label.removed) R.string.ai_change_transition_removed else R.string.ai_change_transition, label.clip)
    is AiChangeLabel.Animation -> stringResource(R.string.ai_change_animation, label.clip)
    is AiChangeLabel.AddText -> stringResource(R.string.ai_change_add_text, label.text)
    is AiChangeLabel.EditText -> stringResource(R.string.ai_change_edit_text, label.text)
    is AiChangeLabel.DeleteTexts -> pluralStringResource(R.plurals.ai_change_delete_texts, label.count, label.count)
    is AiChangeLabel.AddEffect -> stringResource(R.string.ai_change_add_effect, formatDurationMs(label.startMs), formatDurationMs(label.endMs))
    is AiChangeLabel.Ratio -> stringResource(R.string.ai_change_ratio, label.ratio)
    is AiChangeLabel.Music -> if (label.name.isBlank()) stringResource(R.string.ai_change_music_unnamed) else stringResource(R.string.ai_change_music, label.name)
    is AiChangeLabel.CutToBeats -> pluralStringResource(R.plurals.ai_change_cut_to_beats, label.clips, label.clips)
}
