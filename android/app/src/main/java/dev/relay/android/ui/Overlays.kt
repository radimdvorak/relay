package dev.relay.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog as WindowDialog
import androidx.compose.ui.window.DialogProperties
import dev.relay.android.ui.theme.Motion
import dev.relay.android.ui.theme.RelayTheme

/** Android's medium window class: an unfolded Fold or a tablet. */
const val WIDE_BREAKPOINT_DP = 600

@Composable
fun rememberIsWide(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { size.width.toDp().value } >= WIDE_BREAKPOINT_DP
}

/**
 * A bottom sheet: inset from the screen's edges with a ✕ and a centred title ([RelaySheetFrame]),
 * its content scrolling inside. Drag down (or fling) to close. On a wide window it's a centred card
 * instead. [halfFirst] opens a long sheet at half height; scrolling its content pulls it up the rest of the way.
 */
@Composable
fun Sheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    halfFirst: Boolean = false,
    /** Opposite the ✕: one more circle. */
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (rememberIsWide()) {
        CenteredCard(onDismiss, modifier.widthIn(max = 480.dp)) {
            RelaySheetFrame(title, onDismiss, handle = false, action = action) { SheetBody(description, content) }
        }
        return
    }
    InsetModalSheet(onDismiss, halfFirst) {
        RelaySheetFrame(title, onDismiss, modifier, action = action) { SheetBody(description, content) }
    }
}

@Composable
private fun SheetBody(description: String?, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxWidth().fadesCutEnds(scroll, RelayTheme.colors.surface).verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (description != null) Text(description, style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
        content()
    }
}

/** A card centred over a dimmed window, fading in from 96% scale. */
@Composable
fun CenteredCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = RelayTheme.colors
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(Motion.BASE, easing = Motion.EaseOutQuint)) }
    WindowDialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        OverKeyboard {
            Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Column(
                    modifier
                        .graphicsLayer {
                            alpha = enter.value
                            val s = 0.96f + 0.04f * enter.value
                            scaleX = s
                            scaleY = s
                        }
                        .shadow(24.dp, RelayTheme.shapes.sheet, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RelayTheme.shapes.sheet)
                        .background(c.raised),
                    content = content,
                )
            }
        }
    }
}

/** A dialog: raised fill, 28dp corners, a centred-sheet's title, the buttons on the right. */
@Composable
fun Dialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    buttons: @Composable () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    CenteredCard(onDismiss, modifier.widthIn(max = 420.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = RelayTheme.type.sheetTitle)
                if (description != null) Text(description, style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
            }
            content?.invoke(this)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) { buttons() }
        }
    }
}

/** Asks before something that can't be taken back. */
@Composable
fun ConfirmDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    destructive: Boolean = false,
) {
    Dialog(onDismiss, title, description = message, buttons = {
        Button(cancelText, onDismiss, variant = ButtonVariant.Outline)
        Button(
            confirmText,
            {
                onConfirm()
                onDismiss()
            },
            variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Default,
            haptic = if (destructive) Haptic.Warning else null,
        )
    })
}
