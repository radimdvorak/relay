package dev.relay.android.features.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.relay.android.ui.LocalToasts
import dev.relay.android.ui.StatusToast
import dev.relay.android.ui.pressable
import dev.relay.android.ui.rememberHaptics
import dev.relay.android.ui.rememberInFront
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A question stays long enough to read and reach; the rest only to be noticed. */
private const val QUESTION_MS = 10_000L
private const val NOTICE_MS = 3_500L

/** How long after coming to the front a list may still bring what notifications already told. */
private const val CATCH_UP_MS = 5_000L

/**
 * Says over whatever is open that another thread finished, failed or needs you; a tap opens it.
 * Draws at the bottom end of [modifier]'s box, above what floats there ([LocalToasts]' clearance).
 */
@Composable
fun StatusToasts(
    remote: Remote,
    /** The thread on screen, which needs no telling. */
    openId: String?,
    /** Nowhere to say it: pairing, a crash page. */
    hidden: Boolean,
    /** Beside the list: open it as the whole pane. */
    pane: Boolean,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val overview by remote.overview.collectAsState()
    val inFront by rememberInFront()
    val queue = rememberStatusNews(remote, openId, inFront)
    val worthSaying = stillNews(queue.news, overview?.listed.orEmpty(), openId)
    val shown = if (inFront && !hidden) worthSaying.firstOrNull() else null

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

/** One toast at a time at the bottom end: [shown]'s, arriving, giving way to the next, and leaving. */
@Composable
internal fun StatusToastHost(
    shown: StatusNews?,
    modifier: Modifier = Modifier,
    onOpen: (StatusNews) -> Unit,
    onDismiss: (StatusNews) -> Unit,
) {
    val toasts = LocalToasts.current
    // Out of the way of a message toast ("Settled · Undo"), which spans the same edge.
    val over = if (toasts.toasts.isEmpty()) 0.dp else 76.dp
    val lift by animateDpAsState(toasts.clearance + over, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "status-lift")
    val arrive = scaleIn(RelayTheme.motion.hero.fastSpatialSpec(), 0.85f, TransformOrigin(1f, 1f)) +
        slideInVertically(RelayTheme.motion.hero.fastSpatialSpec()) { it / 4 } +
        fadeIn(RelayTheme.motion.hero.fastEffectsSpec())
    Box(modifier.fillMaxWidth().padding(16.dp).padding(bottom = lift), contentAlignment = Alignment.BottomEnd) {
        AnimatedContent(
            shown,
            transitionSpec = { ContentTransform(arrive, fadeOut(tween(100)), sizeTransform = null) },
            contentAlignment = Alignment.BottomEnd,
            label = "status-toast",
        ) { news ->
            if (news != null) SwipeableToast(news, { onOpen(news) }, { onDismiss(news) })
        }
    }
}

@Composable
private fun SwipeableToast(news: StatusNews, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    StatusToast(
        thread = news.title,
        status = news.kind.label,
        icon = TablerIcons.Filled.Message,
        statusColor = news.kind.color(RelayTheme.colors),
        modifier = Modifier
            .graphicsLayer {
                translationX = drag.value
                alpha = 1f - (abs(drag.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .draggable(
                rememberDraggableState { dx -> scope.launch { drag.snapTo((drag.value + dx).coerceAtLeast(0f)) } },
                Orientation.Horizontal,
                onDragStopped = { velocity -> if (drag.value > 120f || velocity > 1200f) onDismiss() else drag.animateTo(0f) },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${news.title}: ${news.kind.label}. Open it."
                liveRegion = LiveRegionMode.Polite
                customActions = listOf(CustomAccessibilityAction("Dismiss") { onDismiss(); true })
            }
            .pressable(onOpen),
    )
}

private fun StatusNews.Kind.color(c: RelayColors): Color = when (this) {
    StatusNews.Kind.NeedsYou -> c.waiting
    StatusNews.Kind.Finished -> c.ink
    StatusNews.Kind.Failed -> c.danger
    StatusNews.Kind.UsageLimit -> c.warning
}
