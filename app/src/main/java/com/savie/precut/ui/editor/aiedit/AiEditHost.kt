package com.savie.precut.ui.editor.aiedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.ui.editor.AiEditUi
import com.savie.precut.ui.editor.EditorAiEdit

/** Prompt-to-edit's sheet and working dialog; the review is a panel in the editor (see EditorContent). */
@Composable
fun AiEditHost(aiEdit: EditorAiEdit) {
    val state by aiEdit.state.collectAsStateWithLifecycle()
    val actions = remember(aiEdit) {
        object : AiEditPromptActions {
            override fun onRequestChange(request: String) = aiEdit.onRequestChange(request)
            override fun onSend() = aiEdit.onSend()
            override fun onDismiss() = aiEdit.onDismissPrompt()
        }
    }
    when (val current = state) {
        is AiEditUi.Prompt -> AiEditPromptSheet(current, actions)
        is AiEditUi.Working -> AiEditWorkingDialog(current.progress, aiEdit::onStop)
        AiEditUi.Closed, is AiEditUi.Review -> Unit
    }
}

/** The review panel's actions, wired to [aiEdit]. */
@Composable
fun rememberReviewActions(aiEdit: EditorAiEdit): AiEditReviewActions = remember(aiEdit) {
    object : AiEditReviewActions {
        override fun onToggle(index: Int) = aiEdit.onToggle(index)
        override fun onApply() = aiEdit.onApply()
        override fun onRefine() = aiEdit.onRefine()
        override fun onDiscard() = aiEdit.onDiscard()
    }
}
