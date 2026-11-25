package dev.relay.android.features.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import dev.relay.android.R
import dev.relay.android.features.diagnostics.LocalScrollTrace
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import java.util.concurrent.ConcurrentHashMap

/*
 * An answer's prose as Android's own TextView. Compose's selection handles
 * catch only about 25dp around them, so a touch just below one cleared the
 * selection and the menu flickered; the system's handles, magnifier and menu
 * are the ones the rest of the phone has. Consecutive prose blocks (paragraphs,
 * headings, lists, blockquotes and rules) are laid out together in one TextView
 * so selection drags cleanly across them; code blocks, tables, math and local
 * images keep their own rows.
 */

/** A contiguous run of prose [blocks] rendered in one selectable native TextView. */
@Composable
internal fun NativeProse(blocks: List<MdBlock>, ctx: MdContext, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    val type = RelayTheme.type
    val density = LocalDensity.current
    val context = LocalContext.current
    val fonts = remember(context) { Fonts.prose(context) }
    val text = remember(blocks, ctx, c, type, density, fonts) { spannedProse(blocks, ctx, c, type, density, fonts) }
    if (text.isEmpty()) return
    val base = bodyStyle(ctx.small).copy(color = if (ctx.quoted) c.inkSoft else c.ink)
    NativeTextView(text, base, ctx.selectable, modifier)
}

/** A single inline run in [style], selectable when [selectable]; used inside standalone blocks (e.g. image-bearing paragraphs). */
@Composable
internal fun NativeInlines(inlines: List<MdInline>, ctx: MdContext, style: TextStyle, selectable: Boolean, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    val type = RelayTheme.type
    val density = LocalDensity.current
    val context = LocalContext.current
    val fonts = remember(context) { Fonts.prose(context) }
    val text = remember(inlines, ctx, c, type, density, fonts) {
        spannedProse(listOf(MdBlock.Para(inlines)), ctx, c, type, density, fonts)
    }
    if (text.isEmpty()) return
    NativeTextView(text, style, selectable, modifier)
}

@Composable
private fun NativeTextView(
    text: Spanned,
    style: TextStyle,
    selectable: Boolean,
    modifier: Modifier,
) {
    val c = RelayTheme.colors
    val weight = (style.fontWeight ?: FontWeight.Normal).weight
    val trace = LocalScrollTrace.current
    AndroidView(
        factory = { context -> AnswerText(context).apply { setUp(context) } },
        modifier = modifier.fillMaxWidth().onSizeChanged {
            trace?.event("text_size", "textChars" to text.length, "widthPx" to it.width, "heightPx" to it.height)
        },
        // Reused selectable TextViews can retain another paragraph's measured height.
        // Let each prose run own its view so returning lazy rows measure the same text.
        update = { view ->
            view.applyStyle(StyleKey(style, weight, c))
            view.show(text, selectable)
        },
    )
}

/**
 * Sized to its text, so there's never anything to scroll to. A selectable TextView still brings
 * its cursor into view before drawing, and one laid out before it had any height scrolled its
 * first line out of sight and stayed that way: list items showed all but their first line.
 */
private class AnswerText(context: Context) : TextView(context) {
    private val codePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun scrollTo(x: Int, y: Int) = super.scrollTo(0, 0)

    /** Behind the text and the selection. A BackgroundColorSpan would fill the whole line, its spacing too. */
    override fun onDraw(canvas: Canvas) {
        val shown = text as? Spanned
        val laid = layout
        if (shown != null && laid != null)
            for (mark in shown.getSpans(0, shown.length, CodeBack::class.java)) drawCodeBack(canvas, laid, shown, mark)
        super.onDraw(canvas)
    }

    private fun drawCodeBack(canvas: Canvas, laid: Layout, shown: Spanned, mark: CodeBack) {
        val start = shown.getSpanStart(mark)
        val end = shown.getSpanEnd(mark)
        if (start >= end) return
        codePaint.color = mark.color
        val first = laid.getLineForOffset(start)
        val last = laid.getLineForOffset(end - 1)
        val dp = resources.displayMetrics.density
        val size = mark.sizePx
        for (line in first..last) {
            val left = if (line == first) laid.getPrimaryHorizontal(start) else laid.getLineLeft(line)
            val endsHere = line == last && laid.getLineForOffset(end) == line
            val right = if (endsHere) laid.getPrimaryHorizontal(end) else laid.getLineRight(line)
            val base = laid.getLineBaseline(line)
            canvas.drawRoundRect(
                left - 2 * dp, base - size * 1.02f, right + 2 * dp, base + size * 0.36f,
                5 * dp, 5 * dp, codePaint,
            )
        }
    }
}

/** What a view last showed, so a parent drawing again with the same doesn't reset it. */
private class Shown(var style: StyleKey? = null, var text: Spanned? = null)

private data class StyleKey(val style: TextStyle, val weight: Int, val colors: RelayColors)

private fun TextView.setUp(context: Context) {
    tag = Shown()
    typeface = Fonts.inter(context)
    includeFontPadding = false
    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
    breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
}

private fun TextView.applyStyle(key: StyleKey) {
    val shown = tag as Shown
    if (shown.style == key) return
    shown.style = key
    val (style, weight, c) = key
    typeface = Fonts.weighted(Fonts.inter(context), weight)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
    setTextColor((if (style.color.isUnspecified) c.ink else style.color).toArgb())
    highlightColor = c.accent.copy(alpha = 0.3f).toArgb()
    for (handle in listOfNotNull(textSelectHandle, textSelectHandleLeft, textSelectHandleRight)) handle.mutate().setTint(c.accent.toArgb())
    textSelectHandle?.let(::setTextSelectHandle)
    textSelectHandleLeft?.let(::setTextSelectHandleLeft)
    textSelectHandleRight?.let(::setTextSelectHandleRight)
}

/**
 * Sets [text] unless the same words are already up with a selection on them:
 * a parent drawing again hands over new link actions for the same answer, and
 * setting them would drop what the reader is holding.
 */
private fun TextView.show(text: Spanned, selectable: Boolean) {
    val shown = tag as Shown
    if (isTextSelectable != selectable) {
        setTextIsSelectable(selectable)
        shown.text = null
    }
    val links = if (selectable) SelectableLinks else LinkMovementMethod.getInstance()
    if (movementMethod !== links) movementMethod = links
    if (shown.text === text || hasSelection() && this.text.toString() == text.toString()) return
    shown.text = text
    setText(text, TextView.BufferType.SPANNABLE)
}

private object Fonts {
    @Volatile private var inter: Typeface? = null
    @Volatile private var mono: Typeface? = null
    @Volatile private var ribbon: Typeface? = null

    fun inter(context: Context): Typeface =
        inter ?: (ResourcesCompat.getFont(context, R.font.inter_variable) ?: Typeface.DEFAULT).also { inter = it }

    fun mono(context: Context): Typeface =
        mono ?: (ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE).also { mono = it }

    fun ribbon(context: Context): Typeface =
        ribbon ?: (ResourcesCompat.getFont(context, R.font.relay_ribbon) ?: inter(context)).also { ribbon = it }

    fun prose(context: Context): ProseFonts {
        val base = inter(context)
        return ProseFonts(
            inter = base,
            interSemiBold = weighted(base, 600),
            mono = mono(context),
            ribbon = ribbon(context),
        )
    }

    private val weights = ConcurrentHashMap<Pair<Typeface, Int>, Typeface>()

    /**
     * [base] at [weight] on its variable axis, made once. A paint told the variation itself builds
     * a new font every time, and text measures and draws each bold run with a paint it resets:
     * that was a quarter of the time a fast scroll up a long thread spent.
     */
    fun weighted(base: Typeface, weight: Int): Typeface = weights.getOrPut(base to weight) {
        Paint().apply { typeface = base; fontVariationSettings = "'wght' $weight" }.typeface
    }
}

/**
 * Selection as setTextIsSelectable gives it, plus link taps: TextView opens
 * links in selectable text only when it found them itself (autoLink), and
 * LinkMovementMethod selects the link it opens. A tap that clears a selection
 * doesn't also follow the link under it.
 */
private object SelectableLinks : ArrowKeyMovementMethod() {
    private var selectedAtDown = false

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> selectedAtDown = widget.hasSelection()
            MotionEvent.ACTION_UP -> if (!selectedAtDown && event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) {
                linkAt(widget, buffer, event)?.let { link ->
                    link.onClick(widget)
                    return true
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    private fun linkAt(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan? {
        val layout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
        val at = layout.getOffsetForHorizontal(line, x)
        return buffer.getSpans(at, at, ClickableSpan::class.java).firstOrNull()
    }
}
