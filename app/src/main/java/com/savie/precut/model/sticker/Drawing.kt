package com.savie.precut.model.sticker

/**
 * One freehand stroke (S-02): points as 0..1 fractions of the frame (x right, y down), a width
 * as a fraction of the frame's short side, and a colour; an eraser stroke clears what's under it.
 */
data class DrawStroke(
    val points: List<Pair<Float, Float>>,
    val colorArgb: Int,
    val widthNorm: Float,
    val eraser: Boolean = false,
)

/** A drawing in progress: its strokes, the ones undone (for redo), and the brush. */
data class Drawing(
    val strokes: List<DrawStroke> = emptyList(),
    val undone: List<DrawStroke> = emptyList(),
    val colorArgb: Int = DEFAULT_DRAW_COLOR,
    /** Brush size in dp on a frame whose short side is 360 dp, like text sizes. */
    val size: Float = DEFAULT_DRAW_SIZE,
    val eraser: Boolean = false,
) {
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** Whether anything would be left on the frame (an eraser alone draws nothing). */
    val isBlank: Boolean get() = strokes.none { !it.eraser }

    /** Adds a finished stroke drawn with the current brush; a new stroke clears the redo list. */
    fun withStroke(points: List<Pair<Float, Float>>): Drawing {
        if (points.isEmpty()) return this
        val stroke = DrawStroke(points, colorArgb, size / DRAW_REFERENCE_WIDTH, eraser)
        return copy(strokes = strokes + stroke, undone = emptyList())
    }

    fun undo(): Drawing = if (!canUndo) this else copy(strokes = strokes.dropLast(1), undone = undone + strokes.last())

    fun redo(): Drawing = if (!canRedo) this else copy(strokes = strokes + undone.last(), undone = undone.dropLast(1))
}

const val MIN_DRAW_SIZE = 2f
const val MAX_DRAW_SIZE = 48f
const val DEFAULT_DRAW_SIZE = 12f
const val DEFAULT_DRAW_COLOR = 0xFFFF4D6D.toInt()
const val DRAW_REFERENCE_WIDTH = 360f
