import Foundation
import Testing

@testable import RelayRemote

// Mirrors Android's ChecklistTest (cases from shared/agent-checklist.test.ts).

private func steps(_ states: String...) -> [ChecklistStep] {
    states.enumerated().map { ChecklistStep(text: "Step \($0.offset + 1)", state: $0.element) }
}

private final class Thread {
    private var seq: Int64 = 0

    private func message(_ role: String, _ status: String, parentId: String? = nil) -> ChatMessage {
        seq += 1
        var fields: [String: JSON] = [
            "id": .string("m\(seq)"), "role": .string(role), "body": .string(""), "status": .string(status),
            "created": .number(Double(seq)), "seq": .number(Double(seq)), "provider": .string("codex"), "version": .number(1),
        ]
        if let parentId { fields["parentId"] = .string(parentId) }
        return try! JSON.object(fields).decode()
    }

    func asked(side: Bool? = nil, steered: Bool? = nil, missed: Bool? = nil) -> ChatMessage {
        var m = message("user", "complete")
        m.side = side
        m.steered = steered
        m.missed = missed
        return m
    }

    func answer(
        _ steps: [ChecklistStep]? = nil, status: String = "complete", parentId: String? = nil,
        compaction: Bool? = nil, byAgent: String? = nil
    ) -> ChatMessage {
        var m = message("assistant", status, parentId: parentId)
        m.compaction = compaction
        guard let steps else { return m }
        var trace: AgentTrace = try! JSON.parse(
            #"{"kind":"activity","id":"list","activity":{"id":"list","kind":"tool","label":"Updated progress","status":"complete"}}"#
        ).decode()
        trace.activity?.checklist = steps
        trace.activity?.parentId = byAgent
        m.trace = [trace]
        return m
    }
}

@Test func showsTheLatestChecklistWhileTheAnswerRunsAndOnceItEnds() {
    let t = Thread()
    let old = t.answer(steps("done", "now"))
    let latest = t.answer(steps("now", "next"), status: "streaming")
    // However they come, the thread's order counts.
    #expect(threadChecklist([latest, old]) == steps("now", "next"))
    #expect(threadChecklist([t.asked(), t.answer(steps("done", "done"))]) == steps("done", "done"))
}

@Test func dropsAFinishedChecklistAtTheNextMessageAndKeepsAnUnfinishedOne() {
    let t = Thread()
    #expect(threadChecklist([t.asked(), t.answer(steps("done", "failed")), t.asked()]) == nil)
    let open = steps("done", "next")
    #expect(threadChecklist([t.asked(), t.answer(open), t.asked(), t.answer(status: "streaming")]) == open)
}

@Test func dropsAnUnfinishedChecklistOnceAnAnswerEndsWithoutTouchingIt() {
    let t = Thread()
    let open = steps("done", "next")
    #expect(threadChecklist([t.asked(), t.answer(open), t.asked(), t.answer()]) == nil)
    #expect(threadChecklist([t.asked(), t.answer(open), t.asked(), t.answer(status: "cancelled")]) == open)
    #expect(threadChecklist([t.asked(), t.answer(open), t.answer(compaction: true)]) == open)
    #expect(threadChecklist([t.asked(), t.answer(steps("now")), t.asked(), t.answer([])]) == nil)
}

@Test func leavesOutASubagentsChecklistAndASideConversations() {
    let t = Thread()
    let own = t.answer(steps("now"))
    #expect(threadChecklist([own, t.answer(steps("now", "now"), parentId: own.id)]) == steps("now"))
    #expect(threadChecklist([t.asked(), t.answer(steps("now"), byAgent: "agent-call")]) == nil)
}

@Test func leavesOutABtwQuestionAndAReplyWhoseParentIsntHeld() {
    let t = Thread()
    let finished = [t.asked(), t.answer(steps("done"))]
    let question = t.asked(side: true)
    #expect(threadChecklist(finished + [question, t.answer(parentId: question.id)]) == steps("done"))
    let open = [t.asked(), t.answer(steps("now"))]
    #expect(threadChecklist(open + [t.answer(parentId: "not-held")]) == steps("now"))
}

@Test func docksAFinishedChecklistOnlyWhileTheAnswerRuns() {
    let t = Thread()
    let thread = [t.asked(), t.answer(steps("done", "done"))]
    #expect(dockedChecklist(thread, running: true) == steps("done", "done"))
    #expect(dockedChecklist(thread, running: false) == nil)
    #expect(dockedChecklist([t.asked(), t.answer(steps("now"))], running: false) == steps("now"))
}

@Test func countsTheChecklistStepBeingWorkedOn() {
    #expect(checklistProgress(steps("done", "failed", "now", "next")) == "3 of 4")
    #expect(checklistProgress(steps("done", "done")) == "2 of 2")
    #expect(checklistProgress(steps("next", "next")) == "0 of 2")
    #expect(currentStep(steps("done", "next", "now"))?.text == "Step 3")
}
