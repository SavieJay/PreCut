package com.savie.precut.media.layer

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
import com.savie.precut.media.ItemSpec
import com.savie.precut.media.OverlayItemSpec
import com.savie.precut.media.TransitionLayerSpec
import com.savie.precut.media.frame.CanvasShaderProgram
import com.savie.precut.media.frame.LiveFrames
import com.savie.precut.media.look.LookKeyframes
import com.savie.precut.media.look.LookShaderProgram
import com.savie.precut.media.look.LookUniforms
import com.savie.precut.media.look.LutCache
import com.savie.precut.media.sourceUsAt
import com.savie.precut.media.speedStepsOf
import com.savie.precut.model.OutputSize
import com.savie.precut.model.motion.MotionMath
import com.savie.precut.model.motion.TransitionType
import com.savie.precut.model.frame.Affine
import com.savie.precut.model.frame.LayerOrientation
import com.savie.precut.model.look.ClipLook

/**
 * Draws picture-in-picture layers over the composed main video, bottom layer first. Each video
 * layer decodes its own file ([VideoFrameSource]); each layer is graded with its look, mirrored,
 * turned and cropped, then placed with its transform, animation and opacity on the frame — the
 * same programs and order as a main clip, so a clip looks the same on either track.
 *
 * Runs as a composition-level effect, so preview and export draw overlays identically, and the
 * player keeps a single video input (Media3 1.11's multi-input preview stalls at clip cuts).
 */
@OptIn(UnstableApi::class)
class OverlayLayersEffect(
    private val layers: List<OverlayItemSpec>,
    private val transitions: List<TransitionLayerSpec>,
    private val outputSize: OutputSize,
    private val luts: LutCache,
    private val live: LiveFrames,
    /** Loads an image layer's picture, upright (blocking; called once on the GL thread). */
    private val loadImage: (ItemSpec.Image) -> Bitmap?,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        OverlayLayersShaderProgram(context.applicationContext, layers, transitions, outputSize, luts, live, loadImage)
}

@OptIn(UnstableApi::class)
internal class OverlayLayersShaderProgram(
    private val context: Context,
    specs: List<OverlayItemSpec>,
    transitionSpecs: List<TransitionLayerSpec>,
    private val outputSize: OutputSize,
    private val luts: LutCache,
    private val live: LiveFrames,
    private val loadImage: (ItemSpec.Image) -> Bitmap?,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ false, /* texturePoolCapacity= */ 1) {

    private val copyYuv: GlProgram
    private val copy2d: GlProgram
    private val mixer: GlProgram
    private val layers = specs.map(::Layer)
    /** Each overlapping transition's incoming clip, drawn like a layer, with its type. */
    private val transitions = transitionSpecs.map { Layer(OverlayItemSpec(0L, it.item, it.startUs, it.endUs)) to it.type }
    /** The incoming clip's whole frame during a transition. */
    private var incoming = Target.NONE
    private var width = 1
    private var height = 1

    init {
        try {
            copyYuv = program(LayerShaders.COPY_YUV)
            copy2d = program(LayerShaders.COPY_2D)
            mixer = program(LayerShaders.TRANSITION)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val binding = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, binding, 0)
            layers.forEach { if (!it.isNear(presentationTimeUs)) it.releaseResources() }
            transitions.forEach { (layer, _) -> if (!layer.isNear(presentationTimeUs)) layer.releaseResources() }
            val shown = layers.mapNotNull { layer -> layer.render(presentationTimeUs)?.let { layer to it } }
            val transition = transitions.firstNotNullOfOrNull { (layer, type) ->
                layer.render(presentationTimeUs)?.let { picture -> Triple(layer, picture, type) }
            }
            transition?.let { (layer, picture, _) ->
                // The incoming clip as a whole frame (placed on its canvas), off screen.
                incoming = incoming.sized(width, height)
                GlUtil.focusFramebufferUsingCurrentContext(incoming.fbo, width, height)
                layer.place(picture, presentationTimeUs)
            }

            GlUtil.focusFramebufferUsingCurrentContext(binding[0], width, height)
            if (transition != null) {
                mix(inputTexId, incoming.texture, transition.first.progress(presentationTimeUs), transition.third)
            } else {
                draw(copy2d, inputTexId, Affine.IDENTITY)
            }
            if (shown.isEmpty()) return
            GLES20.glEnable(GLES20.GL_BLEND)
            // The layers are written with straight alpha.
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            shown.forEach { (layer, picture) -> layer.place(picture, presentationTimeUs) }
            GLES20.glDisable(GLES20.GL_BLEND)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    private var released = false

    override fun release() {
        if (released) return
        released = true
        super.release()
        layers.forEach { it.release() }
        transitions.forEach { (layer, _) -> layer.release() }
        try {
            incoming = incoming.delete()
            copyYuv.delete()
            copy2d.delete()
            mixer.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun draw(program: GlProgram, textureId: Int, map: Affine) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", textureId, 0)
        program.setFloatsUniform("uMap", map.matrix())
        program.setFloatsUniform("uOffset", map.offset())
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun mix(outgoing: Int, incomingTexture: Int, progress: Float, type: TransitionType) {
        mixer.use()
        mixer.setSamplerTexIdUniform("uA", outgoing, 0)
        mixer.setSamplerTexIdUniform("uB", incomingTexture, 1)
        mixer.setFloatUniform("uProgress", progress)
        mixer.setFloatUniform(
            "uType",
            when (type) {
                TransitionType.SLIDE -> 1f
                TransitionType.PUSH -> 2f
                TransitionType.WIPE -> 3f
                else -> 0f
            },
        )
        mixer.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun program(fragment: String): GlProgram = GlProgram(LayerShaders.VERTEX, fragment).apply {
        setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    /** A picture ready to place: [textureId] of [width] × [height]. */
    private class Picture(val textureId: Int, val width: Int, val height: Int)

    /** One overlay: its decoder or bitmap, intermediate textures and programs, made on first use. */
    private inner class Layer(private val spec: OverlayItemSpec) {
        private val item = spec.item
        private val orientation = LayerOrientation(item.visual.mirrored, item.visual.rotationDegrees, item.visual.crop)
        private var video: VideoFrameSource? = null
        private var videoFailed = false
        private var imageTexture = Target.NONE
        private var upright = Target.NONE
        private var graded = Target.NONE
        private var oriented = Target.NONE
        /** An image layer's oriented picture never changes, so it's made once. */
        private var stillReady = false
        private var look: LookShaderProgram? = null
        private var canvas: CanvasShaderProgram? = null
        private val steps = (item as? ItemSpec.Video)?.let(::speedStepsOf)

        /** Eased 0..1 through the layer's time range (for transitions). */
        fun progress(timeUs: Long): Float {
            val t = ((timeUs - spec.startUs).toFloat() / (spec.endUs - spec.startUs).coerceAtLeast(1L)).coerceIn(0f, 1f)
            return MotionMath.easeInOutCubic(t)
        }

        /** Whether the layer shows now or soon (resources are kept), with a margin for scrubbing. */
        fun isNear(timeUs: Long): Boolean = timeUs in (spec.startUs - KEEP_US) until (spec.endUs + KEEP_US)

        /** Grades, orients and returns the layer's picture at composition time [timeUs]; null when hidden. */
        fun render(timeUs: Long): Picture? {
            if (timeUs !in spec.startUs until spec.endUs) return null
            val source = when (item) {
                is ItemSpec.Video -> videoUpright(timeUs - spec.startUs) ?: return null
                is ItemSpec.Image -> {
                    if (stillReady) return oriented.picture()
                    imageUpright() ?: return null
                }
            }
            var picture = source
            if (item.visual.look != null || item.visual.chroma != null || item.visual.lookKeyframes.isNotEmpty()) {
                val lookSpec = item.visual.look ?: ClipLook()
                val animated = item.visual.lookKeyframes.takeIf { it.isNotEmpty() }?.let { LookKeyframes(lookSpec, it, item.visual.keyframesStartUs) }
                val program = look ?: LookShaderProgram(
                    LookUniforms.of(lookSpec), lookSpec.filterId?.let(luts::cube), null, item.visual.chroma, animated,
                ).also { look = it }
                graded = graded.sized(picture.width, picture.height)
                GlUtil.focusFramebufferUsingCurrentContext(graded.fbo, picture.width, picture.height)
                program.configure(picture.width, picture.height)
                program.drawFrame(picture.textureId, timeUs)
                picture = graded.picture()
            }
            val (w, h) = orientation.size(picture.width, picture.height)
            oriented = oriented.sized(w, h)
            GlUtil.focusFramebufferUsingCurrentContext(oriented.fbo, w, h)
            draw(copy2d, picture.textureId, orientation.map())
            if (item is ItemSpec.Image) stillReady = true
            return oriented.picture()
        }

        /** Places [picture] on the frame (blending is set up by the caller). */
        fun place(picture: Picture, timeUs: Long) {
            val frame = item.visual.frame ?: return
            val program = canvas ?: CanvasShaderProgram(frame, outputSize, live, picture = null).also { canvas = it }
            program.configure(picture.width, picture.height)
            program.drawFrame(picture.textureId, timeUs)
        }

        private fun videoUpright(localUs: Long): Picture? {
            if (videoFailed) return null
            val spec = item as ItemSpec.Video
            val source = video ?: VideoFrameSource.open(context, spec.uri).also {
                video = it
                if (it == null) videoFailed = true
            } ?: return null
            source.frameAt(spec.sourceUsAt(localUs, steps))
            if (!source.hasFrame) return null
            val turned = source.rotationDegrees % 180 != 0
            val w = if (turned) source.height else source.width
            val h = if (turned) source.width else source.height
            upright = upright.sized(w, h)
            GlUtil.focusFramebufferUsingCurrentContext(upright.fbo, w, h)
            val program = copyYuv
            program.use()
            program.setSamplerTexIdUniform("uY", source.planes[0], 0)
            program.setSamplerTexIdUniform("uU", source.planes[1], 1)
            program.setSamplerTexIdUniform("uV", source.planes[2], 2)
            program.setFloatsUniform("uRange", if (source.fullRange) FULL_RANGE else VIDEO_RANGE)
            program.setFloatsUniform("uCoefficients", if (source.bt709) BT709 else BT601)
            // Image rows run top-down.
            val map = Affine.unrotate(source.rotationDegrees).then(Affine.FLIP_Y)
            program.setFloatsUniform("uMap", map.matrix())
            program.setFloatsUniform("uOffset", map.offset())
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            return upright.picture()
        }

        private fun imageUpright(): Picture? {
            if (imageTexture == Target.NONE) {
                val bitmap = loadImage(item as ItemSpec.Image) ?: return null
                imageTexture = Target(GlUtil.createTexture(bitmap), fbo = 0, bitmap.width, bitmap.height)
            }
            upright = upright.sized(imageTexture.width, imageTexture.height)
            GlUtil.focusFramebufferUsingCurrentContext(upright.fbo, upright.width, upright.height)
            draw(copy2d, imageTexture.texture, Affine.FLIP_Y)
            return upright.picture()
        }

        /** Frees what's only needed while the layer is near (decoder, textures); kept: programs. */
        fun releaseResources() {
            video?.release()
            video = null
            videoFailed = false
            stillReady = false
            imageTexture = imageTexture.delete()
            upright = upright.delete()
            graded = graded.delete()
            oriented = oriented.delete()
        }

        fun release() {
            releaseResources()
            // Once each: a second release would delete GL names that may belong to someone else by then.
            look?.release()
            look = null
            canvas?.release()
            canvas = null
        }
    }

    /** A texture with its framebuffer; [NONE] before it's needed. */
    private data class Target(val texture: Int, val fbo: Int, val width: Int, val height: Int) {

        fun picture() = Picture(texture, width, height)

        /** This target at [w] × [h], made or remade as needed. */
        fun sized(w: Int, h: Int): Target {
            if (this != NONE && width == w && height == h && fbo != 0) return this
            delete()
            val texture = GlUtil.createTexture(w, h, /* useHighPrecisionColorComponents= */ false)
            return Target(texture, GlUtil.createFboForTexture(texture), w, h)
        }

        fun delete(): Target {
            if (this == NONE) return NONE
            if (fbo != 0) GlUtil.deleteFbo(fbo)
            GlUtil.deleteTexture(texture)
            return NONE
        }

        companion object {
            val NONE = Target(-1, 0, 0, 0)
        }
    }

    private companion object {
        /** A layer's decoder and textures stay this long around its time range. */
        const val KEEP_US = 2_000_000L

        /** Luma offset, luma scale, chroma scale. */
        val VIDEO_RANGE = floatArrayOf(16f / 255f, 255f / 219f, 255f / 224f)
        val FULL_RANGE = floatArrayOf(0f, 1f, 1f)

        /** Cr→R, Cb→G, Cr→G, Cb→B. */
        val BT601 = floatArrayOf(1.402f, 0.344136f, 0.714136f, 1.772f)
        val BT709 = floatArrayOf(1.5748f, 0.187324f, 0.468124f, 1.8556f)
    }
}
