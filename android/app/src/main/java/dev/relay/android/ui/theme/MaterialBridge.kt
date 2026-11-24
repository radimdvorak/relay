package dev.relay.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight

/**
 * Relay's roles on Material's colour slots, so a stock M3 component looks like ours without
 * per-call colours. Containers step up from the content surface the way the roles do:
 * lowest = raised (sheets, the composer), container = chrome, high = tool, highest = selected.
 */
internal fun materialScheme(c: RelayColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.strong,
        onPrimary = c.onStrong,
        primaryContainer = c.tool,
        onPrimaryContainer = c.ink,
        inversePrimary = c.accent,
        secondary = c.inkSoft,
        onSecondary = c.surface,
        secondaryContainer = c.tool,
        onSecondaryContainer = c.ink,
        tertiary = c.accent,
        onTertiary = c.onAccent,
        tertiaryContainer = c.bubble,
        onTertiaryContainer = c.onBubble,
        background = c.surface,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        surfaceVariant = c.tool,
        onSurfaceVariant = c.inkSoft,
        // No tonal tint: the roles already say how far each container sits from the surface.
        surfaceTint = c.surface,
        inverseSurface = c.strong,
        inverseOnSurface = c.onStrong,
        surfaceBright = c.raised,
        surfaceDim = c.chrome,
        surfaceContainerLowest = c.raised,
        surfaceContainerLow = lerp(c.surface, c.chrome, 0.5f),
        surfaceContainer = c.chrome,
        surfaceContainerHigh = c.tool,
        surfaceContainerHighest = c.selected,
        outline = c.inkFaint,
        outlineVariant = c.line,
        error = c.danger,
        onError = c.onDanger,
        errorContainer = c.removedBg,
        onErrorContainer = c.danger,
        scrim = Color.Black,
    )
}

/** Material's slots in Relay's scale, so stock text (menus, dialogs, bars) reads the same. */
internal val materialTypography: Typography = DefaultType.let { t ->
    Typography(fontFamily = Inter).copy(
        displaySmall = t.greeting,
        headlineSmall = t.title,
        titleLarge = t.heading,
        titleMedium = t.sheetTitle,
        titleSmall = t.meta.copy(fontWeight = FontWeight.Medium),
        bodyLarge = t.row,
        bodyMedium = t.body,
        bodySmall = t.meta,
        labelLarge = t.meta.copy(fontWeight = FontWeight.Medium),
        labelMedium = t.label,
        labelSmall = t.small.copy(fontWeight = FontWeight.Medium),
    )
}
