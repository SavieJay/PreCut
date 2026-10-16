package com.savie.precut.media.effect

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
import com.savie.precut.media.look.LookShaders
import com.savie.precut.model.effect.EffectParams
import com.savie.precut.model.effect.EffectSettings

/**
 * A video effect (Phase 11) as one GL pass. It animates from [timeOriginUs] (composition time)
 * and, for an effect layer, only changes frames from [startUs] until [endUs]; other frames pass
 * through.
 */
@OptIn(UnstableApi::class)
class VideoEffectGl(
    private val settings: EffectSettings,
    private val timeOriginUs: Long,
    private val startUs: Long? = null,
    private val endUs: Long? = null,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        EffectShaderProgram(settings, timeOriginUs, startUs, endUs)
}

@OptIn(UnstableApi::class)
internal class EffectShaderProgram(
    private val settings: EffectSettings,
    private val timeOriginUs: Long,
    private val startUs: Long?,
    private val endUs: Long?,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private val params = EffectParams.of(settings)
    private var texel = floatArrayOf(0f, 0f)
    private var aspect = 1f

    init {
        try {
            program = GlProgram(LookShaders.VERTEX, EffectShaders.FRAGMENT)
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texel = floatArrayOf(1f / inputWidth, 1f / inputHeight)
        aspect = inputWidth.toFloat() / inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uType", settings.type.shaderId.toFloat())
            program.setFloatsUniform("uParams", params)
            program.setFloatUniform("uTime", EffectParams.timeSeconds(presentationTimeUs, timeOriginUs, settings.speed))
            program.setFloatsUniform("uTexel", texel)
            program.setFloatUniform("uAspect", aspect)
            program.setFloatUniform("uActive", if (EffectParams.isActive(presentationTimeUs, startUs, endUs)) 1f else 0f)
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
