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

/// What a project has saved with Always, by name.
public struct SavedSecrets: Codable, Sendable, Equatable {
    public struct Secret: Codable, Sendable, Equatable {
        public var name: String
    }

    public var secrets: [Secret]
    /// The computer has no credential store, so Always lasts only until Relay quits.
    public var sessionOnly: Bool?

    public init(secrets: [Secret] = [], sessionOnly: Bool? = nil) {
        self.secrets = secrets
        self.sessionOnly = sessionOnly
    }
}

/// shared/secret-request.ts secretNameKey: the same name however it is cased or spaced.
public func secretNameKey(_ name: String) -> String {
    name.split(whereSeparator: \.isWhitespace).joined(separator: " ").lowercased()
}

/// shared/secret-request.ts alwaysExplained: what the card says Always does,
/// the names the project already has saved, and how long it lasts where the
/// computer can't seal it.
public func alwaysExplained(name: String, saved: SavedSecrets) -> String {
    var parts = [
        "Always also keeps it for this project: any agent here that asks for “\(name)” by exactly that name gets it without a card."
    ]
    if saved.sessionOnly == true {
        parts.append("This computer has no credential store, so it is kept only until Relay quits.")
    }
    // The asked name itself can be saved by now, from another thread's card.
    let others = saved.secrets.map(\.name).filter { secretNameKey($0) != secretNameKey(name) }
    if !others.isEmpty {
        parts.append("Already saved here under other names: \(others.joined(separator: ", ")).")
    }
    return parts.joined(separator: " ")
}
