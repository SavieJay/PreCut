package com.savie.precut.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
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
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.TextPrimary

/** RD-H02 header: 34 dp logo and "PreCut" (22/700), then AI Edit (lime: AI), the camera menu (H-06) and Settings. */
@Composable
fun HomeTopBar(cameraEnabled: Boolean, onCamera: (video: Boolean) -> Unit, onSettings: () -> Unit = {}, onAiEdit: () -> Unit = {}) {
    var cameraMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().height(60.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(34.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        PreCutIconButton(
            R.drawable.ic_auto_awesome,
            stringResource(R.string.home_ai_edit),
            onAiEdit,
            enabled = cameraEnabled,
            tint = AiAccent,
        )
        Box {
            PreCutIconButton(R.drawable.ic_photo_camera, stringResource(R.string.home_camera), { cameraMenu = true }, enabled = cameraEnabled)
            CaptureMenu(expanded = cameraMenu, onDismiss = { cameraMenu = false }, onCamera = onCamera)
        }
        PreCutIconButton(R.drawable.ic_settings, stringResource(R.string.settings_open), onSettings)
    }
}
