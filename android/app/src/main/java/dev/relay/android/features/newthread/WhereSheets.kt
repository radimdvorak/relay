package dev.relay.android.features.newthread

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.relay.android.features.home.computerLine
import dev.relay.android.remote.model.RemoteProject
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListGroup
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.Folder
import io.github.lyxnx.compose.ui.tablericons.outline.GitBranch
import io.github.lyxnx.compose.ui.tablericons.outline.Notebook

enum class WhereSheet { Project, Workspace }

internal class WorkspaceOption(val value: String, val label: String, val hint: String) {
    val icon get() = if (value == "worktree") TablerIcons.Outline.GitBranch else TablerIcons.Outline.Folder
}

private val workspaceOptions = listOf(
    WorkspaceOption("checkout", "Project folder", "Works in the checkout, on whatever branch it has."),
    WorkspaceOption(
        "worktree", "Its own worktree",
        "A branch and folder of its own, made from the checkout with the first message. Your checkout stays as it is.",
    ),
)
internal const val SCRATCH_HINT = "A chat of its own, outside your projects"

internal fun workspaceOption(workspace: String): WorkspaceOption? = workspaceOptions.find { it.value == workspace }

/** Its sidebar folder and, for one on another computer, which: "Work · On thinkcenter". */
internal fun projectPlace(project: RemoteProject?): String? =
    listOfNotNull(project?.folder?.takeIf(String::isNotEmpty), computerLine(project?.computer))
        .joinToString(" · ").ifEmpty { null }

/** The project list, or the workspace choice. */
@Composable
fun WhereSheets(
    open: WhereSheet?,
    projects: List<RemoteProject>,
    picked: Where?,
    workspace: String,
    onPick: (Where) -> Unit,
    onWorkspace: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = RelayTheme.colors
    val check: @Composable (Boolean) -> (@Composable () -> Unit)? = { on ->
        if (on) ({ Icon(TablerIcons.Outline.Check, null, tint = c.accent, size = 20.dp) }) else null
    }
    when (open) {
        WhereSheet.Project -> Sheet(onClose, title = "Start in") {
            ListGroup {
                ListRow(
                    "Scratchpad", subtitle = SCRATCH_HINT, selected = picked == SCRATCH,
                    leadingIcon = TablerIcons.Outline.Notebook,
                    trailing = check(picked == SCRATCH),
                    onClick = { onClose(); onPick(SCRATCH) },
                )
                projects.forEach { p ->
                    ListRow(
                        p.name, subtitle = projectPlace(p), selected = p.id == picked,
                        leading = { LocalProjectBadge.current(p, 20.dp) },
                        trailing = check(p.id == picked),
                        onClick = { onClose(); onPick(p.id) },
                    )
                }
            }
        }
        WhereSheet.Workspace -> Sheet(onClose, title = "Where it works") {
            ListGroup {
                workspaceOptions.forEach { option ->
                    ListRow(
                        option.label, subtitle = option.hint, selected = option.value == workspace, subtitleLines = 3,
                        leadingIcon = option.icon,
                        trailing = check(option.value == workspace),
                        onClick = { onClose(); onWorkspace(option.value) },
                    )
                }
            }
        }
        null -> {}
    }
}
