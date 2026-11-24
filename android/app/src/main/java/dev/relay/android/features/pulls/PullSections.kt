package dev.relay.android.features.pulls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relay.android.features.git.CiMark
import dev.relay.android.features.git.DiffCounts
import dev.relay.android.features.git.FileNames
import dev.relay.android.features.git.FileRowHeight
import dev.relay.android.features.git.ListNote
import dev.relay.android.features.git.plural
import dev.relay.android.features.git.rowMeta
import dev.relay.android.features.home.ThreadRow
import dev.relay.android.features.home.ago
import dev.relay.android.features.home.rememberNow
import dev.relay.android.features.markdown.Markdown
import dev.relay.android.remote.model.CiStatus
import dev.relay.android.remote.model.PullComment
import dev.relay.android.remote.model.PullDetail
import dev.relay.android.remote.model.PullFile
import dev.relay.android.remote.model.PullReview
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListInset
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import io.github.lyxnx.compose.ui.tablericons.outline.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.outline.CircleX
import io.github.lyxnx.compose.ui.tablericons.outline.MessageQuestion
import io.github.lyxnx.compose.ui.tablericons.outline.Robot
import io.github.lyxnx.compose.ui.tablericons.outline.Tool

/** Where a PR page's reading starts: level with the section labels and the groups' text. */
private val Inset = 8.dp + RowTextInset

/** State, title, who wants what merged where, and how big it is. */
@Composable
fun PullHeader(pull: PullDetail) {
    val c = RelayTheme.colors
    val now = rememberNow(60_000)
    val state = when {
        pull.merged -> "Merged"
        pull.state == "closed" -> "Closed"
        pull.draft -> "Draft"
        else -> "Open"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Inset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PullStateIcon(pull.state, pull.draft, pull.merged)
            Text("$state · ${pull.user.login} · ${ago(isoMs(pull.updated_at), now)}", style = rowMeta, color = c.inkSoft, maxLines = 1)
        }
        Text(pull.title, style = RelayTheme.type.heading)
        Text("${pull.head.ref} → ${pull.base.ref}", style = rowMeta, color = c.inkSoft, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plural(pull.changed_files, "file"), style = rowMeta, color = c.inkSoft)
            DiffCounts(pull.additions, pull.deletions)
        }
    }
}

/**
 * The agent buttons, the reason this tab exists: a failing check or a change request turns
 * into a thread on the PR's branch in one tap. Says so when no project here is the repo.
 */
@Composable
fun AgentSection(
    page: PullPage,
    projectName: String?,
    busy: Boolean,
    onFixCi: () -> Unit,
    onAddress: () -> Unit,
    onAsk: () -> Unit,
) {
    val failing = page.ci?.runs.orEmpty().any { it.state == "failure" }
    val changes = verdictOf(page.reviews) == "changes"
    Section("Hand it to an agent", projectName?.let { "in $it" }) {
        if (projectName == null) {
            Quiet("No project on your computer is a checkout of ${page.pull.name}. Add it there to start threads from here.")
            return@Section
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failing) Button("Fix CI", onFixCi, variant = ButtonVariant.Default, leadingIcon = TablerIcons.Outline.Tool, enabled = !busy)
            if (changes) Button(
                "Address review",
                onAddress,
                variant = if (failing) ButtonVariant.Secondary else ButtonVariant.Default,
                leadingIcon = TablerIcons.Outline.Robot,
                enabled = !busy,
            )
            Button("Ask about it", onAsk, variant = ButtonVariant.Secondary, leadingIcon = TablerIcons.Outline.MessageQuestion, enabled = !busy, loading = busy)
        }
        Quiet("Starts a thread in a worktree on ${page.pull.head.ref}")
    }
}

/** A labelled stretch of the page, straight on the sheet. */
@Composable
private fun Section(title: String, count: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(title, Modifier.padding(horizontal = 8.dp), count = count)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Inset, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = rowMeta, color = RelayTheme.colors.inkSoft)
}

/** Each workflow's latest run, the failures first; a tap opens its run on the host. */
@Composable
fun CheckRows(ci: CiStatus) {
    val c = RelayTheme.colors
    val uri = LocalUriHandler.current
    val order = mapOf("failure" to 0, "running" to 1, "success" to 2)
    for (run in ci.runs.sortedBy { order[it.state] ?: 3 }) {
        Row(
            Modifier.fillMaxWidth().pressable(onClick = { uri.openUri(run.url) }, scale = false).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CiMark(run.state, 18.dp)
            Column(Modifier.weight(1f)) {
                Text(run.workflow, style = RelayTheme.type.meta, color = c.ink, maxLines = 1)
                run.failedJob?.let { Text(it, style = rowMeta, color = c.danger, maxLines = 1) }
            }
            Icon(TablerIcons.Outline.ChevronRight, null, tint = c.inkFaint, size = 14.dp)
        }
    }
}

/**
 * Each reviewer's standing review, newest first: their latest approval or change request
 * as [verdictOf] counts it, else their latest comment. A comment after an approval doesn't undo it.
 */
fun standingReviews(reviews: List<PullReview>) = reviews.filter { it.state != "PENDING" }
    .groupBy { if (it.user.id != 0L) "${it.user.id}" else it.user.login }
    .map { (_, theirs) ->
        val verdicts = theirs.filter { (it.state == "APPROVED" || it.state == "REQUEST_CHANGES") && it.dismissed != true }
        verdicts.ifEmpty { theirs }.maxWith(compareBy({ isoMs(it.submitted_at) }, { it.id }))
    }
    .sortedByDescending { isoMs(it.submitted_at) }

/** Each reviewer's standing verdict, newest first, with what they said. */
@Composable
fun ReviewRows(latest: List<PullReview>) {
    val c = RelayTheme.colors
    val now = rememberNow(60_000)
    for (r in latest) Column(Modifier.padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (r.state) {
                "APPROVED" -> Icon(TablerIcons.Outline.CircleCheck, "Approved", tint = c.success, size = 18.dp)
                "REQUEST_CHANGES" -> Icon(TablerIcons.Outline.CircleX, "Changes requested", tint = c.danger, size = 18.dp)
                else -> Icon(TablerIcons.Outline.MessageQuestion, "Commented", tint = c.inkSoft, size = 18.dp)
            }
            Said(r.user.login, ago(isoMs(r.submitted_at), now))
        }
        if (r.body.isNotBlank()) Markdown(r.body.trim(), Modifier.padding(start = 26.dp), small = true)
    }
}

/** Threads here already on the PR's branch, so you go back to the one that made it. */
@Composable
fun BranchThreads(chats: List<RemoteChatSummary>, onOpen: (RemoteChatSummary) -> Unit) {
    if (chats.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = ListInset)) {
        SectionLabel("Threads on this branch", count = "${chats.size}")
        for (chat in chats) ThreadRow(chat, null, { onOpen(chat) })
    }
}

@Composable
fun DescriptionSection(body: String?) {
    var all by rememberSaveable { mutableStateOf(false) }
    Section("Description") {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return@Section Quiet("No description.")
        // Agent-written descriptions run long; the first screenful, then the rest on a tap.
        val long = text.length > 1200
        Markdown(if (long && !all) text.take(1200).substringBeforeLast('\n') + "\n\n…" else text)
        if (long) Button(if (all) "Show less" else "Show all", { all = !all }, variant = ButtonVariant.Secondary, size = ButtonSize.Sm)
    }
}

/** Changed files with their counts; a tap opens the file's diff on the host. */
@Composable
fun FilesSection(files: List<PullFile>, more: Boolean, total: Int, onOpen: (PullFile) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ListInset)) {
        SectionLabel("Files", count = "$total")
        for (f in files) RowPatch({ onOpen(f) }, minHeight = FileRowHeight) {
            FileNames(f.filename, Modifier.weight(1f))
            DiffCounts(f.additions, f.deletions)
        }
        if (more) ListNote("And more on the host.")
    }
}

@Composable
fun DiscussionSection(comments: List<PullComment>) {
    val now = rememberNow(60_000)
    Section("Conversation", comments.size.takeIf { it > 0 }?.toString()) {
        if (comments.isEmpty()) Quiet("No comments yet.")
        for (comment in comments.takeLast(20)) Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Said(comment.user.login, ago(isoMs(comment.created_at), now))
            Markdown(comment.body.trim())
        }
    }
}

/** Who said something, and when. */
@Composable
private fun Said(login: String, at: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(login, Modifier.weight(1f, fill = false), style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        Text(at, style = rowMeta, color = RelayTheme.colors.inkSoft)
    }
}
