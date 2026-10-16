package com.savie.precut.model.edit

import com.savie.precut.model.Clip
import com.savie.precut.model.Project
import com.savie.precut.model.frame.CanvasKind
import com.savie.precut.model.frame.ClipCanvas
import com.savie.precut.model.frame.ClipMask
import com.savie.precut.model.frame.CropRect
import com.savie.precut.model.frame.FrameGeometry
import com.savie.precut.model.frame.MAX_CANVAS_BLUR
import com.savie.precut.model.frame.MAX_CLIP_SCALE
import com.savie.precut.model.frame.MIN_CLIP_SCALE

internal fun Project.applyFrameEdit(action: EditAction.FrameEdit): Project = when (action) {
    is EditAction.RotateClip -> updateFrame(action.clipId) { it.rotated() }
    is EditAction.ToggleMirror -> updateFrame(action.clipId) { it.mirroredOnScreen() }
    is EditAction.SetCrop -> updateFrame(action.clipId) {
        it.copy(
            crop = action.crop?.normalizedCrop(),
            cropRatio = action.ratio,
            mirrored = action.mirrored,
            rotationDegrees = (action.rotationDegrees / 90 * 90 % 360 + 360) % 360,
        )
    }
    is EditAction.SetClipTransform -> updateFrame(action.clipId) {
        val transform = action.transform
        it.copy(
            fit = action.fit,
            transform = transform.copy(
                scale = transform.scale.coerceIn(MIN_CLIP_SCALE, MAX_CLIP_SCALE),
                rotationDegrees = FrameGeometry.normalizeDegrees(transform.rotationDegrees),
            ),
        )
    }
    is EditAction.SetOpacity -> updateFrame(action.clipId) { it.copy(opacity = action.opacity.coerceIn(0f, 1f)) }
    is EditAction.SetCanvas -> updateFrame(action.clipId) { it.copy(canvas = action.canvas.normalized()) }
    is EditAction.SetMask -> updateFrame(action.clipId) { it.copy(mask = action.mask?.normalized()) }
    is EditAction.SetChroma -> updateFrame(action.clipId) {
        it.copy(chroma = action.chroma?.let { key -> key.copy(intensity = key.intensity.coerceIn(0f, 1f), shadow = key.shadow.coerceIn(0f, 1f)) })
    }
}

private fun ClipMask.normalized(): ClipMask = copy(
    centerX = centerX.coerceIn(-1f, 1f),
    centerY = centerY.coerceIn(-1f, 1f),
    width = width.coerceIn(ClipMask.MIN_SIZE, ClipMask.MAX_SIZE),
    height = height.coerceIn(ClipMask.MIN_SIZE, ClipMask.MAX_SIZE),
    rotationDegrees = FrameGeometry.normalizeDegrees(rotationDegrees),
    feather = feather.coerceIn(0f, 1f),
    cornerRadius = cornerRadius.coerceIn(0f, 1f),
)

/** A further 90° clockwise; the crop and its ratio chip turn with the picture. */
fun Clip.rotated(): Clip = copy(
    rotationDegrees = (rotationDegrees + 90) % 360,
    crop = crop?.let(FrameGeometry::rotateCrop),
    cropRatio = FrameGeometry.rotateRatio(cropRatio),
)

/**
 * Mirror happens before rotation, so on a sideways (90/270°) clip a source flip would look like an
 * up–down flip. Turning it a further 180° makes it a left–right flip on screen, as the user expects.
 */
fun Clip.mirroredOnScreen(): Clip = copy(
    mirrored = !mirrored,
    rotationDegrees = if (rotationDegrees % 180 != 0) (rotationDegrees + 180) % 360 else rotationDegrees,
    crop = crop?.let(FrameGeometry::mirrorCrop),
)

/** Inside the picture and at least [CropRect.MIN_SIDE]; a crop of everything is no crop. */
private fun CropRect.normalizedCrop(): CropRect? {
    val left = left.coerceIn(0f, 1f - CropRect.MIN_SIDE)
    val top = top.coerceIn(0f, 1f - CropRect.MIN_SIDE)
    val rect = CropRect(left, top, right.coerceIn(left + CropRect.MIN_SIDE, 1f), bottom.coerceIn(top + CropRect.MIN_SIDE, 1f))
    return rect.takeUnless { it.isFull }
}

private fun ClipCanvas.normalized(): ClipCanvas {
    val clamped = copy(blur = blur.coerceIn(1, MAX_CANVAS_BLUR))
    // An image canvas holds either a photo or a pattern.
    return if (kind == CanvasKind.IMAGE && imageUri != null) clamped.copy(pattern = null) else clamped
}

private fun Project.updateFrame(clipId: String, transform: (Clip) -> Clip): Project = updateAnyClip(clipId, transform)
