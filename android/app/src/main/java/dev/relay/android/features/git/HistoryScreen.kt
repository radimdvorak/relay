package dev.relay.android.features.git

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.projectHistory
import dev.relay.android.remote.model.CommitLog
import dev.relay.android.remote.model.CommitSummary
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.listPadding
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.GitCommit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

private const val PAGE = 100

class HistoryModel(private val remote: Remote, private val where: String) : ViewModel() {
    val log = MutableStateFlow<CommitLog?>(null)
    val error = MutableStateFlow<String?>(null)
    val limit = MutableStateFlow(PAGE)

    suspend fun load() {
        try {
            log.value = remote.call { projectHistory(where, "head", limit.value) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error.value = errorText(e)
        }
    }
}

/** The checked-out branch's commits, newest first (mobile/src/app/history.tsx). */
@Composable
fun HistoryScreen(remote: Remote, where: String) {
    val nav = LocalNavigator.current
    val model = viewModel(key = "history:$where") { HistoryModel(remote, where) }
    val log by model.log.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val limit by model.limit.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live, limit) { if (live) model.load() }
    HistoryPage(
        log, error,
        onBack = nav.backAction,
        onRetry = model::load,
        onOpen = { nav.push(Route.Commit(where, it.sha)) },
        onOlder = { model.limit.value += PAGE },
    )
}

@Composable
internal fun HistoryPage(
    log: CommitLog?,
    error: String?,
    onBack: (() -> Unit)?,
    onRetry: suspend () -> Unit,
    onOpen: (CommitSummary) -> Unit,
    onOlder: () -> Unit,
) {
    ChromePage("History", onBack = onBack) { bottom ->
        when {
            log == null && error != null -> LoadFailed(error, onRetry)
            log == null -> Loading()
            log.commits.isEmpty() -> EmptyState("No commits yet", Modifier.align(Alignment.Center), icon = TablerIcons.Outline.GitCommit)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(top = 12.dp, bottom = bottom)) {
                items(log.commits, key = { it.sha }, contentType = { "commit" }) { c ->
                    ListRow(c.subject, subtitle = historyLine(c), subtitleLines = 1, onClick = { onOpen(c) })
                }
                if (log.more) item(key = "older") {
                    Button("Older commits", onOlder, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), variant = ButtonVariant.Secondary, size = ButtonSize.Lg)
                }
            }
        }
    }
}

/**
 * "abc1234 · Ada · 5m ago · main, origin/main". The Expo app joined the ref
 * objects themselves and printed "[object Object]"; this shows their names.
 */
fun historyLine(c: CommitSummary, now: Long = System.currentTimeMillis()): String {
    val refs = if (c.refs.isEmpty()) "" else " · " + c.refs.joinToString(", ") { it.name }
    return "${c.sha.take(7)} · ${c.author} · ${ago(c.time * 1000, now)}$refs"
}
