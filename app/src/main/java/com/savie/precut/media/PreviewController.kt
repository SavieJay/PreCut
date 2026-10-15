package com.savie.precut.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import com.savie.precut.media.frame.LiveFrames
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
    /** A timeline drag is in progress (scrubbing mode). */
    val isScrubbing: Boolean = false,
)

/**
 * The preview renders at this short side (export uses the project's own size). Texts scale with
 * the short side, so the picture looks the same with far fewer pixels to process per frame.
 */
const val PREVIEW_SHORT_SIDE_PX = 720

/**
 * Owns the preview player, fed by [CompositionBuilder] so preview matches export.
 * Must be created and used on the main thread; call [release] when done.
 */
@OptIn(ExperimentalApi::class, UnstableApi::class) // CompositionPlayer is experimental in Media3 1.11; it's the only composition preview.
class PreviewController @Inject constructor(
    @ApplicationContext context: Context,
    private val compositionBuilder: CompositionBuilder,
) {

    // Multiple-input graph: overlays are extra video sequences composited together (Phase 9).
    private val compositionPlayer = CompositionPlayer.Builder(context)
        .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    /**
     * Whether the player has something to play. With nothing (e.g. every clip's media is
     * missing), CompositionPlayer must not be seeked: it crashes when no composition was ever set.
     */
    private var hasMedia = false

    /** The position live redraws keep returning to; null when not redrawing. */
    private var redrawBaseMs: Long? = null
    private var redrawNudged = false

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

    /**
     * Rebuilds the preview from [project], starting at [startPositionMs]. [liveClipId] marks a clip
     * whose frame follows [LiveFrames] while it's being transformed.
     */
    fun setProject(project: Project, startPositionMs: Long = 0L, liveClipId: String? = null) {
        val composition = compositionBuilder.build(project, PREVIEW_SHORT_SIDE_PX, liveClipId)
        if (composition == null) {
            // CompositionPlayer can't clear its media; stop it and report an empty timeline.
            hasMedia = false
            redrawBaseMs = null
            if (compositionPlayer.isScrubbingModeEnabled) compositionPlayer.setScrubbingModeEnabled(false)
            compositionPlayer.stop()
            stopTicker()
            _state.value = PreviewState()
            return
        }
        compositionPlayer.setComposition(composition, startPositionMs.coerceAtLeast(0L))
        compositionPlayer.prepare()
        hasMedia = true
    }

    fun togglePlayPause() {
        if (!hasMedia) return
        endScrub()
        if (compositionPlayer.isPlaying) {
            compositionPlayer.pause()
        } else {
            if (compositionPlayer.playbackState == Player.STATE_ENDED) compositionPlayer.seekTo(0L)
            compositionPlayer.play()
        }
    }

    fun pause() = compositionPlayer.pause()

    fun play() {
        if (hasMedia) compositionPlayer.play()
    }

    /** Silences preview playback, e.g. while recording a voiceover so the mic doesn't pick it up. */
    fun setMuted(muted: Boolean) {
        compositionPlayer.volume = if (muted) 0f else 1f
    }

    /**
     * Starts a timeline drag: pauses and switches the player to scrubbing mode, which keeps
     * decoders warm and drops seeks that arrive while a frame is still being rendered, so the
     * preview follows the finger instead of falling behind a queue of exact seeks.
     */
    fun startScrub() {
        if (!hasMedia || compositionPlayer.isScrubbingModeEnabled) return
        compositionPlayer.pause()
        compositionPlayer.setScrubbingModeEnabled(true)
        publishState()
    }

    /** Ends a timeline drag; the player lands exactly on the last requested position. */
    fun endScrub() {
        if (!compositionPlayer.isScrubbingModeEnabled) return
        compositionPlayer.setScrubbingModeEnabled(false)
        publishState()
    }

    fun seekTo(positionMs: Long) {
        if (!hasMedia) return
        // The timeline can be longer than what plays (missing media is left out of the preview).
        val duration = compositionPlayer.duration.takeIf { it != C.TIME_UNSET }
        compositionPlayer.seekTo(if (duration != null) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L))
        publishState()
    }

    /** Starts redrawing the paused frame on demand ([redraw]), e.g. while a clip is transformed. */
    fun beginLiveRedraw() {
        if (!hasMedia || redrawBaseMs != null) return
        startScrub()
        redrawBaseMs = compositionPlayer.currentPosition
    }

    /**
     * Renders the paused frame again so a changed live effect shows. ExoPlayer ignores a seek to
     * the same millisecond, so this alternates between the position and 1 ms beside it (the same
     * frame). Scrubbing mode drops redraws that arrive while one is still rendering.
     */
    fun redraw() {
        if (!hasMedia) return
        val base = redrawBaseMs ?: return
        redrawNudged = !redrawNudged
        val duration = compositionPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val target = when {
            !redrawNudged -> base
            base + 1 < duration -> base + 1
            else -> (base - 1).coerceAtLeast(0L)
        }
        compositionPlayer.seekTo(target)
    }

    fun endLiveRedraw() {
        val base = redrawBaseMs ?: return
        redrawBaseMs = null
        endScrub()
        if (!hasMedia) return
        compositionPlayer.seekTo(base)
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
            isScrubbing = compositionPlayer.isScrubbingModeEnabled,
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
