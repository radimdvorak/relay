package dev.relay.android.features.home

import dev.relay.android.features.notifications.LastAnswer
import dev.relay.android.features.notifications.ThreadNews
import dev.relay.android.features.notifications.threadNews
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.ChatMessage

/** One thread's news as a toast says it. */
internal data class StatusNews(val chatId: String, val title: String, val kind: Kind) {
    enum class Kind(val label: String) {
        NeedsYou("Needs you"),
        Finished("Finished"),
        Failed("Failed"),
        UsageLimit("Hit its usage limit"),
    }
}

/**
 * Which threads' news to toast, out of each list the computer sends: the same news the phone's
 * notifications tell with the app away ([threadNews]).
 */
internal class StatusNewsFeed {
    private var computer: String? = null
    private var before: Map<String, RemoteChatSummary>? = null
    private val answers = HashMap<String, Pair<LastAnswer, Long>>()

    /** The latest answer of a thread's main conversation, for telling a finish from a failure or a stop. */
    fun heard(chatId: String, message: ChatMessage) {
        val answer = message.role == "assistant" && message.parentId == null &&
            message.compaction != true && message.handoff == null && message.reload == null
        if (!answer) return
        val last = answers[chatId]
        if (last != null && last.second > message.created) return
        answers[chatId] = LastAnswer(message.status, message.body, message.error, message.provider) to message.created
    }

    /**
     * What's news in [chats] since the last list. While [catchingUp] (the app just came to the
     * front, and the list brings what notifications already told) only questions are: they're
     * still open.
     */
    fun listed(
        from: String?,
        chats: List<RemoteChatSummary>,
        openId: String?,
        catchingUp: Boolean,
        at: Long = System.currentTimeMillis(),
    ): List<StatusNews> {
        // A list without one before it, or one of another computer's, has no news in it.
        val was = before.takeIf { computer == from }
        if (computer != from) answers.clear()
        computer = from
        before = chats.associateBy { it.id }
        if (was == null) return emptyList()
        return threadNews(was, chats, answers.mapValues { it.value.first }, at)
            .filter { it.chatId != openId }
            .map { StatusNews(it.chatId, it.title, kindOf(it, before?.get(it.chatId))) }
            .filter { it.kind == StatusNews.Kind.NeedsYou || !catchingUp }
    }

    private fun kindOf(news: ThreadNews, chat: RemoteChatSummary?) = when (news.kind) {
        ThreadNews.Kind.Waiting -> StatusNews.Kind.NeedsYou
        ThreadNews.Kind.Finished -> StatusNews.Kind.Finished
        ThreadNews.Kind.Failed -> if (chat?.limitResume != null) StatusNews.Kind.UsageLimit else StatusNews.Kind.Failed
    }
}

/** [queue] with [news] added: a thread's latest replaces what it said before. */
internal fun withNews(queue: List<StatusNews>, news: List<StatusNews>): List<StatusNews> {
    val fresh = news.map { it.chatId }.toSet()
    return queue.filter { it.chatId !in fresh } + news
}

/** [queue] without what's no longer worth saying: the thread is open, gone, or was answered since. */
internal fun stillNews(queue: List<StatusNews>, chats: List<RemoteChatSummary>, openId: String?): List<StatusNews> {
    val byId = chats.associateBy { it.id }
    return queue.filter { news ->
        val chat = byId[news.chatId]
        val answered = news.kind == StatusNews.Kind.NeedsYou && chat?.waiting != true
        chat != null && !answered && news.chatId != openId
    }
}
