package dev.relay.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowLeft

/** How much room a page's floating part takes at the bottom, the gap under it included. */
private val FloatingRoom = 80.dp

/**
 * A page opened from somewhere else: back, a centred title with an optional soft line, and
 * [actions] (raised circles) on the chrome; the content on its sheet. [content] gets the room to
 * leave under its last row: the system bar's, plus [floating]'s (a [PageToolbar]) when there is one.
 */
@Composable
fun ChromePage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floating: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.(bottom: Dp) -> Unit,
) {
    val sides = LocalScreenInsets.current
    val bar = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    Column(
        modifier.fillMaxSize().background(RelayTheme.colors.chrome)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(sides).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        PageHeader(title, subtitle, onBack, actions)
        LocalScreenNotice.current?.invoke()
        ContentSheet(Modifier.weight(1f)) {
            content(bar + if (floating != null) FloatingRoom + 16.dp else 24.dp)
            Floating(floating, bar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** What floats over the end of the page, on a fade of the surface so rows run out under it; it rises in once it's there. */
@Composable
private fun Floating(content: (@Composable () -> Unit)?, bar: Dp, modifier: Modifier) {
    val surface = RelayTheme.colors.surface
    AnimatedVisibility(
        content != null,
        modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 },
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(0f to surface.copy(alpha = 0f), 0.4f to surface.copy(alpha = 0.94f), 0.6f to surface))
                .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = bar + 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) { content?.invoke() }
    }
}

/** The page's header: the title stays in the middle of the band and gives way to whichever side is wider. */
@Composable
private fun PageHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Layout(
        contents = listOf(
            {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LocalPaneToggle.current?.invoke()
                    if (onBack != null) CircleButton(TablerIcons.Outline.ArrowLeft, "Back", onBack, tone = CircleTone.Raised)
                }
            },
            { PageTitle(title, subtitle) },
            { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions) },
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 44.dp),
    ) { (leading, middle, trailing), constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val start = leading.first().measure(loose)
        val end = trailing.first().measure(loose)
        val side = maxOf(start.width, end.width) + 12.dp.roundToPx()
        val text = middle.first().measure(Constraints(maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)))
        val height = maxOf(constraints.minHeight, start.height, text.height, end.height)
        layout(constraints.maxWidth, height) {
            start.place(0, (height - start.height) / 2)
            text.place((constraints.maxWidth - text.width) / 2, (height - text.height) / 2)
            end.place(constraints.maxWidth - end.width, (height - end.height) / 2)
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = RelayTheme.type.body.copy(fontWeight = FontWeight.Medium, lineHeight = RelayTheme.type.row.lineHeight),
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) Text(
            subtitle,
            style = RelayTheme.type.small,
            color = RelayTheme.colors.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The page's own actions, floating over its end: quiet ones as [CircleButton]s with no fill, the
 * one the page is for as a labelled [Button] last.
 */
@Composable
fun PageToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = RelayTheme.colors
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = c.raised,
            toolbarContentColor = c.ink,
        ),
        contentPadding = PaddingValues(8.dp),
        expandedShadowElevation = 6.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
