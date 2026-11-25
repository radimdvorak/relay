# Compose API reference for the expressive redesign

Verified 2026-11-24 against the real artifacts, not memory: the BOM pom
`androidx.compose:compose-bom:2026.11.01` plus the `-sources.jar` and `.aar` of each module
from `dl.google.com/android/maven2`, unzipped and grepped. "Stable" means no opt-in
annotation on the declaration or its enclosing class. Line refs are `File.kt:line` in the
sources jar of the module named in the section title.

## Versions and build requirements

| Artifact (BOM 2026.11.01) | Version |
|---|---|
| material3-android, material3-adaptive-navigation-suite | 1.5.0 |
| foundation, foundation-layout, ui, ui-graphics, ui-text, animation, animation-core, runtime | 1.13.0 |
| graphics-shapes (transitive, `RoundedPolygon`/`Morph`) | 1.0.1 |

- **compileSdk**: `aar-metadata.properties` says `minCompileSdk=37`; `minCompileMinorSdk=1`
  for ui and foundation (material3 is `0`). `minAndroidGradlePluginVersion=9.1.0`. That
  needs `compileSdk { version = release(37) { minorApiLevel = 1 } }`, which `app/` already has
  (AGP 9.4.1).
- **Kotlin**: the Gradle module metadata needs `kotlin-stdlib` 2.2.20 or newer. The project's
  2.4.21 plus the `org.jetbrains.kotlin.plugin.compose` plugin covers it.
- The AARs have `minSdk` 24. The app's `minSdk = 33`, so every blur API below works and
  needs no fallback branch.

## The opt-in picture changed: most of Expressive is stable in 1.5.0

Only these still carry **`@ExperimentalMaterial3ExpressiveApi`**:
- `MaterialShapes` (the whole class), `RoundedPolygon.toShape()`, `RoundedPolygon.toPath()`, `Morph.toPath()`
- `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults`,
  `PullToRefreshDefaults.LoadingIndicator` plus its two colour vals
- `DropdownMenuItem` checked/selected overloads (`Menu.kt:550`, `:792`)
- one hidden-deprecated `SliderDefaults.Thumb(…, sliderState, …)` overload

These still carry **`@ExperimentalMaterial3Api`**: `ModalBottomSheet`, `BottomSheet`, `SheetState`,
`SheetValue`, `BottomSheetDefaults`, `rememberBottomSheetState`, `ModalBottomSheetProperties`,
`BottomSheetScaffold`, `TooltipBox`, `PlainTooltip`, `RichTooltip`, `TooltipDefaults`,
`TooltipState`/`rememberTooltipState`, `TooltipAnchorPosition`, `AppBarWithSearch`,
`appBarWithSearchColors`, `Label` and the carousel parallax effect.

**Stable, no opt-in**: `MaterialExpressiveTheme`, `MotionScheme`, the new `Shapes` slots,
emphasized typography, `ButtonGroup`, `ToggleButton` (all four), `SplitButtonLayout`,
`FloatingActionButtonMenu`, `ToggleFloatingActionButton`, `Horizontal/VerticalFloatingToolbar`,
`FlexibleBottomAppBar`, `ShortNavigationBar`, `WideNavigationRail`, `ModalWideNavigationRail`, the
wavy progress indicators, `Slider`/`SliderState`, `ModalNavigationDrawer` and the other drawers,
`SearchBar(state, …)`, `ExpandedFullScreenSearchBar`, every `TopAppBar` variant including the
Flexible ones, `TextField(state = TextFieldState)`. Every Compose 1.13 foundation/ui API below
is stable too, including `SharedTransitionLayout`.

## Theme

### MaterialExpressiveTheme (`androidx.compose.material3`, stable)
```kotlin
fun MaterialExpressiveTheme(colorScheme: ColorScheme? = null, motionScheme: MotionScheme? = null,
    shapes: Shapes? = null, typography: Typography? = null, content: @Composable () -> Unit)
```
The outermost call fills a null with `expressiveLightColorScheme()` (light only),
`MotionScheme.expressive()`, `Shapes()` and `Typography()`. A nested call inherits from the
enclosing theme instead. **Pass a dark `colorScheme` yourself**, or the app comes out light.
```kotlin
MaterialExpressiveTheme(colorScheme = if (dark) darkColorScheme(...) else expressiveLightColorScheme()) {
    App()
}
```
Plain `MaterialTheme(colorScheme, motionScheme, shapes, typography, content)` also takes a
`motionScheme` now.

### MotionScheme (stable)
`interface MotionScheme { fun <T> {default,fast,slow}{Spatial,Effects}Spec(): FiniteAnimationSpec<T> }`,
plus the factories `MotionScheme.expressive()` and `MotionScheme.standard()`. Read it through
`MaterialTheme.motionScheme`. There is no public `LocalMotionScheme`.
Expressive springs (damping/stiffness): spatial fast 0.6/800, default 0.8/380, slow 0.8/200;
effects 1.0 with stiffness 3800/1600/800.
```kotlin
val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()    // position, size, may overshoot
val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()       // colour, alpha
val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spatial)
```

### Shapes (stable)
`Shapes(extraSmall, small, medium, large, extraLarge, largeIncreased, extraLargeIncreased, extraExtraLarge)`.
The new slots are `largeIncreased` 20dp, `extraLargeIncreased` 32dp and `extraExtraLarge` 48dp.
The others: 4, 8, 12, 16, 28. `ShapeDefaults.LargeIncreased` and the rest are stable.
```kotlin
Surface(shape = MaterialTheme.shapes.extraLargeIncreased) { … }
```

### Emphasized typography (stable)
`Typography` has `{display,headline,title,body,label}{Large,Medium,Small}Emphasized`, 15
`TextStyle`s with a heavier weight. A new constructor `Typography(fontFamily: FontFamily,
displayLarge: TextStyle? = null, …)` applies one family to every style, emphasized ones
included.
```kotlin
Text("Effort", style = MaterialTheme.typography.titleMediumEmphasized)
val type = Typography(fontFamily = InterFamily)
```

### MaterialShapes + morphing (`@ExperimentalMaterial3ExpressiveApi`)
`MaterialShapes.{Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond,
ClamShell, Pentagon, Gem, Sunny, VerySunny, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided,
Cookie12Sided, Ghostish, Clover4Leaf, Clover8Leaf, Burst, SoftBurst, Boom, SoftBoom, Flower, Puffy,
PuffyDiamond, PixelCircle, PixelTriangle, Bun, Heart}`: 35 normalized
`androidx.graphics.shapes.RoundedPolygon`s.
- `@Composable fun RoundedPolygon.toShape(startAngle: Int = 0): Shape` is remembered and scales
  to the layout size.
- `fun Morph.toPath(progress: Float, path: Path = Path(), startAngle: Int = 0): Path`. There is
  no `Morph.toShape()`: write a small `Shape` around `toPath`.
```kotlin
Box(Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(c))
val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
val shape = GenericShape { size, _ -> addPath(morph.toPath(progress).apply {
    transform(Matrix().apply { scale(size.width, size.height) }) }) }   // normalized 0..1 space
```
The foundation `PolygonShape` described below is the stable alternative. There is **no public
bridge** from `RoundedPolygon` or `MaterialShapes` to `PolygonShape`.

## Buttons

### ButtonGroup (stable)
```kotlin
fun ButtonGroup(overflowIndicator: @Composable (ButtonGroupMenuState) -> Unit, modifier: Modifier = Modifier,
    expandedRatio: Float = ButtonGroupDefaults.ExpandedRatio, horizontalArrangement: Arrangement.Horizontal = …,
    verticalAlignment: Alignment.Vertical = Alignment.Top, content: ButtonGroupScope.() -> Unit)
```
`content` is a **DSL, not a composable**: `clickableItem(onClick, label, icon, weight, enabled)`,
`toggleableItem(checked, label, onCheckedChange, icon, weight, enabled)` and
`customItem(buttonGroupContent, menuContent)`. Items that don't fit go into the overflow menu.
`Modifier.animateWidth(interactionSource)` gives the press squish.
```kotlin
ButtonGroup(overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) }) {
    toggleableItem(checked = mode == Fast, label = "Fast", onCheckedChange = { mode = Fast })
    toggleableItem(checked = mode == Deep, label = "Deep", onCheckedChange = { mode = Deep })
}
```
For a connected segmented row, use plain `ToggleButton`s spaced by
`ButtonGroupDefaults.ConnectedSpaceBetween` with
`ButtonGroupDefaults.connected{Leading,Middle,Trailing}ButtonShapes()` (`@Composable`, returns
`ToggleButtonShapes`).

### ToggleButton (stable), plus Elevated, FilledTonal and Outlined variants
```kotlin
fun ToggleButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    buttonSize: ToggleButtonSize = ToggleButtonDefaults.size, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(buttonSize), colors: ToggleButtonColors = …, …,
    content: @Composable RowScope.() -> Unit)
```
`ToggleButtonSize.{ExtraSmall, Small, Medium, Large, ExtraLarge}`. The shape morphs between
round and square when checked or pressed.
```kotlin
ToggleButton(checked, { checked = it }, shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()) { Text("Plan") }
```

### SplitButton (stable): **there is no `SplitButton()` composable**
Use `SplitButtonLayout(leadingButton, trailingButton, modifier, spacing)` with
`SplitButtonDefaults.LeadingButton(onClick, …)` and
`SplitButtonDefaults.TrailingButton(checked, onCheckedChange, …)`. Tonal, Outlined and Elevated
variants of both exist, with sizes from `ExtraSmall` to `ExtraLarge` (`*ContainerHeight`,
`*InnerCornerSize`).
```kotlin
SplitButtonLayout(
    leadingButton = { SplitButtonDefaults.LeadingButton(onClick = send) { Text("Send") } },
    trailingButton = { SplitButtonDefaults.TrailingButton(menuOpen, { menuOpen = it }) { Icon(Icons.Default.KeyboardArrowDown, null) } },
)
```

### FloatingActionButtonMenu (stable)
`FloatingActionButtonMenu(expanded, button, modifier, horizontalAlignment = End, content: FloatingActionButtonMenuScope.() -> Unit)`.
Items are `FloatingActionButtonMenuItem(onClick, text, icon, …)`. The button is usually
`ToggleFloatingActionButton(checked, onCheckedChange, …) { Icon(…, Modifier.animateIcon({ checkedProgress })) }`.
```kotlin
FloatingActionButtonMenu(expanded, button = { ToggleFloatingActionButton(expanded, { expanded = it }) {
    Icon(if (checkedProgress > .5f) Icons.Filled.Close else Icons.Filled.Add, null, Modifier.animateIcon({ checkedProgress })) } }) {
    FloatingActionButtonMenuItem(onClick = newThread, text = { Text("New thread") }, icon = { Icon(Icons.Filled.Edit, null) })
}
```

## Bars, toolbars and navigation

### HorizontalFloatingToolbar / VerticalFloatingToolbar (stable)
```kotlin
fun HorizontalFloatingToolbar(expanded: Boolean, modifier: Modifier = Modifier, colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues = …, scrollBehavior: FloatingToolbarScrollBehavior? = null, shape: Shape = FloatingToolbarDefaults.ContainerShape,
    leadingContent: (@Composable RowScope.() -> Unit)? = null, trailingContent: (@Composable RowScope.() -> Unit)? = null,
    expandedShadowElevation: Dp = …, collapsedShadowElevation: Dp = …, content: @Composable RowScope.() -> Unit)
```
A second overload takes `floatingActionButton` (use `FloatingToolbarDefaults.VibrantFloatingActionButton`
or `StandardFloatingActionButton`), `floatingActionButtonPosition` and `animationSpec`. The
scroll behaviour is a `NestedScrollConnection`.
```kotlin
val sb = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
Box(Modifier.nestedScroll(sb)) { LazyColumn { … }
    HorizontalFloatingToolbar(expanded = true, scrollBehavior = sb, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = Modifier.align(BottomCenter).offset(y = -FloatingToolbarDefaults.ScreenOffset)) { IconButton(…) } }
```
`FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll(expanded, onExpand, onCollapse)`
expands and collapses the toolbar from scrolling.

### FlexibleBottomAppBar (stable)
`FlexibleBottomAppBar(modifier, containerColor, contentColor, contentPadding, horizontalArrangement = BottomAppBarDefaults.FlexibleHorizontalArrangement, expandedHeight = BottomAppBarDefaults.FlexibleBottomAppBarHeight, windowInsets, scrollBehavior: BottomAppBarScrollBehavior? = null, content: RowScope.() -> Unit)`.
Scrolling: `BottomAppBarDefaults.exitAlwaysScrollBehavior()`. This is what the spec calls the
"docked toolbar". `DockedToolbar` exists only as tokens, never as a composable.

### ShortNavigationBar + ShortNavigationBarItem (stable)
```kotlin
ShortNavigationBar(modifier, containerColor, contentColor, windowInsets, arrangement = ShortNavigationBarDefaults.arrangement) {
    ShortNavigationBarItem(selected, onClick, icon = { Icon(…) }, label = { Text("Threads") },
        iconPosition = NavigationItemIconPosition.Top /* or .Start on wide */)
}
```
`ShortNavigationBarArrangement.{EqualWeight, Centered}`. `label` is required but nullable.

### WideNavigationRail / ModalWideNavigationRail (stable)
`WideNavigationRail(modifier, state = rememberWideNavigationRailState(), shape, colors, header, windowInsets, arrangement, contentPadding, content)`
and `ModalWideNavigationRail(…, hideOnCollapse = false, collapsedShape, expandedShape, expandedHeaderTopPadding, expandedProperties, …)`.
Items are `WideNavigationRailItem(selected, onClick, icon, label, railExpanded: Boolean, …)`.
State: `WideNavigationRailValue.{Collapsed, Expanded}`, with `expand()`, `collapse()`,
`toggle()` and `snapTo()`.
```kotlin
val rail = rememberWideNavigationRailState()
ModalWideNavigationRail(state = rail, hideOnCollapse = true) {
    WideNavigationRailItem(selected, onClick, { Icon(…) }, { Text("Inbox") }, railExpanded = rail.targetValue == WideNavigationRailValue.Expanded) }
```

### TopAppBar variants (all stable now)
`TopAppBar(title, …)`, `TopAppBar(title, subtitle, …, titleHorizontalAlignment)`,
`CenterAlignedTopAppBar`, `MediumTopAppBar`, `LargeTopAppBar`,
`MediumFlexibleTopAppBar(title, subtitle?, …, titleHorizontalAlignment, collapsedHeight, expandedHeight)`,
`LargeFlexibleTopAppBar(…)` and `TwoRowsTopAppBar(title: (expanded) -> Unit, …)`. Scroll
behaviours: `TopAppBarDefaults.{pinned,enterAlways,exitUntilCollapsed}ScrollBehavior()`. New
overloads take a `scrollableState`. `AppBarRow`/`AppBarColumn` handle action overflow (DSL with
`clickableItem`/`toggleableItem`).
```kotlin
val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
LargeFlexibleTopAppBar(title = { Text("Relay") }, subtitle = { Text("3 running") }, scrollBehavior = sb)
```

### ModalNavigationDrawer / DismissibleNavigationDrawer (stable)
`ModalNavigationDrawer(drawerContent, modifier, drawerState = rememberDrawerState(Closed), gesturesEnabled, scrimColor, content)`;
`DismissibleNavigationDrawer` is the same without `scrimColor`.
- **Predictive back** only comes with the sheet overloads that take the state:
  `ModalDrawerSheet(drawerState, …)` or `DismissibleDrawerSheet(drawerState, …)`. The overload
  without it never animates on back.
- **Width**: no width parameter. `DrawerSheet` applies `sizeIn(maxWidth = 360.dp)` *after*
  your modifier, and `sizeIn` enforces the incoming constraints, so a fixed width set by your
  modifier wins. `Modifier.fillMaxWidth(0.88f)` therefore gives 88%, judging from the source;
  not run on a device. The drawer's closed anchor is `-measuredWidth`, so drag and close follow
  the real width. Only the overshoot stretch maths still assumes 360dp.
```kotlin
val drawer = rememberDrawerState(DrawerValue.Closed)
ModalNavigationDrawer(drawerState = drawer, drawerContent = {
    ModalDrawerSheet(drawerState = drawer, modifier = Modifier.fillMaxWidth(0.88f)) { ThreadList() } }) { Main() }
```

### Tooltips: long-press labels on bar icons (`@ExperimentalMaterial3Api`)
`TooltipBox(positionProvider, tooltip: TooltipScope.() -> Unit, state, modifier, onDismissRequest, focusable = false, enableUserInput = true, hasAction = false, content)`.
With `enableUserInput = true`, the default, the box shows the tooltip on long press (and on
hover). Positioning: `TooltipDefaults.rememberTooltipPositionProvider(positioning: TooltipAnchorPosition, spacingBetweenTooltipAndAnchor)`
with `TooltipAnchorPosition.{Above, Below, Left, Right, Start, End}`. The no-argument
`rememberPlainTooltipPositionProvider()` and its siblings are deprecated.
`PlainTooltip(modifier, caretShape: Shape? = null, maxWidth = 200.dp, shape, contentColor, containerColor, …)`;
add a caret with `caretShape = TooltipDefaults.caretShape()`.
```kotlin
TooltipBox(TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip(caretShape = TooltipDefaults.caretShape()) { Text("Threads") } },
    state = rememberTooltipState()) { IconButton(onClick) { Icon(…, contentDescription = "Threads") } }
```

### Search
- `SearchBar(state: SearchBarState, inputField, modifier, shape, colors, tonalElevation, shadowElevation)` (stable)
  is the collapsed bar.
- `ExpandedFullScreenSearchBar(state, inputField, …, properties: DialogProperties = DialogProperties(), content)` (stable).
  It is a dialog, so `DialogProperties(blurBehindRadius = …)` works on it.
  `ExpandedFullScreenContainedSearchBar`, `ExpandedDockedSearchBar` and `ExpandedDockedSearchBarWithGap` also exist.
- `SearchBarDefaults.InputField(textFieldState, searchBarState, onSearch, …)` (stable) takes a `TextFieldState`.
- `AppBarWithSearch(state, inputField, modifier, navigationIcon, actions, …, scrollBehavior: SearchBarScrollBehavior?)`
  is **`@ExperimentalMaterial3Api`**. `TopSearchBar` is deprecated in its favour. Scrolling:
  `SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()`.
- The old `SearchBar(query/expanded/active, …)` and `DockedSearchBar` overloads are deprecated.
```kotlin
val sbs = rememberSearchBarState(); val text = rememberTextFieldState()
val field = @Composable { SearchBarDefaults.InputField(text, sbs, onSearch = { run(it) }, placeholder = { Text("Search threads") }) }
SearchBar(sbs, field); ExpandedFullScreenSearchBar(sbs, field) { Results(text.text) }
```

## Sheets (`@ExperimentalMaterial3Api`)
```kotlin
fun ModalBottomSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden), sheetMaxWidth: Dp = 640.dp,
    sheetGesturesEnabled: Boolean = true, shape: Shape = BottomSheetDefaults.ExpandedShape, containerColor, contentColor,
    tonalElevation: Dp = 0.dp, scrimColor: Color = BottomSheetDefaults.ScrimColor, dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = …, properties: ModalBottomSheetProperties = ModalBottomSheetProperties(), content: ColumnScope.() -> Unit)
```
- New: `rememberBottomSheetState(initialValue, enabledValues: Set<SheetValue> = setOf(Hidden, PartiallyExpanded, Expanded), confirmValueChange)`.
  **`rememberModalBottomSheetState(skipPartiallyExpanded)` is deprecated**; replace it with
  `enabledValues = setOf(Hidden, Expanded)`.
- New: a non-modal `BottomSheet(modifier, state, onDismissRequest, maxWidth, gesturesEnabled, backHandlerEnabled, dragHandle, …)`.
- `ModalBottomSheetProperties(securePolicy, shouldDismissOnBackPress, shouldDismissOnClickOutside)`,
  with an overload adding `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars`.
  **No blur or scrim-alpha parameter.** Use `scrimColor`, or draw your own blur.
```kotlin
val s = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
if (open) ModalBottomSheet(onDismissRequest = { open = false }, sheetState = s) { ModelPicker() }
```

## Progress and loading

- `LoadingIndicator(modifier, color, polygons)` and `LoadingIndicator(progress: () -> Float, …)`;
  `ContainedLoadingIndicator(…, containerColor, indicatorColor, containerShape, polygons)`.
  **`@ExperimentalMaterial3ExpressiveApi`**. Defaults: `LoadingIndicatorDefaults.IndeterminateIndicatorPolygons`
  and `DeterminateIndicatorPolygons` (`List<RoundedPolygon>`), size `IndicatorSize`.
- `LinearWavyProgressIndicator(progress: () -> Float, …, amplitude: (Float) -> Float, wavelength, waveSpeed)`
  plus an indeterminate overload, and `CircularWavyProgressIndicator` the same way. **Stable.**
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class) ContainedLoadingIndicator()
LinearWavyProgressIndicator(progress = { upload }, modifier = Modifier.fillMaxWidth())
```

## Slider (stable) and the chunky 5-stop "effort" slider

- **The value-based `Slider(value, onValueChange, …, valueRange, steps)` is deprecated** (warning)
  in favour of the state overload:
```kotlin
fun Slider(state: SliderState, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null, colors: SliderColors = SliderDefaults.colors(), interactionSource: MutableInteractionSource = …,
    thumb: @Composable (SliderState) -> Unit = { SliderDefaults.Thumb(…) }, track: @Composable (SliderState) -> Unit = { SliderDefaults.Track(…) })
```
- `SliderState(value = 0f, steps = 0, trackRange = 0f..1f)`; the range parameter is now
  **`trackRange`, not `valueRange`**. `rememberSliderState(value, steps, trackRange)` is saveable.
  Readable: `value` (settable, snaps to ticks while `shouldAutoSnap`), `coercedValueAsFraction`,
  `isDragging`, `steps`, `trackRange`.
- `onValueChange` must write `state.value = it` itself, as the KDoc says.
- `VerticalSlider(state, onValueChange, …, topToBottom)`, `RangeSlider(RangeSliderState, …)`.
- `SliderDefaults.Track(sliderState, modifier, enabled, colors, drawStopIndicator, drawTick, thumbTrackGapSize, trackInsideCornerSize)`,
  plus an overload with `trackCornerSize`, and `DrawScope.drawStopIndicator(offset, size, color)`.

**How the layout treats a custom track and thumb** (from `SliderImpl`, `Slider.kt:~950-1080`):
the thumb lambda measures at its own size. The track gets `width = sliderWidth - thumbWidth`
and `minHeight = 0`, so it is as tall as you make it, and sits inset by `thumbWidth / 2` on
each side. Slider height is `max(trackHeight, thumbHeight)`, with a 16dp floor plus
`minimumInteractiveComponentSize`. The thumb centre lands at `fraction * trackWidth`. The
default Track also publishes an internal `CornerSizeAlignmentLine` that pulls stepped
positions in by the corner radius. A custom Canvas track doesn't publish it, so stops are
**linear**: draw the stop at `i / steps+1 * size.width` and the thumb sits exactly on it.
Canvas doesn't clip, so a track can paint past its bounds under the thumb ends. Alternatively,
put the capsule background on the `Slider` modifier, which spans thumb plus track.

Five stops means `steps = 3` over `trackRange = 0f..4f` (values 0,1,2,3,4):
```kotlin
val effort = rememberSliderState(value = 2f, steps = 3, trackRange = 0f..4f)
Slider(state = effort, onValueChange = { effort.value = it },
    modifier = Modifier.height(48.dp).clip(CircleShape).background(Color(0xFF1C1B1F)),
    thumb = { Box(Modifier.size(40.dp).background(Color.White, CircleShape)) },
    track = { s -> Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val x = s.coercedValueAsFraction * size.width
        drawRoundRect(accent, Offset(-20.dp.toPx(), 4.dp.toPx()), Size(x + 20.dp.toPx(), size.height - 8.dp.toPx()), CornerRadius(size.height))
        repeat(5) { i -> drawCircle(if (i / 4f <= s.coercedValueAsFraction) onAccent else dim, 3.dp.toPx(), Offset(i / 4f * size.width, center.y)) }
    } })
```
Haptics per stop: watch `snapshotFlow { effort.value }.distinctUntilChanged()`. Not built or
run here; it is assembled from the verified signatures and layout code.

## State-based TextField in M3 (stable)
`TextField(state: TextFieldState, modifier, enabled, readOnly, textStyle, labelPosition: TextFieldLabelPosition = Inside(), label: (TextFieldLabelScope.() -> Unit)?, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, inputTransformation, outputTransformation, keyboardOptions, onKeyboardAction: KeyboardActionHandler?, lineLimits: TextFieldLineLimits = Default, onTextLayout, scrollState, shape, colors, contentPadding, interactionSource)`.
`OutlinedTextField(state, …)` defaults to `labelPosition = Cutout()`. `SecureTextField(state, …, textObfuscationMode)`.
```kotlin
val draft = rememberTextFieldState()
TextField(draft, lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 8), placeholder = { Text("Message") },
    onKeyboardAction = { send(draft.text.toString()); draft.clearText() })
```

## Compose 1.13 foundation / ui / animation / runtime (all stable)

### Progressive blur: `Modifier.blur(BlurRadiusSpec)` (`androidx.compose.ui.draw`, spec in `androidx.compose.ui.graphics.blur`)
```kotlin
fun Modifier.blur(blurRadiusSpec: BlurRadiusSpec, edgeTreatment: BlurredEdgeTreatment = Rectangle, alpha: Float = 1f): Modifier
fun Modifier.blur(block: BlurScope.() -> Unit): Modifier   // BlurScope: size (DpSize), blurRadiusSpec, edgeTreatment, alpha; animatable
```
- Specs: `BlurRadiusSpec.uniform(r)`, `verticalGradient(start, end)`, `verticalGradient(stops)`,
  `horizontalGradient(…)`, `linearGradient(start: DpOffset, end: DpOffset, …)`,
  `radialGradient(…, center, fallOffRadius)` and `shader(maxRadius) { sizePx -> Shader }`.
  `BlurStop(fraction: Float 0..1, radius: Dp)`, at most 16 stops.
- **API levels**: uniform needs API 31 (S) and above; any varying radius needs **API 33**.
  Below that the modifier is *ignored* and draws unblurred, with no crash and no fallback blur.
  Varying radii cap at 150px; uniform has no cap. Our minSdk is 33, so both always work.
- It blurs **its own content**, not what's behind it. A frosted bar over a scrolling list needs
  the list (or a captured `GraphicsLayer` of it) blurred under the bar's bounds. There is no
  backdrop-blur modifier.
```kotlin
LazyColumn(Modifier.blur(BlurRadiusSpec.verticalGradient(listOf(BlurStop(0f, 24.dp), BlurStop(0.12f, 0.dp))),
    BlurredEdgeTreatment.Unbounded)) { … }   // the top 12% fades from 24dp blur to sharp
```

### Window blur and scrim on dialogs and popups (`androidx.compose.ui.window`)
`DialogProperties(…, blurBehindRadius: Dp = Unspecified, backgroundBlurRadius: Dp = Unspecified, scrimAlpha: Float = NaN, windowShape: Shape? = null)`;
`PopupProperties(focusable, …, blurBehindRadius, scrimAlpha)`. These are cross-window blurs:
API 31 and above, and the system can switch them off (battery saver, weak GPUs). Set
`windowShape` so `backgroundBlurRadius` clips to rounded corners. The KDoc suggests about
10dp for blur-behind, about 30dp for frosted background, and nothing over 50dp.
```kotlin
Dialog(onDismiss, DialogProperties(blurBehindRadius = 10.dp, scrimAlpha = 0.3f, windowShape = RoundedCornerShape(28.dp))) { Card { … } }
```

### PolygonShape / MorphPolygonShape (`androidx.compose.foundation.shape`)
- `PolygonShape.{regularPolygon(n, rounding), star(points, innerRadiusRatio, outerRounding, innerRounding), pill(smoothing), pillStar(…), circle(n), rectangle(rounding | 4 corners), absoluteRectangle(…)}`.
- Custom: `PolygonShape { polygon(vertices = listOf(...), rounding = CornerRounding.dp(8.dp)) }`, and
  `.transform { rotate(…); scale(…); scaleToFit() }`.
- **`CornerRounding` here is `androidx.compose.foundation.shape.CornerRounding`**:
  `CornerRounding.dp(radius, smoothing)`, `.fraction(f, smoothing)`, `.Unrounded`. It clashes by
  name with `androidx.graphics.shapes.CornerRounding`, so import carefully.
- `RoundedCornerShape.toPolygonShape()` (and CutCorner and the Absolute variants) converts
  existing shapes so they can morph.
- `MorphPolygonShape(start: PolygonShape, end: PolygonShape, progress: () -> Float): Shape`
  reads progress at outline time, so animating it recomposes nothing. Use one instance per element.
```kotlin
val t by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
val shape = remember { MorphPolygonShape(RoundedCornerShape(50).toPolygonShape(), PolygonShape.star(8, 0.8f, CornerRounding.fraction(0.3f))) { t } }
Box(Modifier.size(56.dp).clip(shape).background(c))
```

### CapturedAnimatedVisibility (`androidx.compose.animation`)
`CapturedAnimatedVisibility(visible, modifier, enter = fadeIn() + expandIn(), exit = shrinkOut() + fadeOut(), label, content)`,
or with `visibleState: MutableTransitionState<Boolean>`. On hide, the content leaves
composition **immediately** and the exit animates a captured bitmap layer. That suits rows
whose data is already gone. The exit takes no input and gets no `AnimatedVisibilityScope`.
```kotlin
CapturedAnimatedVisibility(visible = card != null) { card?.let { ApprovalCard(it) } }
```

### computedStateOf (`androidx.compose.runtime`)
`fun <T> computedStateOf(policy = structuralEqualityPolicy(), calculation: () -> T): State<T>` is
`derivedStateOf` without the cache: it recalculates on every read but only invalidates readers
when the result changes. Prefer it for cheap calculations. `neverEqualPolicy` counts as an error.
```kotlin
val showJump = remember { computedStateOf { listState.firstVisibleItemIndex > 3 } }
```

### FlexBox (`androidx.compose.foundation.layout`)
`inline fun FlexBox(modifier, config: FlexBoxConfig = FlexBoxConfig, content: FlexBoxScope.() -> Unit)`.
`FlexBoxConfig` is a `fun interface` whose receiver `FlexBoxConfigScope` has `direction`,
`wrap`, `justifyContent`, `alignItems`, `alignContent`, `maxItemsInEachLine`, `gap(…)`,
`rowGap`, `columnGap` and `constraints` (for responsive configs). Per item:
`Modifier.flex { grow(1f); shrink(0f); basis(…); alignSelf(…); order(…) }`. A CSS-like `Grid`
also arrived (`Grid(config, modifier) { … }`).
```kotlin
FlexBox(config = FlexBoxConfig { wrap(FlexWrap.Wrap); gap(8.dp) }) { chips.forEach { Chip(it, Modifier.flex { grow(1f) }) } }
```

### LazyLayoutCacheWindow (`androidx.compose.foundation.lazy.layout`)
`LazyLayoutCacheWindow(ahead: Dp = 0.dp, behind: Dp = 0.dp, isNonScrollCachingEnabled = true)`, or
the `aheadFraction`/`behindFraction` form, as a fraction of the viewport. It is now a
**parameter on `LazyColumn`/`LazyRow`** (`cacheWindow = LazyListDefaults.cacheWindow(state)` by
default: ahead 10-50% of the viewport, none behind). The `rememberLazyListState(cacheWindow)`
overload is deprecated.
```kotlin
LazyColumn(state = list, cacheWindow = LazyLayoutCacheWindow(ahead = 600.dp, behind = 300.dp)) { … }
```

### LineHeightStyle.Mode.PerLine (`androidx.compose.ui.text.style`)
`LineHeightStyle(alignment, trim, mode)` with modes `Fixed` (default), `Minimum`, `Tight` and
**`PerLine`**. `PerLine` measures every line on its own, where the others measure only the
first line, so text with mixed sizes or scripts (emoji, CJK) stops clipping.
```kotlin
style = MaterialTheme.typography.bodyLarge.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.PerLine))
```

### SharedTransitionLayout: stable
`SharedTransitionLayout(modifier, content: SharedTransitionScope.() -> Unit)`, `Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope, boundsTransform, placeholderSize, renderInOverlayDuringTransition, zIndexInOverlay, clipInOverlayDuringTransition)`,
`sharedBounds`, `skipToLookaheadSize` and `renderInSharedTransitionScopeOverlay`. None of these
declarations carries `@ExperimentalSharedTransitionApi` any more, so drop the opt-ins.

## Rumoured or assumed, but not in these artifacts
- `SplitButton(...)` as one composable: no. Use `SplitButtonLayout` + `SplitButtonDefaults.*Button`.
- `DockedToolbar` composable: tokens only. Use `FlexibleBottomAppBar`.
- `FlexibleNavigationBar`, `ExpressiveNavigationBar`, `ExpressiveTheme`, `ModalExpandedNavigationRail`:
  none. Use `ShortNavigationBar`, `MaterialExpressiveTheme` and `ModalWideNavigationRail`.
- `Morph.toShape()`, `MaterialShapes.X.toShape()` as a non-composable, or a `RoundedPolygon` →
  `PolygonShape` bridge: none. Only `@Composable RoundedPolygon.toShape()` exists.
- `LocalMotionScheme`: not public.
- `Modifier.backdropBlur`, `Modifier.progressiveBlur`, `Modifier.backgroundBlur`: none. It's
  `Modifier.blur(BlurRadiusSpec)`, and it blurs its own content only.
- Blur or scrim-alpha on `ModalBottomSheet`/`ModalBottomSheetProperties`: none. Dialogs and
  popups have it.
- A width parameter on `ModalDrawerSheet`: none. Use a modifier (see the drawer section).
- `CenteredSlider` composable: none. Only a sample name in the KDoc.
- `rememberSliderState(valueRange = …)`: renamed to `trackRange`.
