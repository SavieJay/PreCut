package com.savie.precut.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.home.GlassDialog
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.RowLine
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** Deleting the account (Phase 30; Play requires it in the app): typed confirmation, then for good. */
@Composable
internal fun DeleteAccountDialog(deleting: Boolean, failed: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val word = stringResource(R.string.account_delete_word)
    var typed by rememberSaveable { mutableStateOf("") }
    GlassDialog(title = stringResource(R.string.account_delete_title), onDismiss = { if (!deleting) onDismiss() }) {
        Text(stringResource(R.string.account_delete_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            enabled = !deleting,
            placeholder = { Text(word) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RowFill,
                unfocusedContainerColor = RowFill,
                focusedBorderColor = ErrorRed,
                unfocusedBorderColor = RowLine,
                cursorColor = ErrorRed,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (failed) Text(stringResource(R.string.account_delete_failed), style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
        if (deleting) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) { PreCutSpinner() }
        } else {
            FilledButton(
                stringResource(R.string.account_delete_confirm),
                onDelete,
                Modifier.fillMaxWidth(),
                icon = R.drawable.ic_delete,
                large = true,
                enabled = typed.trim().equals(word, ignoreCase = true),
                destructive = true,
            )
        }
    }
}
