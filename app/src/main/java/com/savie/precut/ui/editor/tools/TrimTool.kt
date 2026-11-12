package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.DEFAULT_IMAGE_DURATION_MS
import com.savie.precut.ui.components.formatPreciseMs
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.tabular

/**
 * T-01: the clip's whole source as a filmstrip; the kept range is ringed in pink with handles
 * outside it, the rest dimmed. The title shows the length live; the trim applies on release.
 */
@Composable
fun TrimTool(
    clip: Clip,
    loader: ThumbnailLoader,
    onTrim: (trimStartMs: Long, trimEndMs: Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by remember(clip.id, clip.trimStartMs, clip.trimEndMs) { mutableStateOf(clip.trimStartMs to clip.trimEndMs) }
    val full = if (clip.type == ClipType.IMAGE) 0L to DEFAULT_IMAGE_DURATION_MS else 0L to clip.sourceDurationMs
    ToolPanel(
        title = stringResource(R.string.trim_duration, formatSeconds(range.second - range.first)),
        onDone = onDone,
        modifier = modifier,
        actions = {
            ResetIconButton({ onTrim(full.first, full.second) }, enabled = range != full)
        },
    ) {
        TrimFilmstrip(clip, range, loader, onDrag = { range = it }, onCommit = { onTrim(range.first, range.second) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val caption = MaterialTheme.typography.bodySmall
            Text(stringResource(R.string.trim_in, formatPreciseMs(range.first)), style = caption.tabular(), color = TextMuted)
            Text(stringResource(R.string.trim_source, formatSeconds(clip.sourceDurationMs)), style = caption, color = TextMuted)
            Text(stringResource(R.string.trim_out, formatPreciseMs(range.second)), style = caption.tabular(), color = TextMuted)
        }
    }
}
