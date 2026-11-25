package dev.relay.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * computer: the desktop's palette and mode (Relay paper until it's known).
 * phone: Relay paper, following the phone's light or dark.
 * light / dark: Relay paper in that mode.
 */
enum class ThemeMode { Computer, Phone, Light, Dark }

/** What the phone itself picks about its colours, for Settings to change. */
interface ThemeSettings {
    val themeMode: ThemeMode
    fun setTheme(mode: ThemeMode)
    /** 0–100 on top of the theme; [DEFAULT_CONTRAST] leaves it as it comes. */
    var contrast: Int
}

/** The desktop's PhoneAppearance: its mode ("system", "light", "dark") and a palette per kind. */
data class DesktopAppearance(
    val mode: String,
    val light: Map<String, String>,
    val dark: Map<String, String>,
)

val LocalRelayColors = staticCompositionLocalOf { RelayPaperDark }
val LocalRelayType = staticCompositionLocalOf { DefaultType }
val LocalHairline = staticCompositionLocalOf { Dp.Hairline }

object RelayTheme {
    val colors: RelayColors
        @Composable @ReadOnlyComposable get() = LocalRelayColors.current
    val type: RelayType
        @Composable @ReadOnlyComposable get() = LocalRelayType.current
    /** One physical pixel. */
    val hairline: Dp
        @Composable @ReadOnlyComposable get() = LocalHairline.current
    val shapes get() = RelayShapes
    val motion get() = RelayMotion
    val space get() = Space
}

/** Which colours a mode wears, given the desktop's appearance if it has sent one. */
fun resolveColors(mode: ThemeMode, desktop: DesktopAppearance?, systemDark: Boolean): RelayColors {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Phone -> systemDark
        ThemeMode.Computer -> when (desktop?.mode) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
    }
    if (mode == ThemeMode.Computer && desktop != null)
        return colorsFromDesktop(if (dark) desktop.dark else desktop.light)
    return if (dark) RelayPaperDark else RelayPaperLight
}

/** One palette already picked for its kind (the map carries `kind`); computer mode wears it. */
@Composable
fun RelayTheme(
    mode: ThemeMode = ThemeMode.Dark,
    desktopPalette: Map<String, String>?,
    content: @Composable () -> Unit,
) {
    val desktop = desktopPalette?.let {
        DesktopAppearance(mode = it["kind"] ?: "dark", light = it, dark = it)
    }
    RelayTheme(mode, desktop, content)
}

@Composable
fun RelayTheme(
    mode: ThemeMode = ThemeMode.Dark,
    desktop: DesktopAppearance? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = remember(mode, desktop, systemDark) { resolveColors(mode, desktop, systemDark) }
    RelayTheme(colors, content)
}

/**
 * Wears exactly these colours: the app, the gallery's side-by-side themes, shots. Material
 * underneath (stock components, ripples, selection handles) gets the same roles through
 * [materialScheme], with the standard motion scheme so nothing recurring overshoots.
 */
@Composable
fun RelayTheme(colors: RelayColors, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val material = remember(colors) { materialScheme(colors) }
    CompositionLocalProvider(
        LocalRelayColors provides colors,
        LocalRelayType provides DefaultType,
        LocalHairline provides hairline(density),
    ) {
        MaterialExpressiveTheme(
            colorScheme = material,
            motionScheme = RelayMotion.standard,
            shapes = Shapes(),
            typography = materialTypography,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}
