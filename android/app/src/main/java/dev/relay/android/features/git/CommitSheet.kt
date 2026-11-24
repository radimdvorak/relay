package dev.relay.android.features.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.model.WorkingTree
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Text
import dev.relay.android.ui.TextField
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Sparkles

/** The commit's message, written for you or typed, and what to commit with it. */
@Composable
internal fun CommitSheet(
    tree: WorkingTree,
    state: ChangesState,
    onDismiss: () -> Unit,
    onType: (String) -> Unit,
    onWrite: (paths: List<String>) -> Unit,
    onCommit: (paths: List<String>?, push: Boolean) -> Unit,
) {
    Sheet(onDismiss, title = "Commit") { CommitBody(tree, state, onType, onWrite, onCommit) }
}

@Suppress("UnusedReceiverParameter")
@Composable
internal fun ColumnScope.CommitBody(
    tree: WorkingTree,
    state: ChangesState,
    onType: (String) -> Unit,
    onWrite: (paths: List<String>) -> Unit,
    /** Null paths commit what's staged. */
    onCommit: (paths: List<String>?, push: Boolean) -> Unit,
) {
    val c = RelayTheme.colors
    val staged = tree.changes.filter { it.staged }
    val working = tree.changes.filter { it.unstaged }
    val allPaths = everyPath(tree)
    val stop = blocker(tree)
    val draft = state.draft
    // Committing while a message is on its way would go without it, unless you've typed your own.
    val ready = state.busy == null && stop == null && draft.text.isNotBlank() && !(state.writing && !draft.typed)
    val canShip = ready && allPaths.isNotEmpty() && allPaths.size <= MAX_COMMIT_FILES
    val canCommitStaged = ready && staged.isNotEmpty()
    // With some of it staged, the message describes that part.
    val partial = staged.isNotEmpty() && working.isNotEmpty()
    val writeFor = if (partial) staged.map { it.path } else allPaths
    val canWrite = !state.writing && state.busy == null && writeFor.isNotEmpty() && writeFor.size <= MAX_COMMIT_FILES
    val shipTo = tree.pushTarget

    TextField(
        draft.text,
        onType,
        placeholder = if (state.writing && !draft.typed) "Writing a message…" else "Commit message",
        singleLine = false,
        minLines = 3,
        maxLines = 8,
        // The model cuts it there anyway; the counter only matters close to it.
        maxLength = MAX_COMMIT_MESSAGE.takeIf { draft.text.length > it - 1_000 },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(commitHint(tree), Modifier.weight(1f), style = rowMeta, color = if (stop != null) c.danger else c.inkSoft, maxLines = 2)
        Button(
            when {
                state.writing -> "Writing…"
                partial -> "Write for staged"
                else -> "Write it for me"
            },
            { onWrite(writeFor) },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Sm,
            leadingIcon = TablerIcons.Outline.Sparkles,
            enabled = canWrite,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            if (shipTo != null) "Commit & push" else "Commit",
            { onCommit(allPaths, shipTo != null) },
            Modifier.fillMaxWidth(),
            size = ButtonSize.Lg,
            enabled = canShip,
        )
        if (staged.isNotEmpty()) Button(
            "Commit staged",
            { onCommit(null, false) },
            Modifier.fillMaxWidth(),
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Lg,
            enabled = canCommitStaged,
        )
    }
}
