package dev.relay.android.ui

import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** The fields a window over the app has, each of which needs the keyboard. */
internal class WindowFields {
    var count by mutableIntStateOf(0)
}

internal val LocalWindowFields = staticCompositionLocalOf<WindowFields?> { null }

/** Said by a text field, so a sheet it's in takes the keyboard. */
@Composable
internal fun TakesKeyboard() {
    val fields = LocalWindowFields.current ?: return
    DisposableEffect(fields) {
        fields.count++
        onDispose { fields.count-- }
    }
}

/**
 * The content of a window over the app (a sheet, a card), which covers the keyboard while it has no
 * field of its own. Taking it instead would drop the page's keyboard room in a single frame, with
 * the keyboard leaving after and coming back once the window closes: everything behind jumps twice.
 */
@Composable
internal fun OverKeyboard(content: @Composable () -> Unit) {
    val fields = remember { WindowFields() }
    CompositionLocalProvider(LocalWindowFields provides fields, content = content)
    val window = LocalView.current.dialogWindow()
    val covers = fields.count == 0
    DisposableEffect(window, covers) {
        // Focusable, but not for the keyboard: the one below keeps it, under this window.
        if (covers) window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        onDispose { }
    }
}

private fun View.dialogWindow() =
    generateSequence(parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
