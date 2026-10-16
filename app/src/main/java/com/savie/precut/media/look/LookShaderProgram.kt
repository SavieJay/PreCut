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
import com.savie.precut.model.frame.ChromaKey
import com.savie.precut.model.keyframe.ClipKeyframe
import com.savie.precut.model.keyframe.valuesAt
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
    private val chroma: ChromaKey? = null,
    /** Keyframes (times into the clip) animating filter strength, Adjust and chroma strength. */
    private val keyframes: List<ClipKeyframe> = emptyList(),
    private val keyframesStartUs: Long = 0L,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        // Baked/parsed here, on the GL thread, so building a composition stays cheap.
        LookShaderProgram(
            LookUniforms.of(look), look.filterId?.let(luts::cube), activeUs, chroma,
            keyframes.takeIf { it.isNotEmpty() }?.let { LookKeyframes(look, it, keyframesStartUs) },
        )
}

/** What look keyframes change per frame. */
internal class LookKeyframes(val look: ClipLook, val keyframes: List<ClipKeyframe>, val startUs: Long)

@OptIn(UnstableApi::class)
internal class LookShaderProgram(
    private val uniforms: LookUniforms,
    cube: Cube?,
    private val activeUs: LongRange?,
    private val chroma: ChromaKey? = null,
    private val animated: LookKeyframes? = null,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram
    private val lutTexture: Int
    private val lutSize: Float
    private val curveTexture: Int
    private var texelWidth = 0f
    private var texelHeight = 0f
    private var aspect = 1f
    private val chromaUniform = chroma?.let(::chromaUniform) ?: floatArrayOf(0f, 0f, -1f, 0f)

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
            val keyed = animated?.let { it.keyframes.valuesAt((presentationTimeUs - it.startUs) / 1_000) }
            val adjust = keyed?.let { LookUniforms.adjustOf(it.adjust) }
            program.setFloatsUniform("uAdjustA", adjust?.a ?: uniforms.adjustA)
            program.setFloatsUniform("uAdjustB", adjust?.b ?: uniforms.adjustB)
            program.setFloatsUniform("uAdjustC", adjust?.c ?: uniforms.adjustC)
            val seed = (presentationTimeUs / 33_333L % 997L).toFloat()
            val lutMix = animated?.let { a -> keyed?.let { LookUniforms.lutMixOf(a.look.copy(filterIntensity = it.filterIntensity)) } } ?: uniforms.lutMix
            program.setFloatsUniform("uMisc", floatArrayOf(adjust?.sharpen ?: uniforms.sharpen, lutMix, lutSize, seed))
            program.setFloatsUniform(
                "uFlags",
                floatArrayOf(if (enabled) 1f else 0f, if (uniforms.hslEnabled) 1f else 0f, if (uniforms.curveTable != null) 1f else 0f, aspect),
            )
            program.setFloatsUniform("uTexelSize", floatArrayOf(texelWidth, texelHeight))
            program.setFloatsUniform(
                "uChroma",
                if (keyed != null && chroma != null) {
                    chromaUniform(chroma.copy(intensity = keyed.chromaIntensity ?: chroma.intensity, shadow = keyed.chromaShadow ?: chroma.shadow))
                } else {
                    chromaUniform
                },
            )
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

    /** The key colour in CbCr (as the shader computes it) with the key's threshold and soft edge. */
    private fun chromaUniform(key: ChromaKey): FloatArray {
        val r = (key.colorArgb shr 16 and 0xFF) / 255f
        val g = (key.colorArgb shr 8 and 0xFF) / 255f
        val b = (key.colorArgb and 0xFF) / 255f
        val (threshold, soft) = key.thresholds()
        return floatArrayOf(-0.168736f * r - 0.331264f * g + 0.5f * b, 0.5f * r - 0.418688f * g - 0.081312f * b, threshold, soft)
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
