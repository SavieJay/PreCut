package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.AccentFillPressed
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextFaint
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glassRow

/** The top bar's small pink pill (Export, Done): 40 dp, dark label, a soft pink glow. */
@Composable
fun AccentPill(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PinkPill(text, onClick, modifier, icon = null, height = 40.dp, enabled = enabled, style = MaterialTheme.typography.labelLarge)
}

/**
 * The main action: a pink pill with a dark label, 56 dp when [large] (full width at the foot of a
 * screen or sheet), else 40 dp. [destructive] makes it ErrorRed, so a delete never looks like the
 * screen's yes.
 */
@Composable
fun FilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    large: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    PinkPill(
        text, onClick, modifier, icon,
        height = if (large) 56.dp else 40.dp,
        enabled = enabled,
        style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        fill = if (destructive) ErrorRed else AccentFill,
        pressedFill = if (destructive) ErrorRed.copy(alpha = 0.85f) else AccentFillPressed,
    )
}

/** A secondary action: a quiet glass pill (white 6% with a hairline). */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    QuietPill(text, onClick, modifier, icon, TextPrimary)
}

/** Same as [TonalButton]; the 2026 design has no separate outlined style. */
@Composable
fun OutlinedPreCutButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    QuietPill(text, onClick, modifier, icon, TextPrimary)
}

/** A text action (e.g. "Apply to all", "Reset"): 48 dp tall, pink by default. */
@Composable
fun AccentTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    color: Color = Accent,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(48.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PillContent(text, icon, 18.dp, MaterialTheme.typography.labelLarge, if (enabled) color else TextDisabled)
    }
}

/** A tool panel's Done: a 48 dp glass circle with a check. */
@Composable
fun DonePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PreCutIconButton(R.drawable.ic_check, stringResource(R.string.action_done), onClick, modifier, style = IconButtonStyle.Glass)
}

@Composable
private fun PinkPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    @DrawableRes icon: Int?,
    height: Dp,
    enabled: Boolean,
    style: TextStyle,
    fill: Color = AccentFill,
    pressedFill: Color = AccentFillPressed,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(height)
            .pressScale(interaction)
            .then(if (enabled) Modifier.shadow(if (pressed) 14.dp else 10.dp, CircleShape, ambientColor = Color.Transparent, spotColor = fill) else Modifier)
            .clip(CircleShape)
            .background(if (!enabled) Ink3 else if (pressed) pressedFill else fill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (height > 40.dp) 28.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        PillContent(text, icon, if (height > 40.dp) 22.dp else 18.dp, style, if (enabled) OnAccent else TextFaint)
    }
}

@Composable
private fun QuietPill(text: String, onClick: () -> Unit, modifier: Modifier, @DrawableRes icon: Int?, color: Color) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(40.dp)
            .pressScale(interaction)
            .glassRow(CircleShape)
            .clickable(interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        PillContent(text, icon, 18.dp, MaterialTheme.typography.labelLarge, color)
    }
}

@Composable
private fun PillContent(text: String, @DrawableRes icon: Int?, iconSize: Dp, style: TextStyle, color: Color) {
    icon?.let { Icon(painterResource(it), contentDescription = null, tint = color, modifier = Modifier.size(iconSize)) }
    Text(text, style = style, color = color, maxLines = 1)
}
