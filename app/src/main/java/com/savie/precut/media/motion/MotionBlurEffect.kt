package com.savie.precut.media.motion

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.savie.precut.model.motion.MotionBlur

/**
 * Motion blur by frame blending (CapCut Pro), last in a clip's chain: each frame is averaged with
 * up to [MotionBlur.MAX_FRAMES] − 1 earlier frames, kept as copies in its own textures. How many
 * comes from the strength and the measured frame interval ([MotionBlur.framesToBlend]); a seek
 * starts over, so a paused or scrubbed preview shows a sharp frame.
 */
@OptIn(UnstableApi::class)
class MotionBlurEffect(private val strength: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = MotionBlurShaderProgram(strength)
}

@OptIn(UnstableApi::class)
internal class MotionBlurShaderProgram(private val strength: Float) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val blend: GlProgram
    private val copy: GlProgram
    private var width = 0
    private var height = 0
    private var capacity = 0
    private val history = IntArray(MotionBlur.MAX_FRAMES - 1) { NO_TEXTURE }
    private val historyFbos = IntArray(MotionBlur.MAX_FRAMES - 1) { NO_TEXTURE }
    /** Earlier frames kept, newest at [newest]. */
    private var kept = 0
    private var newest = -1
    private var lastUs = Long.MIN_VALUE
    private var intervalUs = 0L

    init {
        try {
            blend = program(FRAGMENT_BLEND)
            copy = program(FRAGMENT_COPY)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (inputWidth != width || inputHeight != height) {
            releaseHistory()
            width = inputWidth
            height = inputHeight
            val pixels = inputWidth.toLong() * inputHeight
            capacity = (if (pixels > MotionBlur.LARGE_FRAME_PIXELS) MotionBlur.MAX_FRAMES_LARGE else MotionBlur.MAX_FRAMES) - 1
            try {
                for (i in 0 until capacity) {
                    history[i] = GlUtil.createTexture(inputWidth, inputHeight, /* useHighPrecisionColorComponents= */ false)
                    historyFbos[i] = GlUtil.createFboForTexture(history[i])
                }
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            kept = 0
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (lastUs == Long.MIN_VALUE || MotionBlur.isJump(lastUs, presentationTimeUs, intervalUs)) {
                kept = 0 // A seek or the first frame: nothing earlier to blend.
            } else {
                intervalUs = presentationTimeUs - lastUs
            }
            lastUs = presentationTimeUs
            val frames = MotionBlur.framesToBlend(strength, intervalUs, width.toLong() * height)
            drawBlend(inputTexId, MotionBlur.weights(frames, kept))
            remember(inputTexId)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    /** The current frame (sampler 0) and the newest earlier ones, equally weighted. */
    private fun drawBlend(inputTexId: Int, weights: FloatArray) {
        blend.use()
        val values = FloatArray(MotionBlur.MAX_FRAMES)
        weights.copyInto(values)
        for (i in 0 until MotionBlur.MAX_FRAMES) {
            // Unused samplers still need a texture; their weight is 0.
            val texture = if (i == 0 || i >= weights.size) inputTexId else history[(newest - (i - 1) + capacity) % capacity]
            blend.setSamplerTexIdUniform("uFrame$i", texture, i)
        }
        blend.setFloatsUniform("uWeightsA", values.copyOfRange(0, 4))
        blend.setFloatsUniform("uWeightsB", floatArrayOf(values[4], values[5]))
        blend.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Copies the current frame into the history ring, then puts the output framebuffer back. */
    private fun remember(inputTexId: Int) {
        if (capacity == 0) return
        val binding = IntArray(1)
        val viewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, binding, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
        newest = (newest + 1) % capacity
        GlUtil.focusFramebufferUsingCurrentContext(historyFbos[newest], width, height)
        copy.use()
        copy.setSamplerTexIdUniform("uFrame0", inputTexId, 0)
        copy.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.focusFramebufferUsingCurrentContext(binding[0], viewport[2], viewport[3])
        kept = minOf(kept + 1, capacity)
    }

    override fun release() {
        super.release()
        try {
            blend.delete()
            copy.delete()
            releaseHistory()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun releaseHistory() {
        for (i in history.indices) {
            if (historyFbos[i] != NO_TEXTURE) GlUtil.deleteFbo(historyFbos[i])
            if (history[i] != NO_TEXTURE) GlUtil.deleteTexture(history[i])
            history[i] = NO_TEXTURE
            historyFbos[i] = NO_TEXTURE
        }
        kept = 0
        newest = -1
    }

    private fun program(fragment: String): GlProgram = GlProgram(VERTEX, fragment).apply {
        setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        val identity = GlUtil.create4x4IdentityMatrix()
        setFloatsUniform("uTransformationMatrix", identity)
        setFloatsUniform("uTexTransformationMatrix", identity)
    }

    private companion object {
        const val NO_TEXTURE = -1

        /** Media3's vertex_shader_transformation_es2.glsl. */
        const val VERTEX = """#version 100
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

        const val FRAGMENT_COPY = """#version 100
precision mediump float;
uniform sampler2D uFrame0;
varying vec2 vTexSamplingCoord;
void main() {
  gl_FragColor = texture2D(uFrame0, vTexSamplingCoord);
}
"""

        /** Six frames (current first), weighted; weights of unused frames are 0. */
        const val FRAGMENT_BLEND = """#version 100
precision mediump float;
uniform sampler2D uFrame0;
uniform sampler2D uFrame1;
uniform sampler2D uFrame2;
uniform sampler2D uFrame3;
uniform sampler2D uFrame4;
uniform sampler2D uFrame5;
uniform vec4 uWeightsA;
uniform vec2 uWeightsB;
varying vec2 vTexSamplingCoord;
void main() {
  vec2 uv = vTexSamplingCoord;
  gl_FragColor = texture2D(uFrame0, uv) * uWeightsA.x + texture2D(uFrame1, uv) * uWeightsA.y
      + texture2D(uFrame2, uv) * uWeightsA.z + texture2D(uFrame3, uv) * uWeightsA.w
      + texture2D(uFrame4, uv) * uWeightsB.x + texture2D(uFrame5, uv) * uWeightsB.y;
}
"""
    }
}
