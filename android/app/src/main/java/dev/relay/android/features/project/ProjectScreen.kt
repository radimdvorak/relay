package dev.relay.android.features.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.relay.android.features.home.HomeServices
import dev.relay.android.features.home.LocalProjectIconPath
import dev.relay.android.features.home.projectIconPath
import dev.relay.android.features.tunnel.rememberTunnels
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.LinkState
import dev.relay.android.remote.client.RemoteClient
import dev.relay.android.remote.client.projectTasks
import dev.relay.android.remote.client.projectWorkingTree
import dev.relay.android.remote.model.WorkingTree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** "3 changed · 1 to push"; the line counts stand beside it in their colours. */
internal fun changesLine(tree: WorkingTree) = listOfNotNull(
    if (tree.changes.isNotEmpty()) "${tree.changes.size} changed" else "No changes",
    if (tree.ahead > 0) "${tree.ahead} to push" else null,
    if (tree.behind > 0) "${tree.behind} to pull" else null,
).joinToString(" · ")

private class Glance(val tree: WorkingTree?, val tasks: Int?, val ports: List<Int> = emptyList())

/** The working tree (git projects only) and how many tasks run; a failed half is left out. */
private suspend fun RemoteClient.glance(id: String, git: Boolean): Glance = coroutineScope {
    suspend fun <T> quiet(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    val tree = async { if (git) quiet { projectWorkingTree(id) } else null }
    val tasks = async { quiet { projectTasks(id) } }
    val running = tasks.await()
    Glance(tree.await(), running?.size, running.orEmpty().flatMap { it.ports }.distinct().sorted())
}

/** A project: its checkout at a glance, its panes, and every thread in it (mobile/src/app/project/[id].tsx). */
@Composable
fun ProjectScreen(id: String, remote: Remote, home: HomeServices) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val overview by remote.overview.collectAsState()
    val status by remote.status.collectAsState()
    val client by remote.client.collectAsState()
    val active by remote.active.collectAsState()
    val iconIndex by home.icons.index.collectAsState()
    val project = overview?.projects?.find { it.id == id }
    val chats = overview?.listed.orEmpty().filter { it.projectId == id }
    val drafts = remember(overview) { home.drafts.list(overview) }
    val git = project != null && project.plain != true
    val online = status.state == LinkState.Online
    val tunnels = rememberTunnels(remote)
    var glance by remember { mutableStateOf<Glance?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        val live = client ?: return
        if (!online) return
        glance = live.glance(id, git)
    }
    // Fresh each time the screen comes back, e.g. after a commit: it leaves composition under another screen.
    LaunchedEffect(id, git, online, client) { load() }

    CompositionLocalProvider(LocalProjectIconPath provides { projectIconPath(iconIndex, active, it) }) {
        ProjectPage(
            ProjectFacts(
                id, project?.name, project?.computer, git,
                tree = glance?.tree,
                tasks = glance?.tasks,
                ports = glance?.ports.orEmpty(),
                tunnels = tunnels,
                host = overview?.name,
            ),
            chats, drafts, refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    try {
                        coroutineScope {
                            launch { load() }
                            launch { runCatching { remote.refresh() } }
                        }
                    } finally {
                        refreshing = false
                    }
                }
            },
            onBack = navigator.backAction,
            open = navigator::push,
        )
    }
}
