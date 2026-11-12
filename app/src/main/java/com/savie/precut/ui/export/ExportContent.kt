package com.savie.precut.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.export.ExportResolution
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutProgressRing
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.components.projectDisplayName
import com.savie.precut.ui.theme.PreCutTheme
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.afterglowBackground
import com.savie.precut.ui.theme.glassRow

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

/** EX-02 progress (RD-EX02: a big pink ring around the cover), EX-04 done and EX-05 failures. */
@Composable
fun ExportContent(state: ExportUiState, info: ExportInfo?, actions: ExportActions, recovery: ExportRecovery? = null) {
    // No Scaffold here, so the light text and icon colour is provided explicitly (else Compose's black).
    CompositionLocalProvider(LocalContentColor provides TextPrimary) {
        Column(Modifier.fillMaxSize().afterglowBackground().statusBarsPadding().navigationBarsPadding()) {
            when (state) {
                ExportUiState.Idle, is ExportUiState.Exporting, ExportUiState.Saving -> Progress(state, info, actions)
                is ExportUiState.Done -> Done(state, info, actions)
                is ExportUiState.Failed -> Failed(state, recovery, actions)
            }
        }
    }
}

@Composable
private fun ColumnScope.Progress(state: ExportUiState, info: ExportInfo?, actions: ExportActions) {
    val percent = (state as? ExportUiState.Exporting)?.percent
    val saving = state == ExportUiState.Saving
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!saving) PreCutIconButton(R.drawable.ic_close, stringResource(R.string.action_cancel), actions::onCancel) else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                when {
                    saving -> stringResource(R.string.export_saving)
                    info != null -> stringResource(R.string.export_exporting_named, projectDisplayName(info.name, info.createdAt))
                    else -> stringResource(R.string.export_in_progress)
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            info?.let { Text(qualityOf(it), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = TextMuted, maxLines = 1) }
        }
    }
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            if (percent == null && !saving) {
                PreCutSpinner(size = 300.dp)
            } else {
                PreCutProgressRing(if (saving) 1f else (percent ?: 0) / 100f, size = 300.dp, stroke = 10.dp)
            }
            Cover(info?.coverPath, Modifier.size(118.dp, 210.dp), radius = 18)
        }
        Text(
            if (saving) "100%" else percent?.let { stringResource(R.string.export_percent, it) }.orEmpty(),
            style = PreCutType.timecode.copy(fontSize = 44.sp, lineHeight = 48.sp),
            color = TextPrimary,
        )
        Row(
            Modifier.fillMaxWidth().glassRow(Radius.dock).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = null, tint = TextSecondary)
            Text(stringResource(R.string.export_keep_open), style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Start)
        }
    }
    Spacer(Modifier.height(40.dp))
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
