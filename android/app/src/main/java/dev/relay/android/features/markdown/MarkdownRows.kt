package dev.relay.android.features.markdown

import androidx.compose.runtime.Immutable

/**
 * One renderable slice of a markdown document: either consecutive prose blocks
 * drawn together in one native TextView (so text selection flows across
 * paragraphs, headings, lists and quotes), or a standalone block that needs
 * its own layout or scroll container (code, table, math, local image).
 */
@Immutable
sealed interface MarkdownRow {
    val sourceLine: Int

    @Immutable
    data class Prose(override val sourceLine: Int, val blocks: List<MdBlock>) : MarkdownRow

    @Immutable
    data class Standalone(override val sourceLine: Int, val block: MdBlock) : MarkdownRow
}

/** Whether [block] can live inside a single native prose TextView. */
internal fun isProse(block: MdBlock, hasImages: Boolean = true): Boolean = when (block) {
    is MdBlock.Para -> !hasImages || block.inlines.none(::isDrawnImage)
    is MdBlock.Heading -> true
    is MdBlock.Raw -> true
    MdBlock.Rule -> true
    is MdBlock.Quote -> block.blocks.all { isProse(it, hasImages) }
    is MdBlock.Items -> block.items.all { item -> item.blocks.all { isProse(it, hasImages) } }
    is MdBlock.Code, is MdBlock.Math, is MdBlock.Table -> false
}

/** Groups consecutive prose blocks into [MarkdownRow.Prose], anchored at the first block's source line. */
fun groupMarkdownRows(blocks: List<MarkdownBlock>, hasImages: Boolean = true): List<MarkdownRow> {
    if (blocks.isEmpty()) return emptyList()
    val out = ArrayList<MarkdownRow>(blocks.size)
    var runStart = -1
    var run: ArrayList<MdBlock>? = null
    fun flush() {
        val current = run ?: return
        out += MarkdownRow.Prose(runStart, current)
        run = null
    }
    for (entry in blocks) {
        if (isProse(entry.block, hasImages)) {
            if (run == null) {
                runStart = entry.sourceLine
                run = ArrayList()
            }
            run!!.add(entry.block)
        } else {
            flush()
            out += MarkdownRow.Standalone(entry.sourceLine, entry.block)
        }
    }
    flush()
    return out
}

/** Convenience for callers without source line anchors (e.g. inline Markdown()). */
internal fun groupMdBlocks(blocks: List<MdBlock>, hasImages: Boolean): List<MarkdownRow> =
    groupMarkdownRows(blocks.mapIndexed { i, b -> MarkdownBlock(i, b) }, hasImages)
