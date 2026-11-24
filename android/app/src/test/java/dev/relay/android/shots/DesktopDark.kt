package dev.relay.android.shots

import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.colorsFromDesktop

/** The desktop app's own dark theme as a phone gets it. */
internal val desktopDark: RelayColors = colorsFromDesktop(
    mapOf(
        "kind" to "dark", "sidebar" to "#232326", "surface" to "#1e1e21", "toolbar" to "#242427", "inbox" to "#28282c",
        "text" to "#e1e1e5", "muted" to "#94949f", "border" to "#37373d", "hover" to "#34343c", "selected" to "#41414f",
        "accent" to "#aaa8e5", "accentSoft" to "#32313f", "onAccent" to "#1b1b22", "send" to "#aaa8e5", "onSend" to "#1b1b22",
        "diffAddition" to "#213f2d", "diffDeletion" to "#45272c",
    ),
)
