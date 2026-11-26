package dev.relay.android.features.thread.logic

import dev.relay.android.remote.model.ChatMessage
import dev.relay.android.remote.model.ChecklistStep

// shared/agent-checklist.ts: the steps an agent set itself, and which of them a thread shows.

private fun isOver(step: ChecklistStep) = step.state == "done" || step.state == "failed"

fun checklistIsOver(steps: List<ChecklistStep>) = steps.all(::isOver)

/** Where it stands, as "3 of 5": the step it's on counts. */
fun checklistProgress(steps: List<ChecklistStep>): String {
    val over = steps.count(::isOver)
    val working = steps.any { it.state == "now" }
    return "${if (working) over + 1 else over} of ${steps.size}"
}

/** The step the agent is on, or else the first it hasn't reached. */
fun currentStep(steps: List<ChecklistStep>): ChecklistStep? =
    steps.firstOrNull { it.state == "now" } ?: steps.firstOrNull { it.state == "next" }

/** The checklist an answer last left; a subagent's is its own. */
private fun checklistOf(message: ChatMessage): List<ChecklistStep>? =
    message.trace?.lastOrNull { it.activity?.checklist != null && it.activity.parentId == null }?.activity?.checklist

/** An agent's finished answer, not a row Relay or a compaction left between answers. */
private fun isFinishedAnswer(m: ChatMessage) =
    m.role == "assistant" && m.status == "complete" && m.compaction != true &&
        m.handoff == null && m.reload == null && m.worktreeCommand == null

/** A steer the agent read mid-answer: the answer above it ended only because the turn carried on below. */
private fun steeredIn(m: ChatMessage) = m.steered == true && m.missed != true

/** The thread's own conversation: no `/btw` question, and no reply, even one whose parent isn't held. */
private fun ownConversation(messages: List<ChatMessage>) =
    messages.sortedWith(threadOrder).filter { it.parentId == null && it.side != true }

/**
 * The checklist the main conversation is on. A finished one stays until the next message; an
 * unfinished one carries over, until an answer ends without having touched it.
 */
fun threadChecklist(messages: List<ChatMessage>): List<ChecklistStep>? {
    var askedSince = false
    var steeredBelow = false
    for (message in ownConversation(messages).asReversed()) {
        val steps = checklistOf(message)
        if (steps != null) {
            val putAway = askedSince && checklistIsOver(steps)
            return steps.takeIf { it.isNotEmpty() && !putAway }
        }
        if (isFinishedAnswer(message) && !steeredBelow) return null
        steeredBelow = steeredIn(message)
        if (message.role == "user" && !steeredBelow) askedSince = true
    }
    return null
}

/** The agent is still writing the main conversation's answer, not compacting or running something beside it. */
private fun answerStreaming(messages: List<ChatMessage>) =
    ownConversation(messages).lastOrNull()?.let { it.role == "assistant" && it.status == "streaming" && it.compaction != true } == true

/** What the dock shows: a phone hasn't the room to keep a finished one there once the answer is in. */
fun dockedChecklist(messages: List<ChatMessage>, running: Boolean): List<ChecklistStep>? =
    threadChecklist(messages)?.takeIf { !checklistIsOver(it) || (running && answerStreaming(messages)) }
