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
import com.savie.precut.media.TransitionSpec
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.motion.TransitionLook

/**
 * A clip's edge transitions (T-16) on its placed frame: the incoming transition's second half at
 * its start, the outgoing one's first half at its end. Frames in between pass through unchanged.
 */
@OptIn(UnstableApi::class)
class TransitionEffect(private val spec: TransitionSpec) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = TransitionShaderProgram(spec)
}

@OptIn(UnstableApi::class)
internal class TransitionShaderProgram(private val spec: TransitionSpec) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private var width = 1f
    private var height = 1f

    init {
        try {
            program = GlProgram(TransitionShaders.VERTEX, TransitionShaders.FRAGMENT)
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth.toFloat()
        height = inputHeight.toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val atMs = (presentationTimeUs - spec.time.startUs) / 1_000
        val look = MotionMath.transitionAt(spec.head, spec.headMs, spec.tail, spec.tailMs, atMs, spec.time.lengthMs)
            ?.let { (type, progress) -> MotionMath.transitionLook(type, progress) }
            ?: TransitionLook.NONE
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uSize", floatArrayOf(width, height))
            program.setFloatsUniform("uScaleTurn", floatArrayOf(look.scale.coerceAtLeast(0.01f), Math.toRadians(look.rotationDegrees.toDouble()).toFloat()))
            program.setFloatsUniform("uAmounts", floatArrayOf(look.colorMix, if (look.mixWhite) 1f else 0f, look.flash, look.blur))
            // A new glitch pattern every 1/15 s.
            program.setFloatsUniform("uGlitch", floatArrayOf(look.glitch, (presentationTimeUs / 66_667L % 97L).toFloat()))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}
