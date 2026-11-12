package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.GlassLevel
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glass

/**
 * The 2026 top bar (RD frames' `.top`): 56 dp, a back arrow, a title with an optional muted
 * subtitle (e.g. "Saved · 9:16 · 0:18"), then [actions] (icon buttons, then one pink pill).
 */
@Composable
fun PreCutTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    @DrawableRes backIcon: Int = R.drawable.ic_back,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(56.dp).padding(start = if (onBack != null) 4.dp else 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        onBack?.let { PreCutIconButton(backIcon, stringResource(R.string.action_back), it) }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = TextMuted, maxLines = 1) }
        }
        actions()
    }
}

/** The 2026 snackbar (RD-CMP `.snk`): raised glass, 20 dp corners, a pink text action. */
@Composable
fun PreCutSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .glass(SnackbarShape, GlassLevel.Raised, solid = true)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = TextPrimary, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        data.visuals.actionLabel?.let { label ->
            AccentTextButton(label, onClick = { data.performAction() }, color = Accent)
        }
    }
}

private val SnackbarShape = RoundedCornerShape(20.dp)

/**
 * An empty state (RD-CMP): a 32 dp pink icon, a title, one line of help and at most one pink
 * action.
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center) }
        action?.let { FilledButton(it, onAction, Modifier.padding(top = 8.dp)) }
    }
}
