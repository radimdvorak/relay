package dev.relay.android.features.pulls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.relay.android.features.git.CiMark
import dev.relay.android.features.git.ciSummary
import dev.relay.android.features.git.rowMeta
import dev.relay.android.features.thread.MenuItem
import dev.relay.android.features.thread.MenuSheet
import dev.relay.android.remote.model.MergeReadiness
import dev.relay.android.remote.model.PullReview
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Group
import dev.relay.android.ui.Icon
import dev.relay.android.ui.IconButton
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AlertTriangle
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronDown
import io.github.lyxnx.compose.ui.tablericons.outline.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.outline.CircleDashed
import io.github.lyxnx.compose.ui.tablericons.outline.CircleX
import io.github.lyxnx.compose.ui.tablericons.outline.GitMerge
import io.github.lyxnx.compose.ui.tablericons.outline.MessageCircle

private val methodLabels = mapOf("squash" to "Squash and merge", "merge" to "Merge", "rebase" to "Rebase and merge")
private val methodHints = mapOf(
    "squash" to "One commit with all its changes",
    "merge" to "Keeps every commit, joined by a merge commit",
    "rebase" to "Replays its commits onto the base",
)

fun methodLabel(method: String) = methodLabels[method] ?: method

/**
 * What the PR waits on and the way out: checks and reviews folded to one line each,
 * whether it merges cleanly, then the merge button for an open PR.
 */
@Composable
fun MergeGroup(page: PullPage, busy: Boolean, onMerge: (method: String) -> Unit, modifier: Modifier = Modifier) {
    val pull = page.pull
    val open = pull.state == "open" && !pull.merged
    val latest = standingReviews(page.reviews)
    var checksOpen by rememberSaveable { mutableStateOf(false) }
    var reviewsOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Group {
            item {
                Folded(checksOpen, line = { ChecksLine(page, checksOpen) { checksOpen = !checksOpen } }) {
                    page.ci?.let { CheckRows(it) }
                }
            }
            item {
                Folded(reviewsOpen && latest.isNotEmpty(), line = { ReviewsLine(page, latest, reviewsOpen) { reviewsOpen = !reviewsOpen } }) {
                    ReviewRows(latest)
                }
            }
            if (open && page.merges) item { ConflictsLine(page.readiness, pull.base.ref) }
            if (open && !page.merges) item {
                Line(
                    icon = { Icon(TablerIcons.Outline.CircleDashed, null, tint = RelayTheme.colors.inkFaint, size = 20.dp) },
                    title = "Update Relay on your computer to merge",
                    trailing = null,
                )
            }
        }
        if (open && page.merges) MergeButton(page.readiness, pull.draft, busy, onMerge)
    }
}

/** A line of the box and what it opens, unfolding in place. */
@Composable
private fun Folded(open: Boolean, line: @Composable () -> Unit, rows: @Composable ColumnScope.() -> Unit) {
    Column {
        line()
        AnimatedVisibility(
            open,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) { Column(Modifier.padding(start = 50.dp, end = 16.dp, bottom = 10.dp), content = rows) }
    }
}

@Composable
private fun ChecksLine(page: PullPage, expanded: Boolean, onToggle: () -> Unit) {
    val c = RelayTheme.colors
    val runs = page.ci?.runs.orEmpty().filter { it.state != "skipped" }
    val failing = runs.count { it.state == "failure" }
    val passed = runs.count { it.state == "success" }
    val state = ciSummary(runs)?.state
    val title = when {
        runs.isEmpty() -> "No checks"
        failing > 0 -> "$failing ${if (failing == 1) "check" else "checks"} failing"
        state == "running" -> "Checks running"
        else -> "All checks passed"
    }
    Line(
        icon = { if (state == null) Icon(TablerIcons.Outline.CircleDashed, null, tint = c.inkFaint, size = 20.dp) else CiMark(state, 20.dp) },
        title = title,
        trailing = if (runs.isEmpty()) null else "$passed of ${runs.size}",
        expanded = expanded.takeIf { runs.isNotEmpty() },
        onClick = onToggle.takeIf { runs.isNotEmpty() },
    )
}

@Composable
private fun ReviewsLine(page: PullPage, latest: List<PullReview>, expanded: Boolean, onToggle: () -> Unit) {
    val c = RelayTheme.colors
    val verdict = verdictOf(page.reviews)
    val counts = listOf("APPROVED" to "approved", "REQUEST_CHANGES" to "want changes", "COMMENT" to "commented")
        .mapNotNull { (state, word) -> latest.count { it.state == state }.takeIf { it > 0 }?.let { "$it $word" } }
    Line(
        icon = {
            when (verdict) {
                "approved" -> Icon(TablerIcons.Outline.CircleCheck, null, tint = c.success, size = 20.dp)
                "changes" -> Icon(TablerIcons.Outline.CircleX, null, tint = c.danger, size = 20.dp)
                else -> Icon(TablerIcons.Outline.MessageCircle, null, tint = c.inkSoft, size = 20.dp)
            }
        },
        title = when (verdict) {
            "approved" -> "Approved"
            "changes" -> "Changes requested"
            else -> if (latest.isEmpty()) "No reviews yet" else "Reviews"
        },
        trailing = counts.joinToString(" · ").ifEmpty { null },
        expanded = expanded.takeIf { latest.isNotEmpty() },
        onClick = onToggle.takeIf { latest.isNotEmpty() },
    )
}

@Composable
private fun ConflictsLine(readiness: MergeReadiness?, base: String) {
    val c = RelayTheme.colors
    val (icon: ImageVector, tint, title) = when (readiness?.status) {
        "clean" -> Triple(TablerIcons.Outline.CircleCheck, c.success, "No conflicts with $base")
        "conflicts" -> Triple(TablerIcons.Outline.CircleX, c.danger, "Conflicts with $base")
        "blocked" -> Triple(TablerIcons.Outline.AlertTriangle, c.warning, "Branch rules hold it back")
        "unknown" -> Triple(TablerIcons.Outline.CircleDashed, c.inkFaint, "Checking for conflicts…")
        else -> Triple(TablerIcons.Outline.CircleDashed, c.inkFaint, "Couldn't check for conflicts")
    }
    val behind = readiness?.behind
    Line(
        icon = { Icon(icon, null, tint = tint, size = 20.dp) },
        title = title,
        trailing = when {
            behind == null -> null
            behind == 0 -> "Up to date"
            else -> "$behind behind $base"
        },
    )
}

/** The merge, in the repository's preferred way; the chevron picks another it allows. */
@Composable
private fun MergeButton(readiness: MergeReadiness?, draft: Boolean, busy: Boolean, onMerge: (String) -> Unit) {
    val methods = readiness?.methods.orEmpty().ifEmpty { listOf("merge") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val method = chosen?.takeIf { it in methods } ?: methods.first()
    // A host that couldn't say still gets asked: it refuses with its own reason.
    val mergeable = !draft && readiness?.status != "conflicts"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                methodLabel(method),
                { onMerge(method) },
                Modifier.weight(1f),
                size = ButtonSize.Lg,
                leadingIcon = TablerIcons.Outline.GitMerge,
                enabled = mergeable,
                loading = busy,
            )
            if (methods.size > 1) IconButton(
                TablerIcons.Outline.ChevronDown,
                "Merge another way",
                { picking = true },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Lg,
                enabled = !busy,
            )
        }
        val why = when {
            draft -> "It's a draft. Mark it ready on the host to merge."
            readiness?.status == "conflicts" -> "Resolve the conflicts first, or hand them to an agent."
            else -> null
        }
        if (why != null) Text(why, Modifier.padding(horizontal = RowTextInset), style = rowMeta, color = RelayTheme.colors.inkSoft)
    }
    if (picking) MenuSheet(
        methods.map { m -> MenuItem(methodLabel(m), methodHints[m]) { chosen = m } },
        onClose = { picking = false },
        title = "Merge how?",
    )
}

/** One folded line of the box: a mark, what it says, a count, and a chevron when it opens. */
@Composable
private fun Line(icon: @Composable () -> Unit, title: String, trailing: String?, expanded: Boolean? = null, onClick: (() -> Unit)? = null) {
    val c = RelayTheme.colors
    RowPatch(onClick, minHeight = 52.dp) {
        icon()
        Text(title, Modifier.weight(1f), style = RelayTheme.type.body.copy(lineHeight = RelayTheme.type.row.lineHeight), maxLines = 2)
        if (trailing != null) Text(trailing, style = rowMeta, color = c.inkSoft, maxLines = 1)
        if (expanded != null) {
            val turn by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "fold")
            Icon(TablerIcons.Outline.ChevronDown, null, Modifier.graphicsLayer { rotationZ = turn }, tint = c.inkFaint, size = 16.dp)
        }
    }
}
