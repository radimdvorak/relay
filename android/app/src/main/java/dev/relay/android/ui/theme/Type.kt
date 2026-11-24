package dev.relay.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.relay.android.R

/** Between regular and medium. */
val Book = FontWeight(520)

private fun inter(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(Book),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

/** Newsreader's optical size axis set for display sizes, where its contrast and spacing are drawn for. */
private fun newsreader(weight: FontWeight) = Font(
    R.font.newsreader_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", 24f),
    ),
)

/** The display serif: the home greeting, empty states. */
val Newsreader = FontFamily(newsreader(FontWeight.Normal), newsreader(FontWeight.Medium))

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** The type scale in docs/expressive/design-brief.md ("Type"). */
@Immutable
data class RelayType(
    /** Newsreader 27/34: the empty home's line, empty states. */
    val greeting: TextStyle,
    /** 22/28 at 500: a tab's title on the chrome (Projects, PRs). */
    val title: TextStyle,
    /** 20/26 at 600: markdown H1–H2, headings inside a screen. */
    val heading: TextStyle,
    /** 17/22 at 500: the centred title of a sheet or dialog. */
    val sheetTitle: TextStyle,
    /** 15/24: answers, messages, paragraphs. */
    val body: TextStyle,
    /** 16/22: the composer's text, inputs, a card's title. */
    val row: TextStyle,
    /** 15/20: a list row's title; no louder than the text it opens. */
    val listTitle: TextStyle,
    /** 13/17: what's under it, and times. */
    val listMeta: TextStyle,
    /** 14/18: descriptions and notes outside lists. */
    val meta: TextStyle,
    /** 12/16: the smallest text, for dense rows. */
    val small: TextStyle,
    /** 12/16 at 500: chips, tooltips; section labels use it at 13. */
    val label: TextStyle,
    /** JetBrains Mono 13/20: code blocks, paths. */
    val code: TextStyle,
    /** JetBrains Mono 11/16: code in dense places (diff gutters, tool rows). */
    val codeSmall: TextStyle,
)

private val centred = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: TextUnit = 0.sp,
    family: FontFamily = Inter,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking,
    lineHeightStyle = centred,
)

val DefaultType = RelayType(
    greeting = style(27, 34, tracking = (-0.01).em, family = Newsreader),
    title = style(22, 28, FontWeight.Medium, (-0.02).em),
    heading = style(20, 26, FontWeight.SemiBold, (-0.01).em),
    sheetTitle = style(17, 22, FontWeight.Medium),
    body = style(15, 24),
    row = style(16, 22),
    listTitle = style(15, 20),
    listMeta = style(13, 17),
    meta = style(14, 18),
    small = style(12, 16),
    label = style(12, 16, FontWeight.Medium),
    code = style(13, 20, family = JetBrainsMono),
    codeSmall = style(11, 16, family = JetBrainsMono),
)
