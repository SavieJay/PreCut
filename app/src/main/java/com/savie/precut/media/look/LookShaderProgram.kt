package com.savie.precut.media.look

import android.content.Context
import android.graphics.Bitmap
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
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.Cube

/**
 * PreCut's look effect (filter LUT with intensity, Adjust, HSL, Curves) as one GL pass. With
 * [activeUs], it only grades frames in that composition-time range (look layers); other frames
 * pass through unchanged.
 */
@OptIn(UnstableApi::class)
class LookEffect(
    private val look: ClipLook,
    private val luts: LutCache,
    private val activeUs: LongRange? = null,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        // Baked/parsed here, on the GL thread, so building a composition stays cheap.
        LookShaderProgram(LookUniforms.of(look), look.filterId?.takeIf { look.hasFilter }?.let(luts::cube), activeUs)
}

@OptIn(UnstableApi::class)
internal class LookShaderProgram(
    private val uniforms: LookUniforms,
    cube: Cube?,
    private val activeUs: LongRange?,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private val lutTexture: Int
    private val lutSize: Float
    private val curveTexture: Int
    private var texelWidth = 0f
    private var texelHeight = 0f
    private var aspect = 1f

    init {
        try {
            program = GlProgram(LookShaders.VERTEX, LookShaders.FRAGMENT)
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            // Samplers must always be bound to something; unused tables get a 1x1 texture.
            lutTexture = textureOf(cube?.let { Bitmap.createBitmap(it.toTexturePixels(), it.size * it.size, it.size, Bitmap.Config.ARGB_8888) })
            lutSize = (cube?.size ?: 2).toFloat()
            curveTexture = textureOf(uniforms.curveTable?.let(::curveBitmap))
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelWidth = 1f / inputWidth
        texelHeight = 1f / inputHeight
        aspect = inputWidth.toFloat() / inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val enabled = activeUs == null || presentationTimeUs in activeUs
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uLut", lutTexture, 1)
            program.setSamplerTexIdUniform("uCurves", curveTexture, 2)
            program.setFloatsUniform("uAdjustA", uniforms.adjustA)
            program.setFloatsUniform("uAdjustB", uniforms.adjustB)
            program.setFloatsUniform("uAdjustC", uniforms.adjustC)
            val seed = (presentationTimeUs / 33_333L % 997L).toFloat()
            program.setFloatsUniform("uMisc", floatArrayOf(uniforms.sharpen, uniforms.lutMix, lutSize, seed))
            program.setFloatsUniform(
                "uFlags",
                floatArrayOf(if (enabled) 1f else 0f, if (uniforms.hslEnabled) 1f else 0f, if (uniforms.curveTable != null) 1f else 0f, aspect),
            )
            program.setFloatsUniform("uTexelSize", floatArrayOf(texelWidth, texelHeight))
            program.setFloatsUniform("uHslHueA", uniforms.hslHue.copyOfRange(0, 4))
            program.setFloatsUniform("uHslHueB", uniforms.hslHue.copyOfRange(4, 8))
            program.setFloatsUniform("uHslSatA", uniforms.hslSaturation.copyOfRange(0, 4))
            program.setFloatsUniform("uHslSatB", uniforms.hslSaturation.copyOfRange(4, 8))
            program.setFloatsUniform("uHslLightA", uniforms.hslLightness.copyOfRange(0, 4))
            program.setFloatsUniform("uHslLightB", uniforms.hslLightness.copyOfRange(4, 8))
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
            GlUtil.deleteTexture(lutTexture)
            GlUtil.deleteTexture(curveTexture)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun textureOf(bitmap: Bitmap?): Int =
        GlUtil.createTexture(bitmap ?: Bitmap.createBitmap(intArrayOf(0xFF000000.toInt()), 1, 1, Bitmap.Config.ARGB_8888))

    private fun curveBitmap(table: FloatArray): Bitmap {
        fun byte(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val pixels = IntArray(256) { i ->
            (0xFF shl 24) or (byte(table[i * 3]) shl 16) or (byte(table[i * 3 + 1]) shl 8) or byte(table[i * 3 + 2])
        }
        return Bitmap.createBitmap(pixels, 256, 1, Bitmap.Config.ARGB_8888)
    }
}
