// shared/secret-request.ts: what a thread keeps of a secret an agent asked
// for (request_secret). Never the value, only how the request ended.

/// The one hidden question a secret request carries, under which its outcome is kept.
public let secretQuestionId = "secret"

/// How a secret request ended: the row's label, and where the value went under it.
public struct SecretOutcome: Sendable, Equatable {
    public var label: String
    public var note: String?

    public init(label: String, note: String? = nil) {
        self.label = label
        self.note = note
    }
}

/// `kept` is what the group's answers hold under `secretQuestionId`; nil when the user declined.
public func secretOutcome(name: String, kept: String?) -> SecretOutcome {
    switch kept {
    case nil:
        SecretOutcome(label: "Declined \"\(name)\"")
    case "Remembered":
        SecretOutcome(label: "Used saved \"\(name)\"", note: "Saved for this project with Always, so there was no card")
    case "Always":
        SecretOutcome(
            label: "Saved \"\(name)\"",
            note: "In a private file, not in the chat. Always: this project's agents get it without asking")
    default:
        SecretOutcome(label: "Saved \"\(name)\"", note: "In a private file, not in the chat")
    }
}

/// Where the card says the value goes.
public func secretDestination(agent: String) -> String {
    "It goes to a private file that \(agent)'s commands read, not into the chat. Relay deletes the file after 24 hours."
}
