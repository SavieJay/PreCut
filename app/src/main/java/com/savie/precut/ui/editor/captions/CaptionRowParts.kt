package com.savie.precut.ui.editor.captions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.wordRanges
import com.savie.precut.ui.components.IconButtonStyle
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.OnAiAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary

// The caption row's text and its start/end nudges (RD-CAP2).

/** The line's text; while it is being heard, the spoken word (where it can still be found in the text) is lime. */
internal fun lineText(caption: TextOverlay, spokenWord: Int?): AnnotatedString {
    val range = spokenWord?.let { caption.caption?.wordRanges(caption.text)?.getOrNull(it) } ?: return AnnotatedString(caption.text)
    return buildAnnotatedString {
        append(caption.text)
        addStyle(SpanStyle(background = AiAccent, color = OnAiAccent), range.first, range.last + 1)
    }
}

internal val CaptionRowShape = RoundedCornerShape(16.dp)

/** "Start 0:03.2" between an earlier and a later button. */
@Composable
internal fun Nudge(label: Int, timeMs: Long, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PreCutIconButton(R.drawable.ic_remove, stringResource(R.string.captions_earlier, stringResource(label)), onEarlier, style = IconButtonStyle.Glass, small = true)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(label).uppercase(), style = PreCutType.section.copy(fontSize = 10.sp), color = TextMuted)
            Text(formatPreciseMs(timeMs), style = PreCutType.timecode, color = TextPrimary)
        }
        PreCutIconButton(R.drawable.ic_add, stringResource(R.string.captions_later, stringResource(label)), onLater, style = IconButtonStyle.Glass, small = true)
    }
}
