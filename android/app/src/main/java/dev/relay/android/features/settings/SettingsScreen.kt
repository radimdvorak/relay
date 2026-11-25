package dev.relay.android.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.features.applock.AppLock
import dev.relay.android.features.diagnostics.KeepScrollIncidentRow
import dev.relay.android.features.diagnostics.SaveScrollLogsRow
import dev.relay.android.features.dictation.DictationEngine
import dev.relay.android.features.dictation.DictationEngines
import dev.relay.android.features.notifications.NotificationsSection
import dev.relay.android.features.notifications.ThreadNotifications
import dev.relay.android.features.push.Push
import dev.relay.android.features.updates.AppOfferRow
import dev.relay.android.features.updates.InstalledAppRow
import dev.relay.android.features.updates.LookForAppRow
import dev.relay.android.features.updates.Updates
import dev.relay.android.features.updates.appOffer
import dev.relay.android.features.updates.computerName
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.ui.Group
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Page
import dev.relay.android.ui.navBarRoom
import dev.relay.android.ui.theme.ThemeSettings
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Components
import io.github.lyxnx.compose.ui.tablericons.outline.License
import io.github.lyxnx.compose.ui.tablericons.outline.Power
import kotlinx.coroutines.CoroutineScope

/**
 * mobile/src/app/settings.tsx: theme and contrast, notifications, this app's version and
 * updates, the paired computers, and how to reach the one in use.
 */
@Composable
fun SettingsScreen(
    remote: Remote,
    updates: Updates,
    notifications: ThreadNotifications,
    push: Push,
    lock: AppLock,
    theme: ThemeSettings,
    /** Outlives this screen, for a switch or unpair that navigates away. */
    scope: CoroutineScope,
    stopRelay: () -> Unit,
) {
    val nav = LocalNavigator.current
    Page("Settings", onBack = nav.backAction) {
        SettingsList(remote, updates, notifications, push, lock, theme, scope, stopRelay, bottom = 24.dp + navBarRoom())
    }
}

/** Rows wider than this are hard to follow across; a wide pane centres them. */
private val MaxWidth = 640.dp

/** Settings' groups, leaving [bottom] free under them. */
@Composable
fun SettingsList(
    remote: Remote,
    updates: Updates,
    notifications: ThreadNotifications,
    push: Push,
    lock: AppLock,
    theme: ThemeSettings,
    scope: CoroutineScope,
    stopRelay: () -> Unit,
    bottom: Dp = 24.dp,
) {
    val name = computerName(remote)
    val overview by remote.overview.collectAsState()
    val appearance = overview?.appearance
    val desktop = remember(appearance) { appearance?.toDesktop() }
    BoxWithConstraints {
        val side = maxOf(16.dp, (maxWidth - MaxWidth) / 2)
        // Build only the sections in view. A scrolling Column also built and measured every
        // computer, route and developer row on each tab visit, before drawing the first frame.
        LazyColumn(
            contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "theme") { ThemeSection(theme, name, desktop) }
            item(key = "notifications") { NotificationsSection(notifications, push, name) }
            item(key = "lock") { LockSection(lock) }
            item(key = "dictation") { DictationSection(name) }
            item(key = "app") { AppSection(updates, name, stopRelay) }
            item(key = "computers") { ComputersSection(remote, updates, name, scope) }
            item(key = "routes") { RouteSection(remote, name) }
            item(key = "developer") { DeveloperSection() }
        }
    }
}

@Composable
private fun AppSection(updates: Updates, computer: String, stopRelay: () -> Unit) {
    val nav = LocalNavigator.current
    val offer = appOffer(updates)
    Group(label = "App") {
        item { InstalledAppRow(updates) }
        if (offer != null) item { AppOfferRow(updates, offer) }
        item { LookForAppRow(updates) }
        item {
            ListRow(
                "Stop Relay",
                subtitle = "Disconnects from $computer and stays off until you open Relay again. Pairings and settings are kept.",
                leadingIcon = TablerIcons.Outline.Power,
                subtitleLines = 3,
                onClick = stopRelay,
            )
        }
        item {
            ListRow(
                "Open-source licences",
                leadingIcon = TablerIcons.Outline.License,
                chevron = true,
                onClick = { nav.push(Route.Licences) },
            )
        }
    }
}

@Composable
private fun DeveloperSection() {
    val nav = LocalNavigator.current
    Group(label = "Developer") {
        item { SaveScrollLogsRow() }
        item { KeepScrollIncidentRow() }
        item {
            ListRow(
                "Component gallery",
                subtitle = "Every building block, light and dark",
                leadingIcon = TablerIcons.Outline.Components,
                chevron = true,
                onClick = { nav.push(Route.Gallery) },
            )
        }
    }
}

@Composable
private fun DictationSection(name: String) {
    val engines = DictationEngines.of(LocalContext.current)
    if (!engines.available) return
    val picked by engines.picked.collectAsState()
    Group(label = "Dictation") {
        item {
            ChoiceRow("On the computer", "$name turns speech into text; the audio goes over the link.", picked == DictationEngine.Computer) {
                engines.pick(DictationEngine.Computer)
            }
        }
        item {
            ChoiceRow("On this phone", "The phone's own speech engine. Needs no computer and sends no audio.", picked == DictationEngine.Phone) {
                engines.pick(DictationEngine.Phone)
            }
        }
    }
}
