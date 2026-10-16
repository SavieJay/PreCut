package com.savie.precut.media

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.UnderlineSpan
import com.savie.precut.media.sticker.StickerFrames
import com.savie.precut.media.sticker.StickerRenderer
import com.savie.precut.media.text.TextFonts
import com.savie.precut.model.OutputSize
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.MAX_TEXT_CURVE
import com.savie.precut.model.text.TextAlign
import com.savie.precut.model.validSpans
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a text overlay into a bitmap at output resolution (X-01, X-04, X-05): font and style,
 * alignment and spacing, then background, shadow, glow, stroke and the coloured text (rich-text
 * spans included), wrapped at 90% of the frame width, or along an arc when curved. The editor's
 * on-screen box uses the same bitmap, so it matches the export.
 */
class TextOverlayRenderer @Inject constructor(private val fonts: TextFonts, private val stickers: StickerRenderer) {

    /** A GIF sticker's frames at its size; null for anything else. */
    fun stickerFrames(spec: TextSpec): StickerFrames? = spec.style.sticker?.let { stickers.frames(it, spec.textSizePx) }

    /** A sticker's picture, or [reveal] characters drawn (typewriter); the rest keep their place, invisible. Null draws all. */
    fun render(spec: TextSpec, outputSize: OutputSize, reveal: Int? = null): Bitmap {
        val style = spec.style
        style.sticker?.let { return stickers.render(it, spec.textSizePx) }
        // Effect sizes are in text-size units; this turns them into output pixels.
        val unit = if (style.textSizeSp > 0f) spec.textSizePx / style.textSizeSp else 1f
        val typeface = fonts.typeface(style.fontId, style.bold, style.italic)
        val margin = effectMargin(style) * unit
        return if (abs(style.curve) >= 1f) {
            renderCurved(spec, typeface, unit, margin, reveal)
        } else {
            renderStraight(spec, outputSize, typeface, unit, margin, reveal)
        }
    }

    private fun renderStraight(spec: TextSpec, outputSize: OutputSize, typeface: Typeface, unit: Float, margin: Float, reveal: Int?): Bitmap {
        val style = spec.style
        val maxWidth = (outputSize.width * 0.9f).toInt().coerceAtLeast(1)
        fun layout(paint: TextPaint, colours: Boolean) = StaticLayout.Builder
            .obtain(spanned(spec, unit, colours, reveal), 0, spec.text.length, paint, maxWidth)
            .setAlignment(style.align.layoutAlignment())
            .setLineSpacing(0f, style.lineSpacing.coerceIn(0.8f, 2f))
            .setIncludePad(false)
            .build()
        val fill = layout(paint(spec, typeface, spec.colorArgb), colours = true)
        val left = (0 until fill.lineCount).minOfOrNull { fill.getLineLeft(it) } ?: 0f
        val right = (0 until fill.lineCount).maxOfOrNull { fill.getLineRight(it) } ?: 0f
        val textWidth = right - left
        val padding = (style.backgroundPadding ?: (style.textSizeSp * TEXT_PADDING_RATIO)) * unit
        val width = ceil(textWidth + 2 * (padding + margin)).toInt().coerceAtLeast(1)
        val height = ceil(fill.height + 2 * (padding + margin)).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, spec, unit, RectF(margin, margin, width - margin, height - margin))
        canvas.translate(margin + padding - left, margin + padding)
        drawEffects(spec, unit) { colour, dx, dy, setUp ->
            canvas.save()
            canvas.translate(dx, dy)
            layout(paint(spec, typeface, colour).apply(setUp), colours = false).draw(canvas)
            canvas.restore()
        }
        fill.draw(canvas)
        return bitmap
    }

    /** One line along an arc (X-04c5): up for a positive curve, down for a negative one; spans drawn run by run. */
    private fun renderCurved(spec: TextSpec, typeface: Typeface, unit: Float, margin: Float, reveal: Int?): Bitmap {
        val style = spec.style
        val runs = runsOf(spec, spec.text.replace('\n', ' '), unit)
        fun runPaint(run: Run, colour: Int) = paint(spec, typeface, colour).apply(run.setUp)
        val widths = runs.map { runPaint(it, spec.colorArgb).measureText(it.text) }
        val textWidth = widths.sum().coerceAtLeast(1f)
        val sweep = Math.toRadians((abs(style.curve) / MAX_TEXT_CURVE * MAX_SWEEP_DEGREES).toDouble()).toFloat()
        val radius = textWidth / sweep
        val up = style.curve > 0f
        val path = Path().apply {
            val oval = RectF(-radius, -radius, radius, radius)
            val sweepDeg = Math.toDegrees(sweep.toDouble()).toFloat()
            if (up) addArc(oval, -90f - sweepDeg / 2, sweepDeg) else addArc(oval, 90f + sweepDeg / 2, -sweepDeg)
        }
        val bounds = RectF().also { path.computeBounds(it, true) }
        val metrics = runs.map { runPaint(it, spec.colorArgb).fontMetrics }
        val ascent = metrics.maxOfOrNull { -it.ascent } ?: 0f
        val descent = metrics.maxOfOrNull { it.descent } ?: 0f
        // Glyphs stand on the path, outwards for an arch up and inwards for an arch down.
        bounds.top -= ascent
        bounds.bottom += descent
        // The end glyphs lean out past the arc's ends.
        bounds.left -= ascent + descent
        bounds.right += ascent + descent
        bounds.inset(-margin, -margin)
        val width = ceil(bounds.width()).toInt().coerceAtLeast(1)
        val height = ceil(bounds.height()).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-bounds.left, -bounds.top)
        fun drawRuns(colourOf: (Run) -> Int, dx: Float, dy: Float, setUp: TextPaint.() -> Unit) {
            var offset = 0f
            var index = 0
            runs.forEachIndexed { i, run ->
                val shown = reveal?.let { run.text.take((it - index).coerceAtLeast(0)) } ?: run.text
                if (shown.isNotEmpty()) canvas.drawTextOnPath(shown, path, offset + dx, dy, runPaint(run, colourOf(run)).apply(setUp))
                offset += widths[i]
                index += run.text.length
            }
        }
        drawEffects(spec, unit) { colour, dx, dy, setUp -> drawRuns({ colour }, dx, dy, setUp) }
        drawRuns({ it.colour ?: spec.colorArgb }, 0f, 0f) {}
        return bitmap
    }

    /** [text] cut where rich-text spans start and end; each run with its span's colour and paint changes. */
    private fun runsOf(spec: TextSpec, text: String, unit: Float): List<Run> {
        val style = spec.style
        val spans = style.validSpans()
        val cuts = (spans.flatMap { listOf(it.start, it.end) } + 0 + text.length).filter { it in 0..text.length }.distinct().sorted()
        return cuts.zipWithNext().filter { (a, b) -> b > a }.map { (a, b) ->
            val span = spans.lastOrNull { it.start <= a && it.end >= b }
            Run(text.substring(a, b), span?.colorArgb) {
                span?.sizeSp?.let { textSize = it * unit }
                if (span != null && (span.bold != null || span.italic != null)) {
                    typeface = fonts.typeface(style.fontId, span.bold ?: style.bold, span.italic ?: style.italic)
                }
                if (span?.underline == true) isUnderlineText = true
            }
        }
    }

    private class Run(val text: String, val colour: Int?, val setUp: TextPaint.() -> Unit)

    /** Shadow, glow and stroke passes, in that order, through [draw] (colour, offset, paint set-up). */
    private fun drawEffects(spec: TextSpec, unit: Float, draw: (Int, Float, Float, TextPaint.() -> Unit) -> Unit) {
        val style = spec.style
        style.shadow?.let { shadow ->
            val radians = Math.toRadians(shadow.angleDegrees.toDouble())
            val dx = (cos(radians) * shadow.distance * unit).toFloat()
            val dy = (sin(radians) * shadow.distance * unit).toFloat()
            draw(shadow.colorArgb, dx, dy) {
                if (shadow.blur > 0f) maskFilter = BlurMaskFilter(shadow.blur * unit, BlurMaskFilter.Blur.NORMAL)
            }
        }
        style.glow?.let { glow ->
            val alpha = (glow.strength.coerceIn(0f, 1f) * 255).toInt()
            repeat(2) {
                draw((glow.colorArgb and 0x00FFFFFF) or (alpha shl 24), 0f, 0f) {
                    maskFilter = BlurMaskFilter((glow.spread * unit).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                }
            }
        }
        style.stroke?.takeIf { it.width > 0f }?.let { stroke ->
            draw(stroke.colorArgb, 0f, 0f) {
                this.style = Paint.Style.STROKE
                strokeWidth = stroke.width * unit * 2f
                strokeJoin = Paint.Join.ROUND
                strokeMiter = 2f
            }
        }
    }

    private fun drawBackground(canvas: Canvas, spec: TextSpec, unit: Float, box: RectF) {
        val colour = spec.backgroundArgb ?: return
        val style = spec.style
        val radius = (style.backgroundCorners ?: (style.textSizeSp * TEXT_CORNER_RATIO)) * unit
        val alpha = (Color.alpha(colour) * style.backgroundOpacity.coerceIn(0f, 1f)).toInt()
        canvas.drawRoundRect(box, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (colour and 0x00FFFFFF) or (alpha shl 24) })
    }

    private fun paint(spec: TextSpec, typeface: Typeface, colour: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = spec.textSizePx
        this.typeface = typeface
        color = colour
        letterSpacing = spec.style.letterSpacing * LETTER_SPACING_EM
        isUnderlineText = spec.style.underline
    }

    /**
     * The text with its spans: sizes and styles always (they change the layout); colours only for
     * the fill pass ([colours]), so effect passes keep their own colour. Characters from [reveal]
     * on are transparent.
     */
    private fun spanned(spec: TextSpec, unit: Float, colours: Boolean, reveal: Int?): CharSequence {
        val style = spec.style
        val builder = SpannableStringBuilder(spec.text)
        style.validSpans().forEach { span ->
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            if (colours) span.colorArgb?.let { builder.setSpan(ForegroundColorSpan(it), span.start, span.end, flags) }
            span.sizeSp?.let { builder.setSpan(AbsoluteSizeSpan((it * unit).toInt().coerceAtLeast(1)), span.start, span.end, flags) }
            if (span.bold != null || span.italic != null) {
                val typeface = fonts.typeface(style.fontId, span.bold ?: style.bold, span.italic ?: style.italic)
                builder.setSpan(TypefaceSpan(typeface), span.start, span.end, flags)
            }
            if (span.underline == true && !style.underline) builder.setSpan(UnderlineSpan(), span.start, span.end, flags)
        }
        if (reveal != null && reveal < spec.text.length) {
            builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), reveal.coerceAtLeast(0), spec.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }

    /** Room around the text for its stroke, shadow and glow, in text-size units. */
    private fun effectMargin(style: TextOverlay): Float = max(
        style.stroke?.width ?: 0f,
        max(
            style.shadow?.let { it.blur * 2f + it.distance } ?: 0f,
            style.glow?.let { it.spread * 2f } ?: 0f,
        ),
    )

    /** A run in a given typeface (rich-text bold/italic). */
    private class TypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = typeface
        }

        override fun updateDrawState(paint: TextPaint) {
            paint.typeface = typeface
        }
    }

    private companion object {
        /** An arch of curve 100 bends the text through this many degrees. */
        const val MAX_SWEEP_DEGREES = 300f
        /** Letter spacing 1 = this many ems. */
        const val LETTER_SPACING_EM = 0.025f
    }
}

private fun TextAlign.layoutAlignment(): Layout.Alignment = when (this) {
    TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
    TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
    TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
}

const val TEXT_PADDING_RATIO = 0.25f
const val TEXT_CORNER_RATIO = 0.2f
