package dev.relay.android.features.pulls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.features.git.CiMark
import dev.relay.android.features.git.DiffCounts
import dev.relay.android.features.git.plural
import dev.relay.android.features.git.rowMeta
import dev.relay.android.features.home.rememberNow
import dev.relay.android.features.home.shortAge
import dev.relay.android.ui.Icon
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.GitMerge
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequest
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequestClosed
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequestDraft

/** Open, draft, merged or closed. Colour is the state, nothing else. */
@Composable
fun PullStateIcon(state: String, draft: Boolean, merged: Boolean, size: Dp = 18.dp) {
    val c = RelayTheme.colors
    when {
        merged -> Icon(TablerIcons.Outline.GitMerge, "Merged", tint = c.info, size = size)
        state == "closed" -> Icon(TablerIcons.Outline.GitPullRequestClosed, "Closed", tint = c.danger, size = size)
        draft -> Icon(TablerIcons.Outline.GitPullRequestDraft, "Draft", tint = c.inkFaint, size = size)
        else -> Icon(TablerIcons.Outline.GitPullRequest, "Open", tint = c.success, size = size)
    }
}

/** What a row says your PR waits on: a failure or change request first, then whether it's ready. */
fun signalText(s: PullSignals?): Pair<String, Boolean>? = when {
    s == null -> null
    s.ci == "failure" -> "CI failing" to true
    s.verdict == "changes" -> "Changes requested" to true
    s.ci == "running" -> "CI running" to false
    s.verdict == "approved" && s.ci != "failure" -> "Ready to merge" to false
    else -> null
}

private fun signalColor(s: PullSignals?, stuck: Boolean, c: RelayColors): Color = when {
    stuck -> c.danger
    s?.ci == "running" -> c.waiting
    else -> c.success
}

/**
 * A PR in the list: its title, then "name #12 · branch · +46 −31" (the author, until its details
 * are in), with when it last moved, CI's mark and its state at the end. Your own say what they
 * wait on first.
 */
@Composable
fun PullRow(p: ListedPull, signals: PullSignals?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    val now = rememberNow(60_000)
    val waits = signalText(signals)
    val about = listOfNotNull(
        "${p.hit.repository.name} #${p.hit.number}",
        signals?.branch,
        p.hit.user.login.takeIf { p.relation != Relation.Mine && it.isNotEmpty() },
        plural(p.hit.comments, "comment").takeIf { p.hit.comments > 0 },
    ).joinToString(" · ")
    RowPatch(onClick, modifier, selected) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.hit.title, style = RelayTheme.type.row, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(about, Modifier.weight(1f, fill = false), style = rowMeta, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                if (signals != null) DiffCounts(signals.additions, signals.deletions)
            }
            if (waits != null) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                signals?.ci?.let { CiMark(it, 14.dp, contentDescription = null) }
                Text(waits.first, style = rowMeta, color = signalColor(signals, waits.second, c), maxLines = 1)
            }
        }
        Text(shortAge(p.updated, now), style = rowMeta, color = c.inkSoft, maxLines = 1)
        PullStateIcon(p.hit.state, p.hit.draft == true, p.merged)
    }
}
