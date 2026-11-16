import Foundation

/// `agentResponseSchema` (shared/agent-modes.ts): the user's answer to an agent.
public enum AgentResponse: Sendable, Equatable {
    /// "accept" | "acceptForSession" | "decline" | "cancel"
    case decision(String)
    /// Each question's id to the chosen labels or typed text.
    case answers([String: [String]])
    /// A page's answer; nil skips it.
    case page(String?)
    /// What was typed for a secret request, nil to decline it; `remember` for Always.
    case secret(String?, remember: Bool = false)

    var json: JSON {
        switch self {
        case .decision(let d): ["kind": "approval", "decision": .string(d)]
        case .answers(let a): ["kind": "question", "answers": .object(a.mapValues { .array($0.map(JSON.string)) })]
        case .page(let answer): ["kind": "page", "answer": answer.map(JSON.string) ?? .null]
        case .secret(let value, let remember):
            remember
                ? ["kind": "secret", "value": value.map(JSON.string) ?? .null, "remember": true]
                : ["kind": "secret", "value": value.map(JSON.string) ?? .null]
        }
    }
}

public extension RemoteClient {
    /// Answers a request the running turn waits on (`chat.requests`).
    func respond(chatId: String, requestId: String, _ response: AgentResponse) async throws {
        _ = try await desktop("respondProjectChat", [.string(chatId), .string(requestId), response.json])
    }

    /// Answers a question group the agent asked without stopping (`message.questions`).
    func answerQuestion(chatId: String, messageId: String, groupId: String, _ response: AgentResponse) async throws {
        _ = try await desktop(
            "answerProjectChatQuestion", [.string(chatId), .string(messageId), .string(groupId), response.json])
    }

    /// What the thread's project has saved with Always; from `Bridge.secretAlways`.
    func savedSecrets(chatId: String) async throws -> SavedSecrets {
        try await desktop("threadSecrets", [.string(chatId)]).decode()
    }

    func setQuestionDismissed(chatId: String, messageId: String, groupId: String, _ dismissed: Bool) async throws {
        _ = try await desktop(
            "setProjectChatQuestionDismissed",
            [.string(chatId), .string(messageId), .string(groupId), .bool(dismissed)])
    }
}
