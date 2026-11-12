package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** RD-H01: a fan of three clips, "Start your first video", and a full-width pink New project pill. */
@Composable
fun EmptyProjects(onNewProject: () -> Unit, onAutoCut: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        ) {
            ClipFan()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
            }
        }
        FilledButton(
            text = stringResource(R.string.home_new_project),
            onClick = onNewProject,
            icon = R.drawable.ic_add,
            large = true,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )
        TonalButton(
            stringResource(R.string.autocut_title),
            onAutoCut,
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp).fillMaxWidth(),
            icon = R.drawable.ic_beats,
        )
    }
}

/**
 * RD-H07 / RD-H08: shown while picked or shared media is copied in, as a big pink ring around a
 * fan of clips. The importer reports no progress, so the ring sweeps instead of filling.
 */
@Composable
fun ImportingMedia(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        ImportRing()
        Text(stringResource(R.string.home_preparing), style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.rd_app_import_note), style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
    }
}
