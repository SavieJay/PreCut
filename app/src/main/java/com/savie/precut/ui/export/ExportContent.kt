package com.savie.precut.ui.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.ui.components.OutlinedPreCutButton
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.components.projectDisplayName
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.PreCutTheme
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.tabular

/** What the export screens can do. */
interface ExportActions {
    fun onCancel()
    fun onRetry()
    fun onOpen(uri: String)
    fun onShare(uri: String)
    fun onBackToEdit()
    fun onProjects()
    /** EX-05: retry one resolution lower. */
    fun onTryLower()
    /** EX-05b: clear the preview cache, then retry. */
    fun onClearCache()
}

/** What the failure screens need to explain themselves: the resolution tried, the lower one, the cache size. */
data class ExportRecovery(val resolution: ExportResolution, val lower: ExportResolution, val cacheBytes: Long)

/** EX-02 progress (ring around the cover), EX-04 done (cover, Open, Share) and failures. */
@Composable
fun ExportContent(state: ExportUiState, info: ExportInfo?, actions: ExportActions, recovery: ExportRecovery? = null) {
    Column(Modifier.fillMaxSize().background(Ink0).statusBarsPadding().navigationBarsPadding()) {
        when (state) {
            ExportUiState.Idle, is ExportUiState.Exporting, ExportUiState.Saving -> Progress(state, info, actions)
            is ExportUiState.Done -> Done(state, info, actions)
            is ExportUiState.Failed -> Failed(state, recovery, actions)
        }
    }
}

@Composable
private fun ColumnScope.Progress(state: ExportUiState, info: ExportInfo?, actions: ExportActions) {
    val percent = (state as? ExportUiState.Exporting)?.percent
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            if (percent == null) {
                CircularProgressIndicator(Modifier.size(240.dp), color = Accent, trackColor = Ink3, strokeWidth = 9.dp)
            } else {
                Canvas(Modifier.size(240.dp)) {
                    val stroke = 9.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(Ink3, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(Accent, -90f, 360f * percent / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Cover(info?.coverPath, Modifier.size(116.dp, 184.dp))
            Box(Modifier.size(116.dp, 184.dp).clip(RoundedCornerShape(12.dp)).background(Ink0.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Text(
                    if (state == ExportUiState.Saving) "100%" else percent?.let { stringResource(R.string.export_percent, it) }.orEmpty(),
                    style = MaterialTheme.typography.displaySmall.tabular(),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    state == ExportUiState.Saving -> stringResource(R.string.export_saving)
                    info != null -> stringResource(R.string.export_exporting_named, projectDisplayName(info.name, info.createdAt))
                    else -> stringResource(R.string.export_in_progress)
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
            )
            info?.let {
                val quality = if (it.audio) formatDurationMs(it.durationMs) else stringResource(R.string.export_quality, stringResource(it.settings.resolution.label()), it.settings.frameRate, formatDurationMs(it.durationMs))
                Text(quality, style = MaterialTheme.typography.bodyMedium.tabular(), color = TextSecondary)
            }
            Text(stringResource(R.string.export_keep_open), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        }
    }
    if (state != ExportUiState.Saving) {
        OutlinedPreCutButton(stringResource(R.string.action_cancel), actions::onCancel, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 56.dp))
    } else {
        Spacer(Modifier.height(96.dp))
    }
}

@Preview
@Composable
private fun ExportContentPreview() {
    PreCutTheme {
        ExportContent(
            ExportUiState.Exporting(64),
            ExportInfo("Lagos weekend", 0L, 18_000L, null),
            object : ExportActions {
                override fun onCancel() = Unit
                override fun onRetry() = Unit
                override fun onOpen(uri: String) = Unit
                override fun onShare(uri: String) = Unit
                override fun onBackToEdit() = Unit
                override fun onProjects() = Unit
                override fun onTryLower() = Unit
                override fun onClearCache() = Unit
            },
        )
    }
}
