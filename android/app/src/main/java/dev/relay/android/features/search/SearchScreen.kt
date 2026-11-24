package dev.relay.android.features.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.features.home.HomeServices
import dev.relay.android.features.home.ProvideProjectIcons
import dev.relay.android.features.home.rememberNow
import dev.relay.android.features.pulls.PullsModel
import dev.relay.android.features.pulls.hostName
import dev.relay.android.features.pulls.pullRoute
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.LinkState
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.ChromeScreen
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.LocalScreenInsets
import dev.relay.android.ui.SearchField
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.fill
import dev.relay.android.ui.screenSettled
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import kotlinx.coroutines.launch

/** Full-screen search over threads and projects, and pull requests when it was opened from them or asked to. */
@Composable
fun SearchScreen(route: Route.Search, remote: Remote, home: HomeServices) {
    val navigator = LocalNavigator.current
    val overview by remote.overview.collectAsState()
    val now = rememberNow(30_000)
    var query by rememberSaveable { mutableStateOf("") }
    var withPulls by rememberSaveable { mutableStateOf(route.pulls) }
    val focus = remember { FocusRequester() }
    val settled = screenSettled()
    // The keyboard comes once the screen is in; coming back from a result leaves it away.
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settled) {
        if (!settled || focused) return@LaunchedEffect
        focused = true
        focus.requestFocus()
    }
    val pulls = if (withPulls) pullSearch(remote, now) { navigator.push(it) } else null

    SearchFrame(
        query, { query = it },
        placeholder = if (route.pulls) "Search pull requests and threads" else "Search threads and projects",
        focus, onBack = { navigator.pop() },
    ) {
        val o = overview
        if (o == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(color = RelayTheme.colors.inkSoft) }
        else ProvideProjectIcons(home) {
            // From the pull requests, an empty query lists those rather than every thread too.
            val found = remember(query, o, route.pulls) {
                if (route.pulls && query.isBlank()) Found(emptyList(), emptyList()) else search(query, o.listed, o.projects)
            }
            SearchResults(
                query, found, now,
                onThread = { navigator.push(Route.Thread(it.id)) },
                onProject = { navigator.push(Route.Project(it.id)) },
                bottom = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding() + 16.dp,
                onSearchPulls = if (withPulls) null else ({ withPulls = true }),
                pulls = pulls,
            )
        }
    }
}

/** Search's chrome: back and the field in the header, results on the sheet. */
@Composable
internal fun SearchFrame(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    focus: FocusRequester?,
    onBack: () -> Unit,
    results: @Composable () -> Unit,
) {
    ChromeScreen(
        header = {
            ChromeHeader(
                leading = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LocalPaneToggle.current?.invoke()
                        CircleButton(TablerIcons.Outline.ArrowLeft, "Close search", onBack, tone = CircleTone.Raised)
                        SearchField(
                            query, onQuery,
                            Modifier.weight(1f),
                            placeholder = placeholder,
                            focusRequester = focus,
                            fill = CircleTone.Raised.fill(RelayTheme.colors),
                        )
                    }
                },
            )
        },
        modifier = Modifier.background(RelayTheme.colors.chrome).windowInsetsPadding(
            WindowInsets.safeDrawing.only(LocalScreenInsets.current).only(WindowInsetsSides.Horizontal),
        ),
    ) { results() }
}

/** The pull requests part, loading while it's on screen: every host the computer is signed in to. */
@Composable
private fun pullSearch(remote: Remote, now: Long, open: (Route) -> Unit): LazyListScope.(List<String>) -> Unit {
    val model = viewModel(key = "search-pulls") { PullsModel(remote) }
    val scope = rememberCoroutineScope()
    val status by remote.status.collectAsState()
    val live = status.state == LinkState.Online
    LaunchedEffect(live) { if (live) model.load() }
    val pulls by model.pulls.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val signedOut by model.signedOut.collectAsStateWithLifecycle()
    val pending by model.pending.collectAsStateWithLifecycle()
    val failed by model.failed.collectAsStateWithLifecycle()
    val note = when {
        signedOut -> "Sign in to GitHub (`gh auth login`), Gitea or Azure DevOps on your computer to see pull requests here."
        pulls == null && error != null -> error
        pending.isNotEmpty() -> "Waiting for ${pending.map(::hostName).distinct().joinToString(", ")}…"
        pulls == null -> "Loading pull requests…"
        failed.isNotEmpty() -> "Couldn't reach ${failed.keys.map(::hostName).distinct().joinToString(", ")}."
        else -> null
    }
    return { terms ->
        pullResults(terms, pulls.orEmpty(), state, { next -> scope.launch { model.show(next) } }, note, now) { open(pullRoute(it)) }
    }
}
