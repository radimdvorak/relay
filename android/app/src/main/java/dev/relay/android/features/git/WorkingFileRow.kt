package dev.relay.android.features.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.model.WorkingChange
import dev.relay.android.ui.ButtonSize
import dev.relay.android.ui.IconButton
import dev.relay.android.ui.RowPatch
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Minus
import io.github.lyxnx.compose.ui.tablericons.outline.Plus

/** How tall a file's row is, so a long list of them stays dense. */
internal val FileRowHeight = 56.dp

/** One changed file: Git's letter for it, its name over its folder, and + or − to stage or unstage it. */
@Composable
internal fun WorkingFileRow(f: WorkingChange, staged: Boolean, busy: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val c = RelayTheme.colors
    val code = if (staged) f.index else f.worktree
    RowPatch(onOpen, minHeight = FileRowHeight, end = 8.dp) {
        Text(
            when {
                f.conflict -> "!"
                code == "?" -> "U"
                else -> code
            },
            Modifier.width(16.dp),
            style = RelayTheme.type.code.copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            color = when (changeKind(code, f.conflict)) {
                ChangeKind.Added -> c.added
                ChangeKind.Deleted, ChangeKind.Conflict -> c.removed
                ChangeKind.Modified -> c.inkSoft
            },
        )
        FileNames(f.path, Modifier.weight(1f))
        IconButton(
            if (staged) TablerIcons.Outline.Minus else TablerIcons.Outline.Plus,
            "${if (staged) "Unstage" else "Stage"} ${f.path}",
            onToggle,
            size = ButtonSize.Default,
            enabled = !busy,
            tint = c.inkSoft,
        )
    }
}

/** A path as rows show it: the file's name, its folder under it cut from the start so the end stays readable. */
@Composable
internal fun FileNames(path: String, modifier: Modifier = Modifier, color: Color = RelayTheme.colors.ink, struck: Boolean = false) {
    val folder = path.substringBeforeLast('/', "")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            path.substringAfterLast('/'),
            style = RelayTheme.type.body.copy(
                lineHeight = RelayTheme.type.row.lineHeight,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        if (folder.isNotEmpty()) Text(
            folder,
            style = rowMeta,
            color = RelayTheme.colors.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
        )
    }
}

/** A section's "Stage all": coloured text at the end of its label. */
@Composable
internal fun ToggleAll(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = RelayTheme.colors
    Text(
        text,
        Modifier.pressable(onClick, enabled = enabled).padding(horizontal = 4.dp, vertical = 8.dp),
        style = rowMeta.copy(fontWeight = FontWeight.Medium),
        color = if (enabled) c.ink else c.inkFaint,
    )
}
