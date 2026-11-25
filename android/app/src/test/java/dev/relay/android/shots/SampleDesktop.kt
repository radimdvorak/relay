package dev.relay.android.shots

import dev.relay.android.features.settings.toDesktop
import dev.relay.android.remote.model.PhoneAppearance
import dev.relay.android.remote.model.PhonePalette

/** The desktop's own Relay theme (src/lib/builtin-themes.ts), as it sends it to a phone. */
val RelayDesktop = PhoneAppearance(
    mode = "system",
    light = PhonePalette(
        "light", sidebar = "#ececee", surface = "#ffffff", toolbar = "#f9f9fa", inbox = "#f7f7f8", text = "#303237", muted = "#676972",
        border = "#e2e3e6", hover = "#e5e5e9", selected = "#dedee8", accent = "#6565a9", accentSoft = "#e8e8f2", onAccent = "#ffffff",
        send = "#aaa8e5", onSend = "#1b1b22", diffAddition = "#d6efdd", diffDeletion = "#f9dcdf",
    ),
    dark = PhonePalette(
        "dark", sidebar = "#232326", surface = "#1e1e21", toolbar = "#242427", inbox = "#28282c", text = "#e1e1e5", muted = "#94949f",
        border = "#37373d", hover = "#34343c", selected = "#41414f", accent = "#aaa8e5", accentSoft = "#33333f", onAccent = "#1b1b22",
        send = "#aaa8e5", onSend = "#1b1b22", diffAddition = "#213f2d", diffDeletion = "#45272c",
    ),
)

val SampleDesktop = RelayDesktop.toDesktop()
