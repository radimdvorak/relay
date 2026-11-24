package dev.relay.android.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.BarTab
import dev.relay.android.ui.BottomBar
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.Folders as FoldersFilled
import io.github.lyxnx.compose.ui.tablericons.filled.Messages as MessagesFilled
import io.github.lyxnx.compose.ui.tablericons.outline.Folders
import io.github.lyxnx.compose.ui.tablericons.outline.GitPullRequest
import io.github.lyxnx.compose.ui.tablericons.outline.Messages

enum class HomeTab { Threads, Projects, Pulls }

/** The bar's 56dp and the room around it. */
private val BAR_ROOM = 64.dp

/** Over the composer (or the bar) the list fades out; from there down the dock is solid. */
internal val DOCK_FADE = 28.dp

/** Between the composer and what's under it: the bar, or the keyboard. */
private val COMPOSER_GAP = 8.dp

internal fun homeTabs(threadsNews: Boolean) = listOf(
    BarTab(HomeTab.Threads, TablerIcons.Outline.Messages, "Threads", TablerIcons.Filled.MessagesFilled, dot = threadsNews),
    BarTab(HomeTab.Projects, TablerIcons.Outline.Folders, "Projects", TablerIcons.Filled.FoldersFilled),
    BarTab(HomeTab.Pulls, TablerIcons.Outline.GitPullRequest, "Pull requests"),
)

/**
 * What floats over a home page: the [composer] on the tabs that have one, and the bar of tabs.
 * The bar stays where it is and the keyboard comes up over it; the composer rides up with the
 * keyboard once that's higher than the bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeDock(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    /** False where the side pane carries the destinations. */
    bar: Boolean,
    threadsNews: Boolean,
    /** How much the dock covers above the system's bar, its fade left out. */
    onHeight: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    hasComposer: Boolean,
    composer: @Composable () -> Unit,
) {
    val surface = RelayTheme.colors.surface
    val density = LocalDensity.current
    var composerHeight by remember { mutableStateOf(0.dp) }
    val height = composerHeight + if (bar) BAR_ROOM else 0.dp
    LaunchedEffect(height) { onHeight(height) }
    val under = if (bar) WindowInsets.navigationBars.add(WindowInsets(bottom = BAR_ROOM)) else WindowInsets.navigationBars
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface), endY = with(density) { DOCK_FADE.toPx() }),
            ),
        )
        Column(Modifier.fillMaxWidth().padding(top = DOCK_FADE), horizontalAlignment = Alignment.CenterHorizontally) {
            val motion = RelayTheme.motion.standard
            AnimatedVisibility(
                hasComposer,
                Modifier.onSizeChanged { composerHeight = with(density) { it.height.toDp() } },
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec(), clip = false),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec(), clip = false),
            ) {
                Box(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 15.dp).padding(bottom = COMPOSER_GAP)
                        // This dock already stands on the keyboard.
                        .consumeWindowInsets(WindowInsets.ime.union(WindowInsets.navigationBars)),
                ) { composer() }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime.union(under)))
        }
        if (bar) AnimatedVisibility(
            !WindowInsets.isImeVisible,
            Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(RelayTheme.motion.standard.fastEffectsSpec()),
            exit = fadeOut(RelayTheme.motion.standard.fastEffectsSpec()),
        ) {
            BottomBar(
                homeTabs(threadsNews), tab, onTab,
                Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp).widthIn(max = 480.dp),
            )
        }
    }
}
