package dev.relay.android.features.git

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.model.GitAction
import dev.relay.android.remote.model.GitCommit
import dev.relay.android.remote.model.RemoteDiffSource
import dev.relay.android.remote.model.WorkingChange
import dev.relay.android.remote.model.WorkingTree
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.ribbon.RibbonRefreshBox
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.GitCompare

/** The desktop's Changes pane: commit and push everything in one go, or stage and commit a part. */
@Composable
fun ChangesScreen(remote: Remote, where: String) {
    val nav = LocalNavigator.current
    val model = viewModel(key = "changes:$where") { ChangesModel(remote, where) }
    val state by model.state.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live) { if (live) model.load() }
    var committing by rememberSaveable { mutableStateOf(false) }
    val tree = state.tree

    ChangesPage(
        state,
        onBack = nav.backAction,
        onRetry = model::load,
        onRefresh = model::refresh,
        onOpen = { change, area -> nav.push(diffRoute(RemoteDiffSource.working(where, change.path, area))) },
        onAct = { model.act(it.kind, it) },
        onPush = {
            val now = model.state.value.tree ?: return@ChangesPage
            val (title, message) = pushQuestion(now)
            model.asks.confirm(title, message, "Push") { model.act("push", GitAction.push(now.revision)) }
        },
        onCommit = { committing = true },
    )
    if (committing && tree != null && tree.changes.isNotEmpty()) CommitSheet(
        tree,
        state,
        onDismiss = { committing = false },
        onType = model::type,
        onWrite = model::writeNow,
        onCommit = { paths, push ->
            committing = false
            model.commit(paths, push)
        },
    )
    AskHost(model.asks)
}

@Composable
internal fun ChangesPage(
    state: ChangesState,
    onBack: (() -> Unit)?,
    onRetry: suspend () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (WorkingChange, area: String) -> Unit,
    /** Stage, unstage, fetch or pull: what needs no question first. */
    onAct: (GitAction) -> Unit,
    onPush: () -> Unit,
    onCommit: () -> Unit,
) {
    val tree = state.tree
    ChromePage(
        "Changes",
        subtitle = tree?.branch?.ifEmpty { null },
        onBack = onBack,
        floating = tree?.let { { ChangesToolbar(it, state, onAct, onPush, onCommit) } },
    ) { bottom ->
        when {
            tree == null && state.error != null -> LoadFailed(state.error, onRetry)
            tree == null -> Loading()
            else -> RibbonRefreshBox(state.refreshing, onRefresh, Modifier.fillMaxSize()) {
                ChangesList(tree, busy = state.busy != null, bottom, onOpen, onAct)
            }
        }
    }
}

@Composable
private fun ChangesList(
    tree: WorkingTree,
    busy: Boolean,
    bottom: Dp,
    onOpen: (WorkingChange, String) -> Unit,
    onAct: (GitAction) -> Unit,
) {
    val danger = RelayTheme.colors.danger
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(bottom = bottom)) {
        if (tree.operation != null) item(key = "operation") {
            ListNote("${operationInProgress(tree.operation)}. Finish it on your computer.", Modifier.padding(top = 10.dp), danger)
        }
        if (tree.changes.isEmpty()) item(key = "clean") {
            EmptyState("No uncommitted changes", icon = TablerIcons.Outline.GitCompare)
        }
        files("Staged changes", "staged", tree.changes.filter { it.staged }, tree.revision, busy, onOpen, onAct)
        files("Working changes", "unstaged", tree.changes.filter { it.unstaged }, tree.revision, busy, onOpen, onAct)
        item(key = "remote") {
            SectionLabel("Remote")
            ListNote(remoteLine(tree))
        }
        items(tree.outgoing, key = { "out:${it.sha}" }) { OutgoingCommit(it) }
    }
}

private fun LazyListScope.files(
    title: String,
    area: String,
    files: List<WorkingChange>,
    revision: String,
    busy: Boolean,
    onOpen: (WorkingChange, String) -> Unit,
    onAct: (GitAction) -> Unit,
) {
    if (files.isEmpty()) return
    val staged = area == "staged"
    fun toggle(paths: List<String>) = if (staged) GitAction.unstage(revision, paths) else GitAction.stage(revision, paths)
    item(key = "head:$area") {
        SectionLabel(title, count = "${files.size}", action = {
            ToggleAll(if (staged) "Unstage all" else "Stage all", enabled = !busy) { onAct(toggle(files.map { it.path })) }
        })
    }
    items(files, key = { "$area:${it.path}" }, contentType = { "file" }) { f ->
        WorkingFileRow(f, staged, busy, onOpen = { onOpen(f, area) }, onToggle = { onAct(toggle(listOf(f.path))) })
    }
}

@Composable
private fun OutgoingCommit(commit: GitCommit) {
    val c = RelayTheme.colors
    androidx.compose.material3.Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.inkFaint, fontFamily = RelayTheme.type.code.fontFamily)) { append(commit.sha.take(7) + "  ") }
            append(commit.subject)
        },
        Modifier.fillMaxWidth().padding(horizontal = RowTextInset, vertical = 4.dp),
        style = RelayTheme.type.meta.copy(color = c.ink),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
