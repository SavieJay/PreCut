package com.savie.precut.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.transformer.CompositionPlayer
import com.savie.precut.model.Project
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
 * Owns the preview player, fed by [CompositionBuilder] so preview matches export.
 * Must be created and used on the main thread; call [release] when done.
 */
@OptIn(ExperimentalApi::class) // CompositionPlayer is experimental in Media3 1.11; it's the only composition preview.
class PreviewController @Inject constructor(
    @ApplicationContext context: Context,
    private val compositionBuilder: CompositionBuilder,
) {

    private val compositionPlayer = CompositionPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    /** Exposed only so the UI can attach a video surface. */
    val player: Player get() = compositionPlayer

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
            if (player.isPlaying) startTicker() else stopTicker()
        }
    }

    init {
        compositionPlayer.addListener(listener)
    }

    /** Rebuilds the preview from [project], starting at [startPositionMs]. */
    fun setProject(project: Project, startPositionMs: Long = 0L) {
        val composition = compositionBuilder.build(project)
        if (composition == null) {
            // CompositionPlayer can't clear its media; stop it and report an empty timeline.
            compositionPlayer.stop()
            stopTicker()
            _state.value = PreviewState()
            return
        }
        compositionPlayer.setComposition(composition, startPositionMs.coerceAtLeast(0L))
        compositionPlayer.prepare()
    }

    fun togglePlayPause() {
        if (compositionPlayer.isPlaying) {
            compositionPlayer.pause()
        } else {
            if (compositionPlayer.playbackState == Player.STATE_ENDED) compositionPlayer.seekTo(0L)
            compositionPlayer.play()
        }
    }

    fun pause() = compositionPlayer.pause()

    fun play() = compositionPlayer.play()

    /** Silences preview playback, e.g. while recording a voiceover so the mic doesn't pick it up. */
    fun setMuted(muted: Boolean) {
        compositionPlayer.volume = if (muted) 0f else 1f
    }

    fun seekTo(positionMs: Long) {
        compositionPlayer.seekTo(positionMs)
        publishState()
    }

    fun release() {
        scope.cancel()
        compositionPlayer.removeListener(listener)
        compositionPlayer.release()
    }

    private fun publishState() {
        val duration = compositionPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        _state.value = PreviewState(
            isPlaying = compositionPlayer.isPlaying,
            positionMs = compositionPlayer.currentPosition.coerceIn(0L, duration),
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
