package dev.relay.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where a toast shows: under the header, or in the bottom end corner by the thumb. */
enum class ToastEdge { Top, Bottom }

data class Toast(
    val id: Long,
    val message: String,
    val description: String? = null,
    val tone: Tone = Tone.Neutral,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 4000,
    val edge: ToastEdge = ToastEdge.Bottom,
)

/** The app's toasts, newest in front, drawn by a [ToastHost] on each edge. */
@Stable
class ToastState {
    val toasts = mutableStateListOf<Toast>()
    private var next = 0L

    /** Where what asks for nothing goes: up out of the way over a thread, whose end is what's being read. */
    var plainEdge by mutableStateOf(ToastEdge.Bottom)

    private val claims = mutableStateListOf<ToastClearance>()

    /** Room kept free under the stack for what floats at the bottom of the newest screen to claim it. */
    val clearance: Dp get() = claims.lastOrNull()?.height ?: 0.dp

    internal fun claim(one: ToastClearance) {
        claims.add(one)
    }

    internal fun release(one: ToastClearance) {
        claims.remove(one)
    }

    fun show(
        message: String,
        description: String? = null,
        tone: Tone = Tone.Neutral,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = if (actionLabel != null) 6000 else 4000,
    ): Long {
        // Something to tap stays in the thumb's reach.
        val edge = if (actionLabel != null) ToastEdge.Bottom else plainEdge
        val toast = Toast(next++, message, description, tone, actionLabel, onAction, durationMs, edge)
        toasts.add(toast)
        while (toasts.size > MAX_KEPT) toasts.removeAt(0)
        return toast.id
    }

    /** "Settled · Undo": the usual shape for a reversible action. */
    fun undo(message: String, onUndo: () -> Unit): Long = show(message, actionLabel = "Undo", onAction = onUndo)

    fun dismiss(id: Long) {
        toasts.removeAll { it.id == id }
    }

    private companion object { const val MAX_KEPT = 5 }
}

val LocalToasts = staticCompositionLocalOf<ToastState> { error("No ToastHost above this screen") }

/** One screen's room at the bottom. Screens overlap while one slides over another, so each has its own. */
@Stable
internal class ToastClearance {
    var height by mutableStateOf(0.dp)
}

/** Keeps toasts [height] higher while the caller is on screen, so they don't cover it. */
@Composable
fun KeepToastsAbove(height: Dp) {
    val toasts = LocalToasts.current
    val claim = remember { ToastClearance() }
    SideEffect { claim.height = height }
    DisposableEffect(toasts) {
        toasts.claim(claim)
        onDispose { toasts.release(claim) }
    }
}

/** Keeps toasts above what this is put on, however tall it grows. */
@Composable
fun Modifier.keepsToastsAbove(): Modifier {
    val density = LocalDensity.current
    var height by remember { mutableStateOf(0.dp) }
    KeepToastsAbove(height)
    return onSizeChanged { height = with(density) { it.height.toDp() } }
}
