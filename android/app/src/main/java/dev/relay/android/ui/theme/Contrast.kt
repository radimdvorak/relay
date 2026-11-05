package dev.relay.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.sqrt

/** 0–100, as on the desktop; the middle keeps the colours as they come. */
const val DEFAULT_CONTRAST = 50

/**
 * The desktop's contrast (reshape in src/lib/themes.ts) on top of whatever
 * palette the phone wears: surfaces and borders stand out half to twice as
 * much, and secondary text moves toward the text as contrast goes up.
 */
fun RelayColors.withContrast(contrast: Int): RelayColors {
    if (contrast == DEFAULT_CONTRAST) return this
    val strength = 2.0.pow((contrast - DEFAULT_CONTRAST) / DEFAULT_CONTRAST.toDouble()).toFloat()
    val line = BackgroundToText(background, foreground)
    val surface = { c: Color -> line.place(c) { at -> at * strength } }
    // Drifts less than surfaces, so muted text stays readable at low contrast.
    val secondary = { c: Color -> line.place(c) { at -> 1 - (1 - at) / sqrt(strength) } }
    return copy(
        card = surface(card),
        popover = surface(popover),
        muted = surface(muted),
        border = surface(border),
        input = surface(input),
        ring = surface(ring),
        selected = surface(selected),
        sidebar = surface(sidebar),
        code = surface(code),
        accentSoft = surface(accentSoft),
        mutedForeground = secondary(mutedForeground),
        faint = secondary(faint),
    )
}

/** Where a colour sits from the background (0) to the text (1), and moving it along that line. */
private class BackgroundToText(private val from: Color, to: Color) {
    private val span = floatArrayOf(to.red - from.red, to.green - from.green, to.blue - from.blue)
    private val length = span.sumOf { (it * it).toDouble() }.toFloat().takeIf { it > 0f } ?: 1f

    fun place(color: Color, scale: (Float) -> Float): Color {
        // A see-through hairline is already a step from whatever it lies on: scale how much shows.
        if (color.alpha < 1f) return color.copy(alpha = scale(color.alpha).coerceIn(0f, 1f))
        val channels = floatArrayOf(color.red, color.green, color.blue)
        val start = floatArrayOf(from.red, from.green, from.blue)
        val at = channels.indices.sumOf { ((channels[it] - start[it]) * span[it]).toDouble() }.toFloat() / length
        val move = scale(at).coerceIn(-1f, 1f) - at
        val moved = channels.indices.map { (channels[it] + span[it] * move).coerceIn(0f, 1f) }
        return Color(moved[0], moved[1], moved[2], color.alpha)
    }
}
