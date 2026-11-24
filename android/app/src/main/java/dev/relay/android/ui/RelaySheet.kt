package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft
import io.github.lyxnx.compose.ui.tablericons.outline.X
import dev.relay.android.ui.theme.RelayTheme

/** How far an inset sheet stays from the screen's sides and bottom. */
private val SheetInset = 8.dp

/**
 * A bottom sheet in the brief's style: inset from the edges, 28dp corners all round, a ✕ at the
 * top-left (a ← when [onBack] is set, for a sheet opened from another) and a centred title.
 */
@Composable
fun RelaySheet(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    InsetModalSheet(onDismiss, halfFirst = false) {
        RelaySheetFrame(title, onClose = onBack ?: onDismiss, back = onBack != null, modifier = modifier, content = content)
    }
}

/** The sheet itself, without the window it opens in: what [RelaySheet] shows, and what shots draw. */
@Composable
fun RelaySheetFrame(
    title: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    back: Boolean = false,
    handle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = RelayTheme.colors
    Column(modifier.fillMaxWidth().clip(RelayTheme.shapes.sheet).background(c.raised)) {
        if (handle) Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                .size(36.dp, 4.dp).background(c.line, RelayTheme.shapes.circle),
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = if (handle) 4.dp else 16.dp).height(56.dp)) {
            CircleButton(
                if (back) TablerIcons.Outline.ArrowLeft else TablerIcons.Outline.X,
                if (back) "Back" else "Close",
                onClose,
                Modifier.align(Alignment.CenterStart),
            )
            if (title != null) Text(
                title,
                Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                style = RelayTheme.type.sheetTitle,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        content()
    }
}

/**
 * Material's modal sheet with its own surface switched off, so the frame inside can float inset.
 * [halfFirst] opens a long sheet at half height; scrolling its content pulls it up the rest of the way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InsetModalSheet(onDismiss: () -> Unit, halfFirst: Boolean, frame: @Composable () -> Unit) {
    val c = RelayTheme.colors
    // Its replacement, rememberBottomSheetState, stops a halfFirst sheet at half its content
    // rather than half the screen; kept until a screen decides where that sheet stops.
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !halfFirst)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        contentColor = c.ink,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (c.isDark) 0.5f else 0.16f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        // Held short of the top: a sheet whose content reached it bounced about a dozen times a second
        // there (the notes sheet with a long table), while one that stops short sat still. Measured
        // against the room between the bars, which the sheet adds outside this cap: 88% of the whole
        // window plus a 3-button nav bar still reached the top, and the bouncing sheet took drags from the list.
        val windowHeight = LocalWindowInfo.current.containerSize.height
        val bars = WindowInsets.safeDrawing
        val room = with(LocalDensity.current) { (windowHeight - bars.getTop(this) - bars.getBottom(this)).toDp() }
        Box(
            Modifier
                .heightIn(max = room * SHEET_MAX)
                .navigationBarsPadding()
                .padding(start = SheetInset, end = SheetInset, bottom = SheetInset)
                .nestedScroll(KeepUpFlingsOffSheet),
        ) { frame() }
    }
}

/** How much of the room between the system bars a bottom sheet's content may take. */
private const val SHEET_MAX = 0.88f

/**
 * Overscrolling the end of a sheet's list handed the leftover upward fling to the sheet, which settled
 * with it and sprang past its top for a frame. The sheet only rises on a drag, before the list scrolls,
 * so it never needs these.
 */
private val KeepUpFlingsOffSheet = object : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
        if (available.y < 0f) available else Velocity.Zero
}
