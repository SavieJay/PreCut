package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.GlassLevel
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glass

/**
 * The 2026 dialog (RD-H04, RD-H05 `.dlg`): raised glass with 32 dp corners, 24 dp from the screen
 * edges, a round close button top right, a 22/700 title, then [content] (body and one main pill).
 * Drawn solid: nothing is blurred behind it, so the fill carries the legibility.
 */
@Composable
internal fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .imePadding()
                .padding(horizontal = 24.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .glass(Radius.sheet, GlassLevel.Raised, solid = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.padding(end = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    leading?.invoke()
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                content()
            }
            PreCutIconButton(
                R.drawable.ic_close,
                stringResource(R.string.action_cancel),
                onDismiss,
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
            )
        }
    }
}
