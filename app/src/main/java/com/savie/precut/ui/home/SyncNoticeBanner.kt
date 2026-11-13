package com.savie.precut.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.data.sync.SyncNotice
import com.savie.precut.data.sync.SyncStatus
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.glass
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SyncNoticeViewModel @Inject constructor(status: SyncStatus) : ViewModel() {
    val notice = status.notice
}

/** Projects' note when files wait (Phase 30D): for Wi-Fi, or because cloud storage is full. */
@Composable
fun SyncNoticeBanner(viewModel: SyncNoticeViewModel = hiltViewModel()) {
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val text = syncNoticeText(notice ?: return)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).glass(Radius.card).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_cloud), contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
fun syncNoticeText(notice: SyncNotice): String = when (notice) {
    SyncNotice.WaitingForWifi -> stringResource(R.string.sync_waiting_wifi)
    is SyncNotice.StorageFull -> {
        val context = LocalContext.current
        stringResource(R.string.sync_storage_full, Formatter.formatShortFileSize(context, notice.limit))
    }
}
