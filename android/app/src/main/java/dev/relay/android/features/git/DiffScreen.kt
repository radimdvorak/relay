package dev.relay.android.features.git

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.diff
import dev.relay.android.remote.model.RemoteDiff
import dev.relay.android.remote.model.RemoteDiffSource
import dev.relay.android.remote.model.RemoteJson
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.rememberIsWide
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Binary
import io.github.lyxnx.compose.ui.tablericons.outline.Columns1
import io.github.lyxnx.compose.ui.tablericons.outline.Columns2
import io.github.lyxnx.compose.ui.tablericons.outline.FileDiff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

private const val LAYOUT_KEY = "relay-diff-layout"

/** Side by side, remembered as "split" or "unified" like the Expo app's AsyncStorage key. */
private class DiffLayout(context: Context) {
    private val prefs = context.getSharedPreferences("relay-diff", Context.MODE_PRIVATE)
    var split by mutableStateOf(prefs.getString(LAYOUT_KEY, null) == "split")
        private set

    fun toggle() {
        split = !split
        prefs.edit().putString(LAYOUT_KEY, if (split) "split" else "unified").apply()
    }
}

class DiffModel(private val remote: Remote, val source: RemoteDiffSource) : ViewModel() {
    val diff = MutableStateFlow<RemoteDiff?>(null)
    val error = MutableStateFlow<String?>(null)

    suspend fun load() {
        try {
            diff.value = remote.call { diff(source) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error.value = errorText(e)
        }
    }
}

/**
 * One file's change with wrapped lines: unified, or side by side on a wide
 * screen (mobile/src/app/diff.tsx). [sourceJson] is Route.Diff's RemoteDiffSource.
 */
@Composable
fun DiffScreen(remote: Remote, sourceJson: String) {
    val nav = LocalNavigator.current
    val model = viewModel(key = "diff:$sourceJson") { DiffModel(remote, RemoteJson.decodeFromString<RemoteDiffSource>(sourceJson)) }
    val diff by model.diff.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live) { if (live && model.diff.value == null) model.load() }
    val context = LocalContext.current
    val layout = remember { DiffLayout(context.applicationContext) }
    // The Expo app also hid the sidebar for two columns; here they get what the pane has.
    val wide = rememberIsWide()
    val split = wide && layout.split
    val path = model.source.path

    DiffPage(
        path, diff, error, split,
        onBack = nav.backAction,
        onRetry = model::load,
        onToggleSplit = if (wide) layout::toggle else null,
    )
}

@Composable
internal fun DiffPage(
    path: String,
    diff: RemoteDiff?,
    error: String?,
    split: Boolean,
    onBack: (() -> Unit)?,
    onRetry: suspend () -> Unit,
    /** Null where there's no room for two columns. */
    onToggleSplit: (() -> Unit)?,
) {
    ChromePage(
        path.substringAfterLast('/'),
        subtitle = path.substringBeforeLast('/', "").ifEmpty { null },
        onBack = onBack,
        actions = {
            if (onToggleSplit != null) CircleButton(
                if (split) TablerIcons.Outline.Columns1 else TablerIcons.Outline.Columns2,
                "Side by side",
                onToggleSplit,
                Modifier.semantics { selected = split },
                tone = CircleTone.Raised,
            )
        },
    ) { bottom ->
        when {
            diff == null && error != null -> LoadFailed(error, onRetry)
            diff == null -> Loading()
            diff.binary -> EmptyState(
                "A binary file",
                Modifier.align(Alignment.Center),
                description = "Open it on your computer to see the change.",
                icon = TablerIcons.Outline.Binary,
            )
            diff.hunks.isEmpty() -> EmptyState("Nothing to show", Modifier.align(Alignment.Center), icon = TablerIcons.Outline.FileDiff)
            else -> DiffBody(diff, split, bottom)
        }
    }
}
