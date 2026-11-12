package com.savie.precut.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.OnAccent
import com.savie.precut.ui.theme.OnAiAccent
import com.savie.precut.ui.theme.TextFaint
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glassRow

/** The 2026 icon buttons (RD-TOK "Components"): plain, glass (Done), pink (keyframe), lime (AI). */
enum class IconButtonStyle { Plain, Glass, Pink, Lime }

/**
 * A 48 dp round icon button; [small] draws a 40 dp circle inside the same 48 dp touch target.
 * Secondary actions in the 2026 design are icon-only, so [description] is required.
 */
@Composable
fun PreCutIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.Plain,
    small: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val circle = if (small) 40.dp else 48.dp
    val (fill, content) = when (style) {
        IconButtonStyle.Plain, IconButtonStyle.Glass -> Color.Transparent to TextPrimary
        IconButtonStyle.Pink -> Accent to OnAccent
        IconButtonStyle.Lime -> AiAccent to OnAiAccent
    }
    Box(
        modifier
            .size(48.dp)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(circle)
                .then(if (style == IconButtonStyle.Glass) Modifier.glassRow(CircleShape) else Modifier.clip(CircleShape).background(fill)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = description,
                tint = if (!enabled) TextFaint else tint ?: content,
                modifier = Modifier.size(if (small) 20.dp else 24.dp),
            )
        }
    }
}
