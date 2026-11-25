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

/**
 * The checklist the main conversation is on. A finished one stays until the next message; an
 * unfinished one carries over, until an answer ends without having touched it.
 */
fun threadChecklist(messages: List<ChatMessage>): List<ChecklistStep>? {
    var askedSince = false
    for (message in mainMessages(messages.sortedWith(threadOrder)).asReversed()) {
        val steps = checklistOf(message)
        if (steps != null) {
            val putAway = askedSince && checklistIsOver(steps)
            return steps.takeIf { it.isNotEmpty() && !putAway }
        }
        if (isFinishedAnswer(message)) return null
        if (message.role == "user") askedSince = true
    }
    return null
}

/** What the dock shows: a phone hasn't the room to keep a finished one there once the answer is in. */
fun dockedChecklist(messages: List<ChatMessage>, running: Boolean): List<ChecklistStep>? =
    threadChecklist(messages)?.takeIf { running || !checklistIsOver(it) }
