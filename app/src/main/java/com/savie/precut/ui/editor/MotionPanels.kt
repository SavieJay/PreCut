package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.savie.precut.model.Clip
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.timelineDurationMs
import com.savie.precut.ui.editor.tools.AnimationTool
import com.savie.precut.ui.editor.tools.ClipAnimations
import com.savie.precut.ui.editor.tools.TAB_COMBO
import com.savie.precut.ui.editor.tools.TAB_IN
import com.savie.precut.ui.editor.tools.TransitionActions
import com.savie.precut.ui.editor.tools.TransitionTool

/** The Transition panel for the junction after [clipId], if it's still between two clips. */
@Composable
fun TransitionPanel(state: EditorUiState.Ready, clipId: String, viewModel: EditorViewModel) {
    val clips = state.project.clips
    val index = clips.indexOfFirst { it.id == clipId }
    if (index < 0 || index >= clips.lastIndex) return
    val motion = viewModel.motion
    val actions = remember(motion) {
        object : TransitionActions {
            override fun onType(type: TransitionType?) = motion.onTransition(type)
            override fun onDuration(durationMs: Long) = motion.onTransitionDuration(durationMs)
            override fun onApplyAll() = motion.onTransitionApplyAll()
        }
    }
    val maxMs = minOf(clips[index].timelineDurationMs(), clips[index + 1].timelineDurationMs())
    TransitionTool(clips[index].transitionOut, maxMs, actions, onDone = viewModel::onToolClosed)
}

/** The Animation panel for [clip]. */
@Composable
fun AnimationPanel(clip: Clip, viewModel: EditorViewModel) {
    AnimationTool(
        clipId = clip.id,
        current = ClipAnimations(clip.animationIn, clip.animationOut, clip.animationCombo),
        clipLengthMs = clip.timelineDurationMs(),
        onChange = { tab, animations ->
            val slot = when (tab) {
                TAB_IN -> AnimationSlot.IN
                TAB_COMBO -> AnimationSlot.COMBO
                else -> AnimationSlot.OUT
            }
            viewModel.motion.onAnimation(slot, animations.animationIn, animations.animationOut, animations.combo)
        },
        onDone = viewModel::onToolClosed,
    )
}
