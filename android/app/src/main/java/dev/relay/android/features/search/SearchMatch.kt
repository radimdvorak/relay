package dev.relay.android.features.search

import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProject

/** The words a query asks for; every one has to be found. */
internal fun searchTerms(query: String): List<String> = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Every place a term shows up in [text], also inside words; overlapping and touching ones as one range. */
internal fun matchRanges(text: String, terms: List<String>): List<IntRange> {
    val found = terms.flatMap { term ->
        generateSequence(text.indexOf(term, ignoreCase = true)) { text.indexOf(term, it + 1, ignoreCase = true) }
            .takeWhile { it >= 0 }
            .map { it until it + term.length }
            .toList()
    }.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (range in found) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        else merged += range
    }
    return merged
}

private fun String.has(term: String) = contains(term, ignoreCase = true)

/** A thread found, and whether only its project's name matched, which its row then shows. */
internal data class FoundThread(val chat: RemoteChatSummary, val project: String, val byProject: Boolean)

/** What threads and projects a query finds. */
internal data class Found(val threads: List<FoundThread>, val projects: List<RemoteProject>)

/**
 * Threads by title and project name, title matches first, both keeping [chats]' order (newest
 * first); then projects by name. With nothing typed, every thread and no project.
 */
internal fun search(query: String, chats: List<RemoteChatSummary>, projects: List<RemoteProject>): Found {
    val terms = searchTerms(query)
    val names = projects.associate { it.id to it.name }
    val threads = chats.mapNotNull { chat ->
        val project = names[chat.projectId].orEmpty()
        if (!terms.all { chat.title.has(it) || project.has(it) }) return@mapNotNull null
        FoundThread(chat, project, byProject = terms.any { !chat.title.has(it) })
    }
    val byTitleFirst = threads.sortedBy { it.byProject }
    if (terms.isEmpty()) return Found(byTitleFirst, emptyList())
    return Found(byTitleFirst, projects.filter { p -> p.scratch != true && terms.all { p.name.has(it) } })
}
