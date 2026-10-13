package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.savie.precut.R
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.PreCutTheme

/** Range slider over the clip's source. The trim is applied when a handle is released. */
@Composable
fun TrimTool(
    clip: Clip,
    onTrim: (trimStartMs: Long, trimEndMs: Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local drag position; re-synced whenever the applied trim changes.
    var range by remember(clip.id, clip.trimStartMs, clip.trimEndMs) {
        mutableStateOf(clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat())
    }
    ToolPanel(
        title = stringResource(R.string.trim_duration, formatDurationMs((range.endInclusive - range.start).toLong())),
        onDone = onDone,
        modifier = modifier,
    ) {
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            onValueChangeFinished = { onTrim(range.start.toLong(), range.endInclusive.toLong()) },
            valueRange = 0f..clip.sourceDurationMs.coerceAtLeast(1L).toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDurationMs(range.start.toLong()), style = MaterialTheme.typography.labelMedium)
            Text(formatDurationMs(range.endInclusive.toLong()), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview
@Composable
private fun TrimToolPreview() {
    PreCutTheme {
        TrimTool(
            clip = Clip("c", "content://c", ClipType.VIDEO, 60_000L, trimStartMs = 5_000L, trimEndMs = 42_000L),
            onTrim = { _, _ -> },
            onDone = {},
        )
    }
}
