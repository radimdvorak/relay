package dev.relay.android.features.drawer

import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.StartedBy
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerThreadsTest {
    private val now = 1_000_000L

    private fun chat(id: String, updated: Long, change: RemoteChatSummary.() -> RemoteChatSummary = { this }) =
        RemoteChatSummary(id, "p", id, scope = "project", updated = updated, created = 0).change()

    private fun startedBy(lead: String) = StartedBy(chatId = lead, agent = "claude")

    private fun recent(vararg chats: RemoteChatSummary, openId: String? = null) =
        drawerThreads(chats.toList(), openId, now).recent.map { it.id }

    @Test
    fun pinnedThreadsComeOutOfTheRestAndStayPinnedWhenSettled() {
        val threads = drawerThreads(
            listOf(
                chat("old pin", 10) { copy(pinnedAt = 900) },
                chat("plain", 30),
                chat("settled pin", 20) { copy(pinnedAt = 5, settledAt = 20) },
            ),
            openId = null, now,
        )
        assertEquals(listOf("settled pin", "old pin"), threads.pinned.map { it.id })
        assertEquals(listOf("plain"), threads.recent.map { it.id })
    }

    @Test
    fun openThreadsComeBeforeSettledAndSnoozedOnesHoweverNew() {
        assertEquals(
            listOf("newer", "older", "settled", "snoozed"),
            recent(
                chat("settled", 900) { copy(settledAt = 900) },
                chat("older", 100),
                chat("snoozed", 800) { copy(snoozedUntil = now + 1) },
                chat("newer", 200),
            ),
        )
    }

    @Test
    fun aThreadAnotherStartedStaysUnderItsLead() {
        val lead = chat("lead", 10)
        assertEquals(listOf("lead"), recent(lead, chat("worker", 50) { copy(startedBy = startedBy("lead")) }))
        // Its lead is archived, so nothing else would show it.
        assertEquals(listOf("orphan", "lead"), recent(lead, chat("orphan", 50) { copy(startedBy = startedBy("gone")) }))
    }

    @Test
    fun aStartedThreadComesOutWhileItWaitsOnYouIsOpenOrIsPinned() {
        val lead = chat("lead", 10)
        val worker = chat("worker", 50) { copy(startedBy = startedBy("lead")) }
        assertEquals(listOf("worker", "lead"), recent(lead, worker.copy(waiting = true)))
        assertEquals(listOf("worker", "lead"), recent(lead, worker, openId = "worker"))
        assertEquals(listOf("worker"), drawerThreads(listOf(lead, worker.copy(pinnedAt = 1)), null, now).pinned.map { it.id })
    }
}
