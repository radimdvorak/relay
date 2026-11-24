# Relay Experimental: design brief

The phone app's full rework on branch `android-expressive`. HextaUI goes; the new look follows the
Claude Android app's structure and calm (spec: `claude-app-spec.md`, frames in
`/tmp/claude-app-frames/`), built on Material 3 Expressive with material3 1.5 / Compose 1.13
(`m3-expressive.md`, `compose-api.md`, `google-reference-apps.md`). Read this file first; the
others when your slice touches them.

What stays: everything Relay does (bridge, ViewModels, features, the pairing screen as it is).
What changes: every screen's look and the app's shell.

## Not copied

No Claude spark, no Anthropic fonts, no "Ask Claude". Relay's own mark on the empty home; the
display serif is **Newsreader** (OFL, bundled variable font in `res/font/`); UI text stays **Inter**
(already bundled), code stays JetBrains Mono. Copy says Relay things: "What should we work on?",
"Message", "Search threads".

## Colour: the user's themes, re-cast

Users keep what they have today: Settings → Theme (Computer / Phone / Light / Dark) and Contrast,
and in Computer mode the desktop's palette (`PhonePalette`: sidebar, surface, toolbar, inbox, text,
muted, border, hover, selected, accent, accentSoft, onAccent, send, onSend, diffAddition,
diffDeletion). Only the roles change, to the ones this layout needs:

| Role (new `RelayColors`) | What it paints | From the desktop palette | Default light / dark |
|---|---|---|---|
| `chrome` | header band, sheet backdrop | `sidebar` | `#ECECEC` / `#161616` |
| `surface` | the content sheet, thread, lists | `surface` | `#FDFDFD` / `#0D0D0D` |
| `raised` | composer pill, sheets, toasts | `inbox` | `#FCFCFC` / `#1C1C1C` |
| `tool` | 44/36dp circle buttons, search field, active tab pill, selected row | `hover` | `#ECECEC` / `#262626` |
| `selected` | current row in lists | `selected` | `#E4E4E4` / `#2C2C2C` |
| `ink` / `inkSoft` / `inkFaint` | text tiers | `text` / `muted` / lerp(surface, muted, .62) | `#1A1A1A` `#6B6B6B` `#9A9A9A` / `#F2F2F2` `#A3A3A3` `#6E6E6E` |
| `strong` / `onStrong` | dark circle button, effort slider, tooltip | `text` / `surface` | `#171717` / `#FAFAFA` inverted in dark |
| `bubble` / `onBubble` | the user's message | `accentSoft` / `text` | `#E4EFF6` / `#1A2A35` |
| `send` / `onSend` | the send button when there's text | `send` ?: `accent` | `strong` unless the desktop sends one |
| `accent` | links, focus, Relay mark, small brand touches | `accent` | `#C86858` coral / `#E08A78` |
| `line` | the rare hairline (inputs, toast rim) | `border` | `#E2E2E2` / `#FFFFFF1A` |
| status: `success`, `danger`, `warning`, `info`, `waiting` | as today | — | as today |
| diff: `added`, `removed` (+ bg) | diff text and backgrounds | `diffAddition` / `diffDeletion` | `#53A664`/`#C8285A` |

Contrast (`withContrast`) keeps working on the new roles. Default palettes replace Hexta neutral
("Relay paper" light/dark). `materialScheme()` maps these roles onto M3's `ColorScheme`
(surface → `surface`, chrome → `surfaceContainer`, tool → `surfaceContainerHigh`,
raised → `surfaceContainerLowest` …) so stock M3 components look right without per-call colours.

## Type

| Style | Font | Size / line | Use |
|---|---|---|---|
| `greeting` | Newsreader 400 | 27 / 34 | empty home, empty states |
| `title` | Inter 500 | 24 / 30 | tab titles (Projects, PRs) |
| `heading` | Inter 600 | 20 / 26 | markdown H1–H2, screen headings |
| `body` | Inter 400 | 15 / 24 | answers, user messages |
| `row` | Inter 400–500 | 16 / 22 | list row titles, composer text |
| `meta` | Inter 400 | 13–14 / 18 | subtitles, times, descriptions |
| `label` | Inter 500 | 12 / 16 | chips, tooltips, section labels (13) |
| `code` | JetBrains Mono | 13 / 20 | code blocks, paths |

Body text goes from 13sp (Hexta) to 15sp: this layout breathes. Use `LineHeightStyle.Mode.PerLine`
where mixed sizes share a paragraph (markdown).

## Shape and spacing

- Circles: 44dp header buttons, 36dp composer buttons. Paired header actions share one 88x44 pill.
- Composer: fully round pill, 53dp tall collapsed; card with 26dp corners when expanded.
- Content sheet over chrome: 24dp top corners. Bottom sheets: 28–30dp, inset 8dp from the edges.
- Lists: no dividers. Rows 48–69dp, 16dp side padding (24 for section labels). Groups (settings)
  in tonal containers: 24dp outer corners, 6dp between neighbours (M3E containment).
- Bubbles: fully round when one line, 22dp corners when several.
- 4dp grid; screen side padding 16dp, thread text 18dp.

## Motion

`MaterialExpressiveTheme` with a Relay `MotionScheme`: the video never overshoots, so recurring
motion uses **standard** springs and only a few hero moments use **expressive** spatial springs.

- Standard (`MaterialTheme.motionScheme` default*): sheets, page changes, composer
  expand/collapse, list items, tool-call rows, streaming. 150–250ms feel, no bounce.
- Expressive spatial (explicit, sparingly): the bottom-bar pill sliding, the tooltip pop, the send
  button appearing/morphing (circle → `MorphPolygonShape` while recording), the toast arriving.
- Effects springs for every colour/alpha change.
- Read animated values in `graphicsLayer {}` / `offset {}` lambdas. Endless loops still step at
  20fps and stop when the app is in the background (android/AGENTS.md).

## Shell

```
┌ status bar ───────────────────────────┐
│ chrome: computer ⌄      (≡) (🔍|⚙)   │  header, 44dp circles
│ Threads                               │  the tab's title, 24sp
│╭─────────────── surface ─────────────╮│  24dp top corners
││  screen content                     ││
││                                     ││
││ ╭─ composer pill ────────────────╮  ││  floats; hidden where it has no job
││ ╰────────────────────────────────╯  ││
││   (Threads)   Projects    PRs       ││  icon bar, active in a `tool` pill
╰┴─────────────────────────────────────┴╯
```

- Bottom bar: **Threads, Projects, PRs** in equal thirds of the composer's width, nothing behind
  the bar itself. The chosen one has an accent-tinted pill, a two-tone icon that makes its own move,
  and its label; a long-press shows a `strong` tooltip. Settings is the gear in the header, beside search.
- **No drawer.** We built one and dropped it: it repeated the thread list, search and new thread.
  Every thread is on the Threads tab, and a new one starts only in its composer. On a Fold's inner
  screen Home is a pane beside the detail.
- The Threads tab is the composer-first home: with nothing running it's the greeting; otherwise
  the activity (needs-you, running, recent) above the composer. Typing in the home composer starts
  a new thread (the project/where/model chips come with it).
- 🔍 opens full-screen **search** (threads and projects, matches in bold).
- Thread screen: chrome header (←, title, ✎ | ⋯), messages on `surface`, composer pinned
  above the keyboard, model + effort chips in the expanded composer opening the **model sheet**
  and **effort sheet**.
- Status toasts ("Finished", "Needs you") float above the composer, right-aligned.

## Building blocks (`ui/`, owned by the foundation slice)

`RelayTheme` (new roles, MaterialExpressiveTheme), `ChromeHeader`, `CircleButton`,
`CirclePair`, `ContentSheet`, `ComposerPill` (look only; the composer feature owns behaviour),
`BottomBar` + `BarTooltip`, `RelaySheet` (inset bottom sheet with ✕/← and centred title),
`SectionLabel`, `ListRow` (title, subtitle, leading, trailing meta), `Group` (tonal container),
`StatusToast`, `SearchField`, `Chip` (a control, not a badge), `EffortSlider`, `EmptyGreeting`.
Screens build from these; no per-screen colours or radii.

House rules that still apply: no pill/chip *badges* as decoration (status is a coloured glyph or
coloured text, never a filled pill); chips are only for controls (model, effort). Readable code
rules from the repo `AGENTS.md`. Files under ~500 lines.

## Seeing it

Every slice renders its screens with the JVM screenshot harness (`android/AGENTS.md`, "Seeing UI
without a device") in light and dark, phone and Fold inner, and compares against the reference
frames. Motion and touch feel get checked on the phone over adb, one agent at a time (take the
lock: `mkdir /tmp/relay-phone-lock` succeeds = yours; `rmdir` when done).
