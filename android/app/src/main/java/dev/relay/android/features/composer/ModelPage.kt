package dev.relay.android.features.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relay.android.features.composer.logic.ModelRow
import dev.relay.android.features.composer.logic.agentName
import dev.relay.android.ui.Group
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.ProviderIcon
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Switch
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight

/** A switch under the models: Fast, the 200k window. */
internal data class ModelToggle(val label: String, val hint: String, val on: Boolean, val onChange: (Boolean) -> Unit)

/** The model sheet: agents across the top, the agent's models on a wheel, the centred one's description, its switches. */
@Composable
internal fun ModelPage(
    agents: List<String>,
    provider: String,
    onAgent: (String) -> Unit,
    /** Null while the list is on its way. */
    rows: List<ModelRow>?,
    chosen: String,
    effort: String,
    onModel: (String) -> Unit,
    onEffort: () -> Unit,
    modifier: Modifier = Modifier,
    toggles: List<ModelToggle> = emptyList(),
    error: String? = null,
) {
    val c = RelayTheme.colors
    Column(
        modifier.fillMaxWidth().heightIn(min = SheetPageHeight).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (agents.size > 1) AgentRow(agents, provider, onAgent)
        if (rows == null) Box(Modifier.fillMaxWidth().height(238.dp), contentAlignment = Alignment.Center) {
            Spinner(color = c.inkSoft)
        } else key(provider) {
            var centred by remember { mutableStateOf(chosen) }
            ModelWheel(
                rows.map { WheelRow(it.id, it.name) }, chosen, onModel,
                onCentre = { centred = it },
                trailing = { id -> rows.find { it.id == id }?.let { EffortLink(it, effort, enabled = id == chosen, onEffort) } },
            )
            Description(rows.find { it.id == centred }?.description.orEmpty())
        }
        if (error != null) Text(
            "Couldn't list ${agentName(provider)}'s models: $error",
            Modifier.padding(horizontal = 30.dp), style = RelayTheme.type.meta, color = c.danger,
        )
        if (toggles.isNotEmpty()) Group(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            toggles.forEach { toggle ->
                item {
                    ListRow(
                        toggle.label, subtitle = toggle.hint, subtitleLines = 3,
                        trailing = { Switch(toggle.on, toggle.onChange) },
                        onClick = { toggle.onChange(!toggle.on) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentRow(agents: List<String>, provider: String, onAgent: (String) -> Unit) {
    val c = RelayTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        agents.forEach { agent ->
            val on = agent == provider
            val view = remember { BringIntoViewRequester() }
            LaunchedEffect(on) { if (on) view.bringIntoView() }
            Row(
                Modifier
                    .bringIntoViewRequester(view)
                    .pressable(onClick = { if (!on) onAgent(agent) }, role = Role.Tab)
                    .clip(RelayTheme.shapes.circle)
                    .background(if (on) c.tool else Color.Transparent)
                    .semantics { selected = on }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProviderIcon(agent, if (on) c.ink else c.inkSoft, size = 15.dp)
                Text(
                    agentName(agent),
                    style = RelayTheme.type.meta.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                    color = if (on) c.ink else c.inkSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The picked row's end: the effort's glyph and name, leading to the effort page. Not tappable while its row only rolls by. */
@Composable
private fun EffortLink(row: ModelRow, effort: String, enabled: Boolean, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val levels = row.levels ?: return
    val label = levels.label(effort)
    // A long name and large text need the room more than the glyph does.
    val roomy = LocalDensity.current.fontScale <= 1.15f && row.name.length <= 18
    Row(
        Modifier
            .pressable(onClick, enabled = enabled, scale = false)
            .semantics { contentDescription = "Effort: $label" }
            .heightIn(min = 48.dp)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (roomy && levels.choosable) {
            EffortGlyph(levels.values.size, levels.indexOf(effort))
            Spacer(Modifier.padding(start = 20.dp))
        }
        Text(label, style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1)
        Icon(TablerIcons.Outline.ChevronRight, null, Modifier.padding(start = 2.dp), tint = c.inkSoft, size = 16.dp)
    }
}

@Composable
private fun Description(text: String) {
    AnimatedContent(
        text,
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        transitionSpec = { fadeIn(tween(120, 60)) togetherWith fadeOut(tween(60)) },
        label = "model-description",
    ) {
        Text(it, Modifier.fillMaxWidth().padding(horizontal = 38.dp), style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft, maxLines = 3)
    }
}
