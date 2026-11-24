package dev.relay.android.features.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleSize
import dev.relay.android.ui.CircleSurface
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.Icon
import dev.relay.android.ui.PairShapes
import dev.relay.android.ui.ProviderIcon
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Bolt
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.ListCheck

/** What the composer sends with, as its chips name it; [effort] is null where the model takes none. */
internal data class ModelChoices(
    val provider: String,
    val model: String,
    val effort: String?,
    val fast: Boolean = false,
    val plan: Boolean = false,
)

/** The model and its effort, joined in one pill, and a mark while plan mode is on. A long model name gives way first. */
@Composable
internal fun ModelChips(choices: ModelChoices, onModel: () -> Unit, onEffort: () -> Unit, onPlan: () -> Unit) {
    val c = RelayTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.CenterVertically) {
        val described = "Model: ${choices.model}" + if (choices.fast) ", Fast" else ""
        val shape = if (choices.effort != null) PairShapes.start else RelayTheme.shapes.circle
        CircleSurface(onModel, described, Modifier.weight(1f, fill = false).height(36.dp), shape, CircleTone.Tool, true, Haptic.Selection) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProviderIcon(choices.provider, c.inkSoft, size = 14.dp)
                Text(
                    choices.model, Modifier.weight(1f, fill = false),
                    style = chipText(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (choices.fast) Icon(TablerIcons.Outline.Bolt, null, tint = c.inkSoft, size = 14.dp)
                if (choices.effort == null) Icon(TablerIcons.Outline.ChevronDown, null, tint = c.inkSoft, size = 16.dp)
            }
        }
        if (choices.effort != null) CircleSurface(
            onEffort, "Effort: ${choices.effort}", Modifier.height(36.dp), PairShapes.end, CircleTone.Tool, true, Haptic.Selection,
        ) {
            Row(Modifier.padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(choices.effort, style = chipText(), color = c.inkSoft, maxLines = 1)
                Icon(TablerIcons.Outline.ChevronDown, null, tint = c.inkSoft, size = 16.dp)
            }
        }
        // Opens the sheet with its switch rather than switching it: a stray tap mustn't let the agent start editing.
        if (choices.plan) CircleButton(
            TablerIcons.Outline.ListCheck, "Plan mode is on", onPlan,
            Modifier.padding(start = 6.5.dp), size = CircleSize.Composer,
        )
    }
}

@Composable
private fun chipText() = RelayTheme.type.meta.copy(fontSize = 13.sp)
