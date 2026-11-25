package dev.relay.android.features.pulls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.features.git.ListNote
import dev.relay.android.features.git.LoadFailed
import dev.relay.android.features.git.Loading
import dev.relay.android.features.git.rememberLive
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.Remote
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.SegmentedTabs
import dev.relay.android.ui.TabOption
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.ribbon.RibbonRefreshBox
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequest
import kotlinx.coroutines.launch

/** The key Home's sidebar marks an open PR by. */
fun pullSelection(route: Route.Pull) = pullKey(dev.relay.android.remote.model.PullRef(route.owner, route.name, route.number, route.server))

fun pullRoute(p: ListedPull) = Route.Pull(p.ref.owner, p.ref.name, p.ref.number, p.ref.server)

/** What the Pull requests tab shows. */
internal data class PullsState(
    val pulls: List<ListedPull>?,
    val error: String? = null,
    /** Hosts still searching. */
    val pending: Set<String> = emptySet(),
    /** Hosts whose search failed, with why. */
    val failed: Map<String, String> = emptyMap(),
    val signals: Map<String, PullSignals> = emptyMap(),
    val signedOut: Boolean = false,
    /** "open" | "closed" | "all" */
    val show: String = "open",
)

/**
 * Home's Pull requests tab: what asks for your review, what's assigned to you and your own,
 * across every host the computer is signed in to. Your own say what they wait on.
 */
@Composable
fun PullsList(remote: Remote, query: String, selected: String?, bottom: Dp, open: (Route) -> Unit) {
    val model = viewModel(key = "pulls") { PullsModel(remote) }
    val pulls by model.pulls.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val signedOut by model.signedOut.collectAsStateWithLifecycle()
    val signals by model.signals.collectAsStateWithLifecycle()
    val pending by model.pending.collectAsStateWithLifecycle()
    val failed by model.failed.collectAsStateWithLifecycle()
    val show by model.state.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    // Hosts don't push; look again whenever the link comes back or the app comes to the front.
    LaunchedEffect(live) { if (live) model.load() }

    PullsBody(
        PullsState(pulls, error, pending, failed, signals, signedOut, show),
        query, selected, bottom, refreshing,
        onShow = { scope.launch { model.show(it) } },
        onRetry = model::load,
        onRefresh = {
            refreshing = true
            scope.launch {
                try {
                    model.load()
                } finally {
                    refreshing = false
                }
            }
        },
        onOpen = { open(pullRoute(it)) },
    )
}

private val STATES = listOf(TabOption("open", "Open"), TabOption("closed", "Closed"), TabOption("all", "All"))

@Composable
internal fun PullsBody(
    state: PullsState,
    query: String,
    selected: String?,
    bottom: Dp,
    refreshing: Boolean,
    onShow: (String) -> Unit,
    onRetry: suspend () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ListedPull) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SegmentedTabs(STATES, state.show, onShow, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.pulls == null && state.error != null -> LoadFailed(state.error, onRetry)
                // Only until the first search answers: an empty answer from one host is still an answer.
                state.pulls == null -> Loading()
                else -> RibbonRefreshBox(refreshing, onRefresh, Modifier.fillMaxSize()) {
                    FoundPulls(state, state.pulls, query, selected, bottom, onOpen)
                }
            }
        }
    }
}

@Composable
private fun FoundPulls(
    state: PullsState,
    pulls: List<ListedPull>,
    query: String,
    selected: String?,
    bottom: Dp,
    onOpen: (ListedPull) -> Unit,
) {
    val danger = RelayTheme.colors.danger
    val shown = remember(pulls, query) { pulls.filter { matches(it, query) } }
    val groups = remember(shown, state.signals, state.show) { pullGroups(shown, state) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(bottom = bottom)) {
        // A slow or failed host says so above what the others found.
        if (state.pending.isNotEmpty()) item(key = "waiting") {
            ListNote("Waiting for ${state.pending.map(::hostName).distinct().joinToString(", ")}…")
        }
        for ((server, why) in state.failed) item(key = "failed:$server") {
            ListNote("Couldn't reach ${hostName(server)}: $why", color = danger, maxLines = 2)
        }
        if (state.signedOut) item(key = "signed-out") {
            EmptyState(
                "Not signed in",
                description = "Sign in to GitHub (`gh auth login`), Gitea or Azure DevOps on your computer to see pull requests here.",
                icon = TablerIcons.Outline.GitPullRequest,
            )
        } else if (groups.isEmpty() && state.pending.isEmpty()) item(key = "none") {
            EmptyState(nothingFound(state.show, query.trim()), icon = TablerIcons.Outline.GitPullRequest)
        }
        for ((label, rows) in groups) {
            item(key = "head:$label") { SectionLabel(label, count = "${rows.size}") }
            items(rows, key = { it.key }, contentType = { "pull" }) { p ->
                PullRow(p, state.signals[p.key], selected = p.key == selected, onClick = { onOpen(p) })
            }
        }
    }
}

private fun matches(p: ListedPull, query: String): Boolean {
    val needle = query.trim().lowercase()
    val text = "${p.hit.title} ${p.hit.repository.name} ${p.hit.number} ${p.hit.user.login}".lowercase()
    return needle.isEmpty() || text.contains(needle)
}

private fun pullGroups(pulls: List<ListedPull>, state: PullsState): List<Pair<String, List<ListedPull>>> {
    val mine = pulls.filter { it.relation == Relation.Mine }
    return listOf(
        (if (state.show == "open") "Needs your review" else "Asked for your review") to pulls.filter { it.relation == Relation.Review },
        "Assigned to you" to pulls.filter { it.relation == Relation.Assigned },
        // Yours that wait on you come first: failing CI or change requests.
        "Yours" to mine.sortedByDescending { signalText(state.signals[it.key])?.second == true },
    ).filter { it.second.isNotEmpty() }
}

private fun nothingFound(show: String, query: String): String = when {
    query.isNotEmpty() -> "No pull request matches “$query”"
    show == "open" -> "No open pull requests need you"
    show == "closed" -> "No closed pull requests of yours"
    else -> "No pull requests of yours"
}
