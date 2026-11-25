package dev.relay.android.features.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.projectBranches
import dev.relay.android.remote.client.projectChangeBranch
import dev.relay.android.remote.client.projectChatWorkOn
import dev.relay.android.remote.model.BranchAction
import dev.relay.android.remote.model.BranchList
import dev.relay.android.remote.model.BranchRef
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.Icon
import dev.relay.android.ui.PageToolbar
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Text
import dev.relay.android.ui.TextField
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** mobile/src/app/branches.tsx. [projectId] is what the bridge's branch calls take. */
class BranchesModel(private val remote: Remote, private val projectId: String) : ViewModel() {
    val list = MutableStateFlow<BranchList?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val asks = Asks()

    suspend fun load() {
        try {
            list.value = remote.call { projectBranches(projectId) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error.value = errorText(e)
        }
    }

    /**
     * Switches to or creates [name]; [done] runs once it worked. A refused
     * switch goes to [refused] when there is one, else says why.
     */
    fun change(kind: String, name: String, refused: (() -> Unit)? = null, done: () -> Unit = {}) {
        val now = list.value ?: return
        busy.value = true
        viewModelScope.launch {
            try {
                list.value = remote.call { projectChangeBranch(projectId, BranchAction(kind, name, now.current, now.head)) }
                done()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (refused != null) refused()
                else asks.alert(if (kind == "switch") "Couldn't switch" else "Couldn't create it", errorText(e))
                load()
            } finally {
                busy.value = false
            }
        }
    }

    /** Moves thread [chatId] to [name], wherever it's worked on; [done] runs once it's there. */
    fun moveThread(chatId: String, name: String, done: () -> Unit) {
        busy.value = true
        viewModelScope.launch {
            try {
                remote.call { projectChatWorkOn(chatId, name) }
                done()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                asks.alert("Couldn't move it", errorText(e))
                load()
            } finally {
                busy.value = false
            }
        }
    }
}

/** The started thread a branch list moves: its id, its branch now, and whether it's in its own worktree. */
data class BranchThread(val chatId: String, val branch: String?, val inWorktree: Boolean)

/** The open thread's branch: its worktree's, or the project folder's when it works there. */
private fun threadBranch(thread: BranchThread?, list: BranchList): String? = when {
    thread == null -> null
    thread.inWorktree -> thread.branch
    else -> list.current
}

@Composable
fun BranchesScreen(remote: Remote, projectId: String, thread: BranchThread? = null) {
    val nav = LocalNavigator.current
    val model = viewModel(key = "branches:$projectId") { BranchesModel(remote, projectId) }
    val list by model.list.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live) { if (live) model.load() }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    // A new branch is made in the project folder, which a thread in its own worktree isn't in.
    val canCreate = thread?.inWorktree != true
    val now = list
    BranchesPage(
        now, error, busy,
        threadBranch = now?.let { threadBranch(thread, it) },
        onBack = nav.backAction,
        onRetry = model::load,
        pick = { b -> now?.let { pick(model, it, b, thread) { nav.pop() } } },
        onCreate = { creating = true }.takeIf { canCreate },
    )
    if (creating) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Sheet({ creating = false }, title = "New branch", description = "From ${list?.current ?: "here"}") {
            TextField(
                name,
                { name = it },
                Modifier.fillMaxWidth(),
                placeholder = "feature/phone-remote",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                focusRequester = focus,
            )
            Button(
                if (busy) "Creating…" else "Create and switch",
                {
                    model.change("create", name.trim()) {
                        creating = false
                        name = ""
                    }
                },
                Modifier.fillMaxWidth(),
                size = ButtonSize.Lg,
                enabled = !busy && name.isNotBlank(),
            )
        }
    }
    AskHost(model.asks)
}

/**
 * What tapping [b] does, null when nothing: switch the project folder to it,
 * or with a [thread], go wherever it's worked on; [moved] runs once it did.
 */
private fun pick(model: BranchesModel, list: BranchList, b: BranchRef, thread: BranchThread?, moved: () -> Unit): (() -> Unit)? {
    if (thread == null) {
        // The checked-out one, here or in a worktree, has nowhere to switch to.
        if (b.current || b.worktree) return null
        return {
            model.asks.confirm("Switch to ${b.name}?", "Uncommitted changes that would be overwritten stop the switch.", "Switch") {
                model.change("switch", b.name)
            }
        }
    }
    val move = branchMove(list, b, threadBranch(thread, list), thread.inWorktree)
    if (move == BranchMove.Stay) return null
    val ask = branchMoveAsk(move, b.name)
    val there = { model.moveThread(thread.chatId, b.name, moved) }
    return {
        model.asks.confirm(ask.title, ask.message, ask.action) {
            if (move == BranchMove.SwitchProjectFolder) model.change("switch", b.name, refused = there, done = moved)
            else there()
        }
    }
}

@Composable
internal fun BranchesPage(
    list: BranchList?,
    error: String?,
    busy: Boolean,
    /** The open thread's branch, when there is one. */
    threadBranch: String?,
    onBack: (() -> Unit)?,
    onRetry: suspend () -> Unit,
    /** What tapping a branch does; null when it's already where it would lead. */
    pick: (BranchRef) -> (() -> Unit)?,
    /** Null where a branch can't be made from here. */
    onCreate: (() -> Unit)?,
) {
    val toolbar: (@Composable () -> Unit)? = if (list == null || onCreate == null) null else {
        {
            PageToolbar {
                Button("New branch", onCreate, size = ButtonSize.Lg, leadingIcon = TablerIcons.Outline.Plus, enabled = !busy)
            }
        }
    }
    ChromePage("Branches", onBack = onBack, floating = toolbar) { bottom ->
        when {
            list == null && error != null -> LoadFailed(error, onRetry)
            list == null -> Loading()
            list.branches.isEmpty() -> EmptyState("No branches yet", Modifier.align(Alignment.Center), icon = TablerIcons.Outline.GitBranch)
            else -> {
                val (remote, local) = remember(list) { list.branches.partition { it.remote } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(bottom = bottom)) {
                    for ((title, rows) in listOf("Local" to local, "Remote" to remote)) {
                        if (rows.isEmpty()) continue
                        item(key = title) { SectionLabel(title, count = "${rows.size}") }
                        items(rows, key = { "$title:${it.ref}:${it.name}" }, contentType = { "branch" }) { b ->
                            BranchRow(b, threadBranch, onPick = if (busy) null else pick(b))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchRow(b: BranchRef, threadBranch: String?, onPick: (() -> Unit)?) {
    val c = RelayTheme.colors
    val checked = if (threadBranch != null) b.name == threadBranch else b.current
    RowPatch(onPick, minHeight = RowSize.Sm.minHeight) {
        Icon(TablerIcons.Outline.GitBranch, null, tint = if (checked) c.ink else c.inkSoft, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            // Cut in the middle: branches of one kind start alike and differ at the end.
            Text(
                b.name,
                style = RelayTheme.type.row.copy(fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            branchRowNote(b, threadBranch)?.let { Text(it, style = rowMeta, color = c.inkSoft, maxLines = 1) }
        }
        if (checked) Icon(TablerIcons.Outline.Check, "Checked out", tint = c.ink, size = 20.dp)
    }
}
