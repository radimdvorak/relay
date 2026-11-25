package dev.relay.android.features.git

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.model.TurnFileChange
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.ChromePage
import dev.relay.android.ui.EmptyState
import dev.relay.android.ui.IconButton
import dev.relay.android.ui.PageToolbar
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.RowTextInset
import dev.relay.android.ui.SectionLabel
import dev.relay.android.ui.Text
import dev.relay.android.ui.listPadding
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowForwardUp
import io.github.lyxnx.compose.ui.tablericons.outline.FileDiff
import io.github.lyxnx.compose.ui.tablericons.outline.GitMerge

/** The files a turn changed, or a worktree's, by folder, with what can be done about all of them floating under. */
@Composable
internal fun TurnPage(
    title: String,
    files: List<TurnFileChange>?,
    /** How many changed, how many were rolled back, or where it landed. */
    note: String,
    error: String?,
    /** The path being rolled back, or "all". */
    busy: String?,
    onBack: (() -> Unit)?,
    onRetry: suspend () -> Unit,
    onOpen: (TurnFileChange) -> Unit,
    /** Rolls one file back, or does it again; null for a worktree's files. */
    onRewind: ((TurnFileChange) -> Unit)?,
    onRollBackAll: () -> Unit,
    onRedoAll: () -> Unit,
    /** The branch a worktree can merge into now. */
    mergeInto: String?,
    onMerge: () -> Unit,
) {
    val rewinds = onRewind != null && !files.isNullOrEmpty()
    val allGone = files?.all { it.revertedBy != null } == true
    val toolbar: (@Composable () -> Unit)? = when {
        mergeInto != null -> {
            {
                PageToolbar {
                    Button("Merge into $mergeInto", onMerge, Modifier.widthIn(max = 280.dp), size = ButtonSize.Lg, leadingIcon = TablerIcons.Outline.GitMerge)
                }
            }
        }
        rewinds -> {
            { RewindAll(allGone, busy, onRollBackAll, onRedoAll) }
        }
        else -> null
    }
    ChromePage(title, subtitle = note.takeIf { files != null }, onBack = onBack, floating = toolbar) { bottom ->
        when {
            files == null && error != null -> LoadFailed(error, onRetry)
            files == null -> Loading()
            files.isEmpty() -> EmptyState("Nothing changed", Modifier.align(Alignment.Center), icon = TablerIcons.Outline.FileDiff)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(top = 12.dp, bottom = bottom)) {
                for ((folder, group) in byFolder(files)) {
                    if (folder.isNotEmpty()) item(key = "dir:$folder") { SectionLabel(folder, count = "${group.size}", overflow = TextOverflow.StartEllipsis) }
                    items(group, key = { it.path }, contentType = { "file" }) { f ->
                        TurnFileRow(f, busy, onOpen = { onOpen(f) }, onRewind = onRewind?.let { { it(f) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun RewindAll(redo: Boolean, busy: String?, onRollBackAll: () -> Unit, onRedoAll: () -> Unit) {
    PageToolbar {
        Button(
            if (redo) "Redo all" else "Roll back all",
            if (redo) onRedoAll else onRollBackAll,
            variant = if (redo) ButtonVariant.Default else ButtonVariant.Destructive,
            size = ButtonSize.Lg,
            leadingIcon = if (redo) TablerIcons.Outline.ArrowForwardUp else TablerIcons.Outline.ArrowBackUp,
            enabled = busy == null || busy == "all",
            loading = busy == "all",
        )
    }
}

@Composable
private fun TurnFileRow(f: TurnFileChange, busy: String?, onOpen: () -> Unit, onRewind: (() -> Unit)?) {
    val c = RelayTheme.colors
    val gone = f.revertedBy != null
    RowPatch(onOpen, minHeight = FileRowHeight, end = if (onRewind != null) 8.dp else RowTextInset) {
        // The section already says the folder.
        FileNames(f.path.substringAfterLast('/'), Modifier.weight(1f), color = if (gone) c.inkSoft else c.ink, struck = gone)
        if (f.binary == true) Text("binary", style = rowMeta, color = c.inkSoft)
        else DiffCounts(f.additions, f.deletions)
        if (onRewind != null) IconButton(
            if (gone) TablerIcons.Outline.ArrowForwardUp else TablerIcons.Outline.ArrowBackUp,
            if (gone) "Redo ${f.path}" else "Roll back ${f.path}",
            onRewind,
            tint = c.inkSoft,
            enabled = busy == null,
            loading = busy == f.path,
        )
    }
}
