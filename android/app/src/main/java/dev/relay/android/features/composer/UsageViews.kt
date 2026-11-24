package dev.relay.android.features.composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.relay.android.features.composer.logic.ProviderUsage
import dev.relay.android.features.composer.logic.UsageConnection
import dev.relay.android.features.composer.logic.UsageMeter
import dev.relay.android.features.composer.logic.agentName
import dev.relay.android.features.composer.logic.formatCost
import dev.relay.android.features.composer.logic.formatTokens
import dev.relay.android.features.composer.logic.paceGap
import dev.relay.android.features.composer.logic.presentWindow
import dev.relay.android.features.composer.logic.reportsUsage
import dev.relay.android.remote.Remote
import dev.relay.android.remote.client.LinkState
import dev.relay.android.remote.client.RemoteClient
import dev.relay.android.remote.model.ContextUsage
import dev.relay.android.remote.model.ThreadCost
import dev.relay.android.ui.Button
import dev.relay.android.ui.ButtonVariant
import dev.relay.android.ui.Group
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.Spinner
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The desktop's usage meters and context meter (mobile/src/ui/Usage.tsx).

private val rank = mapOf("ok" to 0, "warn" to 1, "hot" to 2, "spent" to 3)

private fun paceColor(pace: String, fallback: Color, c: RelayColors) = when (pace) {
    "warn" -> c.waiting
    "hot", "spent" -> c.danger
    else -> fallback
}

private fun contextPace(used: Double) = if (used >= 90) "hot" else if (used >= 75) "warn" else "ok"

/** `unavailable` says the last ask failed and left no plan meters to show. */
class UsageState(val usage: ProviderUsage?, val reload: (force: Boolean) -> Unit, val unavailable: Boolean = false)

/** The agent's plan usage, looked up now and every few minutes while the app is in the foreground. */
@Composable
fun rememberUsage(remote: Remote, provider: String): UsageState {
    val computer by remote.active.collectAsStateWithLifecycle()
    val client by remote.client.collectAsStateWithLifecycle()
    val status by remote.status.collectAsStateWithLifecycle()
    val connection = client?.let { connected ->
        connected.credentials?.key?.let { UsageConnection(it, connected) }
    }
    val online = status.state == LinkState.Online
    var reading by remember(computer, connection, provider) { mutableStateOf(usageReadings[computer, provider]) }
    val scope = rememberCoroutineScope()
    fun current(source: UsageConnection<RemoteClient>) =
        source.matches(remote.active.value, remote.client.value) && remote.status.value.state == LinkState.Online

    suspend fun load(force: Boolean) {
        if (connection == null || connection.computer != computer || !online || !reportsUsage(provider)) return
        val result = usageReadings.read(connection, provider, ::current) { it.providerUsage(provider, force) }
        if (result != null && current(connection)) reading = result
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Not with the phone in a pocket: relay-watch keeps the app alive there.
    LaunchedEffect(computer, connection, provider, online, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                load(false)
                delay(180_000)
            }
        }
    }
    val shown = reading ?: usageReadings[computer, provider]
    return UsageState(shown?.usage, { force -> scope.launch { load(force) } }, unavailable = shown?.unavailable == true)
}

private fun meters(usage: ProviderUsage?): List<UsageMeter> {
    val now = System.currentTimeMillis()
    return usage?.windows?.map { presentWindow(it, now, usage.activeHours) }.orEmpty()
}

private fun usageLabel(usage: ProviderUsage?): String? {
    val list = meters(usage).takeIf { it.isNotEmpty() } ?: return null
    val worst = list.maxBy { rank[it.pace] ?: 0 }
    return list.joinToString(", ") { "${it.label} ${it.leftPercent}% left" } + if (worst.pace == "ok") "" else ", running hot"
}

private data class UsageBarItem(val label: String, val value: String, val percent: Int, val color: Color)

/**
 * The thread settings sheet's footer: the context window and the plan's
 * limits as thin bars, and what a thread billed per token so far, opening
 * the usage sheet on a tap.
 */
@Composable
fun UsageBar(provider: String, usage: ProviderUsage?, context: ContextUsage?, cost: ThreadCost?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = RelayTheme.colors
    val used = context?.maxTokens?.takeIf { it > 0 }?.let { minOf(100, (context.usedTokens * 100.0 / it).roundToInt()) }
    val list = if (reportsUsage(provider)) meters(usage) else emptyList()
    // Like the desktop, the price stays beside the limits: OpenCode prices an
    // answer from a model outside its Go plan, and its limits can be unavailable.
    val spent = cost
    if (used == null && list.isEmpty() && spent == null) return
    val bars = buildList {
        if (used != null) add(UsageBarItem("Context", "$used%", used, paceColor(contextPace(used.toDouble()), c.inkSoft, c)))
        list.forEach { m ->
            add(UsageBarItem(m.label, "${m.leftPercent}% left", m.leftPercent, paceColor(m.pace, if (m.kind == "session") c.strong else c.inkSoft, c)))
        }
    }
    val rows = if (bars.size > 3) bars.chunked(2) else if (bars.isNotEmpty()) listOf(bars) else emptyList()
    val label = listOfNotNull(
        used?.let { "Context $it% full" },
        usageLabel(usage),
        spent?.let { "This thread cost ${formatCost(it.usd)}" },
    ).joinToString(", ")
    Column(
        modifier.fillMaxWidth()
            .semantics { contentDescription = label }
            .pressable(onClick = onClick)
            .clip(RelayTheme.shapes.card).background(c.container)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item -> BarMeter(item.label, item.value, item.percent, item.color) }
                if (bars.size > 3 && row.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (spent != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("This thread", Modifier.weight(1f), style = RelayTheme.type.small, color = c.inkSoft, maxLines = 1)
            Text(formatCost(spent.usd), style = RelayTheme.type.small, color = c.ink, maxLines = 1)
        }
    }
}

/**
 * The plan's limits as rings, the long one (week or month) outside and the
 * session inside, each arc what's left. Nothing for an agent without plan limits;
 * bare rings while the first answer is on its way.
 */
@Composable
fun UsageRings(provider: String, usage: ProviderUsage?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!reportsUsage(provider)) return
    val c = RelayTheme.colors
    val plan = meters(usage?.copy(windows = usage.windows.filter { it.model == null }))
    val list = listOf(setOf("weekly", "monthly"), setOf("session"))
        .mapNotNull { kinds -> plan.filter { it.kind in kinds }.minByOrNull { it.leftPercent } }
    Box(
        modifier.size(36.dp).clip(CircleShape)
            .pressable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = usageLabel(usage) ?: "${agentName(provider)} usage" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.4.dp.toPx()
            val step = stroke + 1.6.dp.toPx()
            for (i in 0 until maxOf(1, list.size)) {
                val inset = stroke / 2 + i * step
                val at = Offset(inset, inset)
                val box = Size(size.width - 2 * inset, size.height - 2 * inset)
                drawArc(c.selected, 0f, 360f, false, at, box, style = Stroke(stroke))
                val m = list.getOrNull(i) ?: continue
                val color = paceColor(m.pace, if (m.kind == "session") c.strong else c.inkSoft, c)
                if (m.leftPercent > 0) drawArc(color, -90f, 360f * m.leftPercent / 100f, false, at, box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun RowScope.BarMeter(label: String, value: String, percent: Int, color: Color) {
    val c = RelayTheme.colors
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, Modifier.weight(1f), style = RelayTheme.type.small, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = RelayTheme.type.small, color = c.ink, maxLines = 1)
        }
        Track(percent.toDouble(), color, height = 4)
    }
}

/** A round bar; [mark] is where an even pace would leave it. */
@Composable
private fun Track(percent: Double, color: Color, mark: Double? = null, height: Int = 8) {
    val c = RelayTheme.colors
    val shape = RelayTheme.shapes.circle
    BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp).background(c.selected, shape)) {
        val p = (percent.coerceIn(0.0, 100.0) / 100).toFloat()
        Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(color, shape))
        if (mark != null) {
            val x = maxWidth * (mark.coerceIn(0.0, 100.0) / 100).toFloat()
            Box(Modifier.offset(x = x - 1.dp).requiredHeight(height.dp + 6.dp).width(2.dp).align(Alignment.CenterStart).background(c.ink, shape))
        }
    }
}

/** What the composer's context meter opens: the context window, then the plan's limits. */
@Composable
fun UsageSheet(
    provider: String,
    usage: ProviderUsage?,
    context: ContextUsage?,
    cost: ThreadCost?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCompact: (suspend () -> Unit)?,
    /** The last ask for the limits failed and none were read before it. */
    unavailable: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var compacting by remember { mutableStateOf(false) }
    Sheet(onDismiss, title = "${agentName(provider)} usage") {
        UsageContent(
            provider, usage, context, cost, onRefresh,
            onCompact = onCompact?.let { compact ->
                {
                    compacting = true
                    scope.launch {
                        try { compact() } finally {
                            compacting = false
                            onDismiss()
                        }
                    }
                }
            },
            compacting = compacting,
            unavailable = unavailable,
        )
    }
}

@Composable
internal fun UsageContent(
    provider: String,
    usage: ProviderUsage?,
    context: ContextUsage?,
    cost: ThreadCost?,
    onRefresh: () -> Unit,
    onCompact: (() -> Unit)?,
    compacting: Boolean = false,
    unavailable: Boolean = false,
) {
    val c = RelayTheme.colors
    val list = meters(usage)
    Group {
        if (context != null) item { ContextTile(context, onCompact, compacting) }
        if (cost != null) item { CostTile(cost) }
    }
    val note = when {
        !reportsUsage(provider) -> "${agentName(provider)} doesn't report plan limits."
        unavailable -> "Couldn't read ${agentName(provider)}'s plan limits."
        usage != null && list.isEmpty() -> usage.message ?: "No usage limits reported"
        else -> null
    }
    when {
        note != null -> Text(note, Modifier.padding(horizontal = 16.dp), style = RelayTheme.type.meta, color = c.inkSoft)
        usage == null -> Spinner(Modifier.padding(16.dp), color = c.inkSoft)
        else -> Group(label = "Plan limits") { list.forEach { m -> item { MeterTile(m) } } }
    }
    if (reportsUsage(provider)) Button("Check again", onRefresh, variant = ButtonVariant.Secondary)
}

@Composable
private fun UsageTile(title: String, value: String, valueColor: Color = RelayTheme.colors.inkSoft, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.weight(1f), style = RelayTheme.type.row, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = RelayTheme.type.meta, color = valueColor, maxLines = 1)
        }
        content()
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = RelayTheme.type.meta.copy(fontSize = 13.sp), color = RelayTheme.colors.inkSoft)

@Composable
private fun ContextTile(context: ContextUsage, onCompact: (() -> Unit)?, compacting: Boolean) {
    val c = RelayTheme.colors
    val used = context.maxTokens?.takeIf { it > 0 }?.let { minOf(100.0, context.usedTokens * 100.0 / it) }
    val size = formatTokens(context.usedTokens) + (context.maxTokens?.let { " of ${formatTokens(it)}" } ?: "")
    UsageTile("Context", size + (used?.let { " · ${it.roundToInt()}% full" } ?: "")) {
        if (used != null) Track(used, paceColor(contextPace(used), c.strong, c))
        context.totalTokens?.let { Hint("${formatTokens(it)} tokens through the session so far") }
        if (onCompact != null) Button(if (compacting) "Compacting…" else "Compact now", onCompact, enabled = !compacting, variant = ButtonVariant.Secondary)
    }
}

@Composable
private fun CostTile(cost: ThreadCost) {
    val c = RelayTheme.colors
    UsageTile("This thread", formatCost(cost.usd)) {
        cost.models.forEach { m ->
            Row {
                Text(m.name, Modifier.weight(1f), style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatCost(m.usd), style = RelayTheme.type.meta, color = c.inkSoft)
            }
        }
        Hint("${cost.answers} ${if (cost.answers == 1) "answer" else "answers"} · priced by the agent")
    }
}

@Composable
private fun MeterTile(m: UsageMeter) {
    val c = RelayTheme.colors
    UsageTile(m.label, "${m.leftPercent}% left", paceColor(m.pace, c.inkSoft, c)) {
        Track(m.leftPercent.toDouble(), paceColor(m.pace, c.strong, c), m.paceLeftPercent)
        val hint = listOfNotNull(paceGap(m), m.limitLabel, m.resetLabel).joinToString(" · ")
        if (hint.isNotEmpty()) Hint(hint)
    }
}
