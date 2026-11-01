package com.savie.precut.model.edit

import com.savie.precut.model.AspectRatio
import com.savie.precut.model.Clip
import com.savie.precut.model.ClipType
import com.savie.precut.model.Project
import com.savie.precut.model.frameRatio
import com.savie.precut.model.frame.ClipTransform
import com.savie.precut.model.frame.FrameFit
import com.savie.precut.model.frame.PathPoint
import com.savie.precut.model.frame.Reframe
import com.savie.precut.model.frame.ReframeSpeed
import com.savie.precut.model.frame.SubjectSample
import com.savie.precut.model.frame.contentAspect
import com.savie.precut.model.sourceToTimelineOffsetMs
import kotlin.math.abs

/** A keyframe this close (timeline ms) to a path point already stands for it. */
private const val SAME_MOMENT_MS = 34L

/** A filled picture must be at least this much bigger than the frame to have room to move. */
private const val MIN_ROOM = 1.01f

/** The project frame's width ÷ height. */
fun Project.frameAspect(): Float = frameRatio().let { (width, height) -> width.toFloat() / height }

/** Whether the clip, filled into a frame of [frameAspect], has room to move (else Auto reframe has nothing to do). */
fun Clip.canReframe(frameAspect: Float): Boolean =
    Reframe.filledSize(contentAspect(), frameAspect, transform.scale).let { (width, height) -> width >= MIN_ROOM || height >= MIN_ROOM }

/**
 * Auto reframe (Phase 22B): the clip filled into a frame of [frameAspect] (width ÷ height), its
 * position keyframed so the frame follows the person in [samples] at [speed]. Only the position
 * changes: other keyframed values stay, and keyframes already there keep their moments. A framing
 * that hardly moves becomes a plain offset (when the clip has no keyframes). Unchanged when nobody
 * was found, or when the filled picture is exactly the frame (there's nothing to move).
 */
fun Clip.reframed(samples: List<SubjectSample>, speed: ReframeSpeed, frameAspect: Float): Clip {
    if (!canReframe(frameAspect)) return this
    val aspect = contentAspect()
    val (width, height) = Reframe.filledSize(aspect, frameAspect, transform.scale)
    val ordered = samples.map { (if (type == ClipType.IMAGE) 0L else sourceToTimelineOffsetMs(it.sourceMs)) to it.point }
        .sortedBy { it.first }
        .distinctBy { it.first }
    val times = LongArray(ordered.size) { ordered[it].first }
    val content = ordered.map { (_, point) -> point?.let { Reframe.toContent(it, mirrored, rotationDegrees, crop) } }
    val targetX = FloatArray(ordered.size) { i -> content[i]?.let { Reframe.centringOffset(it.x, width) } ?: Float.NaN }
    val targetY = FloatArray(ordered.size) { i -> content[i]?.let { Reframe.centringOffset(it.y, height) } ?: Float.NaN }
    val path = Reframe.simplify(Reframe.path(times, targetX, targetY, speed))
    if (path.isEmpty()) return this

    // The path is worked out at the plain scale; a keyframe zoomed in or out centres the same point of the picture.
    fun placed(base: ClipTransform, at: PathPoint): ClipTransform {
        val (w, h) = Reframe.filledSize(aspect, frameAspect, base.scale)
        return base.copy(
            offsetX = Reframe.centringOffset(Reframe.centredAt(at.offsetX, width), w),
            offsetY = Reframe.centringOffset(Reframe.centredAt(at.offsetY, height), h),
        )
    }
    val start = placed(transform, path.first())
    if (path.size == 1 && keyframes.isEmpty()) return copy(fit = FrameFit.FILL, transform = start)
    val kept = keyframes.map { keyframe ->
        keyframe.copy(transform = placed(keyframe.transform, Reframe.valueAt(path, sourceToTimelineOffsetMs(keyframe.atMs))))
    }
    val keptMoments = keyframes.map { sourceToTimelineOffsetMs(it.atMs) }
    val added = path.filter { point -> keptMoments.none { abs(it - point.timeMs) <= SAME_MOMENT_MS } }
        .map { point -> snapshotAt(point.timeMs).let { it.copy(transform = placed(it.transform, point)) } }
    return copy(fit = FrameFit.FILL, transform = start, keyframes = (kept + added).sortedBy { it.atMs })
}

/** Multi-format export's shapes (Phase 26B): upright, square and wide. */
val EXPORT_FORMATS = listOf(AspectRatio.RATIO_9_16, AspectRatio.RATIO_1_1, AspectRatio.RATIO_16_9)

/**
 * This edit in another shape (Phase 26B): the frame set to [ratio] and every clip with room to move
 * reframed on its person ([samples] by clip id, from the tracker); clips without samples, or that
 * already fit, stay as they are.
 */
fun Project.formatVariant(ratio: AspectRatio, samples: Map<String, List<SubjectSample>>, speed: ReframeSpeed = ReframeSpeed.NORMAL): Project {
    val shaped = copy(aspectRatio = ratio)
    val aspect = shaped.frameAspect()
    val edits = clips.mapNotNull { clip ->
        samples[clip.id]?.takeIf { clip.canReframe(aspect) }?.let { EditAction.AutoReframe(clip.id, it, speed) }
    }
    return if (edits.isEmpty()) shaped else EditReducer.reduce(shaped, EditAction.Batch(edits))
}
