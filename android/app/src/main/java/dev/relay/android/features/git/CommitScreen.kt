package dev.relay.android.features.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.projectCommit
import dev.relay.android.remote.model.CommitDetail
import dev.relay.android.remote.model.CommitFileChange
import dev.relay.android.remote.model.RemoteDiffSource
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Text
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

private val statusLabels = mapOf("A" to "Added", "M" to "Modified", "D" to "Deleted", "R" to "Renamed", "C" to "Copied", "T" to "Type changed")

/** "Added", "Renamed · binary"; null for a plain edit, which its counts already say. */
fun commitFileNote(f: CommitFileChange): String? = listOfNotNull(
    (statusLabels[f.status] ?: f.status).takeIf { f.status != "M" },
    "binary".takeIf { f.binary },
).joinToString(" · ").ifEmpty { null }

class CommitModel(private val remote: Remote, private val where: String, private val sha: String) : ViewModel() {
    val commit = MutableStateFlow<CommitDetail?>(null)
    val error = MutableStateFlow<String?>(null)

    suspend fun load() {
        try {
            commit.value = remote.call { projectCommit(where, sha) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error.value = errorText(e)
        }
    }
}

/** One commit: its message, and the files it changed against its first parent (mobile/src/app/commit.tsx). */
@Composable
fun CommitScreen(remote: Remote, where: String, sha: String) {
    val nav = LocalNavigator.current
    val model = viewModel(key = "commit:$where:$sha") { CommitModel(remote, where, sha) }
    val commit by model.commit.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live) { if (live && model.commit.value == null) model.load() }
    CommitPage(
        sha, commit, error,
        onBack = nav.backAction,
        onRetry = model::load,
        onOpen = { nav.push(diffRoute(RemoteDiffSource.commit(where, sha, it.path))) },
    )
}

@Composable
internal fun CommitPage(
    sha: String,
    commit: CommitDetail?,
    error: String?,
    onBack: (() -> Unit)?,
    onRetry: suspend () -> Unit,
    onOpen: (CommitFileChange) -> Unit,
) {
    ChromePage("Commit", subtitle = sha.take(7), onBack = onBack) { bottom ->
        when {
            commit == null && error != null -> LoadFailed(error, onRetry)
            commit == null -> Loading()
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(bottom = bottom)) {
                item(key = "message") { CommitMessage(commit) }
                item(key = "files") { SectionLabel("Files", count = "${commit.files.size}") }
                items(commit.files, key = { it.path }, contentType = { "file" }) { f -> CommitFileRow(f) { onOpen(f) } }
            }
        }
    }
}

@Composable
private fun CommitMessage(commit: CommitDetail) {
    val c = RelayTheme.colors
    SelectionContainer {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = RowTextInset).padding(top = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(commit.subject, style = RelayTheme.type.heading)
            if (commit.body.isNotBlank()) Text(commit.body.trim())
            Text("${commit.author} · ${dateTime(commit.time * 1000)}", style = rowMeta, color = c.inkSoft)
            Text(commit.sha, style = RelayTheme.type.codeSmall, color = c.inkSoft)
        }
    }
}

/** A file in the commit: where it is, its counts, and what happened to it when it wasn't just edited. */
@Composable
private fun CommitFileRow(f: CommitFileChange, onClick: () -> Unit) {
    val c = RelayTheme.colors
    RowPatch(onClick, minHeight = FileRowHeight) {
        Column(Modifier.weight(1f)) {
            FileNames(f.path)
            f.previousPath?.let { RenamedFrom(it) }
        }
        val what = commitFileNote(f)
        if (what != null) Text(what, style = rowMeta, color = c.inkSoft, maxLines = 1)
        if (!f.binary) DiffCounts(f.additions, f.deletions)
    }
}

@Composable
private fun RenamedFrom(path: String) {
    val soft = RelayTheme.colors.inkSoft
    Row {
        Text("from ", style = rowMeta, color = soft, maxLines = 1)
        Text(path, style = rowMeta, color = soft, maxLines = 1, overflow = TextOverflow.StartEllipsis)
    }
}
