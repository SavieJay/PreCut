package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.AccentFillPressed
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextPrimary

/** The top bar's filled pill (Export): 36 dp, AccentFill with a white label. */
@Composable
fun AccentPill(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (!enabled) Ink3 else if (pressed) AccentFillPressed else AccentFill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) OnAccent else TextDisabled)
    }
}

/** Filled AccentFill button: 40 dp, or 56 dp when [large]. */
@Composable
fun FilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentFill,
            contentColor = OnAccent,
            disabledContainerColor = Ink3,
            disabledContentColor = TextDisabled,
        ),
        contentPadding = PaddingValues(horizontal = if (large) 28.dp else 20.dp),
        modifier = modifier.height(if (large) 56.dp else 40.dp),
    ) {
        ButtonContent(text, icon, if (large) 22 else 18, large)
    }
}

/** Tonal Ink3 button (secondary actions). */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Ink3, contentColor = TextPrimary),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.height(40.dp),
    ) {
        ButtonContent(text, icon, 18, large = false)
    }
}

/** Outlined button: 40 dp, 1 dp Outline edge. */
@Composable
fun OutlinedPreCutButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.height(40.dp),
    ) {
        ButtonContent(text, icon, 18, large = false)
    }
}

/** Text action (e.g. "Apply to all", "Reset"): 40 dp, Accent by default, optional 18 dp icon. */
@Composable
fun AccentTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    color: Color = Accent,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = TextDisabled),
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = modifier.height(40.dp),
    ) {
        ButtonContent(text, icon, 18, large = false)
    }
}

/** A tool panel's Done: a 36 dp Ink3 pill with a check. */
@Composable
fun DonePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(Ink3)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.action_done), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?, iconSize: Int, large: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 10.dp else 6.dp)) {
        icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(iconSize.dp)) }
        Text(text, style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
    }
}
