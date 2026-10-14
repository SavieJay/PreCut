package com.savie.precut.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CaptureVideo
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Ink2

/** Dropdown entries for adding media: optional gallery, then camera video and photo. */
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
        shape = RoundedCornerShape(12.dp),
        containerColor = Ink2,
    ) {
        onGallery?.let { gallery ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_from_gallery)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_gallery), contentDescription = null) },
                onClick = { onDismiss(); gallery() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_record_video)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_videocam), contentDescription = null) },
            onClick = { onDismiss(); onCamera(true) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.add_take_photo)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = null) },
            onClick = { onDismiss(); onCamera(false) },
        )
    }
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
