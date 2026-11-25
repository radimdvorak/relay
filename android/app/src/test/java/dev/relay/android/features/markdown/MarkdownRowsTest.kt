package dev.relay.android.features.markdown

import org.junit.Assert.*
import org.junit.Test

class MarkdownRowsTest {
    @Test fun consecutiveProseBlocksMergeIntoOneRowAnchoredAtFirstLine() {
        val md = """
            Opening paragraph.

            ## Heading

            - item one
              - nested item
            - item two

            > Quoted note

            ---

            Closing line.
        """.trimIndent()
        val rows = groupMarkdownRows(MarkdownCache().positioned(md), hasImages = true)
        assertEquals(1, rows.size)
        val prose = rows.single() as MarkdownRow.Prose
        assertEquals(0, prose.sourceLine)
        assertEquals(
            listOf(MdBlock.Para::class, MdBlock.Heading::class, MdBlock.Items::class, MdBlock.Quote::class, MdBlock.Rule::class, MdBlock.Para::class),
            prose.blocks.map { it::class },
        )
    }

    @Test fun codeTablesMathAndLocalImagesSplitProseRuns() {
        val md = """
            Before code.

            ```kotlin
            val x = 1
            ```

            Between code and image.

            ![shot](/tmp/a.png)

            After image with web ![logo](https://example.com/a.png).
        """.trimIndent()
        val rows = groupMarkdownRows(MarkdownCache().positioned(md), hasImages = true)
        assertEquals(5, rows.size)
        assertTrue(rows[0] is MarkdownRow.Prose)
        assertTrue((rows[1] as MarkdownRow.Standalone).block is MdBlock.Code)
        assertTrue(rows[2] is MarkdownRow.Prose)
        assertTrue((rows[3] as MarkdownRow.Standalone).block is MdBlock.Para)
        assertTrue(rows[4] is MarkdownRow.Prose)
        assertEquals(rows.size, rows.map { it.sourceLine }.distinct().size)
    }

    @Test fun listContainingCodeBlockFallsBackToStandalone() {
        val md = """
            1. Step one:

               ```sh
               echo hi
               ```

            2. Step two.
        """.trimIndent()
        val rows = groupMarkdownRows(MarkdownCache().positioned(md), hasImages = true)
        assertEquals(1, rows.size)
        assertTrue((rows.single() as MarkdownRow.Standalone).block is MdBlock.Items)
    }
}
