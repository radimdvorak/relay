import Foundation
import Testing
@testable import RelayRemote

/// Mirrors Android's SecretRequestsTest and the desktop's rows.
@Suite struct SecretRequestsTests {
    @Test func saysHowASecretRequestEndedOnce() {
        #expect(secretOutcome(name: "npm token", kept: nil) == SecretOutcome(label: "Declined \"npm token\""))
        #expect(secretOutcome(name: "npm token", kept: "Saved")
            == SecretOutcome(label: "Saved \"npm token\"", note: "In a private file, not in the chat"))
        #expect(secretOutcome(name: "npm token", kept: "Always")
            == SecretOutcome(
                label: "Saved \"npm token\"",
                note: "In a private file, not in the chat. Always: this project's agents get it without asking"))
        #expect(secretOutcome(name: "npm token", kept: "Remembered")
            == SecretOutcome(label: "Used saved \"npm token\"", note: "Saved for this project with Always, so there was no card"))
    }

    @Test func tellsWhereTheValueGoes() {
        #expect(secretDestination(agent: "Claude")
            == "It goes to a private file that Claude's commands read, not into the chat. Relay deletes the file after 24 hours.")
    }

    @Test func saysWhatAlwaysDoesWhatIsAlreadySavedAndHowLongItLasts() throws {
        let base = "Always also keeps it for this project: any agent here that asks for “npm token” by exactly that name gets it without a card."
        #expect(alwaysExplained(name: "npm token", saved: SavedSecrets()) == base)
        #expect(alwaysExplained(
            name: "npm token",
            saved: SavedSecrets(secrets: [.init(name: "npm publish token"), .init(name: "Staging password")]))
            == "\(base) Already saved here under other names: npm publish token, Staging password.")
        // Saved from another thread while this card was open: not another name.
        #expect(alwaysExplained(
            name: "npm token",
            saved: SavedSecrets(secrets: [.init(name: " NPM  token"), .init(name: "Staging password")]))
            == "\(base) Already saved here under other names: Staging password.")
        // As the desktop sends it.
        let sessionOnly = try JSONDecoder().decode(
            SavedSecrets.self,
            from: Data(#"{"secrets":[{"name":"npm publish token","saved":1}],"sessionOnly":true}"#.utf8))
        #expect(alwaysExplained(name: "npm token", saved: sessionOnly)
            == "\(base) This computer has no credential store, so it is kept only until Relay quits. Already saved here under other names: npm publish token.")
    }

    @Test func sendsTheValueOrADecline() {
        #expect(AgentResponse.secret("abc").json == ["kind": "secret", "value": "abc"])
        #expect(AgentResponse.secret(nil).json == ["kind": "secret", "value": .null])
        #expect(AgentResponse.secret("abc", remember: true).json == ["kind": "secret", "value": "abc", "remember": true])
    }

    @Test func readsASecretRequestFromTheDesktop() throws {
        let json = #"{"id":"g","secret":{"name":"npm publish token","reason":"To publish."},"questions":[{"id":"secret","question":"To publish.","isSecret":true}]}"#
        let group = try JSONDecoder().decode(AsyncAgentQuestions.self, from: Data(json.utf8))
        #expect(group.secret == SecretRequest(name: "npm publish token", reason: "To publish."))
    }
}
