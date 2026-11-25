package dev.relay.android.features.git

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.model.GitAction
import dev.relay.android.remote.model.WorkingTree
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.IconButton
import dev.relay.android.ui.PageToolbar
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowDown
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.GitCommit
import io.github.lyxnx.compose.ui.tablericons.outline.Refresh

/** Fetch, pull and push by their icons and counts; committing (or pushing, once all is committed) by name. */
@Composable
internal fun ChangesToolbar(
    tree: WorkingTree,
    state: ChangesState,
    onAct: (GitAction) -> Unit,
    onPush: () -> Unit,
    onCommit: () -> Unit,
) {
    val busy = state.busy != null
    val hasChanges = tree.changes.isNotEmpty()
    PageToolbar {
        IconButton(
            TablerIcons.Outline.Refresh,
            "Fetch",
            { onAct(GitAction.fetch) },
            size = ButtonSize.Lg,
            enabled = !busy,
            loading = state.busy == "fetch",
        )
        if (tree.behind > 0) CountButton(
            TablerIcons.Outline.ArrowDown,
            tree.behind,
            "Pull ${plural(tree.behind, "commit")}",
            enabled = !busy && tree.operation == null,
            loading = state.busy == "pull",
        ) { onAct(GitAction.pull(tree.revision)) }
        if (hasChanges && pushTo(tree) != null) CountButton(
            TablerIcons.Outline.ArrowUp,
            tree.ahead,
            if (tree.ahead > 0) "Push ${plural(tree.ahead, "commit")}" else "Push",
            enabled = canPush(tree, busy),
            loading = state.busy == "push",
            onClick = onPush,
        )
        if (hasChanges) Button(
            "Commit",
            onCommit,
            size = ButtonSize.Lg,
            // Between two counts the label needs the icon's room.
            leadingIcon = TablerIcons.Outline.GitCommit.takeIf { tree.behind == 0 },
            loading = state.busy == "commit" || state.busy == "ship",
            enabled = !busy || state.busy == "commit" || state.busy == "ship",
        ) else Button(
            when {
                state.busy == "push" -> "Pushing…"
                tree.ahead > 0 -> "Push ${tree.ahead}"
                else -> "Push"
            },
            onPush,
            Modifier.widthIn(max = 220.dp),
            size = ButtonSize.Lg,
            leadingIcon = TablerIcons.Outline.ArrowUp,
            loading = state.busy == "push",
            enabled = canPush(tree, busy) || state.busy == "push",
        )
    }
}

/** An arrow and how many commits it would move; just the arrow when there are none to count. */
@Composable
private fun CountButton(icon: ImageVector, count: Int, description: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    if (count == 0) return IconButton(icon, description, onClick, size = ButtonSize.Lg, enabled = enabled, loading = loading)
    Button(
        if (count > 99) "99+" else "$count",
        onClick,
        Modifier.semantics { contentDescription = description }.height(48.dp),
        variant = ButtonVariant.Ghost,
        size = ButtonSize.Default,
        leadingIcon = icon,
        enabled = enabled || loading,
        loading = loading,
    )
}
