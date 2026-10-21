package com.savie.precut.ui.templates

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.data.StoredTemplate
import com.savie.precut.model.outputSize
import com.savie.precut.ui.theme.TextSecondary

/**
 * Use template's pickers: media (up to [slots]), then, for "Use with a song", a song. Returns the
 * launcher: call it with whether to ask for a song.
 */
@Composable
fun rememberTemplatePicker(slots: Int, onUse: (uris: List<String>, song: String?) -> Unit): (withSong: Boolean) -> Unit {
    var withSong by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val songPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) onUse(picked, uri.toString())
        picked = emptyList()
    }
    val onPicked = { uris: List<String> ->
        if (uris.isNotEmpty() && withSong) {
            picked = uris
            songPicker.launch(arrayOf("audio/*"))
        } else {
            onUse(uris, null)
        }
    }
    val many = rememberLauncherForActivityResult(PickMultipleVisualMedia(maxItems = slots.coerceAtLeast(2))) { uris -> onPicked(uris.map(Uri::toString)) }
    val one = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> onPicked(listOfNotNull(uri?.toString())) }
    return { song ->
        withSong = song
        val request = PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)
        if (slots > 1) many.launch(request) else one.launch(request)
    }
}

/** What shows a template: its preview video looping, else its thumbnail, else its storyboard (built-in ones). */
@Composable
fun TemplatePreview(stored: StoredTemplate?, missing: Boolean) {
    when {
        missing -> Text(stringResource(R.string.template_missing), color = TextSecondary)
        stored?.previewUri != null -> LoopingVideo(stored.previewUri)
        stored?.thumbnailUri != null -> AsyncImage(model = stored.thumbnailUri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        stored != null -> {
            val size = stored.template.project.outputSize()
            TemplateStoryboard(stored.template, fontSize = 32.sp, modifier = Modifier.aspectRatio(size.width.toFloat() / size.height).clip(RoundedCornerShape(12.dp)))
        }
    }
}

/** The preview video, looping, sized to its own shape. */
@Composable
private fun LoopingVideo(uri: String) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
                setVideoURI(uri.toUri())
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
