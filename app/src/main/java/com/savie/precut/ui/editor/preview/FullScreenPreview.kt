package com.savie.precut.ui.editor.preview

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
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
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextSecondary

/**
 * The preview over the whole screen with system bars hidden (E-09). Tap toggles the controls:
 * exit at the top-left over a scrim, and at the bottom a white scrub bar with Play/Pause and time
 * (stacked in portrait, one row in landscape). Back closes it. Shares the editor's player.
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
    val position = scrubMs ?: state.positionMs
    val scrubBar = @Composable { modifier: Modifier ->
        PreCutSlider(
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
            fillColor = Color.White,
            knobSize = 16.dp,
            modifier = modifier,
        )
    }
    val playButton = @Composable {
        IconButton(onClick = onPlayPause) {
            Icon(
                painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        val landscape = maxWidth > maxHeight
        PreviewSurface(player, Modifier.fillMaxSize())
        if (!controls) return@BoxWithConstraints
        Box(Modifier.fillMaxWidth().height(if (landscape) 96.dp else 120.dp).background(Brush.verticalGradient(listOf(TopScrim, Color.Transparent))))
        IconButton(
            onClick = onClose,
            modifier = Modifier.safeDrawingPadding().padding(8.dp).background(ButtonScrim, CircleShape),
        ) {
            Icon(painterResource(R.drawable.ic_fullscreen_exit), stringResource(R.string.preview_exit_full_screen), tint = Color.White)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, BottomScrim)))
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (landscape) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    playButton()
                    Text(formatDurationMs(position), style = PreCutType.timecode, color = Color.White)
                    scrubBar(Modifier.weight(1f))
                    Text(formatDurationMs(state.durationMs), style = PreCutType.timecode, color = TextSecondary)
                }
            } else {
                scrubBar(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    playButton()
                    Text(
                        "${formatDurationMs(position)} / ${formatDurationMs(state.durationMs)}",
                        style = PreCutType.timecode,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private val TopScrim = Color(0x8C000000)
private val BottomScrim = Color(0xB3000000)
private val ButtonScrim = Color(0x990E0E10)

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
