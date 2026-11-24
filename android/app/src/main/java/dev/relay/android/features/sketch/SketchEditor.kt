package dev.relay.android.features.sketch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayPaperDark
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowBackUp
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowForwardUp
import io.github.lyxnx.compose.ui.tablericons.outline.Blur
import io.github.lyxnx.compose.ui.tablericons.outline.EyeOff
import io.github.lyxnx.compose.ui.tablericons.outline.Pencil
import io.github.lyxnx.compose.ui.tablericons.outline.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Draw over a photo going with the message: pen, blur, or black it out, with
 * undo. Done hands back the photo with the ink burned in, or null once every
 * stroke is gone, with the strokes so a second edit can take them back.
 */
@Composable
fun SketchEditor(
    source: String,
    strokes: List<Stroke>,
    onCancel: () -> Unit,
    onDone: (strokes: List<Stroke>, burned: Bitmap?) -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val painter by produceState<SketchPainter?>(null, source) {
            value = withContext(Dispatchers.Default) { decode(source)?.let(::SketchPainter) }
        }
        var history by remember { mutableStateOf(StrokeHistory(strokes)) }
        var ink by remember { mutableStateOf(Ink.Pen) }
        var color by remember { mutableIntStateOf(penColors.first()) }
        var saving by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        BackHandler(onBack = onCancel)

        fun done() {
            val p = painter ?: return
            if (saving) return
            val kept = history.strokes
            if (kept == strokes) return onCancel()
            if (kept.isEmpty()) return onDone(kept, null)
            saving = true
            scope.launch {
                val burned = withContext(Dispatchers.Default) { p.burn(kept) }
                onDone(kept, burned)
            }
        }

        // A photo is judged on a dark ground whatever the theme; a dark theme keeps its own.
        val theme = RelayTheme.colors
        RelayTheme(if (theme.isDark) theme else RelayPaperDark) {
            SketchScreen(
                history, ink, color, saving,
                onCancel = onCancel, onDone = ::done,
                onUndo = { history = history.undo() }, onRedo = { history = history.redo() },
                onInk = { ink = it }, onColor = { color = it },
            ) {
                val p = painter
                if (p == null) Spinner(size = 22.dp)
                else SketchSurface(p, history.strokes, ink, color, onStroke = { history = history.add(it) })
            }
        }
    }
}

/** The editor's chrome around the [photo]: circles over it, the pens in a floating card under it. */
@Composable
internal fun SketchScreen(
    history: StrokeHistory,
    ink: Ink,
    color: Int,
    saving: Boolean,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInk: (Ink) -> Unit,
    onColor: (Int) -> Unit,
    photo: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(RelayTheme.colors.surface)) {
        TopBar(history, saving, onCancel, onUndo, onRedo, onDone)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { photo() }
        Tools(ink, color, onInk, onColor)
    }
}

@Composable
private fun TopBar(history: StrokeHistory, saving: Boolean, onCancel: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleButton(TablerIcons.Outline.X, "Cancel", onCancel)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            CircleButton(TablerIcons.Outline.ArrowBackUp, "Undo", onUndo, enabled = history.canUndo)
            CircleButton(TablerIcons.Outline.ArrowForwardUp, "Redo", onRedo, enabled = history.canRedo)
        }
        val c = RelayTheme.colors
        Box(
            Modifier.heightIn(min = 44.dp).pressable(onClick = onDone, enabled = !saving)
                .clip(RelayTheme.shapes.circle).background(c.strong).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) Spinner(size = 18.dp, color = c.onStrong)
            else Text("Done", style = RelayTheme.type.row.copy(fontWeight = FontWeight.Medium), color = c.onStrong)
        }
    }
}

@Composable
private fun Tools(ink: Ink, color: Int, onInk: (Ink) -> Unit, onColor: (Int) -> Unit) {
    val c = RelayTheme.colors
    Column(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 15.dp, vertical = 12.dp)
            .clip(RelayTheme.shapes.composer).background(c.raised).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kept while blurring too, so the bar doesn't jump between tools.
        Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            penColors.forEach { swatch -> Swatch(swatch, ink == Ink.Pen && swatch == color) {
                onColor(swatch)
                onInk(Ink.Pen)
            } }
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tool(TablerIcons.Outline.Pencil, "Pen", ink == Ink.Pen) { onInk(Ink.Pen) }
            Tool(TablerIcons.Outline.Blur, "Blur", ink == Ink.Blur) { onInk(Ink.Blur) }
            Tool(TablerIcons.Outline.EyeOff, "Black out", ink == Ink.Redact) { onInk(Ink.Redact) }
        }
    }
}

/** A pen colour: the picked one stands off in a ring. */
@Composable
private fun Swatch(swatch: Int, picked: Boolean, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val ring by animateDpAsState(if (picked) 5.dp else 2.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "swatch")
    Box(
        Modifier.pressable(onClick = onClick, role = Role.RadioButton).size(44.dp)
            .semantics {
                contentDescription = "Pen colour"
                selected = picked
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).background(if (picked) c.ink else c.selected, RelayTheme.shapes.circle)
                .padding(if (picked) 2.dp else 0.dp).background(c.raised, RelayTheme.shapes.circle)
                .padding(ring - 2.dp).background(Color(swatch), RelayTheme.shapes.circle),
        )
    }
}

@Composable
private fun RowScope.Tool(icon: ImageVector, label: String, picked: Boolean, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val tint = if (picked) c.ink else c.inkSoft
    Row(
        Modifier.weight(1f).pressable(onClick = onClick, role = Role.RadioButton)
            .clip(RelayTheme.shapes.circle).background(if (picked) c.tool else Color.Transparent)
            .heightIn(min = 44.dp)
            .semantics { selected = picked }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, size = 18.dp, tint = tint)
        Text(label, style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium), color = tint, maxLines = 1)
    }
}

private fun decode(dataUrl: String): Bitmap? = runCatching {
    val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
