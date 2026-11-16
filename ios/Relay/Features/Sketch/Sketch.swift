import CoreGraphics
import RelayRemote

/// What a stroke does to the photo under it.
nonisolated enum Ink: Sendable {
    case pen, blur, redact

    /// Brush width in on-screen points; the stroke keeps it in photo pixels.
    var width: CGFloat {
        switch self {
        case .pen: 4
        case .blur: 30
        case .redact: 16
        }
    }
}

/// One finger's line over a photo, in the photo's own pixels so it survives any on-screen
/// scaling. A closed blur or redact covers its whole inside.
nonisolated struct Stroke: Equatable, Sendable {
    let ink: Ink
    let color: UInt32
    let width: CGFloat
    var points: [CGPoint]
    var closed = false
}

/// The photo before any ink and the strokes over it, kept so a second edit can take strokes back.
nonisolated struct Sketched: Sendable {
    let original: PastedImage
    let strokes: [Stroke]
}

let penColors: [UInt32] = [0xFF3B30, 0xFF9500, 0xFFD100, 0x34C759, 0x007AFF, 0xFFFFFF, 0x1A1A1A]

/// A blur or redact that comes back to where it started covers what it went around: the ends
/// meet within `reach` and it went well beyond that.
nonisolated func closesUp(_ ink: Ink, _ points: [CGPoint], reach: CGFloat) -> Bool {
    guard ink != .pen, points.count >= 8, let first = points.first, let last = points.last else { return false }
    let endsMeet = hypot(first.x - last.x, first.y - last.y) <= reach
    let xs = points.map(\.x), ys = points.map(\.y)
    let wentAround = hypot(xs.max()! - xs.min()!, ys.max()! - ys.min()!) > reach * 3
    return endsMeet && wentAround
}

/// The strokes of one photo with what was undone, for redo; any new stroke drops the undone ones.
struct StrokeHistory {
    private(set) var strokes: [Stroke]
    private var undone: [Stroke] = []

    init(_ strokes: [Stroke]) { self.strokes = strokes }

    var canUndo: Bool { !strokes.isEmpty }
    var canRedo: Bool { !undone.isEmpty }

    mutating func add(_ stroke: Stroke) {
        strokes.append(stroke)
        undone = []
    }

    mutating func undo() {
        guard let last = strokes.popLast() else { return }
        undone.append(last)
    }

    mutating func redo() {
        guard let next = undone.popLast() else { return }
        strokes.append(next)
    }
}
