package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextFaint
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glassRow
import com.savie.precut.ui.theme.tabular

/**
 * The 2026 chip (RD-TOK `.ch`): a 40 dp quiet glass pill; selected = pink with a dark label and a
 * soft glow. An optional [value] shows inline in pink (dark when selected); [dot] marks an edited
 * item. The touch target is 48 dp.
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
    val interaction = remember { MutableInteractionSource() }
    val content = when {
        !enabled -> TextFaint
        selected -> OnAccent
        else -> TextPrimary
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .pressScale(interaction)
            .then(
                if (selected) Modifier.shadow(8.dp, CircleShape, ambientColor = Color.Transparent, spotColor = Accent).clip(CircleShape).background(Accent)
                else Modifier.glassRow(CircleShape),
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
        leading?.invoke()
        Text(label, style = ChipText, color = content, maxLines = 1)
        value?.let { Text(it, style = PreCutType.labelChip.tabular(), color = if (selected) OnAccent else Accent) }
        if (dot) Box(Modifier.size(6.dp).background(if (selected) OnAccent else Accent, CircleShape))
    }
}

/** Chip labels: 14 sp semibold. */
internal val ChipText = PreCutType.labelChip.copy(fontSize = PreCutType.labelChip.fontSize * (14f / 13f), fontWeight = FontWeight.SemiBold)
