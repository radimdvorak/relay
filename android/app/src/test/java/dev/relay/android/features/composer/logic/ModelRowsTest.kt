package dev.relay.android.features.composer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelRowsTest {
    private val claude = listOf(
        AgentModel("opus", "Opus 5.5", "For complex work", efforts = listOf("low", "medium", "high"), defaultEffort = "high", resolved = "claude-opus-5-5"),
        AgentModel("haiku", "Haiku 5.5", "Fast"),
        AgentModel("old", "Opus 4", legacy = true),
    )

    @Test fun `Default comes first, with the effort of the model it falls back to`() {
        val rows = modelRows("claude", claude, "", AgentDefaults(model = "opus", effort = "low"))
        assertEquals(listOf("", "opus", "haiku", "old"), rows.map { it.id })
        assertEquals("Uses Opus 5.5", rows[0].description)
        assertEquals("Low", rows[0].levels!!.label(""))
        assertEquals("High", rows[1].levels!!.label(""))
        assertNull(rows[2].levels)
        assertEquals("Older model", rows[3].description)
    }

    @Test fun `without the agent's defaults, Default has nothing to slide`() {
        val rows = modelRows("claude", claude, "", null)
        assertEquals("The agent's own choice", rows[0].description)
        assertNull(rows[0].levels)
    }

    @Test fun `a project's default effort wins over the model's own`() {
        val rows = modelRows("claude", claude, "opus", AgentDefaults(efforts = mapOf("opus" to "medium")))
        assertEquals("Medium", rows[1].levels!!.label(""))
    }

    @Test fun `a model the list lacks keeps a row, one under another id doesn't`() {
        assertEquals("gone", modelRows("claude", claude, "gone", null)[1].id)
        assertEquals("opus", modelRows("claude", claude, "claude-opus-5-5[1m]", null)[1].id)
        assertEquals("opus", chosenRow("claude", claude, "claude-opus-5-5[1m]"))
        assertEquals("gone", chosenRow("claude", claude, "gone"))
    }

    @Test fun `an agent with no list still offers Default and the thread's own`() {
        assertEquals(listOf("", "gpt-x"), modelRows("codex", emptyList(), "gpt-x", null).map { it.id })
    }

    @Test fun `search matches every word across agents, in the agents' order`() {
        val lists = mapOf("claude" to claude, "codex" to listOf(AgentModel("gpt-6-sol", "GPT-6-Sol")))
        assertEquals(listOf("codex", "claude"), searchModels("", listOf("codex", "claude"), lists).map { it.first })
        assertEquals(listOf("claude" to listOf(claude[0])), searchModels("OPUS 5.5", listOf("codex", "claude"), lists))
        assertEquals(listOf("codex"), searchModels("codex sol", listOf("codex", "claude"), lists).map { it.first })
        assertEquals(emptyList<Any>(), searchModels("nope", listOf("codex", "claude"), lists))
    }

    @Test fun `matches are marked once where they overlap`() {
        assertEquals(listOf(0..3, 5..7), matchRanges("Opus 5.5", "5.5 opus"))
        assertEquals(listOf(0..3), matchRanges("Opus 5.5", "op pus"))
        assertEquals(emptyList<IntRange>(), matchRanges("Opus", "zzz"))
    }
}
