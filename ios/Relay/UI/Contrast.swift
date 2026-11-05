import Foundation

/// The desktop's contrast (reshape in src/lib/themes.ts), the phone's own on top of whatever
/// palette it wears: surfaces and borders stand out half to twice as much, and secondary text
/// moves toward the text as it goes up. Android's withContrast does the same.
nonisolated struct Contrast {
    static let key = "relay.contrast"
    /// The middle of 0–100: the palette as it comes.
    static let standard = 50

    private let strength: Double
    private let from: RGB
    private let span: (r: Double, g: Double, b: Double)
    private let length: Double

    init(_ contrast: Int, from background: RGB, to text: RGB) {
        strength = pow(2, Double(contrast - Self.standard) / Double(Self.standard))
        from = background
        span = (text.r - background.r, text.g - background.g, text.b - background.b)
        let squared = span.r * span.r + span.g * span.g + span.b * span.b
        length = squared > 0 ? squared : 1
    }

    /// Cards, fills, borders: their step from the background, times the strength.
    func surface(_ color: RGB) -> RGB { place(color) { $0 * strength } }

    /// Muted and faint text drift less, so they stay readable at low contrast.
    func secondary(_ color: RGB) -> RGB { place(color) { 1 - (1 - $0) / strength.squareRoot() } }

    private func place(_ color: RGB, _ scale: (Double) -> Double) -> RGB {
        guard strength != 1 else { return color }
        var out = color
        // A see-through hairline is already a step from whatever it lies on: scale how much shows.
        if color.a < 1 {
            out.a = min(1, max(0, scale(color.a)))
            return out
        }
        let at = ((color.r - from.r) * span.r + (color.g - from.g) * span.g + (color.b - from.b) * span.b) / length
        let move = min(1, max(-1, scale(at))) - at
        out.r = min(1, max(0, color.r + span.r * move))
        out.g = min(1, max(0, color.g + span.g * move))
        out.b = min(1, max(0, color.b + span.b * move))
        return out
    }
}
