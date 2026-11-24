package dev.relay.android.features.files

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.features.git.LoadFailed
import dev.relay.android.features.git.Loading
import dev.relay.android.features.git.call
import dev.relay.android.features.git.errorText
import dev.relay.android.features.git.rememberLive
import dev.relay.android.features.markdown.Code
import dev.relay.android.features.markdown.MAX_CODE_LINES
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.projectFile
import dev.relay.android.remote.model.LocalFile
import dev.relay.android.ui.Page
import dev.relay.android.ui.navBarRoom
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

class FileModel(private val remote: Remote, private val where: String, private val path: String) : ViewModel() {
    val file = MutableStateFlow<LocalFile?>(null)
    val error = MutableStateFlow<String?>(null)

    suspend fun load() {
        try {
            file.value = remote.call { projectFile(where, path) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error.value = errorText(e)
        }
    }
}

/** A file as it is on disk; editing stays on the desktop (mobile/src/app/file.tsx). */
@Composable
fun FileScreen(remote: Remote, where: String, path: String) {
    val nav = LocalNavigator.current
    val c = RelayTheme.colors
    val model = viewModel(key = "file:$where:$path") { FileModel(remote, where, path) }
    val file by model.file.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live) { if (live && model.file.value == null) model.load() }

    Page(path.substringAfterLast('/'), onBack = nav.backAction) {
        Text(
            path + (file?.branch?.takeIf { it.isNotEmpty() }?.let { "  ·  $it" } ?: ""),
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
            style = RelayTheme.type.codeSmall,
            color = c.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
        )
        val now = file
        when {
            now == null && error != null -> LoadFailed(error!!, model::load)
            now == null -> Loading()
            else -> FileContents(now, path)
        }
    }
}

@Composable
internal fun ColumnScope.FileContents(file: LocalFile, path: String) {
    val total = remember(file) { file.contents.split('\n').size }
    val cut = total > MAX_CODE_LINES
    Code(file.contents, path, Modifier.weight(1f).padding(horizontal = 4.dp))
    if (cut) Text(
        "Showing the first $MAX_CODE_LINES of $total lines.",
        Modifier.fillMaxWidth().padding(16.dp),
        style = RelayTheme.type.meta,
        color = RelayTheme.colors.inkSoft,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(navBarRoom()))
}
