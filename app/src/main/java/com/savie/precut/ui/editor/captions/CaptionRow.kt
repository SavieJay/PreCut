package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.editor.tools.ToolButton
import com.savie.precut.ui.editor.tools.rememberTypedText
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/** How far one tap of a nudge button moves a caption's start or end. */
private const val NUDGE_MS = 100L

/** One caption in the panel: its start time and words; when open, its editor. */
@Composable
fun CaptionRow(caption: TextOverlay, current: Boolean, editing: Boolean, canMerge: Boolean, actions: CaptionsPanelActions) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (editing || current) Ink2 else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().clickable { actions.onRow(caption.id) }.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                formatPreciseMs(caption.startMs),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = if (current) Accent else TextSecondary,
                modifier = Modifier.width(52.dp).padding(top = 2.dp),
            )
            Text(caption.text, style = MaterialTheme.typography.bodyMedium, maxLines = if (editing) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
        }
        if (editing) CaptionLineEditor(caption, canMerge, actions)
    }
}

/** An open caption: its words, its start and end with nudges, then Split, Merge, Regenerate and Delete. */
@Composable
private fun CaptionLineEditor(caption: TextOverlay, canMerge: Boolean, actions: CaptionsPanelActions) {
    val typed = rememberTypedText(caption.id, caption.text)
    Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = typed.value,
            onValueChange = {
                typed.type(it)
                actions.onText(caption.id, it)
            },
            placeholder = { Text(stringResource(R.string.captions_line_placeholder)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Outline,
                focusedContainerColor = Ink0,
                unfocusedContainerColor = Ink0,
                cursorColor = Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Nudge(R.string.captions_start, caption.startMs, onEarlier = { actions.onNudge(caption.id, -NUDGE_MS, 0L) }, onLater = { actions.onNudge(caption.id, NUDGE_MS, 0L) })
            Nudge(R.string.captions_end, caption.endMs, onEarlier = { actions.onNudge(caption.id, 0L, -NUDGE_MS) }, onLater = { actions.onNudge(caption.id, 0L, NUDGE_MS) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(R.drawable.ic_split, R.string.captions_split, (caption.caption?.words?.size ?: 0) >= 2) { actions.onSplit(caption.id) }
            ToolButton(R.drawable.ic_merge, R.string.captions_merge, canMerge) { actions.onMerge(caption.id) }
            ToolButton(R.drawable.ic_refresh, R.string.captions_regenerate, true) { actions.onRegenerate(caption.id) }
            ToolButton(R.drawable.ic_delete, R.string.captions_delete, true) { actions.onDelete(caption.id) }
        }
    }
}

/** "Start 0:03.2" between an earlier and a later button. */
@Composable
private fun Nudge(label: Int, timeMs: Long, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEarlier) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.captions_earlier, stringResource(label)), modifier = Modifier.size(20.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(formatPreciseMs(timeMs), style = MaterialTheme.typography.labelLarge.tabular())
        }
        IconButton(onClick = onLater) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.captions_later, stringResource(label)), modifier = Modifier.size(20.dp))
        }
    }
}
