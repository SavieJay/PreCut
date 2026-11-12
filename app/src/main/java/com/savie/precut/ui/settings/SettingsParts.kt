package com.savie.precut.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glass

/** A settings screen's 56 dp top bar: a round back button and a 20/26 title. */
@Composable
internal fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PreCutIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private val RowDivider = Color.White.copy(alpha = 0.06f)
private val AiGroupLine = AiAccent.copy(alpha = 0.25f)

/**
 * RD-H09: a settings group, an uppercase label (or just a gap when [title] is null) over a glass card (24 dp corners) holding its
 * rows. [ai] groups take a lime label and a lime hairline.
 */
@Composable
internal fun SettingsGroup(title: String?, ai: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(
            title.uppercase(),
            style = PreCutType.section,
            color = if (ai) AiAccent else TextMuted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        )
    } else {
        Spacer(Modifier.height(14.dp))
    }
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .glass(Radius.card)
            .then(if (ai) Modifier.border(1.dp, AiGroupLine, Radius.card) else Modifier),
        content = content,
    )
}

/**
 * A row in a [SettingsGroup] (`.li`): 54 dp, icon, title (15/20) over an optional muted line, then
 * a trailing control or a chevron. A hairline separates it from the row above; on the first row
 * it sits under the card's own top edge.
 */
@Composable
internal fun SettingRow(
    icon: Int,
    title: String,
    value: String?,
    chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    ai: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .drawBehind { drawLine(RowDivider, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (ai) AiAccent else TextSecondary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp), color = TextPrimary)
            value?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = TextMuted) }
        }
        trailing?.invoke()
        if (chevron) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextMuted)
    }
}
