package dev.relay.android.features.thread

import dev.relay.android.features.markdown.MarkdownRow
import dev.relay.android.features.markdown.MdBlock
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.HtmlRender
import dev.relay.android.remote.model.HtmlRenderPage
import org.junit.Assert.*
import org.junit.Test

class ThreadMessageRowsTest {
    private fun message(id: String, body: String = "An answer", renders: List<HtmlRender>? = null) = ChatMessage(
        id = id, role = "assistant", body = body, status = "complete", created = 0,
        provider = "claude", version = 1, renders = renders,
    )
    private fun render(id: String) = HtmlRender(id, "Example", listOf(HtmlRenderPage()), 0)

    @Test fun proseCoalescesAndKeepsAnchorWhenCardsAppearAndTurnFinishes() {
        val cache = ThreadBodyRows()
        val m = message("answer", "First paragraph.\n\nGrowing tail").copy(status = "streaming")
        val original = cache.rows(listOf(m))
        val finished = cache.rows(listOf(m.copy(body = m.body + " is finished.", status = "complete", renders = listOf(render("one")))))
        assertEquals(listOf("answer:header", "answer:line:0", "answer:footer"), original.map { it.key })
        assertEquals(original.map { it.key }, finished.filterNot { it is ThreadMessageRow.Render }.map { it.key })
        assertEquals("answer:render:one", finished[1].key)
        val first = (original.filterIsInstance<ThreadMessageRow.Block>().single().row as MarkdownRow.Prose).blocks.first()
        val after = (finished.filterIsInstance<ThreadMessageRow.Block>().single().row as MarkdownRow.Prose).blocks.first()
        assertSame(first, after)
    }

    @Test fun blockKindCanChangeWithoutReplacingItsAnchor() {
        val cache = ThreadBodyRows()
        val before = cache.rows(listOf(message("answer", "A heading")))
        val after = cache.rows(listOf(message("answer", "A heading\n===")))
        val old = before.filterIsInstance<ThreadMessageRow.Block>().single()
        val new = after.filterIsInstance<ThreadMessageRow.Block>().single()
        assertTrue((old.row as MarkdownRow.Prose).blocks.single() is MdBlock.Para)
        assertTrue((new.row as MarkdownRow.Prose).blocks.single() is MdBlock.Heading)
        assertEquals(old.key, new.key)
    }

    @Test fun cardsReorderWithoutChangingOtherKeys() {
        val a = threadMessageRows(listOf(message("answer", renders = listOf(render("one"), render("two")))))
        val b = threadMessageRows(listOf(message("answer", renders = listOf(render("two"), render("one")))))
        assertEquals(a.map { it.key }.toSet(), b.map { it.key }.toSet())
        assertEquals(a.size, a.map { it.key }.distinct().size)
    }

    @Test fun noteNavigationFindsBeginningAfterRowExpansion() {
        val rows = threadMessageRows(listOf(message("first", renders = listOf(render("one"))), message("second")))
        assertEquals(0, rows.indexOfFirst { it.message.id == "first" })
        assertEquals(4, rows.indexOfFirst { it.message.id == "second" })
    }

    @Test fun syntheticAndUserMessagesKeepWholePresentation() {
        for (m in listOf(message("summary").copy(compaction = true), message("user").copy(role = "user"))) {
            val row = threadMessageRows(listOf(m)).single() as ThreadMessageRow.Content
            assertEquals(MessageSection.Whole, row.section)
            assertEquals(m.id, row.key)
        }
    }

    @Test fun parsesArePrunedToLoadedMessagesAndRetainedForUnchangedMessages() {
        val cache = ThreadBodyRows()
        val m = message("kept")
        val first = (cache.rows(listOf(m, message("removed"))).filterIsInstance<ThreadMessageRow.Block>().first().row as MarkdownRow.Prose).blocks.single()
        val next = (cache.rows(listOf(m)).filterIsInstance<ThreadMessageRow.Block>().single().row as MarkdownRow.Prose).blocks.single()
        assertSame(first, next)
        assertEquals(1, cache.cachedMessages)
        cache.rows(emptyList())
        assertEquals(0, cache.cachedMessages)
    }

    @Test fun proseMergesListsAndQuotesWhileMathAndTablesStayStandalone() {
        val source = "First\n\n\\[x^2\\]\n\n- one\n- two\n\n> quote\n> continued\n\n| A | B |\n|---|---|\n| 1 | 2 |"
        val blocks = threadMessageRows(listOf(message("answer", source))).filterIsInstance<ThreadMessageRow.Block>()
        assertEquals(4, blocks.size)
        assertTrue(blocks[0].row is MarkdownRow.Prose)
        assertTrue((blocks[1].row as MarkdownRow.Standalone).block is MdBlock.Math)
        val midProse = blocks[2].row as MarkdownRow.Prose
        assertEquals(listOf(MdBlock.Items::class, MdBlock.Quote::class), midProse.blocks.map { it::class })
        assertTrue((blocks[3].row as MarkdownRow.Standalone).block is MdBlock.Table)
        assertEquals(blocks.size, blocks.map { it.key }.distinct().size)
    }
}
