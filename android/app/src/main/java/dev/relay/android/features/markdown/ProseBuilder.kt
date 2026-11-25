package dev.relay.android.features.markdown

import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayType
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Inline code's backing, for [AnswerText] to draw behind the text and selection. */
internal class CodeBack(val color: Int, val sizePx: Float)

/** Fonts needed to lay out a prose run natively without per-draw typeface churn. */
internal class ProseFonts(
    val inter: Typeface,
    val interSemiBold: Typeface,
    val mono: Typeface,
    val ribbon: Typeface,
)

/**
 * Turns a run of prose [MdBlock]s (paragraphs, headings, lists, blockquotes,
 * raw blocks, rules) into one [Spanned] so Android selects continuously across
 * all of them in a single TextView.
 */
internal fun spannedProse(
    blocks: List<MdBlock>,
    ctx: MdContext,
    c: RelayColors,
    type: RelayType,
    density: Density,
    fonts: ProseFonts,
): Spanned = ProseBuilder(ctx, c, type, density, fonts).build(blocks)

private class ProseBuilder(
    private val ctx: MdContext,
    private val c: RelayColors,
    private val type: RelayType,
    private val density: Density,
    private val fonts: ProseFonts,
) {
    private val sb = SpannableStringBuilder()
    private val paras = ArrayList<ParaRecord>()
    private val regions = ArrayList<Region>()

    private val bodyStyle = if (ctx.small) type.meta.copy(lineHeight = 20.sp) else type.body
    private val bodySizePx = with(density) { bodyStyle.fontSize.toPx() }
    private val bodyLineHeightPx = with(density) { bodyStyle.lineHeight.toPx() }.roundToInt()
    private val blockGapPx = with(density) { 10.dp.toPx() }.roundToInt()
    private val itemGapPx = with(density) { 4.dp.toPx() }.roundToInt()
    private val headingTopPx = with(density) { (if (ctx.small) 2.dp else 4.dp).toPx() }.roundToInt()
    private val itemIndentPx = with(density) { 20.dp.toPx() }.roundToInt()
    private val numberEndGapPx = with(density) { 5.dp.toPx() }
    private val bulletRadiusPx = with(density) { 2.5.dp.toPx() }
    private val taskBoxSizePx = with(density) { 14.dp.toPx() }
    private val taskStrokePx = with(density) { 1.5.dp.toPx() }
    private val taskCornerPx = with(density) { 3.dp.toPx() }
    private val quoteIndentPx = with(density) { 14.dp.toPx() }.roundToInt()
    private val quoteBarWidthPx = with(density) { 3.dp.toPx() }
    private val quoteBarRadiusPx = with(density) { 1.5.dp.toPx() }
    private val ruleHeightPx = with(density) { 16.dp.toPx() }.roundToInt()
    private val ruleDotRadiusPx = with(density) { 1.5.dp.toPx() }
    private val ruleDotGapPx = with(density) { 8.dp.toPx() }
    private val codeSizePx = with(density) { 13.5.sp.toPx() }

    private var pendingGapPx = 0

    fun build(blocks: List<MdBlock>): Spanned {
        appendBlocks(blocks, gapPx = blockGapPx, quoted = ctx.quoted)
        finalizeSpans()
        return sb
    }

    private fun appendBlocks(blocks: List<MdBlock>, gapPx: Int, quoted: Boolean) {
        blocks.forEachIndexed { i, block ->
            if (i > 0) pendingGapPx = maxOf(pendingGapPx, gapPx)
            appendBlock(block, quoted)
        }
    }

    private fun appendBlock(block: MdBlock, quoted: Boolean) {
        when (block) {
            is MdBlock.Para -> emitPara(lineHeightPx = bodyLineHeightPx, spaceBeforePx = 0) {
                appendInlines(block.inlines, bodySizePx, quoted, inLink = false, keepFace = false)
            }
            is MdBlock.Heading -> {
                val spec = headingSpec(block.level)
                emitPara(
                    lineHeightPx = spec.lineHeightPx,
                    spaceBeforePx = if (paras.isEmpty()) 0 else headingTopPx,
                ) {
                    val start = sb.length
                    appendInlines(block.inlines, spec.sizePx, quoted, inLink = false, keepFace = spec.isRibbon)
                    if (sb.length > start) {
                        val codes = sb.getSpans(start, sb.length, CodeSpan::class.java)
                        sb.setSpan(HeadingSpan(spec.face, spec.sizePx, spec.letterSpacing), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        // Re-attach inline code spans after HeadingSpan so monospace wins over the heading typeface.
                        for (code in codes) {
                            val cs = sb.getSpanStart(code)
                            val ce = sb.getSpanEnd(code)
                            sb.removeSpan(code)
                            sb.setSpan(code, cs, ce, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }
            is MdBlock.Raw -> emitPara(lineHeightPx = bodyLineHeightPx, spaceBeforePx = 0) {
                if (quoted && !ctx.quoted) span(ForegroundColorSpan(c.inkSoft.toArgb())) { sb.append(block.text) }
                else sb.append(block.text)
            }
            MdBlock.Rule -> emitPara(lineHeightPx = ruleHeightPx, spaceBeforePx = 0, isRule = true) {
                span(ForegroundColorSpan(android.graphics.Color.TRANSPARENT)) { sb.append(' ') }
            }
            is MdBlock.Quote -> {
                val firstPara = paras.size
                val regionIndex = regions.size
                val placeholder = Region.Quote(firstPara, firstPara)
                regions += placeholder
                appendBlocks(block.blocks, gapPx = blockGapPx, quoted = true)
                val lastPara = paras.size - 1
                if (lastPara >= firstPara) regions[regionIndex] = placeholder.copy(lastPara = lastPara)
                else regions.removeAt(regionIndex)
            }
            is MdBlock.Items -> {
                val indent = if (block.ordered) orderedIndentPx(block.start, block.items.size) else itemIndentPx
                block.items.forEachIndexed { i, item ->
                    if (i > 0) pendingGapPx = maxOf(pendingGapPx, itemGapPx)
                    val firstPara = paras.size
                    val regionIndex = regions.size
                    val placeholder = Region.Item(firstPara, firstPara, indent, item.task, if (block.ordered) block.start + i else null, quoted)
                    regions += placeholder
                    appendBlocks(item.blocks, gapPx = itemGapPx, quoted = quoted)
                    val lastPara = paras.size - 1
                    if (lastPara >= firstPara) regions[regionIndex] = placeholder.copy(lastPara = lastPara)
                    else regions.removeAt(regionIndex)
                }
            }
            is MdBlock.Code, is MdBlock.Math, is MdBlock.Table -> Unit
        }
    }

    private fun emitPara(
        lineHeightPx: Int,
        spaceBeforePx: Int,
        isRule: Boolean = false,
        body: () -> Unit,
    ) {
        val prev = paras.lastOrNull()
        val beforeNewline = sb.length
        if (prev != null) sb.append('\n')
        val paraStart = sb.length
        body()
        if (sb.length == paraStart) {
            sb.delete(beforeNewline, sb.length)
            return
        }
        if (prev != null) {
            prev.end = paraStart
            prev.spaceAfterPx = maxOf(prev.spaceAfterPx, pendingGapPx)
        }
        pendingGapPx = 0
        paras += ParaRecord(
            start = paraStart,
            end = sb.length,
            lineHeightPx = lineHeightPx,
            spaceBeforePx = spaceBeforePx,
            spaceAfterPx = 0,
            isRule = isRule,
        )
    }

    private fun finalizeSpans() {
        if (paras.isEmpty()) return
        paras.last().end = sb.length
        // Outer regions are attached before inner ones so Android stacks LeadingMarginSpans outside-in.
        for (region in regions) when (region) {
            is Region.Quote -> {
                val first = paras[region.firstPara]
                val last = paras[region.lastPara]
                sb.setSpan(
                    QuoteBarSpan(
                        indentPx = quoteIndentPx,
                        barWidthPx = quoteBarWidthPx,
                        radiusPx = quoteBarRadiusPx,
                        color = c.selected.toArgb(),
                        topTrimPx = first.spaceBeforePx,
                        bottomTrimPx = last.spaceAfterPx,
                    ),
                    first.start,
                    last.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            is Region.Item -> {
                val first = paras[region.firstPara]
                val mark: LeadingMarginSpan = when {
                    region.task != null -> TaskMarkSpan(
                        checked = region.task,
                        indentPx = region.indentPx,
                        boxSizePx = taskBoxSizePx,
                        strokePx = taskStrokePx,
                        cornerPx = taskCornerPx,
                        color = (if (region.task) c.inkSoft else c.inkFaint).toArgb(),
                        bodySizePx = bodySizePx,
                    )
                    region.number != null -> NumberMarkSpan(
                        label = "${region.number}.",
                        indentPx = region.indentPx,
                        endGapPx = numberEndGapPx,
                        color = c.inkSoft.toArgb(),
                        face = fonts.inter,
                        sizePx = bodySizePx,
                    )
                    else -> BulletMarkSpan(
                        indentPx = region.indentPx,
                        radiusPx = bulletRadiusPx,
                        color = (if (region.quoted) c.inkSoft else c.ink).toArgb(),
                        bodySizePx = bodySizePx,
                    )
                }
                sb.setSpan(mark, first.start, first.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (region.lastPara > region.firstPara) {
                    val restStart = paras[region.firstPara + 1].start
                    val restEnd = paras[region.lastPara].end
                    sb.setSpan(LeadingMarginSpan.Standard(region.indentPx), restStart, restEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        for (para in paras) {
            sb.setSpan(
                ParagraphMetricsSpan(para.lineHeightPx, para.spaceBeforePx, para.spaceAfterPx),
                para.start,
                para.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (para.isRule) {
                sb.setSpan(
                    RuleDotsSpan(c.inkFaint.toArgb(), ruleDotRadiusPx, ruleDotGapPx, para.spaceAfterPx),
                    para.start,
                    para.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun orderedIndentPx(start: Int, count: Int): Int {
        val widest = "${start + (count - 1).coerceAtLeast(0)}."
        val paint = Paint().apply {
            typeface = fonts.inter
            textSize = bodySizePx
            fontFeatureSettings = "tnum"
        }
        return maxOf(itemIndentPx, ceil(paint.measureText(widest) + numberEndGapPx + with(density) { 2.dp.toPx() }).toInt())
    }

    private fun headingSpec(level: Int): HeadingSpec = when {
        ctx.small -> HeadingSpec(
            face = fonts.interSemiBold,
            sizePx = with(density) { type.body.fontSize.toPx() },
            lineHeightPx = with(density) { 22.sp.toPx() }.roundToInt(),
            letterSpacing = 0f,
            isRibbon = false,
        )
        level <= 2 -> HeadingSpec(
            face = fonts.ribbon,
            sizePx = with(density) { type.heading.fontSize.toPx() },
            lineHeightPx = with(density) { type.heading.lineHeight.toPx() }.roundToInt(),
            letterSpacing = if (type.heading.letterSpacing.isEm) type.heading.letterSpacing.value else 0f,
            isRibbon = true,
        )
        level == 3 -> HeadingSpec(
            face = fonts.interSemiBold,
            sizePx = with(density) { 17.sp.toPx() },
            lineHeightPx = with(density) { 24.sp.toPx() }.roundToInt(),
            letterSpacing = 0f,
            isRibbon = false,
        )
        else -> HeadingSpec(
            face = fonts.interSemiBold,
            sizePx = with(density) { type.body.fontSize.toPx() },
            lineHeightPx = with(density) { type.body.lineHeight.toPx() }.roundToInt(),
            letterSpacing = 0f,
            isRibbon = false,
        )
    }

    private fun appendInlines(
        inlines: List<MdInline>,
        lineSizePx: Float,
        quoted: Boolean,
        inLink: Boolean,
        keepFace: Boolean,
    ) {
        val soft = quoted && !ctx.quoted && !inLink
        for (inline in inlines) when (inline) {
            is MdInline.Plain -> {
                if (soft) span(ForegroundColorSpan(c.inkSoft.toArgb())) { sb.append(inline.text) }
                else sb.append(inline.text)
            }
            is MdInline.Strong -> {
                if (keepFace) appendInlines(inline.children, lineSizePx, quoted, inLink, keepFace = true)
                else span(WeightSpan(fonts.interSemiBold)) { appendInlines(inline.children, lineSizePx, quoted, inLink, keepFace = false) }
            }
            is MdInline.Em -> span(StyleSpan(Typeface.ITALIC)) { appendInlines(inline.children, lineSizePx, quoted, inLink, keepFace) }
            is MdInline.Del -> span(StrikethroughSpan()) { appendInlines(inline.children, lineSizePx, quoted, inLink, keepFace) }
            is MdInline.Math -> {
                val plain = texToPlain(inline.tex)
                if (plain == null) span(CodeSpan(fonts.mono, codeSizePx), ForegroundColorSpan(c.inkSoft.toArgb())) { sb.append(inline.tex) }
                else if (soft) span(TypefaceSpan(Typeface.SERIF), StyleSpan(Typeface.ITALIC), ForegroundColorSpan(c.inkSoft.toArgb())) { sb.append(plain) }
                else span(TypefaceSpan(Typeface.SERIF), StyleSpan(Typeface.ITALIC)) { sb.append(plain) }
            }
            is MdInline.Code -> {
                val open = if (inLink) null else ctx.code(inline.code)
                val marks = listOfNotNull<Any>(
                    CodeSpan(fonts.mono, codeSizePx),
                    CodeBack(c.container.toArgb(), lineSizePx),
                    ForegroundColorSpan((if (open != null) c.accent else if (quoted) c.inkSoft else c.ink).toArgb()),
                    open?.let { Click(it, null) },
                )
                span(*marks.toTypedArray()) { sb.append(inline.code) }
            }
            is MdInline.Link -> link(inline.href, inLink) { appendInlines(inline.children, lineSizePx, quoted, inLink = true, keepFace) }
            is MdInline.Image ->
                if (isWebUrl(inline.src)) link(inline.src, inLink) { sb.append(inline.alt.ifEmpty { inline.src }) }
                else if (soft) span(ForegroundColorSpan(c.inkSoft.toArgb())) { sb.append(inline.alt) }
                else sb.append(inline.alt)
        }
    }

    private inline fun link(href: String, inLink: Boolean, body: () -> Unit) {
        val open = if (inLink) null else ctx.link(href)
        if (open == null) body() else span(Click(open, c.accent)) { body() }
    }

    private inline fun span(vararg marks: Any, body: () -> Unit) {
        val start = sb.length
        body()
        if (sb.length > start) {
            for (mark in marks) sb.setSpan(mark, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private class ParaRecord(
        val start: Int,
        var end: Int,
        val lineHeightPx: Int,
        val spaceBeforePx: Int,
        var spaceAfterPx: Int,
        val isRule: Boolean,
    )

    private sealed interface Region {
        data class Quote(val firstPara: Int, val lastPara: Int) : Region
        data class Item(
            val firstPara: Int,
            val lastPara: Int,
            val indentPx: Int,
            val task: Boolean?,
            val number: Int?,
            val quoted: Boolean,
        ) : Region
    }

    private class HeadingSpec(
        val face: Typeface,
        val sizePx: Float,
        val lineHeightPx: Int,
        val letterSpacing: Float,
        val isRibbon: Boolean,
    )
}

/** Inter at a pre-built variable weight; each use takes a fresh instance, as a span marks one range. */
private class WeightSpan(private val face: Typeface) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) { paint.typeface = face }
    override fun updateDrawState(paint: TextPaint) { paint.typeface = face }
}

/** JetBrains Mono at InlineText's 13.5sp. */
private class CodeSpan(private val mono: Typeface, private val sizePx: Float) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) = apply(paint)
    override fun updateDrawState(paint: TextPaint) = apply(paint)
    private fun apply(paint: TextPaint) {
        paint.typeface = mono
        paint.textSize = sizePx
    }
}

/** A link or path: the accent colour without an underline, or the text's own colour for code. */
internal class Click(private val open: () -> Unit, val color: Color?) : ClickableSpan() {
    override fun onClick(widget: View) = open()

    override fun updateDrawState(paint: TextPaint) {
        if (color != null) paint.color = color.toArgb()
        paint.isUnderlineText = false
    }
}
