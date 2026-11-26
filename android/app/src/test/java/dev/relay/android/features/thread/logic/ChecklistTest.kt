package dev.relay.android.features.thread.logic

import dev.relay.android.remote.model.AgentActivity
import dev.relay.android.remote.model.AgentTrace
import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.ChecklistStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Cases from shared/agent-checklist.test.ts; ios ChecklistTests.swift mirrors them.
class ChecklistTest {
    private var seq = 0L

    private fun steps(vararg states: String) = states.mapIndexed { i, state -> ChecklistStep("Step ${i + 1}", state) }

    private fun message(
        role: String, status: String = "complete", trace: List<AgentTrace>? = null, parentId: String? = null,
        compaction: Boolean? = null, side: Boolean? = null, steered: Boolean? = null, missed: Boolean? = null,
    ) = (++seq).let {
        ChatMessage(id = "m$it", role = role, body = "", status = status, created = it, seq = it, provider = "codex",
            version = 1, trace = trace, parentId = parentId, compaction = compaction, side = side, steered = steered, missed = missed)
    }

    private fun asked(side: Boolean? = null, steered: Boolean? = null, missed: Boolean? = null) =
        message("user", side = side, steered = steered, missed = missed)

    private fun answer(steps: List<ChecklistStep>? = null, status: String = "complete", parentId: String? = null, compaction: Boolean? = null, byAgent: String? = null) =
        message("assistant", status, steps?.let {
            listOf(AgentTrace("activity", "list", activity = AgentActivity("list", "tool", "Updated progress", "complete", parentId = byAgent, checklist = it)))
        }, parentId, compaction)

    @Test fun showsTheLatestWhileTheAnswerRunsAndOnceItEnds() {
        val old = answer(steps("done", "now"))
        val latest = answer(steps("now", "next"), status = "streaming")
        // However they come, the thread's order counts.
        assertEquals(steps("now", "next"), threadChecklist(listOf(latest, old)))
        assertEquals(steps("done", "done"), threadChecklist(listOf(asked(), answer(steps("done", "done")))))
    }

    @Test fun dropsAFinishedOneAtTheNextMessageAndKeepsAnUnfinishedOne() {
        assertNull(threadChecklist(listOf(asked(), answer(steps("done", "failed")), asked())))
        val open = steps("done", "next")
        assertEquals(open, threadChecklist(listOf(asked(), answer(open), asked(), answer(status = "streaming"))))
    }

    @Test fun dropsAnUnfinishedOneOnceAnAnswerEndsWithoutTouchingIt() {
        val open = steps("done", "next")
        assertNull(threadChecklist(listOf(asked(), answer(open), asked(), answer())))
        assertEquals(open, threadChecklist(listOf(asked(), answer(open), asked(), answer(status = "cancelled"))))
        assertEquals(open, threadChecklist(listOf(asked(), answer(open), answer(compaction = true))))
        assertNull(threadChecklist(listOf(asked(), answer(steps("now")), asked(), answer(emptyList()))))
    }

    @Test fun leavesOutASubagentsAndASideConversations() {
        val own = answer(steps("now"))
        assertEquals(steps("now"), threadChecklist(listOf(asked(), own, answer(steps("now", "now"), parentId = own.id))))
        assertNull(threadChecklist(listOf(asked(), answer(steps("now"), byAgent = "agent-call"))))
    }

    @Test fun leavesOutABtwQuestionAndAReplyWhoseParentIsntHeld() {
        val finished = listOf(asked(), answer(steps("done")))
        val question = asked(side = true)
        assertEquals(steps("done"), threadChecklist(finished + question + answer(parentId = question.id)))
        val open = listOf(asked(), answer(steps("now")))
        assertEquals(steps("now"), threadChecklist(open + answer(parentId = "not-held")))
    }

    @Test fun carriesOneAcrossTheAnswersSteersSplitATurnInto() {
        val open = steps("done", "now")
        val turn = listOf(asked(), answer(open), asked(steered = true), answer(), asked(steered = true), answer(status = "streaming"))
        assertEquals(open, threadChecklist(turn))
        // A steer the turn ended before reading goes out as the next turn's request.
        assertNull(threadChecklist(listOf(asked(), answer(open), asked(steered = true, missed = true), answer())))
    }

    @Test fun docksAFinishedOneOnlyWhileTheAnswerRuns() {
        val thread = listOf(asked(), answer(steps("done", "done")))
        assertEquals(steps("done", "done"), dockedChecklist(thread, running = true))
        assertNull(dockedChecklist(thread, running = false))
        assertEquals(steps("now"), dockedChecklist(listOf(asked(), answer(steps("now"))), running = false))
    }

    @Test fun countsTheStepBeingWorkedOn() {
        assertEquals("3 of 4", checklistProgress(steps("done", "failed", "now", "next")))
        assertEquals("2 of 2", checklistProgress(steps("done", "done")))
        assertEquals("0 of 2", checklistProgress(steps("next", "next")))
        assertEquals("Step 3", currentStep(steps("done", "next", "now"))?.text)
    }
}
