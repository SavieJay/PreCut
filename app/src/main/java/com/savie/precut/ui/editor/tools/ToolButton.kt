package com.savie.precut.ui.editor.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.components.ProBadge
import com.savie.precut.ui.components.pressScale
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.GlassLine
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextDisabled
import com.savie.precut.ui.theme.TextFaint
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glass

/**
 * The floating dock (RD-E03, redline 8): a 76 dp glass bar 12 dp from the screen edges with 28 dp
 * corners, holding [ToolButton]s that scroll sideways; the last 36 dp fade out while more tools
 * are off-screen.
 */
@Composable
fun ToolRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .height(76.dp)
            .glass(Radius.panel),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (scroll.canScrollForward) {
                        val fade = 36.dp.toPx()
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .horizontalScroll(scroll)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The dock's 1 × 36 dp divider between groups of tools. */
@Composable
fun ToolDivider() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 36.dp).background(GlassLine))
}

/** Icon-over-label tool: 66 × 64 with 20 dp corners, 24 dp icon, 11 sp label. */
@Composable
fun ToolButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = ToolButton(icon, stringResource(label), enabled, active = false, onClick = onClick)

/**
 * A dock tool. [active] (its panel is open) is tinted pink on a pink-16% fill; [ai] marks an AI
 * tool in lime (RD-E04b `.tool.aiT`).
 */
@Composable
fun ToolButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    /** A Pro tool for a free user: the small PRO tag (Phase 25). */
    pro: Boolean = false,
    ai: Boolean = false,
    onClick: () -> Unit,
) {
    val iconColor = when {
        !enabled -> TextFaint
        active -> Accent
        ai -> AiAccent
        else -> TextPrimary
    }
    val labelColor = when {
        !enabled -> TextDisabled
        active -> Accent
        ai -> AiAccent
        else -> TextSecondary
    }
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        modifier = Modifier
            .width(66.dp)
            .height(64.dp)
            .pressScale(interaction)
            .clip(Radius.dock)
            .then(if (active) Modifier.background(Accent.copy(alpha = 0.16f)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box {
            Icon(painterResource(icon), contentDescription = null, tint = iconColor)
            if (pro) ProBadge(Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-6).dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = labelColor,
            maxLines = 1,
        )
    }
}

/** A Pro tool: the label from resources, the PRO tag for free users; [ai] tints it lime. */
@Composable
fun ProToolButton(@DrawableRes icon: Int, @StringRes label: Int, pro: Boolean, enabled: Boolean = true, ai: Boolean = false, onClick: () -> Unit) =
    ToolButton(icon, stringResource(label), enabled, active = false, pro = pro, ai = ai, onClick = onClick)

/** An AI tool in the dock: lime icon and label. */
@Composable
fun AiToolButton(@DrawableRes icon: Int, @StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) =
    ToolButton(icon, stringResource(label), enabled, active = false, ai = true, onClick = onClick)
