package com.savie.precut.ui.components

import com.savie.precut.ui.theme.glassRow
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/**
 * A 68 dp preset tile (RD-TOK `.tile`: voice effects, transitions, filters…): icon on a quiet glass
 * square, label below; selected = a 3 dp pink ring with a soft glow and a check in the corner.
 * [ai] gives the square a lime edge for an AI preset. [badge] is a small corner icon.
 */
@Composable
fun PresetTile(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes badge: Int? = null,
    ai: Boolean = false,
) {
    TileFrame(label, selected, onClick, modifier, ai) {
        Icon(painterResource(icon), contentDescription = null, tint = if (ai) AiAccent else TextPrimary)
        badge?.let {
            Icon(painterResource(it), contentDescription = null, tint = TextSecondary, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(12.dp))
        }
    }
}

/** The tile's square, ring, check and label around any [content] (an icon, a swatch, a thumbnail). */
@Composable
fun TileFrame(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ai: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.width(68.dp).pressScale(interaction).selectable(selected, role = Role.RadioButton, interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(68.dp)) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (selected) Modifier.shadow(10.dp, Radius.tile, ambientColor = Color.Transparent, spotColor = Accent) else Modifier)
                    .glassRow(Radius.tile)
                    .then(if (ai && !selected) Modifier.border(1.dp, AiAccent.copy(alpha = 0.5f), Radius.tile) else Modifier),
                contentAlignment = Alignment.Center,
                content = content,
            )
            if (selected) {
                Box(Modifier.matchParentSize().border(3.dp, Accent, Radius.tile))
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = OnAccent, modifier = Modifier.size(15.dp))
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
