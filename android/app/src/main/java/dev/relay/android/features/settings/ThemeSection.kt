package dev.relay.android.features.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Group
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.ring
import dev.relay.android.ui.theme.DesktopAppearance
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import dev.relay.android.ui.theme.ThemeMode
import dev.relay.android.ui.theme.ThemeSettings
import dev.relay.android.ui.theme.resolveColors
import dev.relay.android.ui.theme.withContrast

/** One way to colour the phone: [short] goes under its preview, the rest says what it means. */
private class ThemeChoice(val mode: ThemeMode, val short: String, val label: String, val hint: String)

private fun themeChoices(computer: String, known: Boolean) = listOf(
    ThemeChoice(
        ThemeMode.Computer, "Computer", "Match $computer",
        if (known) "Its theme, light or dark as it is set there." else "Relay's theme until your computer shares its own.",
    ),
    ThemeChoice(ThemeMode.Phone, "Phone", "Follow this phone", "The same theme, light or dark with the phone."),
    ThemeChoice(ThemeMode.Light, "Light", "Light", "The theme's light colours."),
    ThemeChoice(ThemeMode.Dark, "Dark", "Dark", "The theme's dark colours."),
)

/** The theme, each choice drawn in the colours it would give, and the contrast on top. */
@Composable
internal fun ThemeSection(theme: ThemeSettings, computer: String, desktop: DesktopAppearance?) {
    val choices = themeChoices(computer, known = desktop != null)
    val chosen = choices.first { it.mode == theme.themeMode }
    Group(label = "Theme") {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    choices.forEach { choice ->
                        ThemeTile(choice, palettes(choice.mode, desktop, theme.contrast), choice == chosen, Modifier.weight(1f)) {
                            theme.setTheme(choice.mode)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(chosen.label, style = RelayTheme.type.row)
                    Text(chosen.hint, style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
                }
            }
        }
        item { ContrastRow(theme) }
    }
}

/** What [mode] looks like: one palette, or light and dark where it follows something that changes. */
@Composable
private fun palettes(mode: ThemeMode, desktop: DesktopAppearance?, contrast: Int): List<RelayColors> =
    remember(mode, desktop, contrast) {
        listOf(false, true).map { systemDark -> resolveColors(mode, desktop, systemDark).withContrast(contrast) }.distinct()
    }

private val RingGap = 4.dp
private val previewShape = RoundedCornerShape(14.dp)

@Composable
private fun ThemeTile(choice: ThemeChoice, palettes: List<RelayColors>, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val ring by animateFloatAsState(if (chosen) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "ring")
    Column(
        modifier
            .pressable(onClick, role = Role.RadioButton, haptic = Haptic.Selection)
            .semantics {
                selected = chosen
                contentDescription = "${choice.label}. ${choice.hint}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = ring }
                    .border(2.dp, c.strong, RoundedCornerShape(18.dp)),
            )
            ThemePreview(
                palettes,
                Modifier.padding(RingGap).fillMaxWidth().aspectRatio(0.66f).clip(previewShape).ring(c.line, previewShape),
            )
        }
        Text(
            choice.short,
            Modifier.clearAndSetSemantics {},
            style = RelayTheme.type.label.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
            color = if (chosen) c.ink else c.inkSoft,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
