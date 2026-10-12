package com.savie.precut.media

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Owns the preview player. Must be created and used on the main thread; call [release] when done.
 * Phase 3 swaps the ExoPlayer for a CompositionPlayer fed by CompositionBuilder.
 */
class PreviewController @Inject constructor(@ApplicationContext context: Context) {

    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    /** Exposed only so the UI can attach a video surface. */
    val player: Player get() = exoPlayer

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
            if (player.isPlaying) startTicker() else stopTicker()
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    fun setMedia(uri: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0L)
            exoPlayer.play()
        }
    }

    fun pause() = exoPlayer.pause()

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        publishState()
    }

    fun release() {
        scope.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    private fun publishState() {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        _state.value = PreviewState(
            isPlaying = exoPlayer.isPlaying,
            positionMs = exoPlayer.currentPosition.coerceIn(0L, duration),
            durationMs = duration,
        )
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                publishState()
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val TICK_MS = 50L
    }
}
