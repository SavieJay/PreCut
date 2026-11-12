package com.savie.precut.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CaptureVideo
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.GlassLine
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.MusicBar
import com.savie.precut.ui.theme.VoiceoverBar

/**
 * Dropdown entries for adding media (RD-H06): a raised glass menu with 20 dp corners; each entry's
 * icon sits in a tinted squircle. Optional gallery, then camera video and photo.
 */
@Composable
fun CaptureMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCamera: (video: Boolean) -> Unit,
    onGallery: (() -> Unit)? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = GlassSolid,
        border = BorderStroke(1.dp, GlassLine),
        modifier = Modifier.widthIn(min = 236.dp),
    ) {
        onGallery?.let { gallery -> CaptureItem(R.string.add_from_gallery, R.drawable.ic_gallery, MusicBar) { onDismiss(); gallery() } }
        CaptureItem(R.string.add_record_video, R.drawable.ic_videocam, Accent) { onDismiss(); onCamera(true) }
        CaptureItem(R.string.add_take_photo, R.drawable.ic_camera, VoiceoverBar) { onDismiss(); onCamera(false) }
    }
}

@Composable
private fun CaptureItem(@StringRes label: Int, @DrawableRes icon: Int, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) },
        leadingIcon = {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.18f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = tint)
            }
        },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 60.dp),
    )
}

/** Launchers for the camera app; the result says whether it wrote the file. */
class CameraLaunchers(val video: (Uri) -> Unit, val photo: (Uri) -> Unit) {
    fun launch(cameraUri: String, isVideo: Boolean) {
        val uri = Uri.parse(cameraUri)
        if (isVideo) video(uri) else photo(uri)
    }
}

@Composable
fun rememberCameraLaunchers(onResult: (success: Boolean) -> Unit): CameraLaunchers {
    val video = rememberLauncherForActivityResult(
        CaptureVideo(),
        onResult,
    )
    val photo = rememberLauncherForActivityResult(
        TakePicture(),
        onResult,
    )
    return CameraLaunchers(video::launch, photo::launch)
}
