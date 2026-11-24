package dev.relay.android.features.home

import dev.relay.android.features.home.StatusNews.Kind
import dev.relay.android.remote.model.LimitResume
import dev.relay.android.remote.model.RemoteChatSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusNewsTest {
    private val feed = StatusNewsFeed()
    private val running = chat("a", updated = 10) { copy(running = true, runningSince = 5) }
    private val finished = chat("a", updated = 20)
    private val asking = chat("b", updated = 10) { copy(waiting = true) }

    private fun listed(vararg chats: RemoteChatSummary, from: String = "mac", openId: String? = null, catchingUp: Boolean = false) =
        feed.listed(from, chats.toList(), openId, catchingUp, at = 100).map { it.chatId to it.kind }

    @Test
    fun tellsWhatFinishedOrAskedSinceTheLastList() {
        assertEquals(emptyList<Any>(), listed(running, chat("b", updated = 10)))
        assertEquals(listOf("a" to Kind.Finished, "b" to Kind.NeedsYou), listed(finished, asking))
        assertEquals(emptyList<Any>(), listed(finished, asking))
    }

    @Test
    fun theThreadOnScreenNeedsNoTelling() {
        listed(running)
        assertEquals(emptyList<Any>(), listed(finished, openId = "a"))
    }

    @Test
    fun cameToTheFrontOnlyOpenQuestionsAreStillNews() {
        listed(running, chat("b", updated = 10))
        assertEquals(listOf("b" to Kind.NeedsYou), listed(finished, asking, catchingUp = true))
    }

    @Test
    fun anotherComputersThreadsArentNews() {
        listed(running)
        assertEquals(emptyList<Any>(), listed(finished, asking, from = "server"))
    }

    @Test
    fun aUsageLimitSaysSo() {
        listed(running)
        val limited = chat("a", updated = 20) { copy(limitResume = LimitResume("m", "claude", at = 500)) }
        assertEquals(listOf("a" to Kind.UsageLimit), listed(limited))
    }

    @Test
    fun whatsAnsweredOrOpenedLeavesTheQueue() {
        val queue = listOf(StatusNews("a", "A", Kind.Finished), StatusNews("b", "B", Kind.NeedsYou), StatusNews("gone", "", Kind.Finished))
        val answered = chat("b", updated = 30)
        assertEquals(listOf("a"), stillNews(queue, listOf(finished, answered), openId = null).map { it.chatId })
        assertEquals(listOf("b"), stillNews(queue, listOf(finished, asking), openId = "a").map { it.chatId })
    }

    @Test
    fun aThreadsLatestNewsReplacesItsLast() {
        val queue = listOf(StatusNews("a", "A", Kind.Finished), StatusNews("b", "B", Kind.NeedsYou))
        val next = withNews(queue, listOf(StatusNews("a", "A", Kind.Failed)))
        assertEquals(listOf("b" to Kind.NeedsYou, "a" to Kind.Failed), next.map { it.chatId to it.kind })
    }
}
