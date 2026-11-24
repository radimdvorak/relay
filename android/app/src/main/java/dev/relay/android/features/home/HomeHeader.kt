package dev.relay.android.features.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.relay.android.lib.navigation.LocalOpenDrawer
import dev.relay.android.ui.ChromeHeader
import dev.relay.android.ui.CircleAction
import dev.relay.android.ui.CircleButton
import dev.relay.android.ui.CirclePair
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.LocalPaneToggle
import dev.relay.android.ui.MenuIcon
import dev.relay.android.ui.Text
import dev.relay.android.ui.fill
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Edit
import io.github.lyxnx.compose.ui.tablericons.outline.Search

/** What the circles at the header's end take: the pair, the filter beside it, and the gap before the name. */
private val END_ROOM = 152.dp

internal val HomeTab.title: String? get() = when (this) {
    HomeTab.Threads -> null
    HomeTab.Projects -> "Projects"
    HomeTab.Pulls -> "Pull requests"
}

/**
 * Home's chrome: the drawer's circle (or the side pane's toggle), the computer's name to switch
 * it, the filter and the search | new-thread pair. Projects and pull requests put their title
 * large on a line of its own; Threads goes without.
 */
@Composable
internal fun HomeHeader(
    tab: HomeTab,
    onSearch: () -> Unit,
    onNewThread: () -> Unit,
    computer: @Composable () -> Unit,
    hasFilter: Boolean,
    filter: @Composable () -> Unit,
    /** False beside the side pane, which has both. */
    showPair: Boolean = true,
) {
    val motion = RelayTheme.motion.standard
    Column(Modifier.fillMaxWidth().background(RelayTheme.colors.chrome)) {
        ChromeHeader(
            leading = {
                Row(
                    Modifier.padding(end = END_ROOM),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val openDrawer = LocalOpenDrawer.current
                    if (openDrawer != null) CircleButton(MenuIcon, "Threads menu", openDrawer, tone = CircleTone.Raised)
                    LocalPaneToggle.current?.invoke()
                    computer()
                }
            },
            trailing = {
                AnimatedVisibility(hasFilter, enter = fadeIn(motion.defaultEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
                    Box(
                        Modifier.height(44.dp).widthIn(min = 44.dp).clip(RelayTheme.shapes.circle).background(CircleTone.Raised.fill(RelayTheme.colors)),
                        contentAlignment = Alignment.Center,
                    ) { filter() }
                }
                if (showPair) CirclePair(
                    CircleAction(TablerIcons.Outline.Search, "Search", onSearch),
                    CircleAction(TablerIcons.Outline.Edit, "New thread", onNewThread),
                )
            },
        )
        AnimatedVisibility(
            tab.title != null,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            val effects = motion.defaultEffectsSpec<Float>()
            AnimatedContent(tab, transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) }, label = "home-title") { shown ->
                Text(
                    shown.title.orEmpty(),
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    style = RelayTheme.type.title,
                    maxLines = 1,
                )
            }
        }
    }
}
