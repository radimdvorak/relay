package dev.relay.android.features.pairing

import androidx.compose.ui.graphics.Color
import dev.relay.android.ui.theme.RelayPaperDark

/**
 * Relay's dark palette on black, whatever the theme: the field glows best on black, and the camera
 * opens out of it. The accent stays neutral beside the field's violet.
 */
internal val PairColors = RelayPaperDark.copy(
    chrome = Color.Black,
    surface = Color.Black,
    accent = Color(0xFFE5E5E5),
)
