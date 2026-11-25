package dev.relay.android.features.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.features.composer.logic.ProviderCommand
import dev.relay.android.features.composer.logic.agentInfo
import dev.relay.android.features.composer.logic.agentName
import dev.relay.android.features.composer.logic.commandTrigger
import dev.relay.android.features.composer.logic.relayCommands
import dev.relay.android.remote.client.RemoteClient
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.floatShadow
import dev.relay.android.ui.theme.RelayTheme

// The desktop composer's slash menu (mobile/src/ui/CommandMenu.tsx): Relay's
// own actions, then the agent's commands or skills.

/** "relay" | "command" | "skill" */
data class MenuItem(
    val kind: String,
    val name: String,
    val label: String,
    val description: String,
    val args: String? = null,
    val source: String? = null,
)

// Relay's actions that have no phone counterpart yet.
private val unavailable = setOf("openpr")
private val sources = mapOf(
    "app" to "App", "repo" to "Repo", "project" to "Project", "personal" to "Personal",
    "system" to "System", "claude" to "Claude", "other" to "Provider",
)

/** What the menu offers for the text being typed, or null when it's closed. */
fun commandItems(text: String, provider: String, commands: List<ProviderCommand>): List<MenuItem>? {
    val trigger = commandTrigger(text) ?: return null
    // Relay's actions start a message; a slash mid-sentence is just text.
    if (trigger.inline) return null
    val query = trigger.query.lowercase()
    val alone = agentInfo(provider)?.commandsAlone == true
    val slash = trigger.prefix == '/'
    val items = buildList {
        if (slash) relayCommands
            .filter { it.name !in unavailable && (it.name != "btw" || agentInfo(provider)?.side == true) }
            .forEach { add(MenuItem("relay", it.name, "/" + it.name + (it.args?.let { a -> " $a" } ?: ""), it.description, it.args)) }
        if (slash || !alone) commands.forEach { c ->
            add(
                if (alone) MenuItem("command", c.name, "/" + c.name + (c.argumentHint?.let { " $it" } ?: ""), c.description, source = agentName(provider))
                else {
                    val bare = c.name.removePrefix("skill:")
                    MenuItem(
                        "skill", bare, (if (slash) "/skill:" else "$") + (c.displayName?.takeIf { it.isNotEmpty() } ?: bare),
                        c.description, source = "${sources[c.source ?: "other"] ?: "Provider"} skill",
                    )
                },
            )
        }
    }
    return items.filter { "${it.name} ${it.label}".lowercase().contains(query) }.take(60)
}

/** An agent's commands for a project, by agent; "failed" ones ask again on the next connection. */
class ProviderCommands {
    internal val lists = mutableStateMapOf<String, List<ProviderCommand>?>()
}

data class CommandsState(val commands: List<ProviderCommand>, val loading: Boolean, val failed: Boolean)

/** The agent's own commands, fetched once a menu first opens for it. */
@Composable
fun rememberProviderCommands(client: RemoteClient?, online: Boolean, connection: Int, projectId: String, provider: String, wanted: Boolean): CommandsState {
    val held = remember(client, projectId) { ProviderCommands() }
    // A reconnect retries the lists that failed; the ones it got still hold.
    LaunchedEffect(held, connection) { held.lists.entries.removeAll { it.value == null } }
    val known = held.lists.containsKey(provider)
    val list = held.lists[provider]
    LaunchedEffect(held, wanted, provider, known, online) {
        // A new Scratchpad thread has no folder until the computer makes one.
        if (!wanted || projectId.isEmpty() || known || !online || client == null) return@LaunchedEffect
        held.lists[provider] = runCatching { client.projectCommands(projectId, provider) }.getOrNull()
    }
    return CommandsState(
        commands = list.orEmpty(),
        failed = known && list == null,
        loading = wanted && projectId.isNotEmpty() && online && !known,
    )
}

/** The menu over the composer while a command or skill is being typed. */
@Composable
fun CommandMenu(items: List<MenuItem>, loading: Boolean, failed: Boolean, onPick: (MenuItem) -> Unit, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    val shape = RelayTheme.shapes.composer
    @Composable fun Note(text: String) =
        Text(text, Modifier.padding(horizontal = RowTextInset, vertical = 10.dp), style = RelayTheme.type.meta, color = c.inkSoft)
    Column(
        modifier.fillMaxWidth().heightIn(max = 296.dp)
            .floatShadow(shape)
            .clip(shape).background(c.raised),
    ) {
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(6.dp)) {
            items(items, key = { "${it.kind}:${it.name}" }) { CommandRow(it) { onPick(it) } }
            if (items.isEmpty()) item { Note("No matching commands.") }
        }
        if (loading) Note("Loading the agent's commands…")
        if (failed) Note("The agent's commands are unavailable. Relay's actions still work.")
    }
}

@Composable
private fun CommandRow(item: MenuItem, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(RelayTheme.shapes.card)
            .background(if (pressed) c.selected else Color.Transparent)
            .pressable(onClick, scale = false, interaction = interaction)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.label, style = RelayTheme.type.body.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (item.description.isNotEmpty()) Text(
                item.description, style = RelayTheme.type.meta.copy(fontSize = 13.sp), color = c.inkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(if (item.kind == "relay") "Relay" else item.source.orEmpty(), style = RelayTheme.type.small, color = c.inkFaint)
    }
}
