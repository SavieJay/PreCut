package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton

/** CapCut's "Apply to all": the current setting on every clip of the main track. */
@Composable
fun ApplyToAllButton(onClick: () -> Unit) {
    AccentTextButton(stringResource(R.string.action_apply_to_all), onClick, icon = R.drawable.ic_done_all)
}
