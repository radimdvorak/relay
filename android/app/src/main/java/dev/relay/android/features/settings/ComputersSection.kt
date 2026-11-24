package dev.relay.android.features.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.features.updates.ComputerUpdateAction
import dev.relay.android.features.updates.Updates
import dev.relay.android.features.updates.describeUpdate
import dev.relay.android.features.updates.rememberComputerUpdateAction
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.Remote
import dev.relay.android.remote.RouteKind
import dev.relay.android.ui.ConfirmDialog
import dev.relay.android.ui.Group
import dev.relay.android.ui.GroupScope
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceDesktop
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh
import io.github.lyxnx.compose.ui.tablericons.outline.Unlink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The paired computers: switch, update the one in use, pair another, unpair. */
@Composable
internal fun ComputersSection(remote: Remote, updates: Updates, name: String, scope: CoroutineScope) {
    val nav = LocalNavigator.current
    val computers by remote.computers.collectAsState()
    val active by remote.active.collectAsState()
    val overview by remote.overview.collectAsState()
    val update = rememberComputerUpdateAction(updates, remote)
    var unpairing by remember { mutableStateOf(false) }

    Group(label = "Computers") {
        computers.forEach { c ->
            item {
                ComputerRow(c.name, inUse = c.id == active, overview?.version) {
                    nav.home()
                    scope.launch { remote.switchTo(c.id) }
                }
            }
        }
        updateItem(update, name)
        item {
            ListRow(
                "Pair another computer",
                subtitle = "Scan the code in Relay's Settings → Phone there.",
                leadingIcon = TablerIcons.Outline.Plus,
                onClick = { nav.push(Route.Pair()) },
            )
        }
        if (active != null) item {
            ListRow(
                "Unpair from $name",
                titleLines = 2,
                subtitle = "Forgets it here. Pairing again needs a new code from Relay there.",
                leading = { Icon(TablerIcons.Outline.Unlink, null, tint = RelayTheme.colors.danger, size = 20.dp) },
                titleColor = RelayTheme.colors.danger,
                onClick = { unpairing = true },
            )
        }
    }

    if (unpairing) ConfirmDialog(
        "Unpair from $name?",
        onConfirm = {
            unpairing = false
            val others = computers.size > 1
            scope.launch {
                remote.forget()
                if (others) nav.home() else nav.replace(Route.Pair())
            }
        },
        onDismiss = { unpairing = false },
        confirmText = "Unpair",
        destructive = true,
    )
}

@Composable
private fun ComputerRow(name: String, inUse: Boolean, version: String?, onSwitch: () -> Unit) {
    ListRow(
        name,
        titleLines = 2,
        subtitle = if (inUse) {
            "In use${version?.let { ", Relay $it" } ?: ""}. Remove this phone in Relay's Settings → Phone there to cut it off."
        } else "Tap to switch to it.",
        subtitleLines = 4,
        leadingIcon = TablerIcons.Outline.DeviceDesktop,
        selected = inUse,
        trailing = if (inUse) ({ Icon(TablerIcons.Outline.Check, null, tint = RelayTheme.colors.ink, size = 20.dp) }) else null,
        onClick = { if (!inUse) onSwitch() },
    )
}

/** The computer in use runs an older Relay: how its update goes, or a way to start one. */
private fun GroupScope.updateItem(action: ComputerUpdateAction, name: String) {
    val update = action.update
    when {
        update != null -> item {
            ListRow("Updating $name", subtitle = describeUpdate(name, update), leadingIcon = TablerIcons.Outline.Refresh, subtitleLines = 4)
        }
        action.can -> item {
            ListRow(
                "Update $name",
                subtitle = "${action.hint} It restarts into the new version; its agents keep going.",
                subtitleLines = 4,
                leadingIcon = TablerIcons.Outline.Refresh,
                onClick = action.start,
            )
        }
        action.devBuild -> item { Note("${action.hint} It's a development build, so it doesn't update itself.") }
        action.needed && !action.askable -> item {
            Note("$name runs an older Relay. Update it there once; after that this phone can do it.")
        }
    }
}

/** Local network or Tailscale first, and which one carries the link now. */
@Composable
internal fun RouteSection(remote: Remote, name: String) {
    val active by remote.active.collectAsState()
    val routes by remote.routes.collectAsState()
    val client by remote.client.collectAsState()
    // The route isn't a flow; a status change is when it moves.
    remote.status.collectAsState().value
    if (active == null) return
    val first = routes.firstOrNull() ?: RouteKind.Local
    val now = client?.route
    fun hint(kind: RouteKind, otherwise: String) =
        if (now?.kind == kind) "Connected this way now, at ${now.host}." else otherwise
    Group(label = "Reaching $name") {
        item {
            ChoiceRow(
                "Local network first",
                hint(RouteKind.Local, "Straight to it on the same Wi-Fi; Tailscale when that doesn't answer."),
                first == RouteKind.Local,
            ) { remote.setRoutes(listOf(RouteKind.Local, RouteKind.Tailscale)) }
        }
        item {
            ChoiceRow(
                "Tailscale first",
                hint(RouteKind.Tailscale, "Over Tailscale; the local network only when Tailscale doesn't answer."),
                first == RouteKind.Tailscale,
            ) { remote.setRoutes(listOf(RouteKind.Tailscale, RouteKind.Local)) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = RowTextInset, vertical = 14.dp),
        style = RelayTheme.type.meta,
        color = RelayTheme.colors.inkSoft,
    )
}
