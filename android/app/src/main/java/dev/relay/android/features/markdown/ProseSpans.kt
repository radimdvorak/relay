package dev.relay.android.features.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import kotlin.math.min

/**
 * Sets a paragraph's exact line height (centred vertically, matching Compose's
 * LineHeightStyle.Alignment.Center) plus optional space before its first line
 * and after its last line. Must span a full paragraph boundary.
 */
internal class ParagraphMetricsSpan(
    private val lineHeightPx: Int,
    private val spaceBeforePx: Int = 0,
    private val spaceAfterPx: Int = 0,
) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)
        if (spanStart < 0 || spanEnd <= spanStart) return
        val origin = fm.descent - fm.ascent
        if (origin > 0 && lineHeightPx > origin) {
            val extra = lineHeightPx - origin
            val half = extra / 2
            fm.ascent -= half
            fm.descent += extra - half
        }
        if (start == spanStart && spaceBeforePx > 0) fm.ascent -= spaceBeforePx
        if (end >= spanEnd && spaceAfterPx > 0) fm.descent += spaceAfterPx
        fm.top = min(fm.top, fm.ascent)
        fm.bottom = maxOf(fm.bottom, fm.descent)
    }
}

/** Font size, typeface and optional tracking for headings inside a shared TextView. */
internal class HeadingSpan(
    private val face: Typeface,
    private val sizePx: Float,
    private val letterSpacing: Float = 0f,
) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) = apply(paint)
    override fun updateDrawState(paint: TextPaint) = apply(paint)
    private fun apply(paint: TextPaint) {
        paint.typeface = face
        paint.textSize = sizePx
        if (letterSpacing != 0f) paint.letterSpacing = letterSpacing
    }
}

/** Filled dot for an unordered list item, level with its first line. */
internal class BulletMarkSpan(
    private val indentPx: Int,
    private val radiusPx: Float,
    private val color: Int,
    private val bodySizePx: Float,
) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = indentPx

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        val spanned = text as? Spanned ?: return
        if (!first || start != spanned.getSpanStart(this)) return
        val oldStyle = p.style
        val oldColor = p.color
        p.style = Paint.Style.FILL
        p.color = color
        val cx = x + dir * (indentPx / 2f)
        val cy = baseline - bodySizePx * 0.32f
        c.drawCircle(cx, cy, radiusPx, p)
        p.style = oldStyle
        p.color = oldColor
    }
}

/** Right-aligned number ("1.", "10.") for an ordered list item. */
internal class NumberMarkSpan(
    private val label: String,
    private val indentPx: Int,
    private val endGapPx: Float,
    private val color: Int,
    private val face: Typeface,
    private val sizePx: Float,
) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = indentPx

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        val spanned = text as? Spanned ?: return
        if (!first || start != spanned.getSpanStart(this)) return
        val oldColor = p.color
        val oldFace = p.typeface
        val oldSize = p.textSize
        val oldFeature = p.fontFeatureSettings
        val oldStyle = p.style
        p.color = color
        p.typeface = face
        p.textSize = sizePx
        p.fontFeatureSettings = "tnum"
        p.style = Paint.Style.FILL
        val width = p.measureText(label)
        val drawX = if (dir >= 0) x + indentPx - endGapPx - width else x - indentPx + endGapPx
        c.drawText(label, drawX, baseline.toFloat(), p)
        p.color = oldColor
        p.typeface = oldFace
        p.textSize = oldSize
        p.fontFeatureSettings = oldFeature
        p.style = oldStyle
    }
}

/** Square (optionally ticked) for a GFM task list item. */
internal class TaskMarkSpan(
    private val checked: Boolean,
    private val indentPx: Int,
    private val boxSizePx: Float,
    private val strokePx: Float,
    private val cornerPx: Float,
    private val color: Int,
    private val bodySizePx: Float,
) : LeadingMarginSpan {
    private val rect = RectF()
    private val check = Path()

    override fun getLeadingMargin(first: Boolean): Int = indentPx

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        val spanned = text as? Spanned ?: return
        if (!first || start != spanned.getSpanStart(this)) return
        val oldStyle = p.style
        val oldColor = p.color
        val oldStroke = p.strokeWidth
        val oldCap = p.strokeCap
        val oldJoin = p.strokeJoin
        p.style = Paint.Style.STROKE
        p.color = color
        p.strokeWidth = strokePx
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        val left = if (dir >= 0) x.toFloat() + strokePx / 2f else x - boxSizePx - strokePx / 2f
        val cy = baseline - bodySizePx * 0.34f
        val topY = cy - boxSizePx / 2f
        rect.set(left, topY, left + boxSizePx, topY + boxSizePx)
        c.drawRoundRect(rect, cornerPx, cornerPx, p)
        if (checked) {
            check.reset()
            check.moveTo(left + boxSizePx * 0.24f, topY + boxSizePx * 0.52f)
            check.lineTo(left + boxSizePx * 0.43f, topY + boxSizePx * 0.72f)
            check.lineTo(left + boxSizePx * 0.77f, topY + boxSizePx * 0.31f)
            c.drawPath(check, p)
        }
        p.style = oldStyle
        p.color = oldColor
        p.strokeWidth = oldStroke
        p.strokeCap = oldCap
        p.strokeJoin = oldJoin
    }
}

/** Rounded vertical bar on the leading edge of a blockquote, spanning all its lines seamlessly. */
internal class QuoteBarSpan(
    private val indentPx: Int,
    private val barWidthPx: Float,
    private val radiusPx: Float,
    private val color: Int,
    private val topTrimPx: Int = 0,
    private val bottomTrimPx: Int = 0,
) : LeadingMarginSpan {
    private val rect = RectF()

    override fun getLeadingMargin(first: Boolean): Int = indentPx

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)
        val isFirst = start == spanStart
        val isLast = end >= spanEnd
        val drawTop = (top + if (isFirst) topTrimPx else 0).toFloat()
        val drawBottom = (bottom - if (isLast) bottomTrimPx else 0).toFloat()
        if (drawBottom <= drawTop) return
        val oldStyle = p.style
        val oldColor = p.color
        p.style = Paint.Style.FILL
        p.color = color
        val left = if (dir >= 0) x.toFloat() else x - barWidthPx
        val right = left + barWidthPx
        if (isFirst && isLast) {
            rect.set(left, drawTop, right, drawBottom)
            c.drawRoundRect(rect, radiusPx, radiusPx, p)
        } else {
            // Overlap adjacent lines by 0.5px so no hairline seam shows between lines, rounding only the outer ends.
            val r = radiusPx
            rect.set(left, if (isFirst) drawTop else drawTop - 0.5f, right, if (isLast) drawBottom else drawBottom + 0.5f)
            c.drawRoundRect(rect, r, r, p)
            if (!isFirst) c.drawRect(left, drawTop - 0.5f, right, min(drawTop + r, drawBottom), p)
            if (!isLast) c.drawRect(left, maxOf(drawBottom - r, drawTop), right, drawBottom + 0.5f, p)
        }
        p.style = oldStyle
        p.color = oldColor
    }
}

/** Three centred quiet dots for a thematic break (`---`). */
internal class RuleDotsSpan(
    private val color: Int,
    private val radiusPx: Float,
    private val spacingPx: Float,
    private val bottomTrimPx: Int,
) : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        val oldStyle = paint.style
        val oldColor = paint.color
        paint.style = Paint.Style.FILL
        paint.color = color
        val cx = (left + right) / 2f
        val cy = (top + (bottom - bottomTrimPx)) / 2f
        val step = radiusPx * 2f + spacingPx
        canvas.drawCircle(cx - step, cy, radiusPx, paint)
        canvas.drawCircle(cx, cy, radiusPx, paint)
        canvas.drawCircle(cx + step, cy, radiusPx, paint)
        paint.style = oldStyle
        paint.color = oldColor
    }
}
