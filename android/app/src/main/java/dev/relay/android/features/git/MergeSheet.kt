package dev.relay.android.features.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.projectMergeBranch
import dev.relay.android.remote.client.projectMergePlan
import dev.relay.android.remote.model.MergeBranch
import dev.relay.android.remote.model.MergePlan
import dev.relay.android.remote.model.MergeResult
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Group
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Switch
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.GitMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Commits listed before "and N more". */
private const val LISTED = 8

/** What the agent is asked when the merge conflicts (mobile/src/ui/MergeSheet.tsx). */
fun resolveConflictsAsk(plan: MergePlan, conflicts: List<String>) =
    "Merge ${plan.base} into ${plan.branch} and resolve the conflicts in ${conflicts.joinToString(", ")}. " +
        "Then commit, so the branch can merge into ${plan.base} cleanly."

/**
 * The desktop's MergeSheet for a thread's worktree: its branch into the one it
 * came from, pushed along if that has a remote. Merging happens in a folder of
 * its own, so a conflict changes nothing; the phone then hands it to the agent.
 * [where] is the worktree as a workspace id; [onAskAgent] sends the thread's agent a message.
 */
@Composable
fun MergeSheet(
    remote: Remote,
    where: String,
    onDismiss: () -> Unit,
    onMerged: () -> Unit,
    onAskAgent: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<MergePlan?>(null) }
    var push by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<MergeResult?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(base, attempt) {
        try {
            plan = remote.call { projectMergePlan(where, base) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error = errorText(e)
        }
    }
    val close = { if (!busy) onDismiss() }
    fun step(work: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                work()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }
    val code = SpanStyle(fontFamily = RelayTheme.type.code.fontFamily)
    val done = result

    Sheet(close, title = if (done?.merged == true) "Merged into ${done.base}" else "Merge branch") {
        val now = plan
        when {
            done?.merged == true && now != null -> {
                Rich(buildAnnotatedString {
                    withStyle(code) { append(now.branch) }
                    append(" is in ")
                    withStyle(code) { append(done.base ?: now.base) }
                    append(if (done.fastForward) " (fast-forward)" else " as a merge commit")
                    if (done.pushedTo != null) {
                        append(" and pushed to ")
                        withStyle(code) { append(done.pushedTo) }
                    }
                    append(".")
                })
                Button("Done", close, Modifier.fillMaxWidth(), size = ButtonSize.Lg)
            }
            now == null && error != null -> {
                Note(error!!)
                Button("Try again", { error = null; attempt++ }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, size = ButtonSize.Lg)
            }
            now == null -> Loading(Modifier.height(140.dp))
            else -> PlanBody(
                now, done, push, busy, error,
                onBase = { name ->
                    result = null
                    plan = null
                    base = name
                },
                onPush = { push = it },
                onAsk = {
                    val conflicts = done?.conflicts.orEmpty()
                    step {
                        onAskAgent(resolveConflictsAsk(now, conflicts))
                        onDismiss()
                    }
                },
                onMerge = {
                    step {
                        val merged = remote.call {
                            projectMergeBranch(where, MergeBranch(now.branch, now.head, now.base, now.baseHead, push && now.pushTarget != null))
                        }
                        result = merged
                        if (merged.merged) onMerged()
                    }
                },
            )
        }
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
internal fun ColumnScope.PlanBody(
    plan: MergePlan,
    result: MergeResult?,
    push: Boolean,
    busy: Boolean,
    error: String?,
    onBase: (String) -> Unit,
    onPush: (Boolean) -> Unit,
    onAsk: () -> Unit,
    onMerge: () -> Unit,
) {
    val c = RelayTheme.colors
    val code = SpanStyle(fontFamily = RelayTheme.type.code.fontFamily)
    val commits = plan.commits.size
    val pushing = push && plan.pushTarget != null
    val conflicted = result != null && !result.merged
    Rich(buildAnnotatedString {
        withStyle(code) { append(plan.branch) }
        append(" into ")
        withStyle(code) { append(plan.base) }
    })
    if (plan.bases.size > 1) Group {
        for (name in plan.bases.take(5)) item {
            ListRow(
                name,
                size = RowSize.Sm,
                trailing = { if (name == plan.base) Icon(TablerIcons.Outline.Check, "Picked", tint = c.ink, size = 20.dp) },
                onClick = if (busy) null else ({ onBase(name) }),
            )
        }
    }
    if (commits > 0) PlannedCommits(plan) else Note("${plan.base} already has everything on ${plan.branch}.")
    if (plan.pushTarget != null) Group {
        item {
            ListRow(
                "Push ${plan.base}",
                subtitle = "To ${plan.pushTarget}",
                subtitleLines = 1,
                trailing = { Switch(push, onPush, enabled = !busy) },
            )
        }
    }
    if (plan.uncommitted > 0) {
        val one = plan.uncommitted == 1
        Note("${plan.uncommitted} uncommitted ${if (one) "file stays" else "files stay"} in the worktree and ${if (one) "isn’t" else "aren’t"} merged.")
    }
    plan.checkedOutAt?.let {
        Note("Also moves ${plan.base} where it’s checked out, in ${it.substringAfterLast('/')}. Uncommitted edits there stay; if one touches a file this merge changes, nothing happens.")
    }
    if (plan.snapshots > 0) {
        Note("Includes ${if (plan.snapshots == 1) "a commit" else "${plan.snapshots} commits"} Relay made of the checkout’s uncommitted edits when this worktree started; merging lands those edits too.")
    }
    if (conflicted) Conflicts(result.conflicts, busy, onAsk)
    if (error != null) Text(error, style = RelayTheme.type.meta, color = c.danger)
    Button(
        when {
            busy -> "Merging…"
            pushing -> "Merge & push ${plan.base}"
            else -> "Merge into ${plan.base}"
        },
        onMerge,
        Modifier.fillMaxWidth(),
        size = ButtonSize.Lg,
        leadingIcon = TablerIcons.Outline.GitMerge,
        loading = busy,
        enabled = commits > 0 && !conflicted,
    )
}

@Composable
private fun PlannedCommits(plan: MergePlan) {
    val c = RelayTheme.colors
    val commits = plan.commits.size
    val code = SpanStyle(fontFamily = RelayTheme.type.code.fontFamily, color = c.inkSoft)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Note("${if (commits == 50) "Latest 50" else commits} ${if (commits == 1) "commit" else "commits"} · ${if (plan.fastForward) "fast-forward" else "creates a merge commit"}")
        for (commit in plan.commits.take(LISTED)) androidx.compose.material3.Text(
            buildAnnotatedString {
                withStyle(code) { append(commit.sha.take(7)) }
                append("  ")
                append(commit.subject)
            },
            style = RelayTheme.type.meta.copy(color = c.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (commits > LISTED) Note("and ${commits - LISTED} more")
    }
}

/** What stopped the merge, with the way out. */
@Composable
private fun Conflicts(paths: List<String>, busy: Boolean, onAsk: () -> Unit) {
    val c = RelayTheme.colors
    Column(
        Modifier.fillMaxWidth().background(c.container, RelayTheme.shapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("These files conflict, so nothing was merged:", style = RelayTheme.type.meta, color = c.danger)
        for (path in paths) Text(path, style = RelayTheme.type.code, maxLines = 1, overflow = TextOverflow.StartEllipsis)
        Button("Ask the agent to resolve them", onAsk, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !busy)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
}

@Composable
private fun Rich(text: AnnotatedString) {
    androidx.compose.material3.Text(text, style = RelayTheme.type.body.copy(color = RelayTheme.colors.ink))
}
