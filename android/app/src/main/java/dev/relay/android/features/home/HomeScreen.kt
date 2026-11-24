package dev.relay.android.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.LinkState
import dev.relay.android.remote.credentials.ComputerSeen
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.ui.Button
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.Tone
import dev.relay.android.ui.rememberInFront
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dev.relay.android.ui.SectionHeader
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.ribbon.RibbonRefreshBox

/** Home: the threads, the projects and the pull requests, one tab each, under the shell's header and over its dock. */
@Composable
fun HomeScreen(
    remote: Remote,
    home: HomeServices,
    computerSeen: ComputerSeen,
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    /** False where the side pane carries the destinations. */
    bar: Boolean = true,
    /** Update banners, from features that own them. */
    banners: @Composable ColumnScope.() -> Unit = {},
    /** Extra rows for the computers sheet, from features that own them; `close` puts the sheet away. */
    computerRows: @Composable (close: () -> Unit) -> Unit = {},
    /** The Pull requests tab's list, leaving [bottom] free under it. */
    pulls: @Composable (bottom: Dp) -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val active by remote.active.collectAsState()
    val iconIndex by home.icons.index.collectAsState()
    val overview by remote.overview.collectAsState()
    val filter by home.filter.filter.collectAsState()
    // Back from another tab returns to the threads before it leaves the app, as Android's tabs do.
    BackHandler(enabled = tab != HomeTab.Threads) { onTab(HomeTab.Threads) }
    val newThread = { navigator.push(Route.NewThread()) }
    val o = overview

    CompositionLocalProvider(LocalProjectIconPath provides { id -> projectIconPath(iconIndex, active, id) }) {
        HomeShell(
            tab,
            modifier,
            header = {
                HomeHeader(
                    tab,
                    onSearch = { navigator.push(Route.Search(pulls = tab == HomeTab.Pulls)) },
                    onNewThread = newThread,
                    showPair = bar,
                    computer = {
                        // The side pane has the name, search and a new thread already.
                        if (bar) ComputerName(
                            remote, computerSeen, RelayTheme.type.body.copy(fontWeight = FontWeight.Medium), computerRows,
                            color = RelayTheme.colors.inkSoft,
                        )
                    },
                    hasFilter = tab == HomeTab.Threads && o != null,
                    filter = { if (o != null) FilterButton(o, filter, home.filter::set) },
                )
            },
            dock = { dockModifier ->
                HomeDock(
                    tab, onTab, bar,
                    threadsNews = tab != HomeTab.Threads && o != null && o.listed.any { it.waiting == true },
                    modifier = dockModifier,
                    hasComposer = tab == HomeTab.Threads,
                    composer = { HomeComposer(onOpen = newThread) },
                )
            },
        ) { shown, bottom ->
            ConnectionLine(remote)
            when (shown) {
                HomeTab.Pulls -> Box(Modifier.weight(1f).fillMaxWidth()) { pulls(bottom) }
                else -> {
                    banners()
                    HomePage(remote, home, shown, selected = null, bottom, navigator::push)
                }
            }
        }
    }
}

/** The threads or the projects of the computer in use; [selected] is what's open beside them, which they mark. */
@Composable
internal fun ColumnScope.HomePage(
    remote: Remote,
    home: HomeServices,
    tab: HomeTab,
    selected: String?,
    bottom: Dp,
    open: (Route) -> Unit,
) {
    val c = RelayTheme.colors
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val overview by remote.overview.collectAsState()
    val status by remote.status.collectAsState()
    val seen by home.seen.state.collectAsState()
    val folds by home.folds.folds.collectAsState()
    val filter by home.filter.filter.collectAsState()
    val inFront by rememberInFront()
    var refreshing by remember { mutableStateOf(false) }

    if (status.state == LinkState.Denied) {
        PairAgain(Modifier.weight(1f).padding(bottom = bottom)) {
            scope.launch {
                remote.forget()
                navigator.replace(Route.Pair())
            }
        }
        return
    }
    val o = overview ?: return Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Spinner(color = c.inkSoft)
    }

    val draftsVersion by home.drafts.version.collectAsState()
    val drafts = remember(o, draftsVersion) { home.drafts.list(o) }
    val openChat = { chat: RemoteChatSummary -> open(Route.Thread(chat.id)) }
    val triage: Triage = triage@{ chat, action ->
        try {
            val client = remote.client.value ?: error("Not connected to your computer.")
            val after = client.desktop("triageProjectChat", listOf(JsonPrimitive(chat.id), action.toJson()))
            scope.launch { runCatching { remote.refresh() } }
            after as? JsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toasts.show("Couldn't change it", description = e.message ?: e.toString(), tone = Tone.Destructive)
            null
        }
    }
    val refresh: () -> Unit = {
        refreshing = true
        scope.launch {
            try {
                remote.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                refreshing = false
            }
        }
    }

    val shown = filter.current(o)
    RibbonRefreshBox(refreshing, refresh, Modifier.weight(1f).fillMaxWidth()) {
        when (tab) {
            HomeTab.Threads -> ActivityList(
                chats = o.listed.filter(shown::keeps),
                projects = o.projects,
                drafts = drafts.filterKeys(shown::keepsDraft),
                filtered = shown.count > 0,
                unread = { seen.unread(it, selected, inFront) },
                folds = folds,
                onFold = home.folds::fold,
                bridge = o.bridge ?: 1,
                selected = selected,
                onOpen = openChat,
                onOpenDraft = { d, project -> open(Route.NewThread(projectId = project, draftKey = d.key)) },
                onTriage = triage,
                bottom = bottom,
            )
            HomeTab.Projects -> ProjectsList(
                o, drafts, selected,
                onOpenChat = openChat,
                onOpenProject = { open(Route.Project(it.id)) },
                onNewScratch = { open(Route.NewThread(scratch = true)) },
                bottom = bottom,
            )
            HomeTab.Pulls -> {}
        }
    }
}

/** The computer turned this phone away: all that helps is pairing again. */
@Composable
private fun PairAgain(modifier: Modifier, onPair: () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Pair this phone again from Relay’s Settings → Phone on your computer.",
            color = RelayTheme.colors.inkSoft,
            textAlign = TextAlign.Center,
        )
        Button("Pair again", onPair, Modifier.fillMaxWidth())
    }
}
