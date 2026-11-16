package dev.relay.android.features.sketch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
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

private val dim = Color.White.copy(alpha = 0.35f)

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

        Column(Modifier.fillMaxSize().background(Color.Black)) {
            TopBar(history, saving, onCancel = onCancel, onUndo = { history = history.undo() }, onRedo = { history = history.redo() }, onDone = ::done)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val p = painter
                if (p == null) Spinner(size = 22.dp, color = Color.White)
                else SketchSurface(p, history.strokes, ink, color, onStroke = { history = history.add(it) })
            }
            Tools(ink, color, onInk = { ink = it }, onColor = { color = it })
        }
    }
}

@Composable
private fun TopBar(history: StrokeHistory, saving: Boolean, onCancel: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarIcon(TablerIcons.Outline.X, "Cancel", onClick = onCancel)
        Spacer(Modifier.weight(1f))
        BarIcon(TablerIcons.Outline.ArrowBackUp, "Undo", enabled = history.canUndo, onClick = onUndo)
        BarIcon(TablerIcons.Outline.ArrowForwardUp, "Redo", enabled = history.canRedo, onClick = onRedo)
        Box(
            Modifier.padding(start = 8.dp).height(36.dp).background(Color.White, RoundedCornerShape(18.dp))
                .pressable(onClick = onDone, enabled = !saving).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) Spinner(size = 16.dp, color = Color.Black)
            else Text("Done", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color.Black)
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).pressable(onClick = onClick, enabled = enabled).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, size = 22.dp, tint = if (enabled) Color.White else dim)
    }
}

@Composable
private fun Tools(ink: Ink, color: Int, onInk: (Ink) -> Unit, onColor: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kept while blurring too, so the bar doesn't jump between tools.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            penColors.forEach { swatch ->
                val picked = ink == Ink.Pen && swatch == color
                Box(
                    Modifier.size(28.dp)
                        .border(if (picked) 2.dp else 1.dp, if (picked) Color.White else dim, CircleShape)
                        .padding(if (picked) 4.dp else 2.dp)
                        .background(Color(swatch), CircleShape)
                        .pressable(onClick = {
                            onColor(swatch)
                            onInk(Ink.Pen)
                        }, role = Role.RadioButton)
                        .semantics {
                            contentDescription = "Pen colour"
                            selected = picked
                        },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tool(TablerIcons.Outline.Pencil, "Pen", ink == Ink.Pen) { onInk(Ink.Pen) }
            Tool(TablerIcons.Outline.Blur, "Blur", ink == Ink.Blur) { onInk(Ink.Blur) }
            Tool(TablerIcons.Outline.EyeOff, "Black out", ink == Ink.Redact) { onInk(Ink.Redact) }
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, picked: Boolean, onClick: () -> Unit) {
    val tint = if (picked) Color.Black else Color.White
    Row(
        Modifier.height(36.dp)
            .background(if (picked) Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .pressable(onClick = onClick, role = Role.RadioButton)
            .semantics { selected = picked }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, size = 18.dp, tint = tint)
        Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = tint)
    }
}

private fun decode(dataUrl: String): Bitmap? = runCatching {
    val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
