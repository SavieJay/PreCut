package com.savie.precut.model.frame

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * How eagerly Auto reframe (Phase 22B) follows the person: the frame doesn't move while the person
 * stays within [deadZone] of where it points (fractions of the frame), then pans at most
 * [maxSpeedPerSecond]; the person's track is first evened out over [smoothingMs].
 */
enum class ReframeSpeed(val deadZone: Float, val maxSpeedPerSecond: Float, val smoothingMs: Long) {
    SLOW(deadZone = 0.10f, maxSpeedPerSecond = 0.15f, smoothingMs = 1_200L),
    NORMAL(deadZone = 0.07f, maxSpeedPerSecond = 0.30f, smoothingMs = 700L),
    FAST(deadZone = 0.04f, maxSpeedPerSecond = 0.60f, smoothingMs = 400L),
}

/** A point of the clip's upright picture (before mirror, turn and crop): 0..1, y down. */
data class PicturePoint(val x: Float, val y: Float)

/** Where the person is at [sourceMs] of the clip's media; null when there's none to follow. */
data class SubjectSample(val sourceMs: Long, val point: PicturePoint?)

/** The frame's place at [timeMs] (into the clip, timeline): the clip's offset as in [ClipTransform]. */
data class PathPoint(val timeMs: Long, val offsetX: Float, val offsetY: Float)

/** Auto reframe's maths: pure, so preview, export and tests agree. */
object Reframe {

    /** Points closer than this to the simplified path (fractions of the frame) are dropped. */
    const val SIMPLIFY_TOLERANCE = 0.008f

    /** [point] of the upright picture in the clip's content: mirrored, turned clockwise, then cropped (0..1, y down). */
    fun toContent(point: PicturePoint, mirrored: Boolean, rotationDegrees: Int, crop: CropRect?): PicturePoint {
        val x0 = if (mirrored) 1f - point.x else point.x
        val y0 = point.y
        val (x1, y1) = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> 1f - y0 to x0
            180 -> 1f - x0 to 1f - y0
            270 -> y0 to 1f - x0
            else -> x0 to y0
        }
        if (crop == null) return PicturePoint(x1, y1)
        return PicturePoint(
            ((x1 - crop.left) / crop.width).coerceIn(0f, 1f),
            ((y1 - crop.top) / crop.height).coerceIn(0f, 1f),
        )
    }

    /** The filled picture's width and height as fractions of the frame, at the user's [scale]. */
    fun filledSize(contentAspect: Float, frameAspect: Float, scale: Float): Pair<Float, Float> =
        scale * max(1f, contentAspect / frameAspect) to scale * max(1f, frameAspect / contentAspect)

    /** The offset that brings content coordinate [c] to the frame's centre, while the picture still covers the frame. */
    fun centringOffset(c: Float, size: Float): Float {
        if (size <= 1f) return 0f
        val reach = (size - 1f) / 2f
        return (-(c - 0.5f) * size).coerceIn(-reach, reach)
    }

    /** The content coordinate an [offset] centres, for a picture [size] times the frame. */
    fun centredAt(offset: Float, size: Float): Float = if (size <= 1f) 0.5f else 0.5f - offset / size

    /**
     * The frame's path through [times] (timeline ms, ascending) given where it should point at each
     * ([targetX], [targetY]; NaN where nobody was found): gaps hold the last place, the track is
     * evened out, then a steady camera follows it, holding still inside the dead zone and never
     * panning faster than [speed] allows. Empty when nobody was found at all.
     */
    fun path(times: LongArray, targetX: FloatArray, targetY: FloatArray, speed: ReframeSpeed): List<PathPoint> {
        require(times.size == targetX.size && times.size == targetY.size) { "Mismatched samples" }
        val first = targetX.indices.firstOrNull { !targetX[it].isNaN() && !targetY[it].isNaN() } ?: return emptyList()
        val x = filled(targetX, first)
        val y = filled(targetY, first)
        val smoothX = smoothed(times, x, speed.smoothingMs)
        val smoothY = smoothed(times, y, speed.smoothingMs)
        var cameraX = smoothX[0]
        var cameraY = smoothY[0]
        return times.indices.map { i ->
            if (i > 0) {
                val step = speed.maxSpeedPerSecond * (times[i] - times[i - 1]) / 1_000f
                cameraX = follow(cameraX, smoothX[i], speed.deadZone, step)
                cameraY = follow(cameraY, smoothY[i], speed.deadZone, step)
            }
            PathPoint(times[i], cameraX, cameraY)
        }
    }

    /**
     * [points] with every point dropped that lies within [tolerance] of a straight line between the
     * points kept around it (Ramer–Douglas–Peucker); a path that hardly moves becomes one point.
     */
    fun simplify(points: List<PathPoint>, tolerance: Float = SIMPLIFY_TOLERANCE): List<PathPoint> {
        if (points.isEmpty()) return points
        val flat = points.all { abs(it.offsetX - points[0].offsetX) <= tolerance && abs(it.offsetY - points[0].offsetY) <= tolerance }
        if (flat) return listOf(points[0])
        if (points.size == 2) return points
        val keep = BooleanArray(points.size).also { it[0] = true; it[points.lastIndex] = true }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.lastIndex) }
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var worst = -1
            var worstDistance = tolerance
            for (i in from + 1 until to) {
                val distance = distanceFromLine(points[from], points[to], points[i])
                if (distance > worstDistance) {
                    worst = i
                    worstDistance = distance
                }
            }
            if (worst >= 0) {
                keep[worst] = true
                stack.add(from to worst)
                stack.add(worst to to)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** The path's place at [timeMs], straight between its points (held before the first and after the last). */
    fun valueAt(path: List<PathPoint>, timeMs: Long): PathPoint {
        require(path.isNotEmpty()) { "Empty path" }
        if (timeMs <= path.first().timeMs) return path.first().copy(timeMs = timeMs)
        if (timeMs >= path.last().timeMs) return path.last().copy(timeMs = timeMs)
        val next = path.indexOfFirst { it.timeMs >= timeMs }
        val a = path[next - 1]
        val b = path[next]
        val p = (timeMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs)
        return PathPoint(timeMs, a.offsetX + (b.offsetX - a.offsetX) * p, a.offsetY + (b.offsetY - a.offsetY) * p)
    }

    private fun filled(values: FloatArray, first: Int): FloatArray {
        val out = values.copyOf()
        var last = values[first]
        for (i in out.indices) {
            if (out[i].isNaN()) out[i] = last else last = out[i]
        }
        return out
    }

    /** A centred moving average over [windowMs], so evening out adds no delay. */
    private fun smoothed(times: LongArray, values: FloatArray, windowMs: Long): FloatArray {
        val half = windowMs / 2
        var start = 0
        var end = 0
        var sum = 0.0
        return FloatArray(values.size) { i ->
            while (end < values.size && times[end] <= times[i] + half) sum += values[end++]
            while (times[start] < times[i] - half) sum -= values[start++]
            (sum / (end - start)).toFloat()
        }
    }

    /** One step of the camera toward [target]: still inside the dead zone, else up to [maxStep] toward its edge. */
    private fun follow(camera: Float, target: Float, deadZone: Float, maxStep: Float): Float {
        val error = target - camera
        if (abs(error) <= deadZone) return camera
        val wanted = error - sign(error) * deadZone
        return camera + wanted.coerceIn(-maxStep, maxStep)
    }

    /** How far [p] is from the straight line between [a] and [b] at its own time (the larger of x and y). */
    private fun distanceFromLine(a: PathPoint, b: PathPoint, p: PathPoint): Float {
        val t = if (b.timeMs == a.timeMs) 0f else (p.timeMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs)
        return max(abs(a.offsetX + (b.offsetX - a.offsetX) * t - p.offsetX), abs(a.offsetY + (b.offsetY - a.offsetY) * t - p.offsetY))
    }
}
