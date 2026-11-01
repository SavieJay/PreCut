package com.savie.precut.ui.editor.aiedit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.editor.AiEditUi
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary

/** What the request sheet can do. */
interface AiEditPromptActions {
    fun onRequestChange(request: String)
    fun onSend()
    fun onDismiss()
}

/** Ideas shown under an empty request; tapping one fills it in. */
private val SUGGESTIONS = listOf(
    R.string.ai_edit_suggestion_tiktok,
    R.string.ai_edit_suggestion_travel,
    R.string.ai_edit_suggestion_pauses,
    R.string.ai_edit_suggestion_highlight,
    R.string.ai_edit_suggestion_shorter,
    R.string.ai_edit_suggestion_title,
    R.string.ai_edit_suggestion_warm,
    R.string.ai_edit_suggestion_beats,
)

/**
 * Prompt-to-edit's request (Phase 22C): what to change, ideas, what's sent and the requests left.
 * The note on what's sent stays shown; the first Send agrees to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEditPromptSheet(prompt: AiEditUi.Prompt, actions: AiEditPromptActions) {
    ModalBottomSheet(
        onDismissRequest = actions::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink1,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(if (prompt.followUp) R.string.ai_edit_refine_title else R.string.ai_edit_title), style = MaterialTheme.typography.titleMedium)
            prompt.reply?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            prompt.failure?.let { Text(aiFailureMessage(it), style = MaterialTheme.typography.bodyMedium, color = Accent) }
            OutlinedTextField(
                value = prompt.request,
                onValueChange = actions::onRequestChange,
                placeholder = { Text(stringResource(if (prompt.followUp) R.string.ai_edit_refine_hint else R.string.ai_edit_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Outline,
                    focusedContainerColor = Ink0,
                    unfocusedContainerColor = Ink0,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (prompt.request.isBlank() && !prompt.followUp) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTIONS.forEach { idea ->
                        val text = stringResource(idea)
                        PreCutChip(text, selected = false, onClick = { actions.onRequestChange(text) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Text(stringResource(R.string.ai_edit_disclosure), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            prompt.runsLeft?.let {
                Text(pluralStringResource(R.plurals.ai_edit_runs_left, it, it), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            FilledButton(
                stringResource(if (prompt.consented) R.string.ai_edit_send else R.string.ai_edit_agree_send),
                actions::onSend,
                Modifier.fillMaxWidth(),
                icon = R.drawable.ic_auto_awesome,
                large = true,
                enabled = prompt.request.isNotBlank(),
            )
        }
    }
}
