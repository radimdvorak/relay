package dev.relay.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The roles Relay's screens paint with (docs/expressive/design-brief.md, "Colour"). The default
 * palettes are [RelayPaperLight] and [RelayPaperDark]; in Computer mode [colorsFromDesktop] fills
 * the same roles from the desktop's palette.
 */
@Immutable
data class RelayColors(
    val isDark: Boolean,
    /** The header band, the drawer, what a content sheet sits on. */
    val chrome: Color,
    /** The content sheet: thread, lists, screens. */
    val surface: Color,
    /** Floats over the surface: composer pill, bottom sheets, toasts. */
    val raised: Color,
    /** Circle buttons, the search field, the active tab pill, chips. */
    val tool: Color,
    /** The current row in the drawer and lists. */
    val selected: Color,
    val ink: Color,
    /** Secondary text: subtitles, descriptions, inactive labels. */
    val inkSoft: Color,
    /** Tertiary text: times, placeholders, section labels. */
    val inkFaint: Color,
    /** The dark circle button, the effort slider, tooltips. */
    val strong: Color,
    val onStrong: Color,
    /** The user's message. */
    val bubble: Color,
    val onBubble: Color,
    /** The send button once there's something to send. */
    val send: Color,
    val onSend: Color,
    /** Links, focus, the Relay mark: small brand touches only. */
    val accent: Color,
    val onAccent: Color,
    /** The rare hairline: inputs, the toast's rim. */
    val line: Color,
    /** Behind code blocks and inline code. */
    val code: Color,
    val success: Color,
    val danger: Color,
    val onDanger: Color,
    val warning: Color,
    val info: Color,
    /** The desktop's colour for threads waiting on you. */
    val waiting: Color,
    /** Diff text. */
    val added: Color,
    val removed: Color,
    /** Diff line backgrounds. */
    val addedBg: Color,
    val removedBg: Color,
) {
    /** A quiet container on the surface: settings groups, toasts. Follows tool, so contrast moves it too. */
    val container: Color get() = lerp(surface, tool, 0.6f)
}

private val waitingColor = Color(0xFFD99A2B)

/** "Relay paper", light: the Claude app's greys around Relay's coral. */
val RelayPaperLight = RelayColors(
    isDark = false,
    chrome = Color(0xFFECECEC),
    surface = Color(0xFFFDFDFD),
    raised = Color(0xFFFCFCFC),
    tool = Color(0xFFECECEC),
    selected = Color(0xFFE4E4E4),
    ink = Color(0xFF1A1A1A),
    inkSoft = Color(0xFF6B6B6B),
    inkFaint = Color(0xFF9A9A9A),
    strong = Color(0xFF171717),
    onStrong = Color(0xFFFAFAFA),
    bubble = Color(0xFFE4EFF6),
    onBubble = Color(0xFF1A1A1A),
    send = Color(0xFF171717),
    onSend = Color(0xFFFAFAFA),
    accent = Color(0xFFC86858),
    onAccent = Color(0xFFFFFFFF),
    line = Color(0xFFE2E2E2),
    code = Color(0xFFF4F4F4),
    success = Color(0xFF008235),
    danger = Color(0xFFDC2626),
    onDanger = Color(0xFFFAFAFA),
    warning = Color(0xFFBB4D00),
    info = Color(0xFF1F64EB),
    waiting = waitingColor,
    added = Color(0xFF53A664),
    removed = Color(0xFFC8285A),
    addedBg = Color(0xFFDCF5E3),
    removedBg = Color(0xFFFBE0E6),
)

/** "Relay paper", dark: the same steps from near-black, strong turned light. */
val RelayPaperDark = RelayColors(
    isDark = true,
    chrome = Color(0xFF161616),
    surface = Color(0xFF0D0D0D),
    raised = Color(0xFF1C1C1C),
    tool = Color(0xFF262626),
    selected = Color(0xFF2C2C2C),
    ink = Color(0xFFF2F2F2),
    inkSoft = Color(0xFFA3A3A3),
    inkFaint = Color(0xFF6E6E6E),
    strong = Color(0xFFFAFAFA),
    onStrong = Color(0xFF171717),
    bubble = Color(0xFF1A2A35),
    onBubble = Color(0xFFF2F2F2),
    send = Color(0xFFFAFAFA),
    onSend = Color(0xFF171717),
    accent = Color(0xFFE08A78),
    onAccent = Color(0xFF1A0F0C),
    line = Color(0x1AFFFFFF),
    code = Color(0xFF161616),
    success = Color(0xFF05DF72),
    danger = Color(0xFFFF6467),
    onDanger = Color(0xFF171717),
    warning = Color(0xFFFFB900),
    info = Color(0xFF50A2FF),
    waiting = waitingColor,
    added = Color(0xFF6CC47D),
    removed = Color(0xFFF0668D),
    addedBg = Color(0xFF12321F),
    removedBg = Color(0xFF3A1722),
)
