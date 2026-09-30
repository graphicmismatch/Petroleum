# Gui (`Petroleum.Gui`)

A JavaFX visual node-graph editor - plain Java scene-graph construction, no
FXML, since the layout is data-driven (an arbitrary node/wire graph) rather
than fixed. Built against [Patch](patch.md) and [Audio](audio.md) as fixed
APIs. Unlike the rest of the package, `NodeCatalog` deliberately *does*
read `Graph.NodeFactory` and `Oscillators.OscillatorFactory` (read-only,
via their `names()`/`create()` methods) - this is a one-way exception, not a
general policy change: those two factories still know nothing about the
Gui's categories or menu structure.

- **`QuickView`** - the optional generator screen, and the reason
  [Generators](generators.md) exists. Eight category buttons, an offline
  preview, one slider per macro, Play / Mutate / Export WAV..., a code field,
  and a roll history of the last 20 sounds. The graph is not visible here at
  all; **Open in graph editor →** is the only way back out of it, and it
  loads the current sound into the canvas.

  **The app does not open here, and nothing is rolled until the screen is
  reached.** `QuickView` used to roll a `PICKUP` sound in its constructor, and
  since it was also the start screen the only route to the editor was **Open in
  graph editor →** - so the canvas could never be reached empty and always
  arrived prefilled with a generated patch. The constructor now builds controls
  only; `SynthApp` calls `ensureSound()` when the screen is first shown, which
  rolls once with `autoPlay` off (the same silent first roll the old constructor
  produced, since its callbacks were not wired yet). Every later roll, seed-code
  load, and history re-select still auditions.

  It holds one long-lived `PatchDocument` and edits it in place. Macro sliders
  write straight through to the bound `ConstantNode`, so a drag is audible on
  the next block with no rebuild. The preview is rendered **offline** rather
  than tapped from playback, so a sound that has already finished still shows
  its shape; redraws are debounced through a 120 ms `PauseTransition` rather
  than fired per slider tick, since each redraw builds a `Patch` and renders the
  full sound. The debounce replaced a `valueChangingProperty` listener that only
  fired at the end of a *drag*, so clicking the slider track or stepping it with
  the keyboard changed the sound without ever redrawing the preview.

  Roll history stores category, seed, and a snapshot of the macro values, not
  the document. Re-selecting a roll regenerates from the seed and re-applies
  the snapshot, so a mutated sound comes back mutated while still costing one
  `long` to remember.

  **Export all rolls...** batch-exports the whole history in one action.
  `collectHistory()` regenerates each roll from its category and seed, re-applies
  its macro snapshot, and names the file `<category>-<code>.wav` - so the
  filenames double as the sound codes needed to get any of them back. Duplicate
  names get a numeric suffix, since the same seed can legitimately appear twice
  in the history after a mutate. `SynthApp.onExportAll` asks for the format once
  and a target directory once, then writes them all; if one fails partway it
  reports how many were written rather than claiming the whole batch failed.

  There is no return leg carrying edits back from the editor. An
  `adoptEditedDocument(...)` used to replace the held document with
  `GraphEditor.toDocument()` on the way back, but the traffic is now one-way:
  entering this screen discards the graph (see `SynthApp`), so re-importing it
  here would contradict the warning the user just accepted. Auditions build
  through `PatchDocument.buildPatch()` rather than `new Patch(...)` so that the
  editor's shadow-constant values survive the trip - see
  [Serialization](serialization.md).
- **`ExportDialog`** - a static `Dialog<ExportSettings>` collecting sample rate,
  bit depth, channels, normalize, and trim. Both export paths go through it: the
  quick screen's **Export WAV...** and the editor's **Export to WAV...**. It
  remembers the last-used settings in a static field for the session, since a
  user exporting a set of sounds for one project wants the same format every
  time and re-picking 22050/8-bit/mono on every save would be the main friction
  in a batch workflow.
- **`MinimapView`** - a whole-canvas overview drawn on a `Canvas` with a
  `GraphicsContext`, not a scaled scene-graph clone: the canvas is 3000x2000
  by default and a second live copy of every `NodeView` would cost real layout
  work on every drag. One class serves both sizes; the `detailed` constructor
  flag is the only difference (label text next to each waypoint, which is
  unreadable at panel width and so is off there). The colours come from
  `Theme` rather than `petroleum.css` - a `Canvas` cannot be styled by CSS, so
  this is not a violation of the no-`Color.web`-literals rule, it is the one
  place `Theme` constants are read directly.

  **Two extents, easy to confuse.** The minimap scales against
  `getCanvasWidth()`/`getCanvasHeight()` - the *scrollable* extent the
  `ScrollPane` sizes against - not against `getContentMinX()`/`getContentMaxX()`,
  which are the *drawn* extent and already fold in `originX`/`originY`. Using
  the drawn extent would make the viewport rectangle and the node rectangles
  live in different spaces. A node's position in the minimap's space is
  therefore `view.getLayoutX() + editor.getOriginX()`, and the mapping both
  ways is `toMinimap`/`toContent` around a `scaleFor`/`offsetFor` pair that
  letterboxes the canvas into whatever aspect ratio the widget has. Those four
  are static and pure precisely so the round trip is checkable without a
  display.

  The viewport rectangle divides out the zoom: the `Scale` lives in
  `canvas.getTransforms()`, so scroll offsets are in zoomed pixels while the
  minimap draws unzoomed content coordinates. Clicking or dragging anywhere on
  the minimap recentres the editor there via `MinimapView.centerOn(...)`,
  which `SynthApp.centerView()` also calls - one method, so the
  `Hvalue`/`Vvalue` clamping is derived once.

  **Redraw triggers deliberately avoid `GraphEditor`'s callback setters.**
  Every `setOnX` on `GraphEditor` is single-consumer and `SynthApp` already
  owns `setOnGraphChanged`, `setOnMacrosChanged`, `setOnKnobChanged` and
  `setOnCanvasGrown`; taking one over would silently break undo-button
  refresh or scroll preservation. So the minimap listens to JavaFX observables
  instead - `hvalueProperty`, `vvalueProperty`, `viewportBoundsProperty`, and
  `editor.getCanvas().boundsInParentProperty()`, that last one covering zoom
  *and* canvas growth at once since the `Scale` is folded into
  `boundsInParent`. Node *movement* is the one thing no observable reports, so
  `GraphEditor` gained one new callback, `setOnLayoutChanged(...)`, fired from
  `onNodeMoved` and `notifyGraphChanged`. It is deliberately not folded into
  `onGraphChanged`, which rebuilds and restarts the patch during playback -
  that would run on every pixel of a drag.

- **`MapView`** - the minimap expanded, reached by the button in the panel's
  minimap header and dismissed with Escape or its close button. It is an
  **overlay in the existing `canvasStack`**, not a ninth entry in the `screens`
  `StackPane`, following the `debugPanel` visible/managed toggle already in
  that stack. The reason is `savedEditorHvalue`/`savedEditorVvalue`: screen
  switches save and restore editor scroll, so a real screen would fight that
  machinery every time a waypoint was chosen. As an overlay the `ScrollPane`
  stays mounted and live behind it, and "go to waypoint" is just
  `centerOn(...)`, `selectNode(...)`, hide.

  Waypoints come from `MinimapView.waypoints(...)`, one `instanceof` chain
  over the node views plus the macro list - no `Poi` interface, since there is
  exactly one consumer. Annotations use their first line of text (falling back
  to "Empty note", since the text is free-form and may be blank), macros use
  the macro name and are positioned through `GraphEditor.viewOf(...)` because
  `Macro.target()` is a `ConstantNode` and not a view, and the remaining kinds
  are the single `AudioOutNode` and any node carrying its own editor screen
  (`TrackerNode`, `PianoRollNode`, `InstrumentNode`, `PatchNode`).

- **`MacroPanel`** - the editor screen's right-hand strip, listing every macro
  `GraphEditor` currently holds with a live slider and a remove button. It is
  rebuilt wholesale from `GraphEditor.setOnMacrosChanged(...)`, which fires on
  promote, rename, re-range, remove, and `loadDocument`, **and** from
  `SynthApp.onGraphChanged()` (behind `setOnGraphChanged(...)`), which fires on
  every connect/disconnect/add/remove. The panel needs both: a macro's own
  identity only changes through the first set, but a macro can go inactive
  purely from a rewire - dragging a new source directly into a port a macro
  used to drive - with no macro-list change at all, so relying on
  `setOnMacrosChanged` alone left a promoted-then-overridden knob's slider
  looking live when nothing was behind it (see `isMacroInactive` below;
  `SynthApp` calls `macroPanel.refresh()` first thing in `onGraphChanged()` so
  the fix covers rebuild-and-restart-playback too, not just the panel).
  Sliders write straight
  to the bound `ConstantNode`, exactly as the quick screen's do, so the same
  macro is the same control on both screens - literally the same code, now:
  the labelled, `syncing`-guarded slider-and-readout pair both screens build
  per macro is `Gui.MacroSliderRow`, a small shared class holding the
  `Macro`/`Slider`/`Label` triple and a `syncFromModel()` that re-reads the
  bound `ConstantNode` without re-firing its own change listener. `MacroPanel`
  wraps one per row in its header/remove-button/"not driving" layout;
  `QuickView.buildMacroControls` wraps one per row in its own plain-`HBox`
  layout with no remove button. Before this, `QuickView`'s copy had no
  `syncing` guard at all, so `syncMacroControls()` (called after every mutate)
  drove the slider's own change listener, which restarted the 120 ms preview
  debounce on every macro for no reason beyond the `refreshPreview()` call
  `mutate()` already makes explicitly.

  The panel's bottom strip is the minimap - `MacroPanel.setMinimap(...)`
  appends whatever section `SynthApp.buildMinimapSection()` hands it, rather
  than `MacroPanel` building it, because the minimap needs `canvasScroll` and
  that field is not assigned until `SynthApp.start()` while `macroPanel` is
  built at its field declaration. The macro list's
  `VBox.setVgrow(scroll, ALWAYS)` means a fixed-height minimap below it just
  works: the list absorbs the slack.

  A macro whose target no longer drives anything is labelled "not driving
  anything" and its slider disabled, via `GraphEditor.isMacroInactive(...)`.
  That covers both ways a knob can go dead: a real wire dragged into the port
  supersedes the constant, or the node it fed was deleted. A control that
  cannot be heard should say so rather than look functional.

  Because `Macro` is an immutable record, rename and re-range replace the entry
  in `GraphEditor`'s list rather than mutating it, and every view rebuilds from
  the change callback, so nothing holds a stale reference.
- **`Theme` and `petroleum.css`** - the palette lived as ~38 duplicated colour
  literals across seven files. `Theme` now holds the colours and the 8/12/16 px
  spacing steps as constants, and `src/main/resources/petroleum.css` (applied once
  in `SynthApp.start`) styles the JavaFX controls - buttons, sliders, text
  fields, menus, tooltips - plus the `card`, `chip`, `section-title`,
  `macro-active` and `side-panel` classes the screens use. Maven copies
  `src/main/resources` with no `pom.xml` change.

  `Theme`'s colours are Java `Color` constants and `petroleum.css`'s are hex
  literals - two different languages, so they cannot share one literal - but
  every colour `petroleum.css` needs from the palette is still declared exactly
  once *within the stylesheet*, as a named custom property on `.root`
  (`-petroleum-canvas`, `-petroleum-surface`, `-petroleum-node-body`,
  `-petroleum-node-header`, `-petroleum-border`, `-petroleum-accent`, `-petroleum-error`,
  `-petroleum-text`, `-petroleum-text-dim`, `-petroleum-wave-background`), referenced
  by name everywhere else in the file (a JavaFX CSS "looked-up color") instead
  of repeating the hex code at each of its several use sites. The mapping back
  to `Theme`'s Java constants:

  | `Theme` constant | hex | `petroleum.css` property |
  |---|---|---|
  | `CANVAS` | `#1c1e22` | `-petroleum-canvas` |
  | `NODE_BODY` | `#2b2f36` | `-petroleum-node-body` |
  | `NODE_HEADER` | `#3a3f48` | `-petroleum-node-header` |
  | `BORDER` | `#4a4f58` | `-petroleum-border` |
  | `ACCENT` | `#7fd0ff` | `-petroleum-accent` |
  | `TEXT` | `#d8dce2` | `-petroleum-text` |
  | `TEXT_DIM` | `#8a93a0` | `-petroleum-text-dim` |
  | `WAVE_BACKGROUND` | `#15171a` | `-petroleum-wave-background` |
  | - (css-only) | `#23262b` | `-petroleum-surface` |
  | - (css-only) | `#e05a5a` | `-petroleum-error` |

  `WARNING` (`#e0a35a`) has no `petroleum.css` counterpart: the one place that
  colour appears (`MacroPanel`'s "not driving anything" label) sets it via
  `setTextFill(Theme.WARNING)` in Java, never through a stylesheet rule, so
  there is nothing in the css for a custom property to de-duplicate. A few
  hex literals remain undeclared in `petroleum.css` on purpose - `#343941`,
  `#3f4650`, `#2a2e35`, `#2d5f7d`/`#eaf6ff`, `#357195`, `#31353c`, `#5a6272` -
  each is a button/row shading choice with no `Theme` counterpart at all
  (`Theme` holds the *engine-adjacent* palette - canvas, node, wire, text - not
  every hover/pressed shade a `.button` rule needs), so there is no
  cross-language duplication for those to resolve either.

  **Icon buttons.** `Theme.icon(button, name, size, tooltip)` turns a button
  into an icon-only one: it loads `/icons/buttons/<name>_<N>x<N>.png`, sets it
  as the graphic at `size` logical pixels, and stores `tooltip` as both the
  tooltip and the (hidden, `ContentDisplay.GRAPHIC_ONLY`) button text so screen
  readers still have a label. Every icon ships at 1x, 2x and 3x of its display
  size (16/32/48, 12/24/36 for the node-header "?" and the remove crosses,
  10/20/30 for the graph-settings gear); the variant is chosen from the primary
  screen's output scale, rounded up and clamped to 1-3, so a 125%/150% display
  downsamples the 2x file rather than upscaling the 1x one. Images are cached
  by file name. Calling it again swaps the icon and tooltip in place - that is
  how Play/Record turn into Stop and how the main undo/redo buttons carry
  "Undo <action>" in their tooltip. The PNGs are drawn in `-petroleum-text`
  (`#d8dce2`) since CSS cannot tint an image; `.button:disabled`'s opacity
  fades them like text. `.icon-only` squares the padding to 5 px, and
  `.icon-button.icon-only` to 2 px so a 12 px icon fits the 18 px header
  button and a 10 px one the 14 px gear. Buttons whose words carry meaning an
  icon cannot ("+ Track", "Octave +", "Export WAV...", category buttons) stay
  text.
- **`SoundPreview`** - a `Canvas` drawing a whole rendered sound as a per-column
  min/max envelope. Separate from `WaveformView`, which is the per-node scope:
  that one is 36 px tall and capped at one block (`DEFAULT_BLOCK_CAPACITY`),
  which is the wrong shape for showing a one-second sound end to end.
  **Deleting a node removes its macros.** `removeNode` calls
  `dropMacrosOwnedBy(view)`, which walks `NodeView.ownedKnobs()` - every port's
  current `KnobBinding.knob` (its shadow if unpromoted, the promoted node if
  not), plus the node itself when it is a `Constant` - and drops any macro
  bound to one. Without this the macro outlived its target, and since
  `PatchCodec` now throws on an unreachable macro target (see
  [Serialization](serialization.md)) the next save would fail
  outright rather than quietly losing it.
- **`SynthApp`** - the `Application` entry point (`pom.xml`'s
  `javafx-maven-plugin` runs it via `mvn javafx:run`). Holds five screens in a
  `StackPane` - the `QuickView` screen, the graph-editor screen, the
  `DocumentationView` screen (below), the tracker pattern editor screen
  (`Gui.TrackerEditorView`, see [Tracker](tracker.md)), and the waveform
  drawing screen (`Gui.WaveformDrawView`) - via a
  `private enum Screen { QUICK, EDITOR, DOCS, TRACKER, WAVEFORM }` and one
  `showScreen(Screen)` method that swaps `visible`/`managed` across all five,
  replacing what used to be two near-identical
  `showQuickScreen()`/`showEditorScreen()` methods - the newly-shown pane
  also gets a short `FadeTransition` (`SCREEN_FADE`, 160ms) so switching
  screens reads as a soft cross-fade rather than an instant cut; the
  underlying `setVisible`/`setManaged` toggle across all five panes stays
  unconditional either way, so the fade is purely an added visual layer, not
  a correctness-relevant change. One persistent `MenuBar` (File/Edit/View/
  Options/Help) sits above the `StackPane` of screens, built once in `start()` -
  visible regardless of which of the five screens is active, giving a
  stable anchor the per-screen headers alone don't; it deliberately
  supplements rather than replaces those headers' toolbar buttons, holding
  only the handful of actions (Open/Save/Export, Undo/Redo mirroring the
  toolbar's dynamic labels, zoom/view actions, Keyboard Shortcuts/About)
  that benefit from a conventional, discoverable home. **Options** is the one
  menu that does *not* supplement the toolbar: every app-wide setting lives
  there and nowhere else - Output Device, Input Device, Playback Quality,
  Latency, Render Limit - having previously been five header buttons
  competing with Play/Stop/Export for width. The distinction the split
  follows is per-app versus per-node: a setting that applies to the whole
  program is a menu item, while a device attached to one *node* (`NodeView`'s
  "Mic...", "MIDI...", "Line..." buttons) stays on that node's header, since
  it is patch state that gets saved, not a preference. Each Options item
  writes through to `Preferences` immediately on selection, so there is no
  OK/Cancel preferences dialog and no apply step. A `previousScreen` field
  remembers whichever of `QUICK`/`EDITOR` was active before the docs,
  tracker, or waveform screen was opened (never overwritten while `DOCS`,
  `TRACKER`, or `WAVEFORM` is active itself), so each screen's Back
  button returns to wherever the user actually came from rather than a fixed
  screen. The tracker screen reuses `SynthApp`'s existing `waveformTimer`
  `AnimationTimer` to drive its live playhead rather than standing up a
  second redraw loop. The Piano Roll screen (`Screen.PIANO_ROLL`,
  `Gui.PianoRollEditorView`, see [Tracker](tracker.md)) is wired the same
  way: a Piano Roll node's "Edit" button goes through
  `GraphEditor.setOnOpenPianoRollEditor`, the screen is excluded from
  `previousScreen` like the other editors, and the same timer ticks its
  playhead. Opening it needs no background fetch - `PianoRollNode.getClip()`
  is just a copy of a small note list.

  Editing a subpatch (a `Modules.PatchNode`, see [Modules](modules.md)) is a
  deliberate exception to that one-screen-per-editor pattern: its "Edit"
  button follows the same `GraphEditor.setOnOpen*`/`Consumer<Node>` wiring as
  the tracker/instrument/waveform editors, but the callback
  (`SynthApp.openSubpatchEditor`) doesn't add a sixth `Screen` - it pushes the
  current document onto a `Deque<SubpatchFrame>` and reloads the *same*
  `EDITOR` screen with the subpatch's contents, since a subpatch is edited
  with the exact same graph editor, not a dedicated one. A label and a "Done
  Editing Subpatch" button, both normally unmanaged, appear above the canvas
  only while that stack is non-empty.

  The waveform screen is reached the same way the tracker screen is: a
  node-header "Draw" button (`NodeView`, shown only when the node wraps a
  `DrawnWaveform` oscillator, mirroring the existing `isTracker`/"Edit"
  button check) calls `GraphEditor.openWaveformEditor`, which fires a
  `Consumer<Node>` callback into `SynthApp.openWaveformEditor` -
  `setOnOpenWaveformEditor`/`openWaveformEditor` on `GraphEditor` are exact
  mirrors of the pre-existing `setOnOpenTrackerEditor`/`openTrackerEditor`
  pair. Unlike the tracker screen, `WaveformDrawView` has no working-copy or
  undo-stack state to push back on close: it draws directly into the
  `DrawnWaveform`'s live backing array (`getTable()`), so there is nothing to
  synchronize back to the node when the user leaves the screen (see
  [Oscillators](oscillators.md)).

  A sixth screen, the Sound Clip screen (`Gui.SoundClipEditorView`), is
  reached the same way: a node-header "Record" button (`NodeView`, shown
  only when the node wraps a `Wavetable` oscillator) calls
  `GraphEditor.openSoundClipEditor`, mirroring `openWaveformEditor`'s
  `Consumer<Node>` wiring. Unlike `WaveformDrawView`'s always-512-sample
  table, a clip's length changes constantly (record, trim, delete, import,
  paste, splice all produce a different-sized array), so edits go through
  `Wavetable.setTable(float[])` rather than mutating a fixed array's
  elements in place - still applied live, no separate save step.

  The node's one `Wavetable` is backed by several **tracks**
  (`Gui.SoundClipTrack` - a name, a `float[]`, a mute flag, and a solo
  flag) that the editor keeps in a `List<SoundClipTrack>`, one
  `Gui.SoundClipTrackLane` row each. `remix()` sums every audible track's
  samples (zero-padded to the longest one - if any track is soloed, only
  soloed tracks count as audible, otherwise every unmuted one does, the
  same solo-overrides-mute rule most DAWs use), clamps to `[-1, 1]`, and
  calls `clip.setTable(...)` after every structural change - so
  "multi-track" never means the node itself gained more than one output,
  only that the editor composes what becomes that one buffer.

  The layout deliberately follows Audacity's own track-editor shape rather
  than this project's usual single-canvas-with-toolbar screens: each
  `SoundClipTrackLane` is a fixed-width left **control panel** (name field,
  Mute/Solo `ToggleButton` pair, the spectrogram toggle, a per-track Remove
  button) next to a full-width waveform/spectrogram area, stacked in one
  `VBox` under a shared `Gui.SoundClipTimeRuler` - a `Canvas` drawing
  time-axis tick marks at a "nice" step (1/2/5-per-decade seconds,
  widening as you zoom out so labels never crowd together) that all lanes
  and the ruler share one `samplesPerPixel`/pixel-width pair for, computed
  and pushed by `SoundClipEditorView.broadcastView()` - unlike the
  earlier single-track version, a lane no longer manages its own zoom;
  scrolling and zooming the whole composition together (one `ScrollPane`
  wraps the ruler and every lane) is what makes it read as one timeline
  instead of several independent strip charts. `Gui.SoundClipWaveformView`
  itself renders its waveform mode as a **filled** min/max envelope
  (a closed polygon between the per-pixel max and min, not individual
  vertical strokes) - closer to Audacity's solid waveform fill than the
  thin traces `WaveformDrawView`/`WaveformView` use elsewhere in this
  project, which is deliberate here (a full editor screen can afford a
  heavier look a tiny per-node preview shouldn't). Its spectrogram mode
  instead runs a Hann-windowed `Dsp.Fft`
  (window 1024, hop 256 - the same constants `Spectral.StftEngine` and
  `Spectral.AdditiveResynthNode.analyze` already use, though this is a
  one-shot forward-magnitude computation over a static array, not
  `StftEngine`'s realtime overlap-add reconstruction) into a `WritableImage`
  heatmap, cached until the buffer or mode changes. Each lane's "Spectrogram"/
  "Waveform" toggle button switches one lane at a time, not both stacked.

  Recording opens a `TargetDataLine` via `Audio.AudioInputDevice` (the same
  capture-device helper [`Sources.MicInputNode`](sources.md) uses) on its
  own thread, always into a **newly added track** rather than overwriting
  whatever's active, and appends into a plain doubling `float[]`, redrawn
  live by an `AnimationTimer` identical in spirit to the tracker screen's
  playhead timer; on Stop, that buffer becomes the new track's samples.

  **Cut/Copy/Paste and drag-splice** all reduce to plain array surgery on a
  track's flat `float[]` - there is no clip/region-with-offset model.
  Copy reads the active lane's selection into a single `float[] clipboard`
  field (one clipboard, like every OS/DAW); Cut additionally splices that
  range out (shifting everything after it left); Paste splices the
  clipboard in at the selection start (or the track's end with no
  selection), shifting everything after it right - an insert, never an
  overwrite. Dragging an *existing* selection (rather than starting a new
  one) begins a move instead:
  `SoundClipWaveformView.MoveGestureHandler` reports scene-space drag
  updates up to `SoundClipEditorView`, since a lane can't see its sibling
  lanes' screen bounds - the editor hit-tests the cursor's scene Y against
  each lane's `localToScene(getBoundsInLocal())`, paints a dashed drop
  preview on whichever lane is under the cursor
  (`showDropPreview`/`clearDropPreview`), and on release splices the
  segment out of the source track and into the target at the drop sample -
  the same source track twice if dropped back on itself, or two different
  tracks' snapshots in one undo entry if not.

  **Undo/redo is a real multi-level history**: `Gui.EditHistory<SoundClipEditorView>`,
  the exact generic undo/redo stack `GraphEditor` already uses (package-
  private in `Petroleum.Gui`, so reusable as-is with no new abstraction).
  `Gui.SoundClipEditCommands` supplies two `EditCommand` shapes rather than
  one per operation: `trackEdit` (a list of `(track, before-array)`/
  `(track, after-array)` pairs - covers every content mutation, one or two
  tracks) and `trackListEdit` (add/remove a track at a fixed index - reused
  for Add Track, Remove Track, *and* Record, since undoing "record" is just
  removing the track that got added for it, whatever it captured). Ctrl+Z/
  Ctrl+Shift+Z work the same as the graph editor's.

  **The app opens on the graph editor with an empty canvas.** The editor screen
  gains a **Quick sounds...** header button, and that transition is guarded:
  `openQuickView()` shows a confirmation `Alert` when `GraphEditor.isEmpty()`
  is false, then clears the canvas with `loadDocument(new PatchDocument())`
  before showing the quick screen. The clear happens at the transition rather
  than later at **Open in graph editor →**, so the destructive step matches the
  dialog the user accepted and no stale graph survives behind the other screen.
  An empty canvas is skipped straight past the dialog, since there is nothing
  to lose.

  Both screens share one `AudioEngine`, and every
  transition between them calls `stopPlayback()` first: the two screens hand
  the same live `Node` instances to different `Patch` objects, so letting both
  drive audio would run `process()` on shared non-`volatile` state from two
  threads, the same hazard `onRenderToWav` already guards against.

  **Undo/redo** lives on `GraphEditor` as a command stack, surfaced as two
  header buttons (labelled with what they will undo, e.g. "Undo delete
  Envelope") and bound to the platform shortcut plus Shift for redo.

  Shortcuts are an **event filter on the editor screen root**, installed via
  `installShortcuts(Node)`, not a `setOnKeyPressed` handler on the canvas. A
  key handler on the canvas only fires while the canvas holds focus, so
  clicking any button - including Undo or Redo themselves - moved focus away
  and silently killed the shortcut until the user clicked back on the canvas.
  A filter on the screen root sees the event on the capturing phase whatever
  is focused. Delete/Backspace moved with it for the same reason.

  The filter returns early when the event target is a `TextInputControl`, so
  Ctrl+Z inside the expression editor or a knob's value field is text undo
  rather than graph undo, and Backspace there deletes a character rather than
  the selected node.

  It is a **command stack, not snapshots**. Serializing the whole document per
  edit would have been a few lines - `toDocument()` + `PatchCodec.save()` -
  but undo would then rebuild every `Node` from scratch, and `patch.md`'s
  rebuild rule exists precisely because fresh nodes reset delay-line buffers,
  noise state and phase, glitching anything playing. Commands hold the original
  `Node` instances, so undoing a delete puts the *same* node back.

  Recorded operations: add node, delete node, connect, disconnect, move, and
  every macro mutation. Deleting is the interesting one - `NodeSnapshot`
  captures the node, its position, its shadow-knob values, every connection
  touching it, and every macro bound to a knob it owned, because `removeNode`
  destroys all of that. Knob *values* are deliberately not recorded: a slider
  drag emits a change per pixel and would bury every structural edit.

  The stack discipline lives in **`EditHistory<T>`**, deliberately free of any
  JavaFX or `GraphEditor` reference so it can be exercised headlessly - the
  editor itself cannot be constructed without a display, so without this split
  the most error-prone part of undo would have no test at all. `GraphEditor`
  holds an `EditHistory<GraphEditor>` and keeps only the wiring.

  `EditHistory` suppresses recording while a command runs, so undoing a delete
  does not itself push an "add" entry, and `runSuppressed(...)` extends that to
  `loadDocument`, which clears the history rather than making a file load
  undoable. A command whose `undo`/`redo` throws is pushed **back** onto the
  stack it came from rather than being dropped, so one failure does not corrupt
  the rest of the history. History is capped at 100 entries.

  Move commands identify their node by `Node`, not by `NodeView`, and resolve
  the view at apply time. A `NodeView` is replaced whenever a node is deleted
  and restored, so a captured view goes stale after any delete/undo round trip
  and its later undo would silently move nothing.

  **Entering the editor scrolls to the graph.** The canvas is a `Pane` inside
  a `ScrollPane`, starting at 3000x2000 - far larger than any patch, so the
  viewport could sit anywhere relative to the content - opening the editor
  showed empty canvas. `scrollToGraph()` reads `GraphEditor.getContentMinX/Y()`
  (the top-left of the placed nodes), converts to `ScrollPane` h/v values
  against the scrollable range, and applies a 40 px margin. It runs through
  `Platform.runLater` on both entering the editor screen and loading a patch
  file, because the viewport bounds are only meaningful once layout has run.

  **The canvas grows rather than clips.** `GraphEditor` tracks its own
  mutable width/height (starting at the 3000x2000 default) instead of a fixed
  `CANVAS_WIDTH`/`CANVAS_HEIGHT` constant. `addNode` and `onNodeMoved` - the
  latter already firing on every drag-move, resize-drag, and undo/redo
  `placeNode` - call `growCanvasToFit(view)`, which extends the tracked size
  (and `canvas.setPrefSize(...)`, never shrinking it) whenever a node's edge
  plus a 400 px margin passes the current bound. So the workspace has no hard
  ceiling: a patch that outgrows the starting area just keeps scrolling
  further instead of nodes overlapping past a wall. `clear()` resets the
  tracked size back to the 3000x2000 default, so loading a small patch after
  a huge one does not leave a mostly-empty scrollable area behind.

  **It grows left and up too.** `wireLayer`/`nodeLayer` sit inside one
  `content` `Group` translated by `originX`/`originY`, so node layout
  coordinates are free to go negative. When a node's left/top edge passes the
  canvas's 0, `growCanvasToFit` raises the origin by the overshoot plus the
  same 400 px margin and widens the canvas by that much. Nodes are never
  renumbered: their coordinates (and therefore every recorded undo move and
  every saved position) stay valid, only the translate changes. Everything
  that maps between scene and graph space (`showAddNodeMenu`, wiring,
  `WireView.centerInCanvas`) goes through `content.sceneToLocal`, not the
  canvas, and `getContentMin/MaxX/Y()` report canvas coordinates
  (`+ origin`) for `SynthApp`'s scroll maths. After any growth the editor
  fires `onCanvasGrown` with where the old canvas now sits inside the new one;
  `SynthApp.keepViewAfterCanvasGrowth` re-derives the h/v values that keep
  the same content under the viewport and calls `canvasScroll.layout()`
  immediately - otherwise a drag event arriving before the next pulse would
  see the shifted translate with the old scroll offset, jump the node by the
  shift, and trigger another growth.

  **Dragging past the viewport edge auto-scrolls.** A `MOUSE_DRAGGED` filter
  on `canvasScroll` remembers the last drag event (skipping drags that start
  on the bare canvas or inside a `Control` such as a knob slider, so
  scrubbing a value near the edge does not pan) and runs an `AnimationTimer`
  until `MOUSE_RELEASED`. Within 30 px of (or beyond) a viewport edge each
  tick scrolls by a time-based speed (150 px/s rising with distance, capped at
  1500 px/s - per-frame steps raced on machines pulsing well above 60 fps)
  and then re-fires a copy of the last drag event at its original target.
  Because node drag, resize and wiring all convert the event's scene point to
  canvas-local coordinates, the re-fired event moves the node / resize edge /
  wire end along with the scroll, which in turn grows the canvas - holding a
  node against an edge keeps expanding the work area. Resize used raw scene
  deltas before and was switched to local coordinates for this (which also
  makes it track the cursor correctly when zoomed).

  **Zoom is a `Scale` transform on the canvas itself**, pinned to pivot
  `(0, 0)` (`GraphEditor.getZoom()`/`setZoom(double)`, clamped to
  `MIN_ZOOM`/`MAX_ZOOM` = 0.1-4.0, wide enough that a normal scroll gesture
  doesn't run into either end and feel like the wheel stopped responding)
  rather than the `scaleX`/`scaleY`
  properties, whose default pivot is the node's own center and would shift
  the canvas origin on every zoom step. Because every port-picking and
  wire-geometry calculation already goes through `sceneToLocal`/
  `localToScene` (`WireView.centerInCanvas`, `finishWiring`, node dragging),
  none of that code needed to change for zoom to work - JavaFX folds the
  `Scale` into the same transform chain automatically. `SynthApp` owns the
  interaction: Ctrl+scroll zooms anchored at the cursor (solving for the
  canvas-local point under the mouse before rescaling, then re-deriving the
  `ScrollPane` h/v values that keep that point under the cursor after), and
  the header's `−`/`+` buttons zoom anchored at the viewport center instead.

  **A "Center View" button centers the viewport on the content's bounding
  box**, using the zoom-aware midpoint of `getContentMinX/Y()` and the new
  `getContentMaxX/Y()` (mirroring the min variants, but against each node's
  right/bottom edge) rather than `scrollToGraph()`'s top-left-with-margin
  alignment - useful after zooming or panning away from the graph, not just
  after opening the editor.

  **"Organize" (header button and View > Organize Layout) is a layered
  auto-layout.** `GraphEditor.organizeLayout` is a thin JavaFX adapter over
  `GraphLayout.arrange`, which works on plain arrays (edge index pairs, node
  sizes, and each edge's source/target port y within its node, read from the
  `PortHandle`s) so it can be smoke-tested without a display. Only `nodeViews`
  and connections between them are laid out - shadow `ConstantNode`s have no
  view (convention 10). Columns come from Kahn's topological peeling (column =
  1 + the furthest predecessor's column), then each node with successors is
  pulled right to sit just before its nearest successor, so a Constant feeding
  the output lands beside it rather than in column 0. The document may still
  hold a feedback cycle (only `Patch` rejects them), so anything Kahn cannot
  peel goes into one trailing column instead of hanging.

  Vertical placement is an iterative relaxation, not top-aligned stacking.
  The first version stacked every column from the top, so a typical patch came
  out as one flat row of single-node columns next to one tall column of
  sources with a knot of wires between them. Now, over 12 passes alternating
  left-to-right and right-to-left, each column computes every node's *ideal*
  y - the average, over all its wires in either direction, of the y that would
  make that wire perfectly horizontal port-to-port - sorts the column by it
  (this ordering is also the crossing reduction), pushes overlapping nodes
  apart with a `ROW_GAP` of 40, then shifts the column back by the mean
  displacement so pushing doesn't drift it downward. Unconnected nodes keep
  their current y. Columns are `COLUMN_GAP` = 140 apart so fanned-in wires
  have room to curve. The result is normalised to the old content's top-left
  so the patch doesn't jump, and `canvas.layout()` runs first because a freshly
  loaded node measures 0 wide (and its port handles unplaced) until JavaFX lays
  it out. The whole move is recorded as one `EditCommands.arrange` entry, not
  one move per node. Not handled: a wire spanning several columns can pass
  behind a node in between (no dummy nodes / wire routing).

  **`InputDebugLog`/`InputDebugPanel`** is a small dev-facing tool, not a
  user feature: a shared, bounded (300-entry) `ObservableList<String>` that
  `SynthApp`'s Ctrl+scroll/button zoom handler and the tracker's scroll-to-
  pitch handler (`TrackerGridView`) append one line
  to per event - raw `deltaY`, the factor/semitone step applied, and the
  before/after value - so a scroll-input question ("why does zooming out
  feel faster than zooming in") can be answered by reading the actual event
  stream instead of guessing from feel. The `MenuBar`'s View > "Toggle Debug
  Panel" item shows/hides a small `ListView` (one `InputDebugPanel` instance
  each for the editor and the tracker, both reading the same shared log so
  switching screens doesn't lose history) docked bottom-left over the
  canvas/pattern view; hidden by default, since it's a diagnostic aid rather
  than something a session needs open normally. `SynthApp.toggleDebugPanel()`
  routes to whichever of the two panels matches the currently active screen
  (`TrackerEditorView.toggleDebugPanel()` for the tracker screen, the
  editor's own panel otherwise) - deliberately a single menu entry rather
  than two separate always-visible toolbar buttons, since a dev-only toggle
  shouldn't compete for the same visual weight as end-user actions.

  **`MidiDebugLog`/`MidiDebugPanel`** is the same idea, one layer more
  hidden, for [`Sources.MidiInputNode`](sources.md): every `ShortMessage`
  its `NoteReceiver` sees gets one formatted line in `Sources.MidiDebugLog`
  (a plain, JavaFX-free, synchronized ring buffer - `Sources` cannot import
  anything from `Gui`, so the log storage has to live on the `Sources` side
  of that boundary, with `Gui.MidiDebugPanel` reading it rather than the
  reverse) - device name, 1-indexed channel, command name, both data bytes,
  and whether that node's channel filter accepted or dropped it. This is
  the tool for "why isn't my controller doing what I expect": a wrong
  channel, a device sending an unexpected command, or a bend range question
  all show up as raw messages rather than requiring a guess.

  Unlike `InputDebugPanel`, this one has **no menu entry, no button,
  nothing in the Keyboard Shortcuts dialog** - it does not belong on a
  surface every user sees, since nobody but someone actively debugging a
  MIDI controller needs it. The only way to reach it is a scene-level
  `Ctrl+Alt+Shift+M` filter installed once in `SynthApp.start` (not a menu
  accelerator, so it never appears in a menu's shortcut column), toggling
  one `MidiDebugPanel` docked top-right over whichever screen is currently
  showing - top-right rather than `InputDebugPanel`'s bottom-left so the two
  can be open at once without overlapping. Since `MidiDebugLog` is a plain
  `List<String>` snapshot rather than an `ObservableList`, the panel polls
  it on a 150 ms `Timeline` (only while shown - started in `setShown(true)`,
  stopped in `setShown(false)`) instead of subscribing to change events, and
  only touches the `ListView`'s items when the snapshot's size actually
  changed.

  The editor screen lays out a center scrollable canvas (no left sidebar - see
  `NodeMenu` below, and note the canvas sits in a `StackPane` alongside the
  `InputDebugPanel` and the `MapView` overlay), a right-hand `MacroPanel`
  carrying the macro list above the `MinimapView`, and a bottom
  transport bar (Play / Stop / Export to WAV... / Save Patch... / Open
  Patch...). Save and Load use a `FileChooser` filtered to `*.ptr` and go
  through [`Serialization.PatchCodec`](serialization.md) against
  `GraphEditor.toDocument()`/`loadDocument(...)`. `GraphEditor` tracks a
  `dirty` flag internally (set by the same `notifyGraphChanged()` every edit
  already goes through, cleared by `loadDocument(...)` and by a successful
  save) - Open Patch, Quick Sounds, and the window's close button all check
  `isDirty()` before discarding the graph and prompt via `Dialogs.confirm`
  only when there's actually unsaved work to lose, rather than nagging on a
  clean graph. Loading follows the same stop-first discipline as rendering,
  for the same reason: it replaces every live `Node` instance on the canvas,
  so it stops playback, rebuilds, then restarts playback only if it had been
  running. Both directions catch `SerializationException` and `IOException`
  as a non-fatal `Alert`.

  Encoding/decoding and file I/O run **off the FX thread**, through the same
  `runRenderTask` WAV export uses (spinner, one-at-a-time `rendering` guard),
  because a large tracker song made both long enough to freeze the window.
  Save snapshots `toDocument()` on the FX thread first, since that reads
  `NodeView`s. After that only `PatchCodec.save` runs in the background, and
  it reads nodes through `Stateful.saveState`, which is safe because nodes are
  already shared with the audio thread. The dirty flag is cleared when the
  save *starts* and set again if it fails. That way an edit made during the
  save stays dirty, where clearing on success would hide it. The file is
  written to a temp sibling and then atomically moved over the target, so a
  crash mid-write leaves the old file intact. The close button refuses to
  quit while `rendering` is set. Load decodes in the background and calls
  `loadDocument` on the FX thread when it finishes. Opening the tracker
  editor likewise fetches `TrackerNode.getSong()` (which rebuilds the song
  from its snapshot) on a pool thread, then passes the result to
  `TrackerEditorView.open(node, song)` on the FX thread. Play and Render both build
  a `Patch` from current canvas state and catch `Patch`'s construction
  exceptions (`IllegalStateException`/`IllegalArgumentException` - no
  `AudioOutNode`, a cycle, an unknown port name) as a non-fatal `Alert`
  rather than crashing, since the graph is routinely incomplete mid-edit.
  The actual `AudioEngine.renderToWav(...)` call (Export to WAV, and
  QuickView's single/batch WAV export) runs on a background `javafx.
  concurrent.Task` rather than the FX Application Thread - `SynthApp.
  runRenderTask(...)` binds a shared `ProgressIndicator` to the task's
  progress property (indeterminate for a single file, since nothing calls
  `updateProgress`; a real 0-1 fraction for the batch-export loop, which
  does) and re-enables the UI via the task's `setOnSucceeded`/`setOnFailed`
  handlers - `Patch` construction itself stays synchronous beforehand, so a
  malformed graph still fails immediately without ever spinning up a thread.
  While already playing, any further graph edit rebuilds the `Patch` and
  calls `AudioEngine.play(...)` again - the hot-swap `audio.md` describes.
  Because `GraphModel.buildPatch()` hands out the same live `Node` instances
  to every `Patch` built from it, a playing patch and a freshly rendered one
  would otherwise run `process()` on the same stateful nodes (filter
  coefficients, delay-line buffers, etc. - all non-`volatile`, per
  governing-convention 2/3) from two different threads at once, corrupting
  both. `onRenderToWav` therefore serializes the two: it records whether
  playback is currently active, and if so calls `AudioEngine.stop()` (which
  joins the playback thread, so nothing is left running) *before* the
  synchronous `renderToWav` call on the FX thread; once rendering returns
  (success or `IOException`, via a `finally` block so a failed render still
  restarts playback) it rebuilds the `Patch` the same way `Play` does
  (`tryBuildPatch()`) and calls `AudioEngine.play(...)` again if that
  succeeds. If playback wasn't active, rendering just runs directly with no
  stop/restart. This is the same instances-not-copies aliasing that makes
  the shadow-`ConstantNode` disposal below matter, just surfacing as a
  threading hazard instead of a leak. All four ways playback state can
  change (Play button, Stop button, the hot-swap on graph edits, and the
  render stop/restart) funnel through one private `startPlayback(Patch)` /
  `restartPlayback(Patch)` / `stopPlayback()` set, which - besides
  `AudioEngine.play`/`restart`/`stop` and the
  `playing` flag - also tracks the currently-playing `Patch` in a
  `livePatch` field and starts/stops an `AnimationTimer` that drives the
  per-node waveform displays: while playing, the timer (throttled to ~30
  updates/second, since redrawing faster than blocks change is waste) calls
  `GraphEditor.refreshWaveforms(livePatch)`, which fans out to every
  `NodeView`'s `refreshWaveform(Patch)`. When playback stops the timer
  stops with it and each node's display simply freezes on its last drawn
  block.

  **Play always starts the sound over.** The two Play buttons (the editor's
  transport and the quick screen's) call `restartPlayback`, which is
  `AudioEngine.restart` - the patch is swapped *and* the session clock is
  reset, so pressing Play on a one-shot that has already finished fires it
  again instead of doing nothing. `startPlayback` is the non-resetting form and
  is left to the paths that must not retrigger: the hot-swap after a graph
  edit, and resuming after a render. The quick screen's Play no longer stops
  first either - `stopPlayback()` there meant a full line teardown, including
  the `drain()` wait, between one press and the next.
- **`Docs`** and **`DocumentationView`** - in-editor documentation, reachable
  without leaving the app. `Docs` is a plain data/lookup class (no JavaFX):
  a fixed list of `Article(category, title, summary, body, inputs, output,
  example)` records, one per placeable node kind (keyed by exactly the same
  label `NodeCatalog` would give it) plus a fixed set of `"Guide"`-category
  articles covering the editor itself (wiring gestures, knobs/macros,
  undo/redo, save/load, rendering, Quick Sounds, patches-as-modules). `body`
  is a plain multi-paragraph `String` (blank-line-separated); `inputs` is a
  `List<PortInfo(name, description)>` naming every real input port on the
  node (transcribed from the actual `Port` declarations in each `Node`
  constructor, not re-derived from prose, since a wrong port name here would
  directly mislead whoever's reading it) with a beginner-facing one-line
  explanation of what it does and, where the unit isn't obvious, what unit
  it's in (seconds vs. milliseconds matters - `Effects.DelayNode.time` is
  seconds, `Filters.CombFilterNode.time` is milliseconds, and getting that
  backwards on a filter with no dry/wet knob to sanity-check against is an
  easy mistake); `output` is one line stating what the output signal
  actually *is* (audio in roughly -1..1, a held control value, a signal not
  meant to be plumbed straight to `Audio Out`, etc.), since "what do I
  actually get out of this" is a different question from what the node does
  internally and beginners asked for it answered explicitly rather than left
  implicit in the prose. `example` is a short, arrow-notation wiring diagram
  (`"Trigger.out --> Envelope.trigger"`, sometimes several lines) showing one
  concrete, typical use of the node - the same worked-example idea
  `envelopes.md`'s own "coin pickup" diagram uses, just one per node instead
  of one for the whole package. Guide articles leave `inputs`/`output` empty
  (`List.of()`/`""`) since they don't describe a node - `DocumentationView`
  skips those sections entirely when empty rather than rendering a heading
  with nothing under it.

  `DocumentationView` renders an article as HTML inside a `WebView`
  (`javafx.scene.web`, the one new dependency this feature needed -
  `org.openjfx:javafx-web` in `pom.xml`) rather than plain JavaFX `Label`s:
  the `example` field's alignment and monospacing only survive in a `<pre>`
  block, which a wrapping `Label` can't reproduce (no monospace font, no
  preserved whitespace). The HTML itself is generated locally per article (a
  small inline `<style>` block plus `<h1>`/`<p>`/`<pre>` tags built from the
  article's fields, with a two-character `escape(String)` helper against `&`/
  `<`/`>` since `example`'s arrow notation is plain text, not markup) and
  loaded via `WebEngine.loadContent(html)` - there is no external URL, no
  Markdown parser, and no JavaScript involved, just enough HTML to get a
  code-formatted block the rest of JavaFX's control set can't render on its
  own.

  `Docs.keyFor(NodeFactory, Node)` is the resolver from a *placed* `Node`
  instance back to its fixed documentation key, mirroring
  `NodeFactory.nameOf`'s own "does `getName()` equal the registry key" check
  but in reverse. `nodeFactory.nameOf(node)` alone covers every
  `NodeFactory`-registered kind; for the handful `NodeCatalog` constructs by
  hand instead, `getName()` is not reliable (an `OscillatorNode` wrapping a
  `DualOscillator`/`Wavefolder` reports `"Dual " + base.getName()`/
  `"Folded " + base.getName()`, not its catalog label; a `GranularPlayerNode`
  and a `PatchNode` report a user-chosen filename or saved-patch name
  instead, and even a `Wavetable`-backed "Sound Clip" node - whose fixed
  `getName()` happens to already read "Sound Clip" - isn't something
  `nameOf` can find, since it isn't `NodeFactory`-registered at all), so
  `keyFor` falls through to explicit `instanceof` checks for those cases.
  `shortDescription`/
  `nodeArticle` never throw on an unresolved or not-yet-written key - they
  return a placeholder instead, since the content is written incrementally
  by category rather than all 90+ articles at once.

  `DocumentationView` is the screen itself: a `BorderPane` with an index
  pane on the left (one `.section-title` per `NodeCatalog.CATEGORY_ORDER`
  category that actually has articles, followed by a clickable row per
  article, then one final `"Guide"` section) and an article pane on the
  right (title, one-line summary, body paragraphs, inside `.card`).
  `showWelcome()` shows the index-browsing landing state; `showNode(key)`
  jumps straight to one article and highlights its index row - this is what
  a node's "?" button (see `NodeView` below) drives.

  A search field above the index filters it as you type. Each row keeps a
  lowercased haystack built once at index time (`searchText`: category,
  title, summary, body, output, example, and every input port's name and
  description), and `matches` requires every whitespace-separated query term
  to appear somewhere in it - AND, substring, no ranking. `applyFilter` hides
  non-matching rows and any section header left with none, via the same
  `setVisible`/`setManaged` pair `MapView` uses, and shows a "No matching
  articles." label when nothing survives. It filters the index rather than
  producing a separate results list so a hit keeps its category context and
  the existing row highlight still works. Enter opens the first visible hit
  whose title contains the query, falling back to the first visible hit;
  Escape clears a non-empty field. `searchText`/`matches` are static and
  package-private so they can be checked headlessly against the real
  article set.
- **`NodeCatalog`** - the single config file every node passes through to
  become reachable from the GUI. Iterates `NodeFactory.names()` and
  `OscillatorFactory.names()` directly (no hand-maintained parallel string
  arrays to keep in sync), and for each key builds one `Entry(category,
  subPath, label, Function<Window, Optional<Node>> action)`. Category is
  derived from the *package* of a freshly-constructed probe instance
  (`node.getClass().getPackageName()`, mapped through one small fixed table:
  `Sources`/`Sinks` -> "Sources & Sinks", `Envelopes` -> "Envelopes",
  `Filters` -> "Filters", `Effects`
  -> "Effects", `Conditionals` -> "Conditionals", `Mixers` -> "Mixers",
  `Utility` -> "Utility") - not by a per-name lookup, so a brand-new node
  registered in an existing factory needs *zero* `NodeCatalog` changes to
  show up in the right place. `OscillatorFactory` entries are wrapped in
  `new OscillatorNode(...)` and always land under "Oscillators". Exactly
  four entries are hand-written, because they have no factory key to source
  a label from: **Dual Oscillator** and **Wavefolder** (category "Oscillator
  Wrappers", construct immediately with a hardcoded sane default - base
  `Sine`, offset `Math.PI` / gain `2.0` - no dialog, since the base becomes a
  live, swappable field afterward, see `NodeView` below); **Sound Clip**
  (category "Oscillators", constructs immediately with a short silent
  `Wavetable` and no dialog - recording or importing happens afterward on
  the node's own "Record" screen, see [Oscillators](oscillators.md)
  `WavetableLoader` and `Gui.SoundClipEditorView` below); and **Expression**
  (category "Oscillators", constructed
  immediately with a hardcoded default formula `"sin(x)"` and no dialog at
  all - editing the actual formula happens inline on the placed node itself,
  see `NodeView` below). A fifth hand-written entry, **Load patch as node...**
  (category "Patches"), opens a `FileChooser` (`Dialogs.patchFileChooser`,
  shared with `SynthApp`'s own Save/Open Patch dialogs) filtered to
  `*.ptr`, reads it with `PatchCodec`, and wraps the document as a
  [module](modules.md) through `PatchNode.create`. `NodeCatalog` takes that
  `PatchCodec` as a constructor argument rather than building its own - it and
  `SynthApp` share one instance (owned by `GraphEditor`, which already owns
  the matching `NodeFactory`/`OscillatorFactory` these codecs are built from),
  where they used to construct two functionally-identical instances from the
  same two factories. The loaded document is labelled with its saved `name`,
  falling back to the filename without its extension via
  `Dialogs.stripExtension` (also shared with `SynthApp`). This is the only
  catalog entry that reads a file the *user* wrote rather than a factory key,
  so it is also the only one that can fail for a reason worth reading - a
  patch with no Audio Out, a cycle, duplicate macro names - and `NodeMenu`'s
  existing error dialog (`Dialogs.showError`, likewise shared) reports all of
  them. Every `Entry`, factory-backed or hand-written, has the identical
  `Function<Window, Optional<Node>>` shape, so nothing downstream
  special-cases by name: a module is placed, wired, knob-tweaked, undone and
  saved by the same code paths as a Biquad.

  **`subPath` nests an entry under submenus within its category**, purely a
  display concern - it plays no role in factory registration, `Patch`
  construction, or serialization, only in which `Menu` `NodeMenu` (below)
  puts the leaf under. Most entries get `List.of()` (a direct child of the
  category); a category with 8+ flat entries gets its crowded corners split
  out instead, one `Map.entry(label, path)` per node in the static
  `SUBPATH_OVERRIDES` table (for the two loops - `NodeFactory`-registered
  nodes and oscillators - whose label is only known at iteration time) or a
  literal argument at the `Entry(...)` call site (for the handful of
  hand-written entries above, whose label is already a compile-time
  constant). A node with no override renders flat, so most of the catalog
  - anything not called out - needs no entry here at all; only the crowded
  categories (Sources & Sinks' phasor/LFO family, Oscillators' waveform/
  noise/generator split, Filters' Biquad family, Effects' modulation/
  distortion/dynamics split, Utility's math/pitch-time split) are worth the
  extra tap to reach. A handful of smaller, conceptually distinct clusters
  are split out below that 8-flat bar too, on the grounds that "as many
  submenus as ease of use calls for" costs nothing once the table already
  exists: Conditionals' three threshold/branch nodes (`Select`, `CV Gate`,
  `Toggle`) under a `Logic` submenu, its four edge/clock-driven ones
  (`Latch`, `Counter`, `Clock Divider`, `Rungler`) under `Clocking`; Mixers'
  three nodes that only ever feed a `StereoAudioOutNode` (`Panner Equal
  Power`, `Panner Linear`, `Stereo Mix` - see [mixers.md](mixers.md)'s
  "Stereo nodes" section) under `Stereo`; Sources & Sinks' two sinks
  (`Audio Out`, `Stereo Audio Out`) under `Outputs`, so they read as
  distinct from the source generators sharing that category rather than
  interleaved among them; Filters' two one-pole nodes (`One-Pole Filter`,
  `One-Pole Highpass`) under `One-Pole`, mirroring the `Biquad` family's own
  grouping; and Effects' two reverb algorithms (`Reverb`, `Spring Reverb`)
  under `Reverb`. The same table also groups every construction-time-
  arity node family (below) under a same-named submenu, so `Splitter`/
  `Splitter 2`/`Splitter 8` land under Utility > Splitter,
  `Multiplexer`/`Multiplexer 2`/`Multiplexer 8` under Conditionals >
  Multiplexer, `Sequencer`/`Sequencer 4`/`Sequencer 16` under Conditionals >
  Sequencer, `Trance Gate`/`Trance Gate 4`/`Trance Gate 16` under Effects >
  Trance Gate, and `Mix`/`Mix 2`/`Mix 8` under Mixers > Mix - each submenu's
  bare, unsuffixed preset (`Splitter`, `Multiplexer`, ...) keeps its full
  label since it has no trailing size to strip, while the sized presets and
  the family's custom-size entry (below) strip down to just `2`/`8`/
  `(Custom Size)...`. Three entries go a level deeper still, the concrete
  case for the "sub-submenus" the one-segment-`subPath` categories above
  don't need: `DistortionNode`'s five `DistortionType`s share
  `List.of("Distortion & Waveshaping", "Distortion")`, landing under
  Effects > Distortion & Waveshaping > Distortion (a sibling of that same
  category's flat Bitcrusher and Waveshaper); `CompressorNode`'s three
  `CompressorType`s share `List.of("Dynamics", "Compressor")`, landing under
  Effects > Dynamics > Compressor (a sibling of Dynamics' flat Limiter/Noise
  Gate/Level Follower); `ParametricEqNode`'s three preset sizes plus its
  custom-size entry share `List.of("EQ", "Parametric EQ")`, landing under
  Effects > EQ > Parametric EQ (a sibling of EQ's flat Tone Stack/Graphic
  EQ). The outer segment is named "Distortion & Waveshaping" rather
  than plain "Distortion" specifically so the inner per-type submenu can
  still be called "Distortion" without the category and the node family
  reading as the same word twice in a row (`Effects > Distortion >
  Distortion > Soft Clip`) - `Dynamics`/`Compressor`/`EQ` don't collide, so
  those pairs needed no such rename.

  **Construction-time-arity nodes get a preset family plus one custom-size
  entry.** `SplitterNode`, `MultiplexerNode`, `SequencerNode`,
  `TranceGateNode`, `ParametricEqNode`, `MixNode`, and `TrackerNode` all take
  their port count (or, for `MixNode`, an equal-weight port map) as a
  constructor argument with no single universally-right default, so
  `NodeFactory` registers a small spread of preset sizes for each (mirroring
  convention 6's "sane default" treatment, just at three points instead of
  one) and `NodeCatalog` adds one more hand-written entry per family -
  `"<Name> (Custom Size)..."` - that prompts for an arbitrary size via a
  shared `promptSize(window, title, prompt)` helper (a `TextInputDialog`
  parsed as an `int`, rejecting anything under 1 - the same
  `TextInputDialog` shape the Additive Resynth fidelity prompt above uses)
  and constructs the node directly through a `customSized(window, title,
  prompt, IntFunction<Node>)` wrapper, unregistered like `Sound Clip`/`Dual
  Oscillator` above. Every size - preset or custom - saves and loads
  losslessly: `Serialization.PatchCodec` stores each family's actual port
  count (`outputs`/`inputs`/`steps`/`bands`/`ports`/`channels`) rather than
  the preset name that created it, so `NodeFactory`'s preset spread is purely
  a menu convenience, never a serialization constraint.
- **`NodeMenu`** - builds a categorized right-click `ContextMenu` from
  `NodeCatalog`'s entries, grouped by `NodeCatalog.CATEGORY_ORDER`, then each
  category's entries by `subPath`: `populate(...)` walks each entry's path
  one segment at a time, creating a `Menu` the first time a given path
  prefix is seen (keyed by the full `List<String>` prefix so far, so two
  entries sharing a two-segment path reuse the same submenu rather than each
  creating their own) and reusing it for later entries that share it - a
  category's tree can go arbitrarily deep (a submenu's own submenu) purely
  from what each entry's `subPath` declares, with no per-category or
  per-depth branch in `NodeMenu` itself. This replaced an earlier version
  that special-cased exactly one grouping (`"Biquad "`-prefixed labels under
  Filters, string-matched inline) - correct for the one category that had it
  worst at the time, but not a mechanism anything else could reuse; every
  other crowded category (Sources & Sinks, Oscillators, Effects, Utility)
  needed the identical grouping need met identically, not four more bespoke
  special cases. `displayLabel(...)` drops a redundant leading "&lt;submenu
  name&gt; " from a leaf's label once the submenu it's nested under already
  says it (`"Biquad Lowpass"` reads as plain `"Lowpass"` under Filters >
  Biquad; `"LFO Sine"` as `"Sine"` under Sources & Sinks > LFO) - the same
  generalization of what used to be a Biquad-only string trim, now driven by
  whatever the entry's own last `subPath` segment is rather than a hardcoded
  constant, and a no-op wherever a submenu's members don't share its name as
  a prefix (Phasors' members don't, so they render in full). Selecting a leaf
  item invokes its `action` at the point that was right-clicked and, if it
  returns a node (the user didn't cancel a `FileChooser`), calls
  `editor.addNode(node, x, y)`. Any `RuntimeException` from the action
  (unknown factory key, bad WAV file) surfaces as a non-fatal `Alert`
  instead of propagating.
- **`InputKey`** - a package-private top-level record, `(destination,
  destinationInput)`, shared by `GraphModel` (as its connection-map key) and
  `GraphEditor` (as the key of the once-per-edit lookup it hands every
  `NodeView`, see below) - the same "which input port is this" identity was
  previously duplicated as two near-identical private records (`GraphModel`'s
  `DestinationKey`, `GraphEditor`'s `PortKey`); unifying them means both call
  sites and `NodeView.refreshPortControlStates` now share one type instead of
  two structurally-identical ones drifting independently.
- **`GraphModel`** - the plain-Java (no JavaFX) backing store: the actual
  long-lived `Node` list and a `Connection` map, plus `buildPatch()`. Kept
  free of JavaFX so it's usable/testable without the toolkit. Connections are
  stored in a `LinkedHashMap<InputKey, Connection>` keyed by
  `(destination, destinationInput)`, relying on `Node`'s default identity
  `equals`/`hashCode` (never overridden) plus `String.equals` on the port
  name, matching the exact comparison the old linear-scan version used to do
  by hand. `addConnection` is a plain
  `Map.put`, so it replaces any prior connection to the same
  `(destination, destinationInput)` pair in O(1) rather than an O(connections)
  scan - this is the exact mechanism the per-port inline controls below lean
  on to hand off from a "shadow" default to a real wire with no
  special-casing. `LinkedHashMap` (not `HashMap`) preserves insertion order
  so `getConnections()` - and therefore the connection order `Patch`'s
  topological sort sees - is unaffected by the switch away from a list.
- **`GraphEditor`** - the JavaFX-side controller owning the canvas and the
  `NodeView`/`WireView` lists on top of one `GraphModel`, plus its own
  `NodeFactory`/`OscillatorFactory`/`NodeMenu` instances. The canvas returned
  by `getCanvas()` is a coordinate-space root holding two layered child
  `Pane`s - `wireLayer` behind `nodeLayer` - so wires always paint behind
  nodes via plain z-order (a wire is simply a `wireLayer` child, a node a
  `nodeLayer` child) rather than by inserting each new wire at index 0 of one
  shared child list (an O(existing children) shift that got worse as the
  graph grew). Both sub-panes are layout-neutral (no offset/scale/translate
  relative to `canvas`), which is what lets `WireView`'s
  `localToScene`/`sceneToLocal` coordinate math keep working unchanged
  regardless of which sub-pane a `NodeView`/`WireView` actually lives in -
  this constraint must hold for any future layer added the same way.
  Right-clicking empty canvas (`canvas.setOnContextMenuRequested`) opens
  `NodeMenu` at the click point; `addNode(Node, x, y)` places a node there
  directly - the only entry point now, since every caller has an explicit
  place to put a node (a click point, a drop point, a saved canvas position,
  or a materialized knob's offset from its owner) and the older cascading
  auto-spawn position (`addNode(Node)`, no coordinates) had none left.
  Implements the wiring gesture: press an output
  `PortHandle`, drag a temporary curve that follows the mouse, and on release
  resolve whatever's actually under the cursor
  (`MouseEvent.getPickResult().getIntersectedNode()`, walking up to the
  enclosing `PortHandle`) to commit a `Connection` - a real hit-test rather
  than trusting mouse capture, since JavaFX keeps the original press target
  captured through the drag. The gesture is primary-button-only: the
  `PortHandle` press/drag/release handlers in `NodeView.installWireSource`
  ignore (and don't consume) any other button. Without that filter, a
  right-click on an output handle would start a wire *and* let the
  `ContextMenuEvent` bubble to the canvas and open `NodeMenu` - the popup
  then swallows the mouse release, `finishWiring` never runs, and the
  zero-length temp curve (which renders as a ~40 px squiggle thanks to
  `layoutCurve`'s minimum control-point offset) is orphaned on the canvas
  next to whatever node the menu then spawns. Two backstops close the
  remaining leak paths: `beginWiring` starts by calling `cancelWiring()`
  (removing any leftover temp curve instead of silently overwriting the
  `tempWire` reference, e.g. if a second button was pressed mid-drag), and
  the canvas `ContextMenuRequested` handler also calls `cancelWiring()`
  before showing `NodeMenu`, so opening the add-node menu always abandons
  any in-flight wire cleanly. Maintains two incremental indexes alongside
  `wireViews` - `Map<NodeView, List<WireView>> wiresByNode` and
  `Map<PortHandle, WireView> wireByTargetPort` (both `IdentityHashMap`,
  matching the `==`-based comparisons used throughout this class) - updated
  at the two choke points every wire add/remove already funnels through
  (`finishWiring`, `detachWire`). These make node-drag wire-geometry updates
  O(wires actually attached to the dragged node) instead of a scan of every
  wire in the graph, and the "does this input port already have a wire"
  check `finishWiring` needs before accepting a new connection an O(1)
  lookup instead of a scan - both were previously O(all wires) per call, the
  drag-geometry one firing on every single mouse-drag-move tick. Tracks a
  single selected `NodeView`/`WireView`; clicking one selects it
  (highlighted border), clicking empty canvas clears the selection, and
  Delete/Backspace (the same screen-root event filter described above)
  removes whichever is selected, alongside the existing per-item right-click
  "Delete" menus. Deleting a
  wire that was superseding a port's shadow-constant default restores that
  shadow connection, so the port's inline control resumes controlling the
  engine's actual input rather than showing a stale value.

  **Right-clicking an input `PortHandle` deletes the wire into it.**
  `NodeView.installWireTarget` asks `GraphEditor.removeWireInto(handle)`, which
  is the `wireByTargetPort` index read directly - an input port holds at most
  one connection, so the handle identifies the wire with no hit-test and no
  ambiguity. This is a second route to what "Delete Wire" on the curve already
  does (same `removeWire`, so the same undo entry and the same shadow-constant
  restore), added because the 2.5 px curve is a small target while the port
  circle is a large one that is already where the user is looking. The handler
  **consumes the event only when a wire was actually removed**, so
  right-clicking an unconnected port still bubbles to the canvas and opens
  `NodeMenu` rather than being a dead click. Output handles are left alone:
  they fan out to any number of wires, so there is no single wire to mean. Fires an
  `onGraphChanged` callback after every structural edit so `SynthApp` can
  hot-swap the running patch; the same edit also builds one
  `Map<InputKey, Connection>` from a single `model.getConnections()` call and
  passes it to every visible `NodeView` to re-check whether its ports are
  superseded by a real wire (see below) - previously each `NodeView`
  independently called `model.getConnections()` and rescanned it per port,
  turning every edit into work proportional to nodes × ports × connections;
  building the lookup once per edit and handing every node an O(1) lookup
  fixes that. A centered "Right-click to add a node" hint label shows on an
  empty canvas and hides once at least one node exists. The wiring-gesture
  temporary curve and every permanent `WireView` share one cubic-control-point
  formula, `WireView.layoutCurve(CubicCurve, startX, startY, endX, endY)`
  (package-private static): both endpoints plus two control points offset
  horizontally by `max(40, |dx|/2)` so the curve always leaves/enters each
  port handle roughly horizontally regardless of how far apart or vertically
  offset the two ports are. `beginWiring`/`updateWiring` call it directly on
  the drag-preview curve; `WireView.updateGeometry` calls it on itself.

  **Dropping a wire on empty canvas spawns a connected node.** `finishWiring`
  used to return silently when the release landed on nothing. It now opens the
  same `NodeMenu` at the drop point, carrying the dragged `PortHandle` through
  to `addNodeWiredFrom(node, x, y, source)`, which places the chosen node and
  connects the dragged output to its **first input port**. Building a chain no
  longer means place-then-aim-at-a-6px-circle for every node.

  The drop target is classified by walking the pick result's parent chain: if a
  `NodeView` is found the release was on a node, and is ignored as before;
  otherwise it was empty canvas. Dropping on a node's body - as opposed to one
  of its port handles - therefore still does nothing, rather than opening a
  menu on top of an existing node.

  The pending source travels as an explicit parameter through
  `NodeMenu.show(...)` rather than as editor state. A `pendingWireSource` field
  would outlive a dismissed menu and could silently connect an unrelated node
  placed later. The plain right-click path calls the existing overload, which
  passes `null`, and nodes with no input ports (`Trigger`, `Constant`) are
  placed unconnected. Wiring only ever starts from an output handle -
  `installWireSource` is called on output handles only - so there is no
  input-to-empty-canvas direction to handle.

  **Save/load support**: `toDocument()` captures node instances, canvas
  positions, per-node shadow-constant values, real connections, and macros;
  `loadDocument(...)` clears the canvas and rebuilds all of it, resolving
  connections back to `PortHandle`s by port name. `clear()` discards every
  node/wire view and index wholesale (`nodeLayer`/`wireLayer`'s children,
  `GraphModel.clear()`) rather than removing nodes one at a time, then fires
  `notifyGraphChanged()`/`macrosChanged()` exactly once - an earlier version
  called the full single-node `removeNode` teardown (undo-record attempt,
  wire detach, macro-drop scan, shadow disposal, graph-changed notification)
  once per remaining node, which meant clearing an *n*-node graph did O(n)
  full `notifyGraphChanged()` passes (each rebuilding a
  `Map<InputKey, Connection>` and refreshing every still-present `NodeView`)
  for a result that only ever needs one such pass at the end. See
  [Serialization](serialization.md) for why shadow constants are saved as port
  values on their owning node rather than as nodes in their own right.
- **`NodeView`** - a draggable, resizable box per node instance: title (from
  `Node.getName()`), input ports down the left edge, output ports down the
  right, a `WaveformView` strip (see below) directly under the port rows,
  and a footer of inline controls below that (so
  adding/enabling/disabling a control never moves a port handle's Y
  position, which would otherwise force every attached `WireView` to
  recompute its geometry for no reason). A node's size is bound reactively
  (`widthProperty()`/`heightProperty()`) rather than fixed at construction -
  the background rectangle, header strip, title, output-port X position, and
  every footer control's width all follow the node's current size via
  property bindings instead of one-time pixel values, so resizing never
  needs to rebuild anything. A small resize handle in the bottom-right
  corner drives this: dragging it calls `resize(width, height)` clamped to a
  per-node minimum (the fixed `MIN_WIDTH` constant and a `minHeight` field
  computed once at construction from the node's actual port count and footer
  content, the same numbers that used to be the fixed size) - the box can
  grow freely in either direction but never shrinks below the size its ports
  and controls need to render without overlapping. Input port handles stay
  pinned to the left
  edge (`x = 0`) regardless of width, so only output ports/labels and footer
  widths need to track the width binding.

  Two footer mechanisms:
  - **Every input port on every non-`ConstantNode` node** gets one footer
    row: a `Slider` + `TextField` pair bound to an invisible "shadow"
    `ConstantNode`, created and wired into that port
    (`GraphModel.addConnection`) the moment the `NodeView` is built, seeded
    to `Port.defaultValue()`. Dragging a real wire into the same port
    supersedes the shadow connection for free (`GraphModel.addConnection`'s
    existing replace-on-same-destination behavior); `GraphEditor` then
    disables (greys out, doesn't remove) that row via
    `refreshPortControlStates(Map<InputKey, Connection>)` - an O(1)
    lookup per port against the shared map `GraphEditor` builds once per edit
    (see above), not a fresh scan of every connection per port. The row
    disables for **any** driving connection, not only a `ConstantNode` one -
    an oscillator, an LFO, another node's output, anything that isn't this
    binding's own `shadow` - since the shadow stops driving the port either
    way. An earlier version only checked `instanceof ConstantNode`, so wiring
    anything else into a port (the common case - most wires come from signal-
    producing nodes, not bare `Constant`s) left the dead shadow row looking
    live and editable, which is exactly backwards: the knob most likely to
    confuse someone is the one that looks like it still works. `knob`/
    `materialized` (below) still only update for a `ConstantNode` source,
    since only a `ConstantNode` can stand in for the shadow for macro
    purposes; a non-`ConstantNode` source disables the row via a separate
    `externallyDriven` flag that carries no such promotion meaning. Each row's
    slider range is derived from the port's own default (`±4x` its magnitude,
    floor of `±4`) rather than one universal range, since ports span
    everything from unit-range mix weights to kilohertz cutoff frequencies.
    Both this row's `Slider`+`TextField` pair and `ConstantNode`'s own control
    below it are assembled by one shared private helper,
    `buildSliderField(initial, min, max, DoubleConsumer setter, DoubleSupplier
    currentValue)`, returning a small private `SliderField(TextField field,
    Slider slider)` record - it wires the bidirectional slider-drag-updates-
    field / field-Enter-updates-slider listeners, the `%.3f` formatting, and
    the `NumberFormatException` recovery (re-reading `currentValue` to
    restore the field's text) exactly once; each call site then only differs
    in layout (absolute-positioned pair vs. an `HBox` row), sizing/range, and
    which node the `setter`/`currentValue` callbacks close over (`ConstantNode`
    directly vs. the shadow `ConstantNode` for a port).

    **One `KnobBinding` per port, not four parallel maps.** An earlier version
    tracked a port's shadow, its footer row, its macro button, and (once
    promoted) the promoted knob in four separate `Map<String, ...>` fields,
    each keyed by port name (plus a magic empty-string key for `ConstantNode`'s
    own control) - a knob's current state was whichever of two of those maps
    happened to have an entry, resolved by a repeated
    "shadow-or-else-promoted" fallback at every call site that needed to know
    the current knob. That fallback was correct only for the `NodeView`
    instance that lived through the promotion; a freshly-constructed
    `NodeView` - from `loadDocument(...)`, from undoing a node delete, or from
    any other reconstruction - started with an empty "promoted" map regardless
    of what the underlying graph actually looked like, so a macro'd port on a
    reloaded or undone node showed an unpromoted `+` button that, if clicked,
    would materialize a *second*, disconnected knob instead of re-exposing the
    real one.

    Each port now gets one `KnobBinding` (`portName`, `shadow`, `field`, `row`,
    `macroButton`, plus mutable `knob`/`materialized`/`externallyDriven`),
    created once at construction and never discarded. `knob` always names
    whichever `ConstantNode` is *currently* driving the port - the shadow
    before promotion, the promoted node after. Two explicit transitions own
    that field directly: promoting sets `knob`/`materialized` to the new node
    the moment `promoteToMacro` succeeds, and restoring a shadow connection
    (below) resets them back. Between those transitions,
    `refreshPortControlStates` - already called on every graph change with
    the connections-by-port lookup - self-heals a *reconstructed* `NodeView`
    by checking each not-yet-disabled binding's live connection: if some other
    `ConstantNode` (not this binding's own shadow) is now driving the port,
    that is a promoted knob surviving the reconstruction, and the binding
    adopts it (`knob`/`materialized`). This is what makes a promoted macro
    read correctly after a save/reload or an undo, not just within the
    session that promoted it. `shadow` itself is never discarded either way,
    which is what lets the knob un-promote later (below).

    `externallyDriven` is the separate, broader flag that actually gates
    `row.setDisable(...)`: it flips true for *any* non-shadow source, whether
    or not that source is a `ConstantNode`. It exists apart from `materialized`
    because `knob` is typed `ConstantNode` and only a `ConstantNode` source can
    legally be assigned to it - `materialized` stays the narrower "a real
    `ConstantNode` (possibly promoted) has taken over" signal that
    `onMacroButton` and `disposeShadowNodes` (below) still key off, while
    `externallyDriven` alone answers "should this row be disabled." A port fed
    by an oscillator sets `externallyDriven` without ever touching `knob` or
    `materialized`, so `disposeShadowNodes` still correctly reclaims that
    binding's now-unused `shadow` when the node is deleted, rather than
    mistaking it for a materialized knob's own shadow (which must *not* be
    removed twice, see below) just because the row happened to be disabled.
  - **`ConstantNode`** keeps its own directly-bound control instead (its
    value isn't a port at all, so it sits outside the shadow mechanism
    entirely) - a `Slider` (range `-2000..2000`) plus a `TextField` for
    precise entry, both writing straight to `setValue`. It still gets a
    `KnobBinding` (`portName` `null`, `shadow` and `knob` both the node
    itself, permanently `materialized`), so the macro-button code below needs
    no separate case for it.
  - **Every port control row, and `ConstantNode`'s own control, ends in a
    macro button.** Unpromoted it reads `+` with an "Expose as a macro"
    tooltip; once promoted it shows the **macro's name**, takes the
    `macro-active` style class, and opens a menu with Rename..., Set range...,
    and Remove macro. The button replaced a right-click handler, which was
    undiscoverable and left no sign afterwards that a knob was exposed. The
    button's action looks up its `KnobBinding` by port-name key at click time
    (never captures it), so it always reads the binding's current state
    rather than whatever it was when the button was built.

    The name is shown *on the button* rather than on a second line in the row,
    because footer height is computed once as
    `inputs.size() * CONTROL_ROW_HEIGHT` - a taller row for promoted knobs
    would desynchronise that arithmetic for every node.

    **Promoting a port knob materializes its shadow, and the naming dialog
    gates the materialization, not the reverse.** A shadow `ConstantNode` has
    no `NodeView`, so a macro bound to one used to be dropped by
    `toDocument()`/`PatchCodec` and drove nothing (see
    [Serialization](serialization.md)). `GraphEditor.materializeKnob(owner,
    port, shadow)` places the binding's `shadow` as a real `Constant` node to
    the left of its owner and draws the wire; the `Connection` already existed
    in `GraphModel`, so only the `NodeView` and `WireView` are new -
    `connect(...)`'s tail was factored into `attachWire` so both paths share
    it. `NodeView.onMacroButton` calls `materializeKnob` only from *inside*
    the naming dialog's `showAndWait().ifPresent(...)` callback, and only if
    the binding is not already `materialized` - an earlier version
    materialized first and asked for a name after, so cancelling the dialog
    still left a floating, macro-less `Constant` node behind. If a macro is
    later removed from an already-materialized knob (the "Remove macro" menu
    item), the binding keeps pointing at the same materialized node, so the
    button's next click re-exposes it as a macro directly rather than trying
    to materialize an already-gone shadow and silently failing.

    `TriggerNode` no longer gets a Fire button - it fires on its own, see
    [Envelopes](envelopes.md).
  - **Deleting the wire into a materialized knob restores the shadow, live.**
    `GraphEditor.removeWire`/`detachWire(wire, restoreShadow=true)` calls
    `NodeView.restoreShadowConnection`, which re-adds a `Connection` from the
    binding's (never-discarded) `shadow` back into the port and resets
    `knob`/`materialized`/`externallyDriven` and re-enables the row directly,
    in the same call -
    not by waiting for the next `refreshPortControlStates` pass to notice,
    since that pass only ever *upgrades* an unmaterialized binding, it never
    downgrades a materialized one back (a materialized binding whose driving
    node happens to still be there, just temporarily equal to `shadow` by
    reference during the same session a promotion just happened, must not be
    mistaken for "not yet promoted"). An earlier version looked the shadow up
    in a map it had already deleted the entry from at promotion time, found
    nothing, and left the port permanently undriven with its row disabled for
    good.
  - **Deleting the owning node disposes only the shadows still worth
    disposing.** `GraphEditor.removeNode` calls
    `NodeView.disposeShadowNodes(GraphModel)`, which removes a binding's
    `shadow` from the model unless that binding is materialized *and* its
    `shadow` **is** the promoted node (the live-this-session case, where
    promotion handed the very same object its own `NodeView` rather than
    creating a separate one) - in every other case (never promoted, or
    promoted as a genuinely separate node reconstructed from a save/undo) the
    `shadow` is either still privately owned by this port or an unused
    artifact of construction, and is safe to remove. A promoted knob with its
    own independent `NodeView` is never touched here; deleting it goes
    through its own node's delete action, not as a side effect of deleting
    whatever it feeds.
  - **Values stay in sync in both directions.** `buildSliderField` writes into
    the node but nothing observed the node, so moving a macro slider left the
    node's own knob showing a stale number. `NodeView.syncControlsFromNodes()`
    re-reads every binding's current `knob` into its `SliderField`, guarded by
    a `syncing` flag so setting the slider does not write back and loop;
    `GraphEditor` exposes
    `refreshKnobDisplays()` for the panel to call and `setOnKnobChanged(...)`
    for the reverse direction, which `SynthApp` binds to
    `MacroPanel::syncValues`.
  - **Dual Oscillator / Wavefolder placements** (detected via
    `OscillatorNode.getOscillator()` returning a
    [`WrapperOscillator`](oscillators.md) - `DualOscillator` and `Wavefolder`
    are its only two implementations, so `NodeView` checks the interface once
    rather than each concrete class) get one extra footer row above the port
    controls: a `ComboBox<String>` listing every `OscillatorFactory` name,
    initialized to `wrapper.getBase().getName()` and calling
    `wrapper.setBase(...)` on selection - the base oscillator is a live,
    swappable field (see
    [Oscillators](oscillators.md)), not a one-time construction argument.
    Swapping it also calls `NodeView.refreshTitle()`, which re-reads
    `node.getName()` into the header `Label` and the docs button's
    `Tooltip` - both were set once from the constructor's snapshot of
    `getName()` and never touched again, so picking a new base in the combo
    changed what `Wavefolder.getName()`/`DualOscillator.getName()` would
    *return* (`"Folded " + base.getName()`) without changing what the header
    actually *showed*, leaving a node visibly labeled "Folded Sine" driven by
    a Triangle underneath. `title` and `docsTooltip` are `NodeView` fields
    rather than constructor locals specifically so this one shared
    `refreshTitle()` can reach them from here and from the formula editor
    below, instead of two independent, easy-to-desync copies of the same
    "re-read `getName()`" logic.
  - **Formula placements** - any node whose `Expression.FormulaSource`
    (`getSource()`/`setSource(String)`) is reachable - either directly
    (`Effects.WaveshaperNode` implements it itself) or through
    `OscillatorNode.getOscillator()` returning one
    (`Oscillators.Implementations.ExpressionOscillator` does) - get a
    `TextArea` filling the rest of the node's footer (its height is bound to
    follow the node's own height, so resizing the node resizes the editor)
    seeded with the current formula. `NodeView.formulaSourceOf(Node)` checks
    both shapes with one method, so `buildFormulaControl` doesn't care which
    kind of node it's editing. Every keystroke calls
    `FormulaSource.setSource(text)` directly, no separate "apply" step - a
    formula that fails to parse throws `ExpressionException` (see
    [Expression](expression.md)), which is caught right there and turned into
    a red border plus a `Tooltip` showing the parse error, while both
    `FormulaSource` implementations deliberately leave their previous,
    still-valid expression in place until a parseable one replaces it - so a
    syntax error while mid-edit never breaks a patch that's already playing,
    it just flags the box until the formula parses again. A successful
    `setSource(text)` also calls `refreshTitle()` (above), since
    `ExpressionOscillator.getName()` returns `"Expression (" + source + ")"`
    - the same stale-header bug the base-oscillator combo had, and the same
    fix; a failed parse deliberately does *not* refresh the title, matching
    the node's own choice to keep the last-valid expression live rather than
    the just-typed, unparseable one. `ExpressionOscillator`
    is the one oscillator with no other on-node control (no ports beyond
    `phase`), which is why this needed the resizable-node work in the first
    place: a fixed-size box has no room for an editable formula of arbitrary
    length. `WaveshaperNode` (see [Effects](effects.md)) has one ordinary
    port (`in`) alongside its own formula editor, using the same footer slot
    the port-controls loop already reserves before appending the editor
    below it - no extra layout case needed for a node that has both.
  - **Note placements** - any node implementing `Sources.NoteSource`
    (currently just `Sources.NoteNode`) gets one footer row, a single-line
    `TextField` seeded with `getNoteText()`, occupying a fixed
    `NOTE_ROW_HEIGHT` the same way the wrapper-oscillator combo occupies a
    fixed `COMBO_ROW_HEIGHT` - unlike the formula `TextArea` above, a note
    name is always short, so it never needed the resizable, height-bound
    editor formulas do. Detection is a plain `node instanceof NoteSource`
    inline in the constructor rather than a `noteSourceOf(Node)` helper like
    `formulaSourceOf`/`wrapperOscillatorOf` use - those two unwrap an
    `OscillatorNode` to find the interface on the oscillator it wraps,
    `NoteSource` is implemented directly by the node itself, so there is
    nothing to unwrap. Every keystroke calls `NoteSource.setNoteText(text)`
    directly, the identical live-validate/red-border/`Tooltip`/keep-the-
    last-valid-value shape `buildFormulaControl` uses for
    `ExpressionException` - `NoteNode.setNoteText` throws
    `IllegalArgumentException` on an unparseable note name instead, caught
    the same way. A successful call also invokes `refreshTitle()`, since
    `NoteNode.getName()` returns `"Note (" + noteText + ")"` - the same
    dynamic-header case the base-oscillator combo and the formula editor
    both already need it for (above), so no new plumbing was needed, only a
    third caller of the existing method.

  Dragging and the "Delete Node" context menu are scoped to a header strip
  rather than the whole body, so they don't compete with the inline controls
  or port-handle drags for mouse events; both the header context menu and
  `WireView`'s consume their `ContextMenuEvent` so a right-click on a
  node/wire doesn't also pop `NodeMenu`'s canvas-level menu underneath it.

  **Every node's header carries a "?" button and a hover tooltip**, both
  backed by `Docs.keyFor`/`Docs.shortDescription` (see `Docs`/
  `DocumentationView` above). The button is a real `Button` child added after
  `headerBackground`/`title`, so it renders on top and takes its own clicks
  without touching `installDrag`/`installContextMenu` (both installed only on
  `headerBackground`); `title`'s width binding shrinks by the button's width
  to leave room for it. Clicking it calls `editor.openDocumentation(this)`,
  which fires `GraphEditor`'s `onOpenDocumentation` callback - the same
  `Consumer<Node>` pattern `onGraphChanged`/`onKnobChanged` already use - that
  `SynthApp` wires to switch to the docs screen and jump straight to that
  node's article. The hover tooltip installs on `headerBackground` itself
  (not the mouse-transparent `title`), via `Tooltip.install`, matching this
  file's existing plain-shape tooltip convention (used elsewhere for port
  handles) rather than `Control.setTooltip(...)` (used for the "?" button
  itself, an actual `Button`).
- **`WaveformView`** - a package-private `Canvas` strip (36 px tall, width
  bound to its node's width) drawn on every `NodeView` between the port rows
  and the footer controls, showing the node's most recent audio block as an
  oscilloscope-style trace over a midline. `NodeView.refreshWaveform(Patch)`
  fills a reusable per-view scratch array via the patch's visualization taps
  (`Patch.copyOutputBlock` for the node's selected output port, defaulting to
  port 0; `Patch.copyInputBlock` for the `AudioOutNode`, whose only signal is
  its `in` block - so the final mix is visible right on the sink) and hands
  it to `WaveformView.render`, which copies into its own retained buffer (so
  a later resize can redraw without fresh data) and repaints. By default the
  trace is auto-scaled per frame to `max(1, peak)` - unit-range audio draws
  at a stable scale while out-of-range signals like `PhasorNode`'s `0..2pi`
  ramp compress to fit instead of clipping flat - non-finite samples draw as
  `0`, and each pixel column samples the block by stride rather than plotting
  all 1024 points. A snapshot can mix two adjacent blocks because the taps
  are deliberately unsynchronized with the render thread - see
  [Patch](patch.md) for why that's accepted. Nodes not present in the current
  `Patch` (or ports out of range) copy nothing and the display just keeps its
  last frame; before the first play, every strip shows the flat midline it
  painted at construction.
- **Per-node graph settings** - a small "⚙" `Button` overlaid on the
  top-right corner of each `WaveformView` (added after the canvas so it wins
  hit-testing over the mouse-transparent strip) opens `WaveformSettingsDialog`,
  a `Dialog<WaveformSettingsDialog.Result>` built the same way as
  `ExportDialog`. It lets the user toggle the strip on/off
  (`WaveformView.setEnabled`, which also short-circuits
  `NodeView.refreshWaveform` so a disabled strip costs nothing per frame and
  is drawn as a blank midline rather than a stale trace), replace the
  auto-scaled peak range with a fixed min/max (`WaveformView.setRange`), and,
  for nodes with more than one output port (`StereoMixNode`, `PannerNode`,
  `FunctionGeneratorNode`, `CounterNode`, a multi-arity `DemultiplexerNode`,
  ...), pick which output port `refreshWaveform` taps via
  `Patch.copyOutputBlock`'s output-index argument - the source `ChoiceBox` is
  disabled when there is only one possible source (including the
  `AudioOutNode` input-fallback case, listed as a single `"in0"` entry).
  These settings live only on the `NodeView` instance - they are a viewing
  preference, not part of the patch, so they reset to enabled/auto-range/port
  0 on reload and are never written by `PatchCodec`.
- **`WireView`** - a permanent `CubicCurve` between two `PortHandle`s, with a
  "Delete Wire" context menu and click-to-select (highlighted stroke);
  repositions itself off `localToScene`/`sceneToLocal` rather than raw
  layout coordinates so it stays correct inside the canvas's `ScrollPane`.
