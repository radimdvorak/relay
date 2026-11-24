package dev.relay.android.features.files

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.features.git.EmptyNote
import dev.relay.android.features.git.LoadFailed
import dev.relay.android.features.git.Loading
import dev.relay.android.features.git.call
import dev.relay.android.features.git.errorText
import dev.relay.android.features.git.rememberLive
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.projectFiles
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Page
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.SearchField
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.navBarRoom
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.FileText
import io.github.lyxnx.compose.ui.tablericons.outline.Folder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

class FilesModel(private val remote: Remote, private val where: String) : ViewModel() {
    val files = MutableStateFlow<List<String>?>(null)
    val error = MutableStateFlow<String?>(null)

    suspend fun load() {
        try {
            files.value = remote.call { projectFiles(where) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error.value = errorText(e)
        }
    }
}

/** The project's files, folder by folder, or all of them filtered by a search. Read-only on a phone. */
@Composable
fun FilesScreen(remote: Remote, where: String, dir: String?) {
    val nav = LocalNavigator.current
    val folder = dir.orEmpty()
    val model = viewModel(key = "files:$where:$folder") { FilesModel(remote, where) }
    val files by model.files.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live) { if (live) model.load() }
    var query by rememberSaveable { mutableStateOf("") }
    val name = folder.substringAfterLast('/')
    val entries = remember(files, folder, query) { files?.let { fileEntries(it, folder, query) } }

    Page(if (folder.isNotEmpty()) name else "Files", onBack = nav.backAction) {
        SearchField(
            query,
            { query = it },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            placeholder = if (folder.isNotEmpty()) "Search in $name…" else "Search files…",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
        )
        when {
            entries == null && error != null -> LoadFailed(error!!, model::load)
            entries == null -> Loading()
            else -> FileEntries(entries, searching = query.isNotBlank()) { e ->
                nav.push(if (e.folder) Route.Files(where, e.path) else Route.File(where, e.path))
            }
        }
    }
}

/** Folders, then files; a search shows whole paths. */
@Composable
internal fun FileEntries(entries: List<FileEntry>, searching: Boolean, onOpen: (FileEntry) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(bottom = 24.dp + navBarRoom())) {
        if (entries.isEmpty()) item {
            EmptyNote(if (searching) "No files match." else "No files here.", Modifier.fillMaxWidth())
        }
        items(entries, key = { it.path }) { e ->
            ListRow(
                if (searching) e.path else e.name,
                titleLines = if (searching) 2 else 1,
                size = RowSize.Sm,
                leadingIcon = if (e.folder) TablerIcons.Outline.Folder else TablerIcons.Outline.FileText,
                chevron = e.folder,
                onClick = { onOpen(e) },
            )
        }
    }
}
