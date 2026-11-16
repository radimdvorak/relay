import RelayRemote
import SwiftUI

/// An agent asked for a secret only the user has (request_secret). What is
/// typed goes to the computer in one sealed bridge call and is kept nowhere
/// on the phone.
struct SecretRequestCard: View {
    let secret: SecretRequest
    let agent: String
    let busy: Bool
    /// The typed secret, or nil when the user declines.
    let onAnswer: (String?) -> Void
    @State private var value = ""

    private var typed: String { value.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            KeyLabel(secret.name)
                .font(.subheadline.weight(.semibold))
            if let reason = secret.reason, !reason.isEmpty {
                Text(reason).font(.subheadline).foregroundStyle(.secondary)
            }
            // A secure field: VoiceOver doesn't read it back and the keyboard doesn't learn it.
            // Marked as a one-time code so the system offers neither saved logins nor to save it.
            SecureField("Paste or type it…", text: $value)
                .textContentType(.oneTimeCode)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .privacySensitive()
                .submitLabel(.done)
                .onSubmit { if !typed.isEmpty { onAnswer(typed) } }
                .accessibilityLabel(secret.name)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(.quietFill, in: .rect(cornerRadius: 12))
            Text(secretDestination(agent: agent))
                .font(.footnote)
                .foregroundStyle(.secondary)
            HStack {
                Button("Decline") { onAnswer(nil) }
                    .buttonStyle(.glass)
                Spacer()
                Button("Save") { onAnswer(typed) }
                    .buttonStyle(.glassProminent)
                    .disabled(typed.isEmpty)
            }
        }
        .disabled(busy)
    }
}

/// How a secret request ended, said once.
struct SecretOutcomeRow: View {
    let outcome: SecretOutcome

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            KeyLabel(outcome.label)
            if let note = outcome.note {
                Text(note).padding(.leading, 26)
            }
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A line under a key. The key is decoration: VoiceOver calls that symbol "Passwords".
private struct KeyLabel: View {
    let text: String

    init(_ text: String) { self.text = text }

    var body: some View {
        Label(text, systemImage: "key")
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(text)
    }
}
