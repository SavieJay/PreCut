package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.CaptureMenu
import com.savie.precut.ui.theme.TextPrimary

/** Logo and app name, then the camera menu (H-06). Settings (H-09) arrives in Phase 12. */
@Composable
fun HomeTopBar(cameraEnabled: Boolean, onCamera: (video: Boolean) -> Unit, onSettings: () -> Unit = {}) {
    var cameraMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { cameraMenu = true }, enabled = cameraEnabled) {
                Icon(painterResource(R.drawable.ic_camera), contentDescription = stringResource(R.string.home_camera))
            }
            CaptureMenu(expanded = cameraMenu, onDismiss = { cameraMenu = false }, onCamera = onCamera)
        }
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_open))
        }
    }
}
