# Patterns from Google's reference Compose apps

Read 2026-11-24 from androidify, compose-samples (Jetchat, Jetcaster, Jetsnack, JetLagged, Reply)
and nowinandroid. Only androidify and Jetcaster use the expressive theme; Jetchat is the chat
structure reference.

## Theme

androidify `core/theme/.../Theme.kt`:
```kotlin
MaterialExpressiveTheme(
  colorScheme = LightColorScheme, typography = Typography, shapes = shapes,
  motionScheme = MotionScheme.expressive(),
  content = { SharedTransitionLayout {
      CompositionLocalProvider(LocalSharedTransitionScope provides this) { content() } } })
```
- Hand-built `lightColorScheme(...)` with every `surfaceContainer*` role filled; nothing dynamic.
- Jetcaster: one full scheme per light/dark × contrast, picked with a `when`, passed to
  `MaterialExpressiveTheme`. Nobody has user-selectable palettes: ours is
  `scheme(palette, dark): ColorScheme` at the root.
- Fonts: Jetchat pairs downloadable Google Fonts with bundled `R.font.*` fallbacks. Bundle ours:
  downloadable fonts don't resolve in JVM screenshot tests and need a first-run fetch.

## Motion

- Specs from `MaterialTheme.motionScheme` (`slowSpatialSpec()` in a `BoundsTransform`,
  `defaultEffectsSpec()` for nav fades). Jetsnack hand-rolls `spring(0.8f, 380f)` and
  `spring(1f, 1600f)`, the same numbers.
- Shared elements: androidify `sharedBoundsWithDefaults` (RemeasureToBounds, `OverlayClip(shape)`,
  `renderInOverlayDuringTransition = true`); `sharedBoundsRevealWithShapeMorph` clips with a
  `Morph(RoundedPolygon, RoundedPolygon)` path.
- Predictive back: `android:enableOnBackInvokedCallback="true"`; Nav3 `NavDisplay` handles it.
- Drawer: Jetchat uses stock `ModalNavigationDrawer` + `rememberDrawerState`, outside Nav3; closes
  on navigate.
- Cheap animation: read animated values in layout/draw lambdas (`Modifier.offset { }`,
  `graphicsLayer { }`), `derivedStateOf` for scroll thresholds.

## Chat (Jetchat `conversation/`)

- Scaffold excludes nav + IME insets; the input takes them itself:
  `Modifier.imePadding().navigationBarsPadding()` (that order).
- `LazyColumn(reverseLayout = true)`, `key = id`, `contentType` per kind; newest at index 0.
  (Relay already has its own thread scroll; don't swap it out casually.)
- Jump-to-bottom: `derivedStateOf` on first visible index/offset, `updateTransition` offset.
- Composer: `TextFieldState`, `TextFieldLineLimits.MultiLine(maxHeightInLines = 4)`,
  `onKeyboardAction`, an `enum InputSelector` for panels, `BackHandler` only while a panel is open.

## Expressive components seen in use

- `HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarColors(...))` with toggle
  buttons (androidify) or as a bottom pill (Jetcaster). Our composer is a text field, so a plain
  `Surface` pill is the better base.
- `ButtonGroup` + `ToggleButton` with `ButtonGroupDefaults.connectedLeadingButtonShapes()` /
  `connectedTrailingButtonShapes()` → segmented control. `Modifier.animateWidth(interactionSource)`
  squeezes neighbours on press.
- `ContainedLoadingIndicator` (androidify). `MaterialShapes.Cookie9Sided.toShape()` for the camera.

## Screenshot testing

nowinandroid: Roborazzi + Robolectric, `@GraphicsMode(NATIVE)`, `createAndroidComposeRule`,
`RuntimeEnvironment.setQualifiers("w${w}dp-h${h}dp-${dpi}dpi")`,
`DeviceConfigurationOverride(DarkMode(dark))`, `onRoot().captureRoboImage(path)`.
See `android/AGENTS.md` "Seeing UI without a device" for what this repo actually uses.
