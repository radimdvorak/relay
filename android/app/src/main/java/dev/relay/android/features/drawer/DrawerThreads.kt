package dev.relay.android.features.drawer

import dev.relay.android.features.home.ActivitySection
import dev.relay.android.features.home.chatActivitySection
import dev.relay.android.remote.model.RemoteChatSummary

/** Recent threads shown before "Show more", and how many each tap on it adds. */
internal const val RECENT_FIRST = 12
internal const val RECENT_MORE = 24

/** The drawer's two lists, each in the order it shows. */
internal data class DrawerThreads(val pinned: List<RemoteChatSummary>, val recent: List<RemoteChatSummary>)

/**
 * Pinned threads, then the rest: open ones newest first, the settled and snoozed ones after them.
 * A thread another one started stays under its lead in Activity, so it's listed here only while
 * it waits on you, is pinned, or is the one open ([openId]).
 */
internal fun drawerThreads(chats: List<RemoteChatSummary>, openId: String?, now: Long): DrawerThreads {
    val listed = chats.mapTo(HashSet()) { it.id }
    fun staysUnderLead(chat: RemoteChatSummary): Boolean {
        val lead = chat.startedBy?.chatId ?: return false
        return lead in listed && chat.waiting != true && chat.id != openId
    }
    val newest = chats.sortedByDescending { it.updated }
    val (pinned, rest) = newest.partition { it.pinnedAt != null }
    val (open, shelved) = rest.filterNot(::staysUnderLead)
        .partition { chatActivitySection(it, now) == ActivitySection.Active }
    return DrawerThreads(pinned, open + shelved)
}
