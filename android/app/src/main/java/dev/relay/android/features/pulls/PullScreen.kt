package dev.relay.android.features.pulls

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.features.git.AskHost
import dev.relay.android.features.git.rememberLive
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.model.PullRef
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.TextField
import kotlinx.coroutines.launch

private enum class Writing { Comment, Changes, Ask }

/**
 * One pull request: what it is, what it waits on (checks, reviews, conflicts) with the merge
 * button, the agent buttons that act on that, then its description, files and conversation. A wide pane splits it in two:
 * the reading on the left, the state and actions on the right.
 */
@Composable
fun PullScreen(remote: Remote, route: Route.Pull) {
    val nav = LocalNavigator.current
    val uri = LocalUriHandler.current
    val filesDir = LocalContext.current.filesDir
    val ref = PullRef(route.owner, route.name, route.number, route.server)
    val model = viewModel(key = "pull:${pullKey(ref)}") { PullModel(remote, ref) }
    val page by model.page.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val overview by remote.overview.collectAsState()
    val live = rememberLive(remote)
    var writing by rememberSaveable { mutableStateOf<Writing?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(live) { if (live) model.load() }

    val project = projectFor(overview, ref.name)
    val p = page
    val threads = overview?.listed.orEmpty().filter { it.branch == p?.pull?.head?.ref && (project == null || it.projectId == project.id) }
    val start = { prompt: String ->
        val branch = model.page.value?.pull?.head?.ref
        if (project != null && branch != null) model.agent {
            val id = startPullThread(remote, filesDir, project.id, branch, prompt)
            nav.openInPane(Route.Thread(id))
        }
    }
    PullPageBody(
        "${route.name} #${route.number}", p, error, busy, project?.name, threads, refreshing,
        PullActions(
            onBack = nav.backAction,
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
            onHost = { path -> p?.let { uri.openUri(it.pull.html_url + path) } },
            onMerge = { method ->
                if (p != null) {
                    // The head on screen now: a reload while this is open mustn't merge one nobody saw.
                    val head = p.pull.head.sha
                    model.asks.confirm("${methodLabel(method)} #${p.pull.number}?", "${p.pull.head.ref} into ${p.pull.base.ref}", "Merge") { model.merge(head, method) }
                }
            },
            onFixCi = { p?.let { start(fixCiPrompt(it)) } },
            onAddress = { p?.let { start(addressReviewPrompt(it)) } },
            onAsk = { writing = Writing.Ask },
            onThread = { nav.openInPane(Route.Thread(it.id)) },
            onComment = { writing = Writing.Comment },
            onChanges = { writing = Writing.Changes },
            onApprove = {
                if (p != null) model.asks.confirm("Approve #${p.pull.number}?", p.pull.title, "Approve") { model.review("APPROVED", "") }
            },
        ),
    )
    writing?.let { w ->
        WriteSheet(w, onDismiss = { writing = null }) { text ->
            writing = null
            when (w) {
                Writing.Comment -> model.comment(text)
                Writing.Changes -> model.review("REQUEST_CHANGES", text)
                Writing.Ask -> page?.let { start(askPrompt(it, text)) }
            }
        }
    }
    AskHost(model.asks)
}

@Composable
private fun WriteSheet(writing: Writing, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val (title, action, hint) = when (writing) {
        Writing.Comment -> Triple("Comment", "Comment", "Say something on the PR")
        Writing.Changes -> Triple("Request changes", "Request changes", "What needs to change")
        Writing.Ask -> Triple("Ask an agent", "Start thread", "What do you want to know or done?")
    }
    Sheet(onDismiss, title = title, description = if (writing == Writing.Ask) "A new thread on the PR's branch gets this with the PR's details." else null) {
        TextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = hint, singleLine = false, minLines = 4)
        Button(action, { onSend(text.trim()) }, Modifier.fillMaxWidth(), size = ButtonSize.Lg, enabled = text.isNotBlank())
    }
}
