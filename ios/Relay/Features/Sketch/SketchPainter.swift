import UIKit

/// Long side of the copy a blur samples from: small enough that text in it can't be read back.
private nonisolated let blurSide: CGFloat = 96

/// Draws a photo with its strokes, the same way on screen and into the image that's sent, so
/// what you see is what goes. Context units are photo pixels, origin at the top left.
nonisolated final class SketchPainter: Sendable {
    let photo: UIImage
    private let blurred: UIImage

    nonisolated init(_ photo: UIImage) {
        self.photo = photo
        let size = photo.size
        let factor = blurSide / max(size.width, size.height)
        let small = CGSize(width: max(1, (size.width * factor).rounded()), height: max(1, (size.height * factor).rounded()))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        blurred = UIGraphicsImageRenderer(size: small, format: format).image { _ in
            photo.draw(in: CGRect(origin: .zero, size: small))
        }
    }

    var bounds: CGRect { CGRect(origin: .zero, size: photo.size) }

    nonisolated func draw(_ cg: CGContext, _ strokes: [Stroke]) {
        UIGraphicsPushContext(cg)
        photo.draw(in: bounds)
        for stroke in strokes { draw(cg, stroke) }
        UIGraphicsPopContext()
    }

    nonisolated func draw(_ cg: CGContext, _ stroke: Stroke) {
        let path = pathOf(stroke)
        cg.saveGState()
        defer { cg.restoreGState() }
        cg.setLineCap(.round)
        cg.setLineJoin(.round)
        cg.setLineWidth(stroke.width)
        if stroke.ink == .blur {
            blur(cg, under: path.copy(strokingWithWidth: stroke.width, lineCap: .round, lineJoin: .round, miterLimit: 10))
            if stroke.closed { blur(cg, under: path) }
            return
        }
        let color = stroke.ink == .redact ? UIColor.black : UIColor(rgb: stroke.color)
        cg.setStrokeColor(color.cgColor)
        cg.setFillColor(color.cgColor)
        cg.addPath(path)
        cg.drawPath(using: stroke.closed ? .fillStroke : .stroke)
    }

    /// The photo with its strokes burned in, at the photo's size.
    nonisolated func burn(_ strokes: [Stroke]) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: photo.size, format: format).image { context in
            draw(context.cgContext, strokes)
        }
    }

    /// The small copy stretched back over the photo, only inside `area`.
    private nonisolated func blur(_ cg: CGContext, under area: CGPath) {
        cg.saveGState()
        cg.addPath(area)
        cg.clip()
        cg.interpolationQuality = .high
        blurred.draw(in: bounds)
        cg.restoreGState()
    }
}

/// The points smoothed through their midpoints; a tap still leaves a dot.
private nonisolated func pathOf(_ stroke: Stroke) -> CGPath {
    let points = stroke.points
    let path = CGMutablePath()
    guard let first = points.first else { return path }
    path.move(to: first)
    guard points.count > 1 else {
        path.addLine(to: CGPoint(x: first.x + 0.01, y: first.y))
        return path
    }
    for i in 1..<(points.count - 1) {
        let here = points[i], next = points[i + 1]
        path.addQuadCurve(to: CGPoint(x: (here.x + next.x) / 2, y: (here.y + next.y) / 2), control: here)
    }
    path.addLine(to: points[points.count - 1])
    if stroke.closed { path.closeSubpath() }
    return path
}

extension UIColor {
    nonisolated convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255, alpha: 1)
    }
}
