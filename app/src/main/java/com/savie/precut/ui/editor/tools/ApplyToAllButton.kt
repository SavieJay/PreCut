package com.savie.precut.ui.editor.tools

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.savie.precut.R

/** CapCut's "Apply to all": the current setting on every clip of the main track. */
@Composable
fun ApplyToAllButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.action_apply_to_all)) }
}
