import RelayRemote
import SwiftUI

/// A send held while it asks whether to compact first.
struct CompactFirstAsk {
    var steer: Bool
    var sendAt: Int64?
    /// The agent whose session compacts.
    var provider: String
    var tokens: Int64
}

extension View {
    /// The phone's take on the desktop's "Compact first" box by Send: a message that finds a big
    /// conversation's cache cold asks whether to compact first. Cancel sends nothing and keeps the draft.
    func compactFirstAlert(_ ask: Binding<CompactFirstAsk?>, onPick: @escaping (CompactFirstAsk, Bool) -> Void) -> some View {
        let size = ask.wrappedValue.map { formatTokens(Double($0.tokens)) } ?? ""
        return alert(
            "Compact \(size) first?", isPresented: Binding(get: { ask.wrappedValue != nil }, set: { if !$0 { ask.wrappedValue = nil } }),
            presenting: ask.wrappedValue
        ) { pending in
            Button("Compact first") { onPick(pending, true) }
            Button("Send as is") { onPick(pending, false) }
            Button("Cancel", role: .cancel) {}
        } message: { pending in
            Text(
                "The cache went cold, so \(agentName(pending.provider)) would read all \(size) tokens again. "
                    + "Compacting first sums up the thread, then sends your message.")
        }
    }
}
