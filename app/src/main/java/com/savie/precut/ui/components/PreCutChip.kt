package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.OnAccentContainer
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.tabular

/**
 * The design's chip: 32 dp pill, Ink2; selected = AccentContainer with a 1 dp Accent edge. An
 * optional [value] shows inline in Accent (white when selected); [dot] marks an edited item.
 */
@Composable
fun PreCutChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    dot: Boolean = false,
    enabled: Boolean = true,
) {
    val content = when {
        !enabled -> TextDisabled
        selected -> OnAccentContainer
        else -> TextPrimary
    }
    Row(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) AccentContainer else Ink2)
            .then(if (selected) Modifier.border(1.dp, Accent, CircleShape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
        leading?.invoke()
        Text(label, style = PreCutType.labelChip, color = content, maxLines = 1)
        value?.let { Text(it, style = PreCutType.labelChip.tabular(), color = if (selected) OnAccent else Accent) }
        if (dot) Box(Modifier.size(6.dp).background(Accent, CircleShape))
    }
}
