package dev.relay.android.features.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text as MaterialText
import dev.relay.android.remote.model.TurnFileChange
import dev.relay.android.ui.ConfirmDialog
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowForwardUp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Rolls a turn's files back or forward; answers the files later edits overlap, which stop it. */
typealias Rewind = suspend (paths: List<String>?, mode: String, force: Boolean) -> List<String>

/** The files a turn changed, each opening its diff, over rolling them all back. */
@Composable
fun ChangedFiles(
    files: List<TurnFileChange>,
    onOpen: (TurnFileChange) -> Unit,
    onOpenAll: (() -> Unit)?,
    onRewind: Rewind?,
    failures: FailureState,
) {
    val c = RelayTheme.colors
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var overlap by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val add = files.sumOf { it.additions }
    val del = files.sumOf { it.deletions }
    val reverted = files.count { it.revertedBy != null }
    val redo = reverted == files.size
    // The desktop's ChangedFilesCard: confirm a rollback of several files, and
    // offer to overwrite when later edits overlap it.
    fun rewind(mode: String, force: Boolean = false) {
        val job = onRewind ?: return
        working = true
        scope.launch {
            try {
                val conflicts = job(null, mode, force)
                if (conflicts.isNotEmpty()) overlap = mode to conflicts
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.show("Couldn't roll back", e.message ?: e.toString())
            } finally {
                working = false
            }
        }
    }
    QuietCard(padding = 0.dp, gap = 0.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (files.size == 1) "1 file changed" else "${files.size} files changed",
                Modifier.weight(1f).then(if (onOpenAll != null) Modifier.pressable(onClick = onOpenAll, scale = false) else Modifier),
                style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium),
            )
            Counts(add, del, Modifier.padding(end = 8.dp))
            if (onRewind != null) Row(
                Modifier.alpha(if (working) 0.5f else 1f).pressable(
                    onClick = {
                        when {
                            redo -> rewind("redo")
                            files.size - reverted > 1 -> confirm = true
                            else -> rewind("revert")
                        }
                    },
                    enabled = !working,
                    scale = false,
                ).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(if (redo) TablerIcons.Outline.ArrowForwardUp else TablerIcons.Outline.ArrowBackUp, null, size = 16.dp, tint = c.inkSoft)
                Text(if (redo) "Redo" else "Roll back", style = RelayTheme.type.meta, color = c.inkSoft)
            }
        }
        for (f in files) {
            val slash = f.path.lastIndexOf('/')
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).pressable(onClick = { onOpen(f) }, scale = false).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MaterialText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.ink, textDecoration = if (f.revertedBy != null) TextDecoration.LineThrough else null)) {
                            append(f.path.substring(slash + 1))
                        }
                        if (slash > 0) withStyle(SpanStyle(color = c.inkFaint)) { append("  ${f.path.substring(0, slash)}") }
                    },
                    Modifier.weight(1f),
                    style = traceCode(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (f.binary == true) Text("binary", style = traceCode(), color = c.inkFaint) else Counts(f.additions, f.deletions)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (confirm) ConfirmDialog(
        title = "Roll back ${files.size - reverted} files?",
        message = "They go back to how they were before this turn. Later edits are kept where they merge.",
        confirmText = "Roll back",
        destructive = true,
        onConfirm = { confirm = false; rewind("revert") },
        onDismiss = { confirm = false },
    )
    overlap?.let { (mode, conflicts) ->
        val who = if (conflicts.size == 1) "${conflicts[0].substringAfterLast('/')} was" else "${conflicts.size} files were"
        ConfirmDialog(
            title = "Nothing was changed",
            message = "$who edited after ${if (mode == "revert") "this turn" else "the rollback"} in ways that overlap it.",
            confirmText = "Overwrite anyway",
            destructive = true,
            onConfirm = { overlap = null; rewind(mode, force = true) },
            onDismiss = { overlap = null },
        )
    }
}

@Composable
private fun Counts(add: Int, del: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("+$add", style = traceCode(), color = RelayTheme.colors.added)
        Text("−$del", style = traceCode(), color = RelayTheme.colors.removed)
    }
}
