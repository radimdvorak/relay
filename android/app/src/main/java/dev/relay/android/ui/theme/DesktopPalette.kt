package dev.relay.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

/** Parses `#rgb`, `#rrggbb` or `#rrggbbaa`; null for anything else (the desktop sends CSS-resolved hex). */
fun parseHex(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    val full = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6, 8 -> hex
        else -> return null
    }
    val n = full.toLongOrNull(16) ?: return null
    return if (full.length == 6) Color(0xFF000000 or n)
    else Color(((n and 0xFF) shl 24) or (n shr 8))
}

/** How far the chrome keeps from the surface, as a share of the way from the surface to the text. */
private const val CHROME_STEP = 0.05f

/**
 * The desktop draws a line between its sidebar and its surface, so some palettes have the two all
 * but alike. Here the content sheet's edge is only the step between them: one too small grows in
 * the direction it already goes, or toward the text when there's none.
 */
internal fun Color.standingOff(surface: Color, text: Color): Color {
    val span = text.luminanceStep() - surface.luminanceStep()
    if (span == 0f) return this
    val step = (luminanceStep() - surface.luminanceStep()) / span
    if (abs(step) >= CHROME_STEP) return this
    val direction = if (step < 0f) -1f else 1f
    val shift = direction * (CHROME_STEP - abs(step)) * span
    return Color((red + shift).coerceIn(0f, 1f), (green + shift).coerceIn(0f, 1f), (blue + shift).coerceIn(0f, 1f), alpha)
}

/** Perceived lightness, near enough for a small step: no gamma, the eye's weights. */
private fun Color.luminanceStep() = 0.3f * red + 0.59f * green + 0.11f * blue

/**
 * The desktop's PhonePalette (shared/remote-models.ts: kind, sidebar, surface, toolbar, inbox,
 * text, muted, border, hover, selected, accent, accentSoft, onAccent, send, onSend, diffAddition,
 * diffDeletion) laid onto Relay's roles, per the table in docs/expressive/design-brief.md. Keys it
 * doesn't send keep Relay paper's value for that kind.
 */
fun colorsFromDesktop(palette: Map<String, String>): RelayColors {
    val dark = palette["kind"] != "light"
    val base = if (dark) RelayPaperDark else RelayPaperLight
    fun pick(key: String) = parseHex(palette[key])

    val surface = pick("surface") ?: base.surface
    val text = pick("text") ?: base.ink
    val muted = pick("muted") ?: base.inkSoft
    // The desktop's hover and selected are often see-through; fills here sit on more than one colour.
    val tool = pick("hover")?.compositeOver(surface) ?: base.tool
    val selected = pick("selected")?.compositeOver(surface) ?: base.selected
    val sentSend = pick("send") ?: pick("accent")
    return base.copy(
        chrome = pick("sidebar")?.compositeOver(surface)?.standingOff(surface, text) ?: base.chrome,
        surface = surface,
        raised = pick("inbox")?.compositeOver(surface) ?: base.raised,
        tool = tool,
        selected = selected,
        ink = text,
        inkSoft = muted,
        inkFaint = lerp(surface, muted, 0.62f),
        strong = text,
        onStrong = surface,
        bubble = pick("accentSoft")?.compositeOver(surface) ?: base.bubble,
        onBubble = text,
        send = sentSend ?: text,
        onSend = if (sentSend != null) pick("onSend") ?: pick("onAccent") ?: base.onSend else surface,
        accent = pick("accent") ?: base.accent,
        onAccent = pick("onAccent") ?: base.onAccent,
        line = pick("border") ?: base.line,
        code = lerp(if (dark) Color.Black else text, surface, 0.92f),
        addedBg = pick("diffAddition") ?: base.addedBg,
        removedBg = pick("diffDeletion") ?: base.removedBg,
    )
}
