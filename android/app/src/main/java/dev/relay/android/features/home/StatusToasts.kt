package dev.relay.android.features.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.relay.android.lib.navigation.LocalNavigator
import dev.relay.android.lib.navigation.Route
import dev.relay.android.remote.Remote
import dev.relay.android.remote.model.RemoteEvent
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.StatusToast
import dev.relay.android.ui.ToastEdge
import dev.relay.android.ui.origin
import dev.relay.android.ui.swipesAway
import dev.relay.android.ui.pressable
import dev.relay.android.ui.rememberHaptics
import dev.relay.android.ui.rememberInFront
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A question stays long enough to read and reach; the rest only to be noticed. */
private const val QUESTION_MS = 10_000L
private const val NOTICE_MS = 3_500L

/** How long after coming to the front a list may still bring what notifications already told. */
private const val CATCH_UP_MS = 5_000L

/**
 * Says over whatever is open that another thread finished, failed or needs you; a tap opens it.
 * Drawn at the top, over what's read least: a thread's end and its composer are at the bottom.
 */
@Composable
fun StatusToasts(
    remote: Remote,
    /** The thread on screen, which needs no telling. */
    openId: String?,
    /** Nowhere to say it: pairing, a crash page. */
    hidden: Boolean,
    /** The thread list is on screen and says it already. */
    listed: Boolean,
    /** Beside the list: open it as the whole pane. */
    pane: Boolean,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val overview by remote.overview.collectAsState()
    val inFront by rememberInFront()
    val queue = rememberStatusNews(remote, openId, inFront)
    LaunchedEffect(listed, queue.news) {
        if (listed) queue.clear()
    }
    val worthSaying = stillNews(queue.news, overview?.listed.orEmpty(), openId)
    val shown = if (inFront && !hidden && !listed) worthSaying.firstOrNull() else null

    LaunchedEffect(shown) {
        if (shown == null) return@LaunchedEffect
        val question = shown.kind == StatusNews.Kind.NeedsYou
        if (question) haptics.perform(Haptic.Warning)
        delay(if (question) QUESTION_MS else NOTICE_MS)
        queue.drop(shown)
    }
    StatusToastHost(
        shown, modifier,
        onOpen = { news ->
            queue.drop(news)
            val thread = Route.Thread(news.chatId)
            if (pane) navigator.openInPane(thread) else navigator.push(thread)
        },
        onDismiss = queue::drop,
    )
}

/** The news waiting to be shown, first first. */
private class StatusQueue {
    var news by mutableStateOf(emptyList<StatusNews>())
        private set

    fun add(more: List<StatusNews>) {
        if (more.isNotEmpty()) news = withNews(news, more)
    }

    fun drop(one: StatusNews) {
        news = news - one
    }

    fun clear() {
        news = emptyList()
    }
}

@Composable
private fun rememberStatusNews(remote: Remote, openId: String?, inFront: Boolean): StatusQueue {
    val feed = remember(remote) { StatusNewsFeed() }
    val queue = remember(remote) { StatusQueue() }
    val open by rememberUpdatedState(openId)
    var caughtUp by remember { mutableStateOf(false) }
    LaunchedEffect(inFront) {
        caughtUp = false
        if (!inFront) return@LaunchedEffect
        delay(CATCH_UP_MS)
        caughtUp = true
    }
    LaunchedEffect(remote) {
        launch {
            remote.events.collect { event ->
                if (event is RemoteEvent.Message) feed.heard(event.chatId, event.message)
            }
        }
        remote.overviews.collect { overview ->
            if (overview == null) return@collect
            queue.add(feed.listed(remote.active.value, overview.listed, open, catchingUp = !caughtUp))
        }
    }
    return queue
}

/** One toast at a time under the header: [shown]'s, arriving, giving way to the next, and leaving. */
@Composable
internal fun StatusToastHost(
    shown: StatusNews?,
    modifier: Modifier = Modifier,
    onOpen: (StatusNews) -> Unit,
    onDismiss: (StatusNews) -> Unit,
) {
    val arrive = scaleIn(RelayTheme.motion.hero.fastSpatialSpec(), 0.85f, ToastEdge.Top.origin()) +
        slideInVertically(RelayTheme.motion.hero.fastSpatialSpec()) { -it / 4 } +
        fadeIn(RelayTheme.motion.hero.fastEffectsSpec())
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            shown,
            transitionSpec = { ContentTransform(arrive, fadeOut(tween(100)), sizeTransform = null) },
            contentAlignment = Alignment.TopCenter,
            label = "status-toast",
        ) { news ->
            if (news != null) NewsToast(news, { onOpen(news) }, { onDismiss(news) })
        }
    }
}

@Composable
private fun NewsToast(news: StatusNews, onOpen: () -> Unit, onDismiss: () -> Unit) = StatusToast(
    thread = news.title,
    status = news.kind.label,
    icon = TablerIcons.Filled.Message,
    statusColor = news.kind.color(RelayTheme.colors),
    modifier = Modifier
        .padding(bottom = 8.dp)
        .swipesAway(ToastEdge.Top, onDismiss = onDismiss)
        .semantics(mergeDescendants = true) {
            contentDescription = "${news.title}: ${news.kind.label}. Open it."
            liveRegion = LiveRegionMode.Polite
            customActions = listOf(CustomAccessibilityAction("Dismiss") { onDismiss(); true })
        }
        .pressable(onOpen),
)

private fun StatusNews.Kind.color(c: RelayColors): Color = when (this) {
    StatusNews.Kind.NeedsYou -> c.waiting
    StatusNews.Kind.Finished -> c.ink
    StatusNews.Kind.Failed -> c.danger
    StatusNews.Kind.UsageLimit -> c.warning
}
