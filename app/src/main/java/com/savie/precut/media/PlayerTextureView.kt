package com.savie.precut.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer

/**
 * A [TextureView] showing the preview player, for the full-screen preview (a TextureView follows
 * view scaling, so it can be pinch-zoomed; a SurfaceView can't). `CompositionPlayer` (Media3
 * 1.11.1) only takes a SurfaceView or SurfaceHolder through the Player API, and
 * `setVideoTextureView` throws `UnsupportedOperationException`, so this gives it the texture's
 * [Surface] with `setVideoSurface`, follows size changes, and lets go when the texture does.
 * [onSurfaceReady] runs each time the player gets the surface (a paused player draws nothing on
 * a new one until it's asked to).
 */
@OptIn(UnstableApi::class, ExperimentalApi::class) // CompositionPlayer
class PlayerTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    var onSurfaceReady: () -> Unit = {}
    private var player: CompositionPlayer? = null
    private var surface: Surface? = null

    init {
        surfaceTextureListener = this
    }

    /** Shows [player] (the preview's `CompositionPlayer`); a new player takes over from the last. */
    fun bind(player: Player) {
        val next = player as? CompositionPlayer
        if (next === this.player) return
        unbind()
        this.player = next
        surfaceTexture?.let { attach(it, width, height) }
    }

    /** Lets go of the player (when the view leaves the screen). */
    fun unbind() {
        detach()
        player = null
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = attach(texture, width, height)

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        val current = surface ?: return
        if (width > 0 && height > 0) player?.setVideoSurface(current, Size(width, height))
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        detach()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    private fun attach(texture: SurfaceTexture, width: Int, height: Int) {
        val target = player ?: return
        if (width <= 0 || height <= 0 || surface != null) return
        val next = Surface(texture)
        surface = next
        target.setVideoSurface(next, Size(width, height))
        onSurfaceReady()
    }

    private fun detach() {
        val current = surface ?: return
        surface = null
        try {
            player?.clearVideoSurface(current)
        } catch (_: IllegalArgumentException) {
            // The editor's own view took the player first (it only clears its current output).
        } catch (_: IllegalStateException) {
            // The player was already released (replaced after an error).
        }
        current.release()
    }
}
