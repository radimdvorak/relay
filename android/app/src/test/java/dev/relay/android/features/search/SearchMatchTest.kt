package dev.relay.android.features.search

import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.remote.model.RemoteProject
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchMatchTest {
    private val projects = listOf(RemoteProject("r", "relay"), RemoteProject("w", "Website"), RemoteProject("s", "Scratchpad", scratch = true))

    private fun chat(title: String, project: String = "r") =
        RemoteChatSummary(title, project, title, scope = "project", updated = 0, created = 0)

    private fun bold(text: String, query: String) = matchRanges(text, searchTerms(query)).map { text.substring(it) }

    private fun titles(query: String, vararg chats: RemoteChatSummary) = search(query, chats.toList(), projects).threads.map { it.chat.title }

    @Test
    fun whatMatchedIsBoldInsideWordsAndEveryTimeItShowsUp() {
        assertEquals(listOf("Plan"), bold("Plan a 5K training week", "plan"))
        assertEquals(listOf("plan"), bold("Houseplant light check", "PLAN"))
        assertEquals(listOf("plan", "plan"), bold("plan the planet", "plan"))
        assertEquals(emptyList<String>(), bold("Bread baking", "plan"))
        assertEquals(emptyList<String>(), bold("Bread baking", "  "))
    }

    @Test
    fun overlappingAndTouchingMatchesAreOneRun() {
        assertEquals(listOf("android"), bold("android app", "andro droid"))
        assertEquals(listOf("aaa"), bold("aaa", "aa"))
        assertEquals(listOf("ab"), bold("ab", "a b"))
    }

    @Test
    fun rangesFitTextWhoseLowerCaseIsLonger() {
        // "İ".lowercase() is two characters; indices into the lowercased text would be off by one.
        assertEquals(listOf("plan"), bold("İzmir plan", "plan"))
    }

    @Test
    fun everyWordHasToMatchAndAProjectCountsForItsThreads() {
        val chats = arrayOf(chat("Fix the composer"), chat("Pricing page", "w"), chat("Composer on the website", "w"))
        assertEquals(listOf("Composer on the website"), titles("web composer", *chats))
        assertEquals(listOf("Composer on the website", "Pricing page"), titles("web", *chats))
        assertEquals(emptyList<String>(), titles("web android", *chats))
    }

    @Test
    fun aThreadFoundByItsProjectSaysSoAndComesAfterTitles() {
        val found = search("relay", listOf(chat("Composer"), chat("Relay on Windows", "w")), projects).threads
        assertEquals(listOf("Relay on Windows" to false, "Composer" to true), found.map { it.chat.title to it.byProject })
    }

    @Test
    fun projectsShowOnlyForAQueryAndTheScratchpadNever() {
        assertEquals(emptyList<RemoteProject>(), search("", listOf(chat("a")), projects).projects)
        assertEquals(listOf("w"), search("site", emptyList(), projects).projects.map { it.id })
        assertEquals(emptyList<RemoteProject>(), search("scratch", emptyList(), projects).projects)
    }
}
