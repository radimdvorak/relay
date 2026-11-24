package dev.relay.android.features.git

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.backAction
import dev.relay.android.remote.Remote
import dev.relay.android.remote.model.RemoteDiffSource
import dev.relay.android.remote.model.ResumeSettings
import dev.relay.android.remote.model.TurnFileChange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The desktop's Turn changes pane: what one agent turn changed, or with
 * [worktree] everything the thread's worktree has that the branch it came
 * from doesn't yet (mobile/src/app/turn.tsx).
 *
 * [onAskAgent] sends the thread's agent a message when a merge conflicts, on
 * the thread's last settings (null before it has any), the way its composer
 * sends: through the outbox.
 */
@Composable
fun TurnScreen(
    remote: Remote,
    chatId: String,
    messageId: String?,
    worktree: Boolean,
    onAskAgent: suspend (text: String, settings: ResumeSettings?) -> Unit,
) {
    val nav = LocalNavigator.current
    val model = viewModel(key = "turn:$chatId:$messageId:$worktree") { TurnModel(remote, chatId, messageId, worktree) }
    val thread by model.thread.collectAsStateWithLifecycle()
    val threadError by model.threadError.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val statusError by model.statusError.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val live = rememberLive(remote)
    LaunchedEffect(live) { if (live) model.loadStatus() }
    val scope = rememberCoroutineScope()
    var merging by remember { mutableStateOf(false) }

    val turn = messageId?.let { id -> thread?.messages?.find { it.id == id } }
    val files: List<TurnFileChange>? = if (worktree) status?.files else turn?.changes
    val title = when {
        worktree -> status?.branch ?: "Worktree"
        turn != null -> "${agentName(turn.provider)} · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(turn.ended ?: turn.created))}"
        else -> "Changes"
    }
    val reverted = files?.count { it.revertedBy != null } ?: 0
    val all = files?.size ?: 0

    val error = if (worktree) statusError else threadError
    val toMerge = status?.takeIf { worktree && it.from != null && it.landed == null && !it.removed && it.files.isNotEmpty() }

    TurnPage(
        title,
        files,
        note = summary(worktree, status, all, reverted),
        error = error,
        busy = busy,
        onBack = nav.backAction,
        onRetry = { if (worktree) model.loadStatus() else model.reloadThread() },
        onOpen = { f ->
            nav.push(diffRoute(if (worktree) RemoteDiffSource.worktree(chatId, f.path) else RemoteDiffSource.turn(chatId, messageId!!, f.path)))
        },
        onRewind = if (worktree) null else { f -> model.rewind(listOf(f.path), if (f.revertedBy != null) "redo" else "revert") },
        onRollBackAll = {
            model.asks.confirm(
                "Roll back ${all - reverted} files?",
                "They go back to how they were before this turn. Later edits are kept where they merge.",
                "Roll back",
                destructive = true,
            ) { model.rewind(null, "revert") }
        },
        onRedoAll = { model.rewind(null, "redo") },
        mergeInto = toMerge?.from,
        onMerge = { merging = true },
    )
    val chat = thread
    if (merging && worktree && chat != null) MergeSheet(
        remote,
        workspaceId(chat.projectId, chatId),
        onDismiss = { merging = false },
        onMerged = { scope.launch { model.loadStatus() } },
        onAskAgent = { text ->
            onAskAgent(text, model.thread.value?.settings)
            nav.pop()
        },
    )
    AskHost(model.asks)
}

private fun summary(worktree: Boolean, status: dev.relay.android.remote.model.WorktreeStatus?, all: Int, reverted: Int): String {
    val count = plural(all, "file")
    if (!worktree) return "$count changed" + if (reverted > 0) " · $reverted rolled back" else ""
    val landed = status?.landed
    if (landed != null) return "Landed ${if (landed.by == "pr") "through its pull request" else "in"} ${status.from ?: "its branch"}."
    return "$count ${status?.from?.let { "ahead of $it" } ?: "changed"}"
}
