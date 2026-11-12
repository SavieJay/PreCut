package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.OnAiAccent
import com.savie.precut.ui.theme.PreCutType

private val AiChipFill = Brush.linearGradient(listOf(AiAccent.copy(alpha = 0.16f), AiAccent.copy(alpha = 0.05f)))

/**
 * A chip for an AI action (RD-TOK `.ai`): 36 dp, a faint lime gradient with a lime edge and lime
 * label; [selected] fills it lime with a dark label. Lime marks AI only: never a main action.
 */
@Composable
fun AiChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_auto_awesome,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val content = if (selected) OnAiAccent else AiAccent
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(36.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(AiAccent) else Modifier.background(AiChipFill).border(1.dp, AiAccent.copy(alpha = 0.42f), CircleShape))
            .selectable(selected, enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(label, style = PreCutType.labelChip.copy(fontWeight = FontWeight.SemiBold), color = content, maxLines = 1)
    }
}

/** The small lime tag beside an AI feature's name ("AI", "ON-DEVICE", "CLOUD"): 20 dp, caps. */
@Composable
fun AiTag(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = R.drawable.ic_auto_awesome) {
    Row(
        modifier.height(20.dp).background(AiAccent.copy(alpha = 0.14f), CircleShape).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = AiAccent, modifier = Modifier.size(12.dp)) }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = AiAccent,
            maxLines = 1,
        )
    }
}
