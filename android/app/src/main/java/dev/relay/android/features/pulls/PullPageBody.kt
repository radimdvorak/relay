package dev.relay.android.features.pulls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.features.git.LoadFailed
import dev.relay.android.features.git.Loading
import dev.relay.android.remote.model.RemoteChatSummary
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.IconButton
import dev.relay.android.ui.PageToolbar
import dev.relay.android.ui.ribbon.RibbonRefreshBox
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.ExternalLink
import io.github.lyxnx.compose.ui.tablericons.outline.MessagePlus
import io.github.lyxnx.compose.ui.tablericons.outline.MessageX

/** Wide enough for the PR beside what it waits on: an unfolded Fold with the sidebar hidden. */
private val TWO_COLUMNS = 680.dp

internal class PullActions(
    val onBack: (() -> Unit)?,
    val onRetry: suspend () -> Unit,
    val onRefresh: () -> Unit,
    /** Opens the PR on its host, at a path under it ("/files"). */
    val onHost: (path: String) -> Unit,
    val onMerge: (method: String) -> Unit,
    val onFixCi: () -> Unit,
    val onAddress: () -> Unit,
    val onAsk: () -> Unit,
    val onThread: (RemoteChatSummary) -> Unit,
    val onComment: () -> Unit,
    val onChanges: () -> Unit,
    val onApprove: () -> Unit,
)

/** The PR's page; a wide pane splits it in two: the reading on the left, the state and actions on the right. */
@Composable
internal fun PullPageBody(
    title: String,
    page: PullPage?,
    error: String?,
    /** "approve" | "comment" | "changes" | "agent" | "merge" while that goes. */
    busy: String?,
    /** The project here that's a checkout of the PR's repository. */
    project: String?,
    threads: List<RemoteChatSummary>,
    refreshing: Boolean,
    actions: PullActions,
) {
    val open = page != null && page.pull.state == "open" && !page.pull.merged
    ChromePage(
        title,
        onBack = actions.onBack,
        actions = {
            if (page != null) CircleButton(TablerIcons.Outline.ExternalLink, "Open on the host", { actions.onHost("") }, tone = CircleTone.Raised)
        },
        floating = if (open) ({ ReviewToolbar(busy, actions) }) else null,
    ) { bottom ->
        when {
            page == null && error != null -> LoadFailed(error, actions.onRetry)
            page == null -> Loading()
            else -> RibbonRefreshBox(refreshing, actions.onRefresh, Modifier.fillMaxSize()) {
                val pad = PaddingValues(top = 20.dp, bottom = bottom)
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= TWO_COLUMNS) Row(Modifier.fillMaxSize()) {
                        PullColumn(pad, Modifier.weight(1.4f)) {
                            item("header") { PullHeader(page.pull) }
                            reading(page, actions)
                        }
                        PullColumn(pad, Modifier.weight(1f).padding(end = 8.dp)) { state(page, busy, project, threads, actions) }
                    } else PullColumn(pad, Modifier) {
                        item("header") { PullHeader(page.pull) }
                        state(page, busy, project, threads, actions)
                        reading(page, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun PullColumn(pad: PaddingValues, modifier: Modifier, items: LazyListScope.() -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(16.dp), content = items)
}

private fun LazyListScope.reading(page: PullPage, actions: PullActions) {
    item("description") { DescriptionSection(page.pull.body) }
    item("files") { FilesSection(page.files, page.moreFiles, page.pull.changed_files) { actions.onHost("/files") } }
    item("conversation") { DiscussionSection(page.comments) }
}

private fun LazyListScope.state(page: PullPage, busy: String?, project: String?, threads: List<RemoteChatSummary>, actions: PullActions) {
    item("merge") { MergeGroup(page, busy == "merge", actions.onMerge, Modifier.padding(horizontal = 16.dp)) }
    item("agent") { AgentSection(page, project, busy == "agent", actions.onFixCi, actions.onAddress, actions.onAsk) }
    if (threads.isNotEmpty()) item("threads") { BranchThreads(threads, actions.onThread) }
}

/** Comment, request changes or approve, floating where a thumb reaches them. */
@Composable
private fun ReviewToolbar(busy: String?, actions: PullActions) {
    PageToolbar {
        IconButton(TablerIcons.Outline.MessagePlus, "Comment", actions.onComment, size = ButtonSize.Lg, enabled = busy == null, loading = busy == "comment")
        IconButton(TablerIcons.Outline.MessageX, "Request changes", actions.onChanges, size = ButtonSize.Lg, enabled = busy == null, loading = busy == "changes")
        Button(
            "Approve",
            actions.onApprove,
            size = ButtonSize.Lg,
            leadingIcon = TablerIcons.Outline.Check,
            enabled = busy == null || busy == "approve",
            loading = busy == "approve",
        )
    }
}
