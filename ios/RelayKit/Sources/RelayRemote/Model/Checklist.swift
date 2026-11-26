import Foundation

// shared/agent-checklist.ts, as Android's Checklist.kt: the steps an agent set itself, and which
// of them a thread shows.

private func isOver(_ step: ChecklistStep) -> Bool { step.state == "done" || step.state == "failed" }

public func checklistIsOver(_ steps: [ChecklistStep]) -> Bool { steps.allSatisfy(isOver) }

/// Where it stands, as "3 of 5": the step it's on counts.
public func checklistProgress(_ steps: [ChecklistStep]) -> String {
    let over = steps.count(where: isOver)
    let working = steps.contains { $0.state == "now" }
    return "\(working ? over + 1 : over) of \(steps.count)"
}

/// The step the agent is on, or else the first it hasn't reached.
public func currentStep(_ steps: [ChecklistStep]) -> ChecklistStep? {
    steps.first { $0.state == "now" } ?? steps.first { $0.state == "next" }
}

/// The checklist an answer last left; a subagent's is its own.
private func checklistOf(_ message: ChatMessage) -> [ChecklistStep]? {
    message.trace?.last { $0.activity?.checklist != nil && $0.activity?.parentId == nil }?.activity?.checklist
}

/// An agent's finished answer, not a row Relay or a compaction left between answers.
private func isFinishedAnswer(_ m: ChatMessage) -> Bool {
    m.role == "assistant" && m.status == "complete" && m.compaction != true
        && m.handoff == nil && m.reload == nil && m.worktreeCommand == nil
}

/// A steer the agent read mid-answer: the answer above it ended only because the turn carried on below.
private func steeredIn(_ m: ChatMessage) -> Bool { m.steered == true && m.missed != true }

/// The thread's own conversation: no `/btw` question, and no reply, even one whose parent isn't held.
private func ownConversation(_ messages: [ChatMessage]) -> [ChatMessage] {
    messages.sorted(by: threadOrder).filter { $0.parentId == nil && $0.side != true }
}

/// The checklist the main conversation is on. A finished one stays until the next message; an
/// unfinished one carries over, until an answer ends without having touched it.
public func threadChecklist(_ messages: [ChatMessage]) -> [ChecklistStep]? {
    var askedSince = false
    var steeredBelow = false
    for message in ownConversation(messages).reversed() {
        if let steps = checklistOf(message) {
            let putAway = askedSince && checklistIsOver(steps)
            return steps.isEmpty || putAway ? nil : steps
        }
        if isFinishedAnswer(message) && !steeredBelow { return nil }
        steeredBelow = steeredIn(message)
        if message.role == "user" && !steeredBelow { askedSince = true }
    }
    return nil
}

/// What the dock shows: a phone hasn't the room to keep a finished one there once the answer is in.
public func dockedChecklist(_ messages: [ChatMessage], running: Bool) -> [ChecklistStep]? {
    threadChecklist(messages).flatMap { running || !checklistIsOver($0) ? $0 : nil }
}
