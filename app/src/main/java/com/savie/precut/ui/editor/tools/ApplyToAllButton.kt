package com.savie.precut.ui.editor.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutIconButton

/** "Apply to all" in a tool panel's header (RD-T frames): an icon button; the label is read aloud. */
@Composable
fun ApplyToAllButton(onClick: () -> Unit) {
    PreCutIconButton(R.drawable.ic_done_all, stringResource(R.string.action_apply_to_all), onClick)
}
