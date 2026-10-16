package com.savie.precut.media

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
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
 *
 * After an error the CompositionPlayer is replaced, not reused: in Media3 1.11.1 it never clears
 * its error, so it would stay idle for good.
 */
@OptIn(ExperimentalApi::class, UnstableApi::class) // CompositionPlayer is experimental in Media3 1.11; it's the only composition preview.
class PreviewController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositionBuilder: CompositionBuilder,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var stallWatch: Job? = null
    private var stallNudges = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState == Player.STATE_READY) recoveries = 0
            publishState()
            if (player.isPlaying) startTicker() else stopTicker()
            watchForStall(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Preview player failed; replacing it", error)
            // Not from inside the failing player's callback.
            scope.launch(Dispatchers.Main) { recover() }
        }
    }

    private var compositionPlayer = newPlayer()
    private val _player = MutableStateFlow<Player>(compositionPlayer)

    /** The current player, only so the UI can attach a video surface. It changes when replaced. */
    val player: StateFlow<Player> = _player.asStateFlow()

    /**
     * Whether the player has something to play. With nothing (e.g. every clip's media is
     * missing), CompositionPlayer must not be seeked: it crashes when no composition was ever set.
     */
    private var hasMedia = false
    private var composition: Composition? = null
    private var volume = 1f
    private var recoveries = 0

    /** The last position shown before any failure; a failed player reports 0. */
    private var lastGoodPositionMs = 0L

    /** The position live redraws keep returning to; null when not redrawing. */
    private var redrawBaseMs: Long? = null
    private var redrawNudged = false

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    /**
     * Rebuilds the preview from [project], starting at [startPositionMs]. [liveClipId] marks a clip
     * whose frame follows [LiveFrames] while it's being transformed.
     */
    fun setProject(project: Project, startPositionMs: Long = 0L, liveClipId: String? = null) {
        val built = compositionBuilder.build(project, PREVIEW_SHORT_SIDE_PX, liveClipId)
        if (built == null) {
            // CompositionPlayer can't clear its media; stop it and report an empty timeline.
            hasMedia = false
            composition = null
            redrawBaseMs = null
            if (compositionPlayer.isScrubbingModeEnabled) compositionPlayer.setScrubbingModeEnabled(false)
            compositionPlayer.stop()
            stopTicker()
            _state.value = PreviewState()
            return
        }
        recoveries = 0
        load(built, startPositionMs)
    }

    fun togglePlayPause() {
        if (!hasMedia) return
        endScrub()
        if (compositionPlayer.isPlaying) {
            compositionPlayer.pause()
        } else {
            if (compositionPlayer.playbackState == Player.STATE_ENDED) seekTo(0L)
            compositionPlayer.play()
        }
    }

    fun pause() = compositionPlayer.pause()

    fun play() {
        if (hasMedia) compositionPlayer.play()
    }

    /** Silences preview playback, e.g. while recording a voiceover so the mic doesn't pick it up. */
    fun setMuted(muted: Boolean) {
        volume = if (muted) 0f else 1f
        compositionPlayer.volume = volume
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
        // Scrubbing turns the sequences' audio off; one more seek re-primes it while paused, so
        // the next play doesn't start on an empty mixer (see [watchForStall]).
        compositionPlayer.seekTo(compositionPlayer.currentPosition)
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
        seekTo(target)
    }

    fun endLiveRedraw() {
        val base = redrawBaseMs ?: return
        redrawBaseMs = null
        endScrub()
        if (!hasMedia) return
        seekTo(base)
    }

    fun release() {
        scope.cancel()
        compositionPlayer.removeListener(listener)
        compositionPlayer.release()
    }

    private fun load(newComposition: Composition, positionMs: Long) {
        if (compositionPlayer.playerError != null) replacePlayer()
        composition = newComposition
        compositionPlayer.setComposition(newComposition, positionMs.coerceAtLeast(0L))
        compositionPlayer.prepare()
        hasMedia = true
    }

    /** Puts a fresh player in place of a failed one, at the last shown position, paused. */
    private fun recover() {
        val current = composition ?: return
        if (recoveries >= MAX_RECOVERIES) {
            Log.e(TAG, "Preview player keeps failing; giving up until the next edit")
            return
        }
        recoveries++
        load(current, lastGoodPositionMs)
        publishState()
    }

    private fun replacePlayer() {
        val scrubbing = compositionPlayer.isScrubbingModeEnabled
        stopTicker()
        compositionPlayer.removeListener(listener)
        compositionPlayer.release()
        compositionPlayer = newPlayer().also {
            it.volume = volume
            if (scrubbing) it.setScrubbingModeEnabled(true)
        }
        _player.value = compositionPlayer
    }

    /**
     * CompositionPlayer (1.11.1) can deadlock while playing several sequences: when one runs short
     * of audio for a moment (seen right after a seek), it pauses every sequence player until all
     * are ready again, but the main sequence's audio can't become ready while the audio-only
     * sequences that feed its mixer are paused, so playback sits in BUFFERING for good. Seeking
     * in place restarts the pipelines; it's tried a few times, later ones waiting longer.
     */
    private fun watchForStall(player: Player) {
        val stalled = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING && !player.isScrubbingMode()
        if (!stalled) {
            stallWatch?.cancel()
            stallWatch = null
            if (player.playbackState == Player.STATE_READY) stallNudges = 0
            return
        }
        if (stallWatch?.isActive == true || stallNudges >= MAX_STALL_NUDGES) return
        val positionMs = player.currentPosition
        stallWatch = scope.launch {
            delay(STALL_TIMEOUT_MS * (stallNudges + 1))
            if (player !== compositionPlayer || !player.playWhenReady || player.playbackState != Player.STATE_BUFFERING) return@launch
            // A real stall creeps a few ms at most.
            if (player.currentPosition - positionMs > STALL_PROGRESS_MS) return@launch
            Log.w(TAG, "Preview stalled while buffering at ${player.currentPosition} ms; seeking in place")
            stallNudges++
            stallWatch = null
            player.seekTo(player.currentPosition)
        }
    }

    private fun Player.isScrubbingMode() = (this as? CompositionPlayer)?.isScrubbingModeEnabled == true

    private fun newPlayer(): CompositionPlayer = CompositionPlayer.Builder(context).build().also { it.addListener(listener) }

    private fun publishState() {
        val duration = compositionPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val positionMs = compositionPlayer.currentPosition.coerceIn(0L, duration)
        if (compositionPlayer.playerError == null) lastGoodPositionMs = positionMs
        _state.value = PreviewState(
            isPlaying = compositionPlayer.isPlaying,
            positionMs = positionMs,
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
        const val TAG = "PreviewController"
        const val TICK_MS = 50L

        /** Failures in a row, without reaching ready, before the preview stops replacing its player. */
        const val MAX_RECOVERIES = 3
        /** How long playback may sit in BUFFERING without moving before a seek in place. */
        const val STALL_TIMEOUT_MS = 1_200L
        const val MAX_STALL_NUDGES = 3
        const val STALL_PROGRESS_MS = 250L
    }
}
