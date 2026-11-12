package com.savie.precut.ui.editor.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.AiTag
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.ProBadge
import com.savie.precut.ui.components.TileGrid
import com.savie.precut.ui.editor.EditorSheet
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.AiContainer
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** One AI tool PreCut has: where it runs ([cloud]), whether it's a Pro tool, and the dock action it runs. */
private class AiTool(
    @DrawableRes val icon: Int,
    @StringRes val name: Int,
    @StringRes val about: Int,
    val cloud: Boolean,
    val pro: Boolean,
    val run: () -> Unit,
)

/**
 * The AI tools sheet (RD-AI1, Phase 29D): every AI tool PreCut already has, as lime cards with
 * where it runs. A card closes the sheet and does exactly what that tool's dock button does
 * (consent, paywall and all). [free] shows PRO on Pro tools, as the dock does.
 */
@Composable
fun AiToolsSheet(actions: ToolbarActions, enabled: Boolean, free: Boolean, onDismiss: () -> Unit) {
    val tools = listOf(
        AiTool(R.drawable.ic_auto_awesome, R.string.tool_ai_edit, R.string.ai_tools_ai_edit, cloud = true, pro = false, actions::onAiEdit),
        AiTool(R.drawable.ic_closed_caption, R.string.tool_captions, R.string.ai_tools_captions, cloud = true, pro = false, actions::onCaptions),
        AiTool(R.drawable.ic_frame_person, R.string.tool_cutout, R.string.ai_tools_cutout, cloud = false, pro = false, actions::onCutout),
        AiTool(R.drawable.ic_reframe, R.string.tool_reframe, R.string.ai_tools_reframe, cloud = false, pro = false, actions::onReframe),
        AiTool(R.drawable.ic_text_fields, R.string.tool_transcript, R.string.ai_tools_transcript, cloud = true, pro = true, actions::onTranscript),
        AiTool(R.drawable.ic_splitscreen, R.string.tool_shorts, R.string.ai_tools_shorts, cloud = false, pro = true, actions::onShorts),
        AiTool(R.drawable.ic_search, R.string.tool_broll, R.string.ai_tools_broll, cloud = false, pro = true, actions::onBroll),
    )
    EditorSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ai_tools_title), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            AiTag(stringResource(R.string.ai_tools_tag))
            Spacer(Modifier.weight(1f))
            PreCutIconButton(R.drawable.ic_close, stringResource(R.string.action_close), onDismiss)
        }
        Text(stringResource(R.string.ai_tools_subtitle), style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
        TileGrid(tools, columns = 2, modifier = Modifier.padding(horizontal = 16.dp)) { tool ->
            AiToolCard(tool, enabled, free) { onDismiss(); tool.run() }
        }
        Text(stringResource(R.string.ai_tools_cloud_note), style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp))
    }
}

private val CardShape = RoundedCornerShape(22.dp)
private val CardFill = Brush.linearGradient(listOf(AiAccent.copy(alpha = 0.10f), TextPrimary.copy(alpha = 0.03f)))

@Composable
private fun AiToolCard(tool: AiTool, enabled: Boolean, free: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, AiAccent.copy(alpha = 0.26f), CardShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(AiContainer, RoundedCornerShape(14.dp)).border(1.dp, AiAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(tool.icon), contentDescription = null, tint = AiAccent)
            }
            Spacer(Modifier.weight(1f))
            if (tool.pro && free) ProBadge()
        }
        Text(stringResource(tool.name), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(stringResource(tool.about), style = MaterialTheme.typography.bodySmall, color = TextSecondary, minLines = 2)
        AiTag(stringResource(if (tool.cloud) R.string.ai_tools_cloud else R.string.ai_tools_on_device), icon = null)
    }
}
