package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.editor.tools.ToolButton
import com.savie.precut.ui.editor.tools.rememberTypedText
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.RowLine
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glassRow

/** How far one tap of a nudge button moves a caption's start or end. */
private const val NUDGE_MS = 100L

/**
 * One caption in the panel (RD-CAP2 `.cr`): its start time and words; the line under the playhead
 * is tinted pink with the word being spoken ([spokenWord]) in lime; when open, its editor.
 */
@Composable
fun CaptionRow(caption: TextOverlay, current: Boolean, editing: Boolean, canMerge: Boolean, actions: CaptionsPanelActions, spokenWord: Int? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CaptionRowShape)
            .then(
                when {
                    current -> Modifier.background(Accent.copy(alpha = 0.10f)).border(1.5.dp, Accent.copy(alpha = 0.6f), CaptionRowShape)
                    editing -> Modifier.glassRow(CaptionRowShape)
                    else -> Modifier
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { actions.onRow(caption.id) }.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                formatPreciseMs(caption.startMs),
                style = PreCutType.timecode.copy(fontSize = 12.sp, lineHeight = 22.sp),
                color = if (current) Accent else TextMuted,
                modifier = Modifier.width(52.dp),
            )
            Text(
                lineText(caption, if (current && !editing) spokenWord else null),
                style = MaterialTheme.typography.bodyLarge,
                color = if (current || editing) TextPrimary else TextSecondary,
                maxLines = if (editing) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
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
            shape = Radius.tile,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = RowLine,
                focusedContainerColor = RowFill,
                unfocusedContainerColor = RowFill,
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
