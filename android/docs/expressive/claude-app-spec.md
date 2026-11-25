# The look we're after: spec measured from the Claude Android app video

Measured from a 52s product video (1080x1080). Reference frames: `/tmp/claude-app-frames/`
(stills `01_home_zoomed.png` … `18_finished_toast.png`, animation strips `strip_*.png`).
Hex marked (s) is sampled (±2 per channel, video compression), (~) is eyeballed; sizes ±2dp.

This is the *structure and feel* to follow. We do NOT copy Claude's spark logo or Anthropic's
fonts: Relay's own mark, an open serif for display text, and the user's chosen palette for colour
(see `design-brief.md`). The greys below define the *default light* palette's relationships.

## Global

| Token | Value |
|---|---|
| Header / top chrome | `#ECECEC` (s) |
| Content surface (sheets, lists, thread) | `#FDFDFD` (s) |
| Scrim behind sheets | black ~16% |
| Circle buttons, nav pill, selected row | `#ECECEC` / active tab pill `#EFEFEF` |
| Text primary / secondary / tertiary | `#1A1A1A` / `#6B6B6B` / `#8A8A8A`–`#9A9A9A` |
| Dark button, effort slider, tooltip | `#171717` |
| User bubble | `#E4EFF6` (cool pale blue-grey) |
| Toast | `#F3F3F3`, 1dp `#E2E2E2` border |
| Inactive tab icons | `#A0A0A0` |
| Diff + / − | `#53A664` / ~`#C8285A` |
| Accent (logo) | ~`#C86858` coral |
| Notification dot | ~`#2F6FE0` |

- Sans UI font; serif only for the home greeting, drawer title, incognito-style empty states.
- **No dividers.** Separation by grey header vs white sheet, and whitespace.
- Pills/circles fully round. Content sheet top corners ~24dp over the grey header. Bottom sheets ~30dp.

## Home (empty)

- Grey header: 44dp circle menu button 16dp from left. Right: search + second action joined into one
  88x44 pill with a 1dp seam.
- White sheet from the header's bottom edge (24dp top corners).
- Centre: 28dp logo, ~16dp gap, serif greeting ~26–28sp `#1A1A1A`.
- Composer pill: 53dp tall, 15dp from screen edges, fully round, `#FCFCFC`, soft low shadow.
  Left 36dp `#E9E9E9` circle with 22dp plus; 16sp placeholder `#8A8A8A`; right 36dp mic circle
  `#ECECEC`, then 36dp `#171717` circle (send / voice). ~8dp gaps.
- Bottom bar: icons only (22dp, filled glyphs), spread evenly; active one in a 66x43dp `#EFEFEF`
  pill with a black icon; inactive `#A0A0A0`.

## Search (full screen)

- Header becomes back circle (44dp, white) + search field 43dp tall, white, fully round, 20dp
  search icon, 17sp text, clear ✕.
- Rows: 48dp pitch, 16sp, 16dp horizontal padding; right meta: pin / lock 18dp `#555`, then time
  ("12m", "Nov 13") 14sp `#6B6B6B`.
- Match highlight: matched substring bold (~600), also mid-word.
- Results filter live per keystroke; list fades ~300ms with slight stagger.

## Model sheet

- Sheet ~9dp inset from screen edges, ~30dp corners. Handle 36x4dp `#D0D0D0`, 8dp from top.
- Top-left 44dp `#ECECEC` circle ✕; centred title ~17sp medium.
- Rows at 57dp pitch, 16sp medium `#6B6B6B`; selected row `#1A1A1A` inside a 356x64dp `#ECECEC`
  fully round pill, which also shows a tiny 5-dot effort glyph (current stop = short vertical bar),
  then "Medium" 14sp `#6B6B6B` + chevron (opens the effort sheet). List edges fade (masked).
- Description of the selected model below, 14sp `#6B6B6B`, left at 24dp, crossfades on change.
- Opens ~150ms decelerate, no overshoot; keyboard pushed away; scrim fades in. Selection pill
  snaps to the new row in 150–250ms.

## Effort sheet

- Same sheet; ✕ becomes ← back; title "Effort". Model↔Effort swap ~120ms crossfade/slide.
- Level label ("Low"…"Max") ~22sp medium centred, 24dp above the slider, crossfades ~100ms.
- Slider 347x57dp, fully round, 1.5dp `#ECECEC` rim. Left of thumb `#171717`, right `#F0F0F0`.
  Five stops: 4dp `#8A8A8A` dots; passed stops become hollow 8dp rings. Thumb 31dp `#3A3A3A`
  disc with a 2dp white vertical line ~28dp tall at its right edge. At Max the whole track is dark.
- Drag continuous; on release settles to a stop ~250ms ease-out, no bounce; label changes on
  crossing a stop.
- Explanation 15sp / 21dp line, `#6B6B6B`, left at 24dp, well below the slider.

## Composer expanded (keyboard up)

- Grows to a ~90dp card: input on top; row below: plus, model chip ("Opus 5.5", 12sp, `#ECECEC`,
  24dp tall, fully round), effort chip ("High ⌄").
- Sits directly on the keyboard; bottom bar hidden. Keyboard ~200–300ms decelerate.

## Thread

- Header `#ECECEC`: 44dp menu circle, title ~14sp medium, right pill: new + overflow (88x44, seam).
  Body sheet from the header with ~24dp top corners.
- User bubble: right-aligned, 19dp from edge, fully round (multi-line: large radius), `#E4EFF6`,
  15sp, ~16dp horizontal padding.
- Assistant: no bubble, 18dp left padding, 15sp / 24dp line. H2 ~20sp medium, 12dp top margin;
  bold lead-ins ~600; bullets 5dp dots, 20dp indent, ~4dp between items.
- Action row under the answer: 18dp icons `#6B6B6B` at 36dp spacing, ~8dp below the text; only
  shown once the answer is complete.
- Streaming: "…" placeholder ~0.5s, then blocks append; view follows the end.

## Drawer

- ~88% screen width, `#F2F2F2` surface, page stays visible under a scrim.
- Serif title ~22sp. Section labels ("Pinned", "Recents") 13sp `#8A8A8A`. Rows 44dp, 15sp, 20dp
  leading icon, 18dp trailing meta. Current row `#E4E4E4` rounded highlight (~12dp). "Show more"
  in grey.
- Bottom: 44dp settings gear circle, search field (round `#ECECEC` pill, 44dp) and 44dp `#171717`
  compose button.
- Opens 170–250ms decelerate, no overshoot; closes ~170ms.

## List tabs (Projects / Code)

- Large left-aligned title ~24sp medium on the grey header; right: search + filter circles.
- Section labels 13sp `#8A8A8A`, 24dp left. Rows ~69dp: 16sp title, 13sp `#8A8A8A` subtitle
  (`feat/sort-distance · +46 −31`, diff numbers coloured), status glyph on the right (hollow
  circle / dot / green PR glyph).
- Composer on such a tab reads differently ("Start a coding session"), plus + mic only.
- Tab switch: page crossfade ~150ms.

## Bottom bar pill + tooltips

- Switching tabs slides the active pill to the new icon in 200–300ms, ease-in-out, no overshoot.
- A label tooltip appears above the icon: `#171717`, white 12sp medium, ~22dp tall, fully round,
  10dp horizontal padding, ~14dp above the pill; scale+fade in ~100ms, holds ~1.3s.

## Status toast ("Finished")

- Right-aligned, ~25dp above the composer; ~185x68dp, ~22dp radius, 16dp horizontal padding,
  `#F3F3F3` + 1dp border. 12sp `#6B6B6B` line with a small thread/code icon + thread title; 16sp
  `#1A1A1A` status ("Finished").
- A dot appears on the relevant tab; the toast scales+fades in ~200ms, holds ~2.3s, fades ~100ms.

## Motion summary

Nothing overshoots. Everything is ease-out/decelerate, 100–300ms. Fades 100–150ms; sheets and
drawer 150–250ms. Stagger only on search results and streamed text.
