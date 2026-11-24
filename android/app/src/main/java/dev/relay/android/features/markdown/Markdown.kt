package dev.relay.android.features.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.remote.model.TurnFileChange
import dev.relay.android.remote.tunnel.LocalUrls
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import androidx.compose.material3.Text as RichText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import dev.relay.android.ui.Icon
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Square
import io.github.lyxnx.compose.ui.tablericons.outline.SquareCheck

/**
 * What pressing a link's destination or a piece of inline code does, when it
 * names something the app can open; null leaves it as text.
 */
typealias OpenLink = (value: String, inline: Boolean) -> (() -> Unit)?

/** Draws an `![alt](src)` the app can show (a local image); drawing nothing is fine. */
typealias ShowImage = @Composable (src: String, alt: String) -> Unit

/** Paths in the thread's [folder] open through [open]: a changed file's diff, else the file or folder. */
fun fileLinks(folder: String, changes: List<TurnFileChange> = emptyList(), open: (FileLinkTarget) -> Unit): OpenLink =
    { value, inline -> fileLinkTarget(value, inline, folder, changes)?.let { target -> { open(target) } } }

/**
 * Set where the computer tunnels its ports to the phone: a localhost URL in
 * backticks, as agents often write a dev server's address, opens like a link.
 */
val LocalOpensLocalhost = compositionLocalOf { false }

/** What the blocks share; one per Markdown, so blocks that didn't change skip. */
@Immutable
internal class MdContext(
    val small: Boolean,
    val openPath: OpenLink?,
    val openWeb: (String) -> Unit,
    val image: ShowImage?,
    val localhost: Boolean = false,
    /** Text as Android's own views (NativeText.kt), selectable when [selectable]. */
    val native: Boolean = false,
    val selectable: Boolean = false,
    /** Inside a quote: the text is softer. */
    val quoted: Boolean = false,
) {
    fun inQuote() = MdContext(small, openPath, openWeb, image, localhost, native, selectable, quoted = true)

    /** A file first, then a web link; anything else stays text, as on the desktop. */
    fun link(href: String): (() -> Unit)? =
        openPath?.invoke(href, false) ?: if (isWebUrl(href)) ({ openWeb(href) }) else null

    fun code(code: String): (() -> Unit)? = openPath?.invoke(code.trim(), true)
        ?: code.trim().takeIf { localhost && LocalUrls.link(it) != null }?.let { url -> { openWeb(url) } }
}

/**
 * An agent's answer: the markdown agents actually write, drawn natively.
 * Web links go to [onLink] (the browser when null). Paths in the thread open
 * through [openPath]; without it, paths inside [folder] go to [onLink] too.
 * [selectable] lets held text select, across blocks; a streaming answer
 * re-parses only its last blocks. [native] draws the text as Android's own
 * views instead, each paragraph selecting by itself; an answer keeps it while
 * it streams, so finishing doesn't lay it out anew.
 */
@Composable
fun Markdown(
    markdown: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onLink: ((String) -> Unit)? = null,
    folder: String? = null,
    openPath: OpenLink? = null,
    image: ShowImage? = null,
    selectable: Boolean = false,
    native: Boolean = false,
) {
    val cache = remember { MarkdownCache() }
    val blocks = remember(markdown) { cache.blocks(markdown) }
    val uri = LocalUriHandler.current
    val localhost = LocalOpensLocalhost.current
    val context = remember(small, onLink, folder, openPath, image, uri, localhost, native, selectable) {
        val web = onLink ?: { href: String -> runCatching { uri.openUri(href) }; Unit }
        val paths = openPath ?: if (folder != null && onLink != null) {
            { value: String, inline: Boolean ->
                if (fileLinkTarget(value, inline, folder) != null) ({ onLink(value) }) else null
            }
        } else null
        MdContext(small, paths, web, image, localhost, native, selectable)
    }
    val content = @Composable {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(blockGap(context))) {
            for (block in blocks) Block(block, context)
        }
    }
    if (selectable && !native) SelectionContainer { CompositionLocalProvider(LocalInSelection provides true, content) } else content()
}

/** A single lazy-list row, using the same link, image and native selection behavior as Markdown. */
@Composable
fun MarkdownBlockView(
    block: MarkdownBlock,
    modifier: Modifier = Modifier,
    openPath: OpenLink? = null,
    image: ShowImage? = null,
    selectable: Boolean = false,
) {
    val uri = LocalUriHandler.current
    val localhost = LocalOpensLocalhost.current
    val context = remember(openPath, image, selectable, uri, localhost) {
        MdContext(false, openPath, { href -> runCatching { uri.openUri(href) } }, image,
            localhost, native = true, selectable = selectable)
    }
    Column(modifier) { Block(block.block, context) }
}

/** Inside a selectable answer: a code block mustn't open a selection of its own within it. */
internal val LocalInSelection = staticCompositionLocalOf { false }

@Composable
internal fun bodyStyle(small: Boolean): TextStyle =
    if (small) RelayTheme.type.meta.copy(lineHeight = 20.sp) else RelayTheme.type.body

@Composable
private fun headingStyle(level: Int, small: Boolean): TextStyle = when {
    small -> RelayTheme.type.body.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    level <= 2 -> RelayTheme.type.heading
    level == 3 -> RelayTheme.type.row.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    else -> RelayTheme.type.body.copy(fontWeight = FontWeight.SemiBold)
}

/** Where an item's text starts past its mark. */
private val ItemIndent = 20.dp

/** Inter's own line, in ems: a TextView's first line is this tall, and its spacing comes after it. */
private const val NATURAL_LINE = 1.21f

/** A native paragraph ends without the spacing its lines have between them; this gives it back. */
private val NativeLead = 6.dp

/** Between two blocks, and between two items of a list, over the line height the text itself has. */
private fun blockGap(ctx: MdContext) = if (ctx.native && !ctx.small) 8.dp + NativeLead else 10.dp

private fun itemGap(ctx: MdContext) = if (ctx.native && !ctx.small) 4.dp + NativeLead else 4.dp

@Composable
private fun Block(block: MdBlock, ctx: MdContext) {
    val c = RelayTheme.colors
    when (block) {
        is MdBlock.Para -> Paragraph(block.inlines, ctx)
        is MdBlock.Heading -> Inlines(
            block.inlines,
            ctx,
            headingStyle(block.level, ctx.small),
            Modifier.padding(top = if (ctx.small) 2.dp else 4.dp),
        )
        is MdBlock.Code -> CodeBlock(block.language, block.code)
        is MdBlock.Math -> MathBlock(block.tex, block.closed)
        is MdBlock.Quote -> {
            val bar = c.selected
            val quote = remember(ctx) { ctx.inQuote() }
            Column(
                Modifier
                    .drawBehind { drawRoundRect(bar, size = Size(3.dp.toPx(), size.height), cornerRadius = CornerRadius(1.5.dp.toPx())) }
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(blockGap(ctx)),
            ) { for (inner in block.blocks) Block(inner, quote) }
        }
        is MdBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(itemGap(ctx))) {
            block.items.forEachIndexed { i, item ->
                Row {
                    ItemMark(item.task, number = if (block.ordered) block.start + i else null, ctx)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(itemGap(ctx))) {
                        for (inner in item.blocks) Block(inner, ctx)
                    }
                }
            }
        }
        is MdBlock.Table -> if (ctx.native && ctx.selectable) SelectionContainer { Table(block, ctx) } else Table(block, ctx)
        is MdBlock.Raw -> Text(block.text, style = bodyStyle(ctx.small), color = c.ink)
        // Room between two parts says the same as a line would, more quietly.
        MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.size(3.dp).background(c.inkFaint, CircleShape)) }
            }
        }
    }
}

/** A list item's dot, number or checkbox, level with its first line. */
@Composable
private fun ItemMark(task: Boolean?, number: Int?, ctx: MdContext) {
    val c = RelayTheme.colors
    val body = bodyStyle(ctx.small)
    val style = if (ctx.native) body.copy(lineHeight = body.fontSize * NATURAL_LINE) else body
    val line = with(LocalDensity.current) { style.lineHeight.toDp() }
    if (number != null && task == null) return Text(
        "$number.",
        Modifier.widthIn(min = ItemIndent).padding(end = 5.dp),
        style = style.copy(fontFeatureSettings = "tnum"),
        color = c.inkSoft,
        textAlign = TextAlign.End,
    )
    Box(Modifier.size(ItemIndent, line).padding(end = if (task == null) 0.dp else 4.dp), contentAlignment = if (task == null) Alignment.Center else Alignment.CenterStart) {
        when (task) {
            null -> Box(Modifier.size(5.dp).background(if (ctx.quoted) c.inkSoft else c.ink, CircleShape))
            true -> Icon(TablerIcons.Outline.SquareCheck, "Done", tint = c.inkSoft, size = 16.dp)
            false -> Icon(TablerIcons.Outline.Square, "Not done", tint = c.inkFaint, size = 16.dp)
        }
    }
}

/** A web image stays a link in the text: loading it would tell its server the thread was read. */
private fun drawn(inline: MdInline) = inline is MdInline.Image && !isWebUrl(inline.src)

/**
 * A paragraph's text stays whole and its images follow it: text can't hold a
 * block, and an image that doesn't load then leaves the sentence as it reads.
 */
@Composable
private fun Paragraph(inlines: List<MdInline>, ctx: MdContext) {
    val images = if (ctx.image == null) emptyList() else inlines.filter(::drawn).map { it as MdInline.Image }
    if (images.isEmpty()) {
        Inlines(inlines, ctx, bodyStyle(ctx.small))
        return
    }
    val text = remember(inlines) { withoutImages(inlines) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (text.any { it !is MdInline.Plain || it.text.isNotBlank() }) Inlines(text, ctx, bodyStyle(ctx.small))
        for (image in images) ctx.image?.invoke(image.src, image.alt)
    }
}

/** "Before ![](a.png) after" reads "Before after", not with the image's two spaces. */
internal fun withoutImages(inlines: List<MdInline>): List<MdInline> {
    val out = ArrayList<MdInline>()
    var gap = false
    for (inline in inlines) {
        if (drawn(inline)) {
            gap = true
            continue
        }
        val before = out.lastOrNull()
        if (inline is MdInline.Plain && before is MdInline.Plain) {
            val joined = if (gap && before.text.lastOrNull()?.isWhitespace() == true) inline.text.trimStart() else inline.text
            out[out.size - 1] = MdInline.Plain(before.text + joined)
        } else out += inline
        gap = false
    }
    return out
}

@Composable
internal fun Inlines(inlines: List<MdInline>, ctx: MdContext, style: TextStyle, modifier: Modifier = Modifier, native: Boolean = ctx.native) {
    val c = RelayTheme.colors
    val ink = if (ctx.quoted) c.inkSoft else c.ink
    if (native) return NativeInlines(inlines, ctx, style.copy(color = ink), ctx.selectable, modifier)
    val text = remember(inlines, ctx, c) { inlineText(inlines, ctx, c) }
    RichText(text, modifier, style = style.copy(color = ink))
}
