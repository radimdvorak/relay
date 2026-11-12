package dev.relay.android.features.composer.logic

import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.ContextUsage
import dev.relay.android.remote.model.ModelChoice
import dev.relay.android.remote.model.PromptCache
import dev.relay.android.remote.model.ResumeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColdCompactTest {
    private val hour = 60 * 60_000L
    private val claude = ResumeSettings("claude", ModelChoice(""), null, "auto", "default")

    private fun answer(tokens: Long, at: Long = 0, provider: String = "claude", compaction: Boolean? = null) =
        ChatMessage("a$tokens", "assistant", "Done", "complete", at, provider, at, compaction = compaction,
            context = ContextUsage(tokens, cache = PromptCache(at, hour)))

    private fun send(body: String = "Carry on", settings: ResumeSettings = claude, delivery: String? = null) =
        composeSend(settings, body, "s1", delivery = delivery)

    private fun tokens(messages: List<ChatMessage>, body: String = "Carry on", queueWaiting: Boolean = false, now: Long = 2 * hour) =
        coldCompactTokens(messages, contextAgent(messages), send(body), queueWaiting, now)

    @Test fun `a big conversation whose cache went cold asks`() {
        assertEquals(214_000L, tokens(listOf(answer(214_000))))
    }

    @Test fun `a warm cache or a small conversation goes as is`() {
        assertNull(tokens(listOf(answer(214_000)), now = hour / 2))
        assertNull(tokens(listOf(answer(90_000))))
    }

    @Test fun `another agent, a queue or a command goes as is`() {
        val messages = listOf(answer(214_000))
        assertNull(coldCompactTokens(messages, "claude", send(settings = claude.copy(provider = "codex")), false, 2 * hour))
        assertNull(tokens(messages, queueWaiting = true))
        assertNull(tokens(messages, body = "@claude /goal ship it"))
        assertNull(coldCompactTokens(messages, "claude", send(delivery = "queue"), false, 2 * hour))
    }
}
