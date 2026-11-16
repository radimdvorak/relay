import SwiftUI

/// Draw over a photo going with the message: pen, blur, or black it out, with undo. Done hands
/// back the strokes and the photo with them burned in, or nil once every stroke is gone.
struct SketchEditor: View {
    let source: UIImage
    let strokes: [Stroke]
    let cancel: () -> Void
    let done: (_ strokes: [Stroke], _ burned: UIImage?) async -> Void

    @State private var painter: SketchPainter?
    @State private var history = StrokeHistory([])
    @State private var ink = Ink.pen
    @State private var color = penColors[0]
    @State private var saving = false

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ZStack {
                if let painter {
                    SketchSurface(painter: painter, strokes: history.strokes, ink: ink, color: color) { history.add($0) }
                } else {
                    ProgressView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            tools
        }
        .background(.black)
        .preferredColorScheme(.dark)
        .task {
            history = StrokeHistory(strokes)
            let photo = source
            painter = await Task.detached { SketchPainter(photo) }.value
        }
    }

    private var topBar: some View {
        HStack(spacing: 4) {
            Button("Cancel", systemImage: "xmark", action: cancel)
                .labelStyle(.iconOnly)
                .frame(width: 44, height: 44)
            Spacer()
            Button("Undo", systemImage: "arrow.uturn.backward") { history.undo() }
                .labelStyle(.iconOnly)
                .frame(width: 44, height: 44)
                .disabled(!history.canUndo)
            Button("Redo", systemImage: "arrow.uturn.forward") { history.redo() }
                .labelStyle(.iconOnly)
                .frame(width: 44, height: 44)
                .disabled(!history.canRedo)
            Button {
                Task { await finish() }
            } label: {
                if saving { ProgressView() } else { Text("Done") }
            }
            .buttonStyle(.accentGlass)
            .disabled(painter == nil || saving)
            .padding(.leading, 8)
        }
        .font(.system(size: 18, weight: .medium))
        .foregroundStyle(.white)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }

    private var tools: some View {
        VStack(spacing: 14) {
            // Kept while blurring too, so the bar doesn't jump between tools.
            HStack(spacing: 14) {
                ForEach(penColors, id: \.self) { swatch in
                    let picked = ink == .pen && swatch == color
                    Button {
                        color = swatch
                        ink = .pen
                    } label: {
                        Circle()
                            .fill(Color(uiColor: UIColor(rgb: swatch)))
                            .padding(picked ? 4 : 2)
                            .overlay(Circle().strokeBorder(picked ? .white : .white.opacity(0.35), lineWidth: picked ? 2 : 1))
                            .frame(width: 28, height: 28)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Pen colour")
                    .accessibilityAddTraits(picked ? .isSelected : [])
                }
            }
            HStack(spacing: 8) {
                tool("Pen", "pencil.tip", .pen)
                tool("Blur", "drop.halffull", .blur)
                tool("Black out", "eye.slash", .redact)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private func tool(_ label: String, _ symbol: String, _ value: Ink) -> some View {
        let picked = ink == value
        return Button {
            ink = value
        } label: {
            Label(label, systemImage: symbol)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(picked ? .black : .white)
                .padding(.horizontal, 14)
                .frame(height: 36)
                .background(picked ? .white : .white.opacity(0.12), in: .capsule)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(picked ? .isSelected : [])
    }

    private func finish() async {
        guard let painter, !saving else { return }
        let kept = history.strokes
        if kept == strokes { return cancel() }
        if kept.isEmpty { return await done(kept, nil) }
        saving = true
        let burned = await Task.detached { painter.burn(kept) }.value
        await done(kept, burned)
    }
}

/// Its ends meeting within this many points closes a blur or redact.
private let closeReach: CGFloat = 28

/// The photo fitted into the space, drawn on with one finger.
private struct SketchSurface: View {
    let painter: SketchPainter
    let strokes: [Stroke]
    let ink: Ink
    let color: UInt32
    let add: (Stroke) -> Void
    @State private var live: Stroke?

    var body: some View {
        GeometryReader { geometry in
            let fit = Fit(photo: painter.photo.size, in: geometry.size)
            Canvas { context, _ in
                context.withCGContext { cg in
                    cg.translateBy(x: fit.origin.x, y: fit.origin.y)
                    cg.scaleBy(x: fit.scale, y: fit.scale)
                    cg.clip(to: painter.bounds)
                    painter.draw(cg, strokes)
                    if let live { painter.draw(cg, live) }
                }
            }
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { drag in
                        let at = fit.toPhoto(drag.location)
                        if var stroke = live {
                            guard let last = stroke.points.last, hypot(at.x - last.x, at.y - last.y) * fit.scale >= 2 else { return }
                            stroke.points.append(at)
                            live = stroke
                        } else {
                            live = Stroke(ink: ink, color: color, width: ink.width / fit.scale, points: [at])
                        }
                    }
                    .onEnded { _ in
                        guard var stroke = live else { return }
                        live = nil
                        stroke.closed = closesUp(stroke.ink, stroke.points, reach: closeReach / fit.scale)
                        add(stroke)
                    }
            )
        }
    }
}

/// Where the photo sits in the view: its scale and top-left corner, fitted and centred.
private struct Fit {
    let scale: CGFloat
    let origin: CGPoint

    init(photo: CGSize, in space: CGSize) {
        scale = min(space.width / photo.width, space.height / photo.height)
        origin = CGPoint(x: (space.width - photo.width * scale) / 2, y: (space.height - photo.height * scale) / 2)
    }

    func toPhoto(_ at: CGPoint) -> CGPoint {
        CGPoint(x: (at.x - origin.x) / scale, y: (at.y - origin.y) / scale)
    }
}
