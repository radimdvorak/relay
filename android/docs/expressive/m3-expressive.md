# Material 3 Expressive: what matters for Relay

Research notes from 2026-11-24. Motion and shape numbers come from androidx source; items marked
*(unverified)* came from press coverage, since m3.material.io doesn't render for fetchers.

## Motion

`MotionScheme.standard()` and `MotionScheme.expressive()`, each with `{fast,default,slow}SpatialSpec()`
(bounds/position, may overshoot) and `{fast,default,slow}EffectsSpec()` (colour/alpha, no overshoot).
Expressive is "for prominent UI elements and hero interactions"; standard "for utilitarian UI
elements and recurring interactions".

| Token (damping / stiffness) | Expressive | Standard |
|---|---|---|
| Fast spatial | 0.6 / 800 | 0.9 / 1400 |
| Default spatial | 0.8 / 380 | 0.9 / 700 |
| Slow spatial | 0.8 / 200 | 0.9 / 300 |
| Fast / default / slow effects | 1.0 / 3800, 1600, 800 | same |

## Shape

Corner scale: 4, 8, 12, 16, 20 (LargeIncreased), 28, 32 (ExtraLargeIncreased), 48 (ExtraExtraLarge),
full. `MaterialShapes` (35 RoundedPolygons, `toShape()`) is still experimental. Pressed/selected
states morph corner radius (round ↔ squarer).

## Containment

Group related rows in tonal containers: large outer radius, small radius between neighbours
(Pixel Settings, Gmail). Preferred over dividers.

## Components (material3 1.5.0)

Stable: ButtonGroup, ToggleButtons, SplitButton, HorizontalFloatingToolbar, FAB menu, wavy progress,
flexible top/bottom app bars, expressive menu items. Experimental: LoadingIndicator /
ContainedLoadingIndicator, MaterialShapes, AppBarWithSearch, static sheet.

- Floating toolbar: contextual actions for the current page; don't pair it with a bottom nav bar
  on the same screen. Nav bar on primary pages, toolbars on subsequent pages.
- Toggle buttons: selection shown by shape morph.
- Loading indicator: short waits; wavy progress: determinate or long.
- Don't remove text labels from primary actions (Google's research: hurts usability).

## For Relay (calm, text-heavy, Claude-app look)

Do
- Expressive spatial springs for hero moments only: sheets, composer expand, drawer, bar pill,
  send button.
- Grouped containers for list rows (threads, projects, PRs, settings).
- Toggle buttons / connected button group for choices; the effort slider in a sheet.
- Floating toolbar on git changes/review screens (stage, commit, push) — screens without the bar.
- LoadingIndicator for connecting/refresh; wavy progress for long agent runs.

Don't
- No bouncy springs on chat scroll, message appearance, streaming text or tool-call rows:
  effects springs or `standard()` there.
- No tonal colour behind message text; the chat stays on plain surface.
- No MaterialShapes cookies/clovers as decoration.
- Experimental APIs only with a fallback.

## Sources

- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://design.google/library/expressive-material-design-google-research
- https://9to5google.com/2025/05/18/material-3-expressive-toolbars/
- m3.material.io: blog/building-with-m3-expressive, styles/motion, components/toolbars,
  components/button-groups (open in a browser)
