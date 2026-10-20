package com.savie.precut.media.export

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.PassthroughShaderProgram
import com.savie.precut.model.export.FrameRateLimiter

/**
 * Lowers the export's frame rate ([FrameRateLimiter]) by passing frames through untouched or
 * dropping them. Replaces Media3's `FrameDropEffect`: in 1.11.1 its default program holds one frame
 * back and, at the end of every media item, drops the held frame and keeps using its released
 * texture, so each cut lost a frame and the rest of the video showed every picture one frame late
 * (measured by `DeviceChecksTest`). Nothing is held here, and the grid runs on across items.
 */
@OptIn(UnstableApi::class)
class FrameRateLimitEffect(private val frameRate: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FrameRateLimitShaderProgram(frameRate)
}

@OptIn(UnstableApi::class)
private class FrameRateLimitShaderProgram(frameRate: Int) : PassthroughShaderProgram() {
    private val limiter = FrameRateLimiter(frameRate)

    override fun queueInputFrame(glObjectsProvider: GlObjectsProvider, inputTexture: GlTextureInfo, presentationTimeUs: Long) {
        if (limiter.keep(presentationTimeUs)) {
            super.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeUs)
        } else {
            inputListener.onInputFrameProcessed(inputTexture)
            inputListener.onReadyToAcceptInputFrame()
        }
    }

    override fun flush() {
        limiter.reset()
        super.flush()
    }
}
