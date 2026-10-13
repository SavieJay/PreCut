package com.savie.precut.ui.editor.preview

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.savie.precut.R
import com.savie.precut.media.PreviewState
import com.savie.precut.ui.components.formatDurationMs

/**
 * The preview over the whole screen with system bars hidden. Tap to show or hide the controls
 * (play/pause, scrub bar, close); Back closes it. Shares the editor's player.
 */
@Composable
fun FullScreenPreview(
    player: Player,
    previewState: State<PreviewState>,
    onPlayPause: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val state = previewState.value
    HideSystemBars()
    var controls by remember { mutableStateOf(true) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        PreviewSurface(player, Modifier.fillMaxSize())
        if (controls) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()) {
                Icon(painterResource(R.drawable.ic_fullscreen_exit), stringResource(R.string.preview_exit_full_screen), tint = Color.White)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .safeDrawingPadding()
                    .padding(horizontal = 12.dp),
            ) {
                val position = scrubMs ?: state.positionMs
                Slider(
                    value = position.toFloat(),
                    onValueChange = {
                        if (scrubMs == null) onScrubStart()
                        scrubMs = it.toLong()
                        onSeek(it.toLong())
                    },
                    onValueChangeFinished = {
                        scrubMs = null
                        onScrubEnd()
                    },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                            tint = Color.White,
                        )
                    }
                    Text(
                        "${formatDurationMs(position)} / ${formatDurationMs(state.durationMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** Hides the status and navigation bars while composed (swipe from an edge shows them briefly). */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
