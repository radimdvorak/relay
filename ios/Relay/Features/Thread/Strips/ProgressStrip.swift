import RelayRemote
import SwiftUI

/// The step the agent is on, over the composer; a tap lists them all. One line, whatever the step says.
struct ProgressStrip: View {
    let steps: [ChecklistStep]
    let open: () -> Void
    @Environment(\.colors) private var colors

    var body: some View {
        Button(action: open) {
            HStack(spacing: 10) {
                Image(systemName: "checklist").font(.footnote).foregroundStyle(colors.muted).frame(width: 18)
                // One Text, the count first so a long step cuts its own end off: split into views it can loop layout.
                Text("\(Text(checklistProgress(steps)).monospacedDigit().foregroundStyle(colors.muted))  \(currentStep(steps)?.text ?? "Progress")")
                    .foregroundStyle(colors.foreground)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.up").font(.caption.weight(.semibold)).foregroundStyle(colors.muted)
            }
            .font(.subheadline)
            .lineLimit(1)
            .padding(.horizontal, 14)
            .frame(height: 40)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 20))
        .padding(.horizontal, 20)
        .padding(.bottom, 6)
        .accessibilityHint("Lists the steps")
    }
}

struct ProgressSheet: View {
    let steps: [ChecklistStep]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                ChecklistSteps(steps: steps, spacing: 14).font(.body).padding(20)
            }
            .navigationTitle("Progress · \(checklistProgress(steps))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done", systemImage: "checkmark") { dismiss() } }
            }
        }
    }
}

/// Each step and where it stands. Still: the running call is what moves.
struct ChecklistSteps: View {
    let steps: [ChecklistStep]
    var spacing: CGFloat = 6
    @Environment(\.colors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    mark(step.state)
                    Text(step.text).foregroundStyle(step.state == "now" ? colors.foreground : colors.muted)
                }
                .accessibilityElement(children: .combine)
                .accessibilityValue(spoken(step.state))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Ticked, half-filled while the agent is on it, crossed when it failed, or an empty ring.
    private func mark(_ state: String) -> some View {
        Group {
            switch state {
            case "done": Image(systemName: "checkmark").foregroundStyle(colors.muted)
            case "failed": Image(systemName: "xmark").foregroundStyle(.red)
            case "now": Image(systemName: "circle.inset.filled").foregroundStyle(colors.accent)
            default: Image(systemName: "circle").foregroundStyle(colors.faint)
            }
        }
        .font(.footnote)
        .frame(width: 18)
        .accessibilityHidden(true)
    }

    private func spoken(_ state: String) -> String {
        switch state {
        case "done": "Done"
        case "now": "Working on it"
        case "failed": "Failed"
        default: "Not started"
        }
    }
}
