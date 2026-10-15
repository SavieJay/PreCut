package com.savie.precut.media.frame

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
import com.savie.precut.media.FrameSpec
import com.savie.precut.model.OutputSize
import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.motion.Pose
import com.savie.precut.model.frame.FrameGeometry
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Places a clip in the output frame (Fit/Fill, then the user's transform), at its opacity, over its
 * canvas: a colour, a blur of the clip filling the frame, or a picture. Replaces `Presentation`
 * for clips that aren't plainly fitted on black.
 */
@OptIn(UnstableApi::class)
class CanvasEffect(
    private val frame: FrameSpec,
    private val outputSize: OutputSize,
    private val live: LiveFrames,
    /** Loads the canvas picture (blocking; called once on the effects thread). */
    private val picture: () -> Bitmap?,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        CanvasShaderProgram(frame, outputSize, live, if (frame.canvas.kind == CanvasKind.IMAGE) picture() else null)
}

@OptIn(UnstableApi::class)
internal class CanvasShaderProgram(
    private val frame: FrameSpec,
    private val outputSize: OutputSize,
    private val live: LiveFrames,
    picture: Bitmap?,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val compose: GlProgram
    private val fill: GlProgram?
    private val blur: GlProgram?
    private val pictureTexture: Int
    private val pictureAspect: Float
    // An overlay has no canvas of its own: what's below it in the composition shows through.
    private val blurred: Boolean = frame.canvas.kind == CanvasKind.BLUR && !frame.transparent
    private var inputWidth = 1
    private var inputHeight = 1

    // Blur: a 1/8-size copy of the clip filling the frame, blurred across into B and back down into A.
    private var smallWidth = 1
    private var smallHeight = 1
    private var smallA = NO_TEXTURE
    private var smallB = NO_TEXTURE
    private var fboA = NO_TEXTURE
    private var fboB = NO_TEXTURE

    init {
        try {
            compose = program(CanvasShaders.COMPOSE)
            fill = if (blurred) program(CanvasShaders.FILL) else null
            blur = if (blurred) program(CanvasShaders.BLUR) else null
            pictureTexture = GlUtil.createTexture(picture ?: Bitmap.createBitmap(intArrayOf(BLACK), 1, 1, Bitmap.Config.ARGB_8888))
            pictureAspect = picture?.let { it.width.toFloat() / it.height } ?: 1f
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        if (blurred && smallA == NO_TEXTURE) {
            try {
                smallWidth = max(1, outputSize.width / BLUR_DOWNSCALE)
                smallHeight = max(1, outputSize.height / BLUR_DOWNSCALE)
                smallA = GlUtil.createTexture(smallWidth, smallHeight, /* useHighPrecisionColorComponents= */ false)
                smallB = GlUtil.createTexture(smallWidth, smallHeight, /* useHighPrecisionColorComponents= */ false)
                fboA = GlUtil.createFboForTexture(smallA)
                fboB = GlUtil.createFboForTexture(smallB)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
        return Size(outputSize.width, outputSize.height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (blurred) blurBackground(inputTexId)
            drawClip(inputTexId, presentationTimeUs)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private fun drawClip(inputTexId: Int, presentationTimeUs: Long) {
        val override = live.current(frame.liveKey)
        val fit = override?.fit ?: frame.fit
        val pose = frame.animation?.let { spec ->
            val atMs = (presentationTimeUs - spec.time.startUs) / 1_000
            MotionMath.pose(spec.animationIn, spec.animationOut, spec.combo, atMs, spec.time.lengthMs)
        } ?: Pose.REST
        val base = override?.transform ?: frame.transform
        val transform = ClipTransform(
            scale = base.scale * pose.scale,
            rotationDegrees = base.rotationDegrees + pose.rotationDegrees,
            offsetX = base.offsetX + pose.offsetX,
            offsetY = base.offsetY + pose.offsetY,
        )
        val opacity = (override?.opacity ?: frame.opacity) * pose.opacity.coerceIn(0f, 1f)
        val outW = outputSize.width.toFloat()
        val outH = outputSize.height.toFloat()
        val placement = FrameGeometry.placement(inputWidth.toFloat(), inputHeight.toFloat(), outW, outH, fit, transform)
        val radians = Math.toRadians(placement.rotationDegrees.toDouble())
        val canvas = frame.canvas
        val textured = canvas.kind != CanvasKind.COLOR
        compose.use()
        compose.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        compose.setSamplerTexIdUniform("uBackground", if (blurred) smallA else pictureTexture, 1)
        compose.setFloatsUniform(
            "uColor",
            if (textured) {
                floatArrayOf(0f, 0f, 0f, 1f)
            } else {
                floatArrayOf(channel(canvas.colorArgb, 16), channel(canvas.colorArgb, 8), channel(canvas.colorArgb, 0), 0f)
            },
        )
        compose.setFloatsUniform("uBackgroundScale", if (blurred) floatArrayOf(1f, 1f) else fillScale(pictureAspect, outW / outH, flipY = true))
        compose.setFloatsUniform("uOutSize", floatArrayOf(outW, outH))
        // The placement is y down; GL output space is y up.
        compose.setFloatsUniform("uCenter", floatArrayOf(placement.centerX * outW, (1f - placement.centerY) * outH))
        compose.setFloatsUniform("uHalfSize", floatArrayOf(placement.width * outW / 2f, placement.height * outH / 2f))
        compose.setFloatsUniform("uCosSin", floatArrayOf(cos(radians).toFloat(), sin(radians).toFloat()))
        compose.setFloatsUniform("uOpacity", floatArrayOf(opacity))
        compose.setFloatsUniform("uTransparent", floatArrayOf(if (frame.transparent) 1f else 0f))
        compose.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Fills smallA with the blurred clip, then puts the output framebuffer back. */
    private fun blurBackground(inputTexId: Int) {
        val fill = fill ?: return
        val blur = blur ?: return
        val binding = IntArray(1)
        val viewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, binding, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)

        GlUtil.focusFramebufferUsingCurrentContext(fboA, smallWidth, smallHeight)
        fill.use()
        fill.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        fill.setFloatsUniform("uFillScale", fillScale(inputWidth.toFloat() / inputHeight, outputSize.width.toFloat() / outputSize.height, flipY = false))
        fill.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // sigma = 4 taps, so the tap spacing sets the blur: Light 1.5 … Max 8 small pixels.
        val spacing = BLUR_SIGMAS[(frame.canvas.blur - 1).coerceIn(0, BLUR_SIGMAS.lastIndex)] / 4f
        blurPass(blur, smallA, fboB, floatArrayOf(spacing / smallWidth, 0f))
        blurPass(blur, smallB, fboA, floatArrayOf(0f, spacing / smallHeight))

        GlUtil.focusFramebufferUsingCurrentContext(binding[0], viewport[2], viewport[3])
    }

    private fun blurPass(blur: GlProgram, from: Int, toFbo: Int, step: FloatArray) {
        GlUtil.focusFramebufferUsingCurrentContext(toFbo, smallWidth, smallHeight)
        blur.use()
        blur.setSamplerTexIdUniform("uTexSampler", from, 0)
        blur.setFloatsUniform("uStep", step)
        blur.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        try {
            compose.delete()
            fill?.delete()
            blur?.delete()
            GlUtil.deleteTexture(pictureTexture)
            if (smallA != NO_TEXTURE) {
                GlUtil.deleteFbo(fboA)
                GlUtil.deleteFbo(fboB)
                GlUtil.deleteTexture(smallA)
                GlUtil.deleteTexture(smallB)
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun program(fragment: String): GlProgram = GlProgram(CanvasShaders.VERTEX, fragment).apply {
        setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        val identity = GlUtil.create4x4IdentityMatrix()
        setFloatsUniform("uTransformationMatrix", identity)
        setFloatsUniform("uTexTransformationMatrix", identity)
    }

    private companion object {
        const val NO_TEXTURE = -1
        const val BLACK = 0xFF000000.toInt()
        const val BLUR_DOWNSCALE = 8
        val BLUR_SIGMAS = floatArrayOf(1.5f, 3f, 5f, 8f)

        fun channel(argb: Int, shift: Int): Float = (argb shr shift and 0xFF) / 255f

        /**
         * Texture-coordinate scale that makes a picture of [pictureAspect] fill a frame of
         * [frameAspect] (cropping the overflowing axis); [flipY] turns a bitmap's top-down rows upright.
         */
        fun fillScale(pictureAspect: Float, frameAspect: Float, flipY: Boolean): FloatArray {
            val scale = if (pictureAspect > frameAspect) floatArrayOf(frameAspect / pictureAspect, 1f) else floatArrayOf(1f, pictureAspect / frameAspect)
            if (flipY) scale[1] = -scale[1]
            return scale
        }
    }
}
