# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Petroleum (formerly JSynth) is a Java synthesis engine: oscillators/noise generators, a recursive-descent
math-expression parser for user-defined waveforms, a realtime-safe node
graph (filters, effects, mixers, conditionals) wired together by a `Patch`
execution engine, a cross-platform `javax.sound.sampled`-based audio engine
(realtime playback + offline WAV rendering), a JavaFX visual node-graph
editor on top, and Diesel - a C-like text format (`.dsl`) that converts to and
from a patch losslessly and renders to WAV from the `petroleum render` command
line. Binary patches are `.ptr` zips (legacy `.jsynth` files still open). The engine and the JavaFX editor are one single-module
Maven project rooted at the repository root - an earlier split into a
`JSynthLib` library module plus a separate app module was abandoned, so
there is no nested module directory and no parent POM.

## Build & development commands

- Build: `mvn -o compile` (or `mvn compile`) from the project root. All
  lifecycle plugins are pinned in `pom.xml` rather than left to Maven's
  defaults (which on Maven 3.8 means compiler 3.1 from 2013); `maven.compiler.
  release` is used instead of `source`/`target`, so the JDK 17 *API* is checked
  and not just the syntax level. **`mvn -o package` does not work** - the jar
  and surefire plugins have transitive dependencies that are not in the local
  cache, and that was equally true of the old default versions, so it needs one
  online run. `compile` and `javafx:run` are the offline-capable targets. The
  project depends on JavaFX (`org.openjfx:javafx-controls`/`javafx-graphics`/
  `javafx-web` - the last one backs the in-editor documentation screen's
  article pane, a `WebView`, and is the only reason this project has a web
  dependency at all - resolved to the current OS via the `os-maven-plugin`
  build extension, so the same `pom.xml` works unmodified on
  Windows/macOS/Linux) - the *first* build after a fresh `~/.m2` cache must
  be online (`mvn compile`, not `-o`) so those dependencies download; `-o`
  works again afterward.
- Run the GUI: `mvn javafx:run` (needs a real display - fails immediately
  in a headless environment).
- Render a patch headlessly: `java -cp target/classes Petroleum.Main render
  song.dsl -o song.wav [--duration s]`. `Petroleum.Main` is the packaged entry
  point: `render` never touches JavaFX, and anything else launches `SynthApp`.
  See `docs/internals/diesel.md` (implementation) and `DIESEL.md` at the repo
  root (the user-facing language guide, whose node reference table is
  generated from `NodeFactory` - regenerate it when node types or ports change).
- No test framework is configured (no JUnit dependency, no `src/test`
  directory) - there is no `mvn test` to run. Verification so far has been
  done by writing a throwaway smoke-test class, compiling it against
  `target/classes`, and running it directly:
  ```
  javac -cp target/classes -d /path/to/scratch YourSmoke.java
  java -cp target/classes:/path/to/scratch YourSmoke
  ```
  Playback (`AudioEngine.play`) and the JavaFX editor need a real audio
  device / display respectively, so they can only be smoke-tested this way
  indirectly (e.g. via `AudioEngine.renderToWav`, which needs neither) -
  actually hearing sound or exercising the editor requires a real machine.
- No linter is configured.
- **Native builds**: `mvn -Pnative-windows package`, `mvn -Pnative-mac
  package`, or `mvn -Pnative-linux package` produce a real platform installer
  (`.msi`, `.dmg`, `.deb`) under `target/dist/`, via three opt-in profiles in
  `pom.xml` that shell out to the `jpackage` binary bundled with the JDK
  (14+) - there is no cross-building, so each profile only works run on that
  OS, matching how the plain `os-maven-plugin`-resolved JavaFX dependencies
  already behave. Each profile stages a `target/jpackage-input/` directory
  (the project's own jar plus every runtime dependency jar, via
  `maven-dependency-plugin`/`maven-resources-plugin`) and points `jpackage`
  at it with `--main-class Petroleum.Main` (the Windows profile also adds a
  console launcher, `petroleum-cli`, from `src/main/jpackage/`); the resulting installer
  bundles a full Java runtime, so nothing but the OS's own installer
  mechanism is needed to run it - not even a JDK. Like `mvn -o package`
  above, the first run needs to be online (`exec-maven-plugin` and a newer
  `maven-dependency-plugin` than the one already cached for other plugins
  aren't in a fresh `~/.m2`); `-o` works afterward. `${jpackage.type}`
  defaults to the most common installer per OS (`msi` / `dmg` / `deb`) but
  can be overridden, e.g. `-Djpackage.type=rpm` on Linux or
  `-Djpackage.type=app-image` on any OS to skip the installer step
  (`app-image` needs no extra tooling); the Windows `msi`/`exe` types need
  the WiX Toolset installed, and Linux `deb`/`rpm` need `dpkg-deb`/`rpmbuild`
  on `PATH` - macOS's `dmg`/`pkg` need nothing beyond Xcode's command line
  tools. `${app.version}` (not `${project.version}`) feeds `--app-version`,
  since jpackage rejects a `-SNAPSHOT` suffix; each profile's `--icon` points
  at the matching file under `src/main/resources/icons/` (`petroleum-icon.ico`
  for Windows, `petroleum-icon.icns` for macOS, `petroleum-256.png` for Linux) -
  the same mark `SynthApp` sets as the in-app window icon via
  `stage.getIcons()`.

## Architecture

### Package layout (all under `src/main/java/Petroleum`)

- `Oscillators` - `IOscillator` interface (`getSample(phase)`, `getName()`),
  `OscillatorFactory` (name -> `Supplier` registry), `OscillatorNode` (bridges
  an `IOscillator` into the `Graph` node system), and
  `Oscillators.Implementations` (every concrete waveform/noise generator).
- `Expression` - a standalone recursive-descent parser/evaluator for
  user-supplied math expressions (e.g. `"sin(x) + 0.3*sin(3*x)"`), consumed
  by `Oscillators.Implementations.ExpressionOscillator` but otherwise
  independent of the rest of the codebase.
- `Dsp` - pure-DSP helper classes shared across node packages (`Filters`,
  `Effects`, `Envelopes`, `Conditionals`, `Sources`, `Granular`):
  `DelayLine` (fractional-sample circular buffer), `LfoPhase` (free-running
  phase accumulator), `EnvelopeFollower` (attack/release exponential
  smoother), `DspMath` (one-pole cutoff-to-alpha formulas, linear
  interpolation), `EdgeDetector` (the rising/falling-edge check convention 8
  requires), `GrainPool` (fixed-size, allocation-free granular-synthesis
  voice pool - see `Granular` below). Deliberately not `Node` implementations
  - no `Port`s, no `process()` - so they live in their own sibling package
  rather than `Utility`, which is reserved for actual graph nodes.
- `Graph` - the node-graph abstraction only: `Node`, `AbstractNode`,
  `NodeContext`, `Port`, `OneShot`, `Stateful`, `NodeFactory`. Deliberately
  holds no concrete node implementations. `OneShot` (`boolean isIdle()`) is the
  marker a node implements when it has a defined end, letting
  `Patch.isFinished()` work against an abstraction rather than importing
  `Envelopes`; `Stateful` (`saveState`/`loadState` over a `String` map) is the
  marker for mutable state that must survive a save/load cycle, letting
  `Serialization` stay generic.
- `Filters`, `Effects`, `Mixers`, `Conditionals` - concrete `Node`
  implementations, each a sibling package to `Oscillators`/`Graph`/
  `Expression`, not nested inside `Graph`. `Conditionals` also holds the
  trigger-driven state nodes (`LatchNode`, `CounterNode`, `SequencerNode`),
  since edge detection is the thing they have in common.
- `Granular` - two more concrete `Node` implementations, same sibling
  treatment: `GranularPlayerNode` (grains a static imported sample buffer,
  deliberately unregistered in `NodeFactory` like `Wavetable`/`OscillatorNode`
  since it needs a buffer at construction) and `GranularFreezeNode` (records
  its input live and grains it in real time, with a freeze gate - fully
  `NodeFactory`-registered, no construction argument). Both drive the one
  shared `Dsp.GrainPool` engine rather than duplicating grain scheduling,
  windowing, and voice-stealing twice.
- `Instruments` - one more concrete `Node` implementation, same sibling
  treatment: `InstrumentNode`, an OpenMPT-style multisample instrument (a
  mutable `Instrument` data holder of imported-WAV sample zones, each with a
  root pitch and a key range, plus a breakpoint volume envelope). Polyphonic:
  `InstrumentNode(int channelCount)` exposes a `pitchN`/`gateN` pair and an
  `outN` per channel, all sharing one `Instrument` definition, so a single
  node covers a whole song's channels rather than one node per voice. Its
  `pitchN` input is a raw Hz value, never quantized
  to a note number anywhere in the signal path, so it plays back whatever
  frequency it's given - fully microtonal with no dedicated tuning system
  needed. `NodeFactory`-registered at four sizes (`Instrument 2`/`4`/`8`/`16`,
  each baking in a channel count per convention 6; an instrument starts empty
  and samples/zones are added from its own "Edit" screen,
  `Gui.InstrumentEditorView`), but still needs `PatchCodec`
  special-casing (`Serialization.InstrumentCodec`) to embed each zone's
  sample data via `PatchArchive`, the same reason `GranularPlayerNode`/
  `Wavetable` do. See `docs/internals/instruments.md`.
- `Special` - `TurretNode` (one trigger in, a burst of `count` pulses
  `interval` seconds apart out); the editor's "Special" category.
- `Sources`, `Sinks` - more concrete `Node` implementations, same sibling
  treatment: `Sources.ConstantNode`/`PhasorNode` originate signals (a
  settable live value, a frequency-to-phase accumulator);
  `Sinks.AudioOutNode` terminates a patch.
- `Envelopes` - `Node` implementations that shape a signal over one sound
  event rather than one cycle: `ArpeggioNode` (stepped multiplicative pitch
  ladder), `TriggerNode` (single-sample pulse source,
  self-firing off the `NodeContext` clock with `delay`/`interval` ports - never
  give it a manual `fire()` again, see convention 9),
  `EnvelopeNode` (sfxr's attack/sustain/punch/decay one-shot), `AdsrNode`
  (conventional gate-driven ADSR), `RampNode` (one-shot line, the pitch-slide
  primitive). Sibling package, same treatment as `Sources`/`Sinks`.
- `Utility` - generic signal-math `Node` implementations that don't fit any
  of the above families: `SplitterNode` (1 input fanned out to N outputs),
  `AmplifierNode`, `MapRangeNode`, `ClampNode`.
- `Modules` - `PatchNode`: a saved patch used as a node, so a user can build
  custom instruments and effects out of ordinary `.ptr` files. The inner
  `AudioOutNode` becomes the single output port; every macro becomes an input
  port, fed through a `PortBridgeNode` that replaces the macro's `ConstantNode`
  inside the built patch (so an input carries audio, not just a knob value).
  `PatchNode.create` is the only entry point - it returns the `OneShot`
  subclass only when the document holds a one-shot. The one node package that
  depends on `Serialization` (for `PatchDocument`), which depends on it back;
  see `docs/internals/modules.md` for why that edge is deliberate.
- `Patch` - the wiring/execution engine `Graph` deliberately doesn't
  provide: `Connection` (named-port edge) and `Patch` (topological sort,
  cycle/single-`AudioOutNode` validation, allocation-free `renderBlock`, plus
  `renderNested` - the same render at an offset that does *not* advance the
  `NodeContext`, so a nested patch shares the outermost clock).
- `Serialization` - saving/loading patches as a zip archive (a JSON
  document plus lossless binary sample/data entries): `Json` (hand-rolled
  reader/writer, so the project keeps its JavaFX-only dependency set),
  `PatchCodec`, `OscillatorCodec`, `PatchArchive` (the `.ptr` zip container:
  `patch.json` plus deduplicated sample entries and opaque node data entries),
  `SampleCodec` (bit-exact sample compression), `FormatV5Migration`,
  `Diesel` (the `.dsl` text syntax: it prints and parses the same tree
  `PatchCodec.saveRoot`/`loadRoot` use, so it has no per-node-type code; samples
  and data blobs are inlined as base64 - never add a Diesel-only name table or
  node switch), 
  `PatchDocument` (the editable document
  - name, nodes, connections, canvas positions, port values, macros), `Macro`,
  `SerializationException`. Depends on `Graph`/`Patch`, never the reverse.
  **Nothing external is ever saved by reference** - an imported sample is
  written as audio data and a patch used as a node is embedded by value, so a
  `.ptr` file always opens on its own. Exactly one format version is
  written (`PatchCodec.FORMAT_VERSION`); an older file is read only by walking
  it forward through `PatchCodec.MIGRATIONS` (5 -> 6 is `FormatV5Migration`),
  and anything without a migration path is rejected on load.
- `Generators` - seeded category generators (`SoundCategory`, `CategorySpec`,
  `SoundGenerator`) that emit a real `PatchDocument` with `Macro`s attached,
  never a separate parameter struct - this is what makes "open the generated
  sound in the graph editor" a no-op rather than a conversion. Depends on
  `Serialization`/`Envelopes`/`Oscillators`; nothing depends on it except `Gui`.
- `Audio` - `AudioEngine`, realtime playback and offline WAV rendering via
  `javax.sound.sampled` only (no native deps - cross-platform by
  construction), plus `ExportSettings` (sample rate, bit depth, channels,
  normalize, trim). Export sample rate drives the `NodeContext`, so a lower
  rate is a genuine lower-rate render rather than a resample; `normalize`/
  `trimSilence` are the only options that force the buffered render path
  instead of the streaming one.
- `Collab` - `CollabClient`, the headless client for opt-in realtime
  collaboration (plain or TLS socket, newline-delimited JSON, to the separate
  `../Petroleum-Server` relay). The editor side is `Gui.CollabBridge`, which
  polls and diffs `GraphEditor.toDocument()` as a key map and applies remote
  ops through the editor's silent mutators. Nothing connects unless the user
  picks Session > Host/Join. See `docs/internals/collaboration.md`.
- `Gui` - `SynthApp` (eight screens, switched through one `StackPane` and a
  private `Screen` enum: `QuickView`, the graph editor, `DocumentationView`,
  and the dedicated per-node editors `TrackerEditorView`,
  `PianoRollEditorView`, `WaveformDrawView`, `SoundClipEditorView` and
  `InstrumentEditorView`, each opened from its node's own button and returning
  to whichever screen launched it), built on
  `Patch`/`Audio`/`Serialization`/`Generators`
  as fixed APIs. `Docs` holds the in-editor documentation content (a short
  description, a longform article, and a worked wiring example per placeable
  node kind, plus editor-feature guide articles), reachable via each node's
  header "?" button or the docs screen's own index; `DocumentationView`
  renders an article's body as HTML in a `WebView` rather than plain JavaFX
  controls, specifically so the wiring examples render as proper monospace
  code blocks. Colours and spacing live in `Gui.Theme`; JavaFX control
  styling lives in `src/main/resources/petroleum.css`, applied once in
  `SynthApp.start` - do not reintroduce per-node `setStyle`/`Color.web`
  literals.

### Governing conventions

1. **`Graph` and the node-implementation packages must stay separate.** New
   filter/effect/mixer/conditional types belong in their own sibling package
   (or as new files in the existing `Filters`/`Effects`/`Mixers`/
   `Conditionals` packages) - never inside `Graph` itself or a `Graph.Nodes.*`
   subpackage. This layout was deliberately corrected once already; don't
   reintroduce the old structure.
2. **The `Node.process()` hot path never allocates.** Nodes process audio in
   blocks, not one sample at a time:
   `process(float[][] inputs, float[][] outputs, int frameCount, NodeContext
   context)` reads/writes primitive arrays indexed first by port position,
   then by sample index within the block (`inputs[portIndex][sampleIndex]`)
   - no `Map`, no boxed `Float`, no per-block or per-sample object creation.
   A node must never write into its `inputs` arrays: an input port's
   backing array may be aliased directly to the producing node's output
   array (zero-copy wiring set up once, at `Patch` construction), so
   mutating it would corrupt the producer's own output and any other
   consumer sharing that array - nodes only ever read `inputs` and write
   `outputs`. Unconnected input ports are expected to already be filled
   with that port's `Port.defaultValue()` (across the block's full
   capacity) by `Patch` at construction time. Port name -> index resolution
   (the `Node.inputIndexOf`/`outputIndexOf` interface defaults) happens once,
   at patch-wiring time, never per block or per sample. `NodeContext.advance`
   is likewise called once per block by `Patch`, not once per sample.
   Parameter inputs are still read one sample at a time inside a node's
   per-sample loop (e.g. `inputs[CUTOFF][s]`), since a parameter can be
   modulated within a block - see convention 3 for how that per-sample read
   coexists with coefficient caching.
3. **Cache expensive per-sample coefficients.** Any node whose parameters
   require trig/`pow`/`exp` to turn into usable coefficients (see
   `BiquadFilterNode`, `OnePoleFilterNode`, `CompressorNode`,
   `BitcrusherNode`) checks - inside its per-sample loop - whether the
   relevant inputs (or the sample rate) actually changed since the last
   sample, and only recomputes those coefficients when they did, rather than
   recomputing every sample regardless - most patches hold a knob steady far
   more often than they sweep it.
4. **Avoid `%` in circular-buffer indexing.** Ring-buffer wraparound (delay
   lines, comb/allpass filters) uses `if (++index >= length) index = 0;`
   rather than `index = (index + 1) % length`.
5. **`IOscillator.getSample(phase)` expects pre-wrapped phase.** Callers
   pass phase already wrapped to a single cycle in `[0, 2*PI)`;
   implementations (e.g. `Sawtooth`, `Triangle`) don't re-wrap it themselves.
6. **Factories register a name -> a fully self-contained `Supplier`.**
   `OscillatorFactory` and `NodeFactory` are name -> `Supplier` registries;
   most entries wrap a bare no-arg constructor, but some bake in a fixed
   default argument for a type that technically needs one (`BiquadFilterNode`'s
   filter type, `MixNode`'s default 4-input weight map, `SwitchNode`'s
   default 4-way arity, `SplitterNode`'s default 4-way arity) - the factory
   just supplies a sane default, the class itself doesn't change. Only types
   needing a value with no sane universal default (`Wavetable`,
   `DualOscillator`, `Wavefolder` need a base oscillator; `OscillatorNode`
   needs an `IOscillator`) are meant to be constructed directly and stay
   unregistered.
7. **No comments in source, ever.** This is a strict, deliberate
   project-wide policy - no Javadoc, no inline comments, anywhere. All
   explanatory documentation (design rationale, algorithm references,
   grammars, port lists) lives in `docs/internals/*.md` instead. When adding
   or changing code, update the matching doc file rather than commenting
   the code.
8. **A trigger is an ordinary signal, never an event object.** Nodes that
   start on an event (`EnvelopeNode`, `RampNode`) take an ordinary `float`
   input port carrying `1.0` for exactly one sample and `0.0` otherwise, and
   detect a **rising edge** (`previousTrigger <= 0 && trigger > 0`) rather
   than any non-zero value - so a trigger held high across a whole block
   fires once, not every sample. `TriggerNode` produces such a pulse; so does
   any `SelectNode`, phasor-through-threshold, or `ConstantNode` the user
   wires up, with no special-casing. Never add a side-channel event queue to
   `NodeContext` or `Patch` for this: it would put allocation (or a
   preallocated ring) in the realtime path and split signal data into two
   kinds only some nodes understand. Keeping triggers as plain samples is
   what lets convention 2 hold unchanged.
9. **Per-session state keys off `NodeContext`, never off node lifetime.**
   `Node` instances are deliberately long-lived and shared across every
   `Patch` built from a graph (see the rebuild note in `patch.md`), while
   `AudioEngine` creates a **fresh `NodeContext` per playback loop and per
   `renderToWav`**. So anything meaning "once per sound / once per render"
   must be driven by `context.sampleIndex()`, not by a flag on the node.
   `TriggerNode` detects a new session by comparing `context.sampleIndex()`
   against where its previous block ended - not against zero, so consecutive
   single-block renders still read as two sessions. Getting this wrong is what
   made Render-to-WAV write silence after a Play: the node had already spent
   its one-shot flag. Do not add a manual re-arm/fire API to paper over it.
10. **A `Macro` may only target a node that is really in the document.**
    `Gui.NodeView`'s shadow `ConstantNode`s are a GUI implementation detail
    with no `NodeView`, so they never reach `GraphEditor.toDocument()`'s node
    list. Binding a macro to one produced a macro that was dropped on save and
    drove nothing - promoting a port knob therefore **materializes** the shadow
    into a real `Constant` node first (`GraphEditor.materializeKnob`).
    `PatchCodec.save` throws on an unreachable macro target rather than
    skipping it; keep it loud, since the silent `continue` is what hid this.
    The other half of the invariant is deletion: `GraphEditor.removeNode` must
    drop every macro bound to a knob that node owned (`NodeView.ownedKnobs()`),
    or the next save fails on a macro whose target is gone.

## Documentation

Documentation is split by audience.

`docs/internals/` holds the design and implementation docs, one per package
(`oscillators.md`, `expression.md`, `graph.md`, `dsp.md`, `filters.md`,
`effects.md`, `granular.md`, `instruments.md`, `physical.md`, `spectral.md`,
`mixers.md`, `conditionals.md`, `special.md`, `tracker.md`, `sources.md`, `envelopes.md`,
`sinks.md`, `utility.md`, `modules.md`, `patch.md`, `serialization.md`,
`diesel.md`, `generators.md`, `audio.md`, `gui.md`, `collaboration.md`), indexed by
`docs/internals/README.md`, plus `conventions.md` - the governing conventions
below, written up for contributors so the public docs never have to cite this
file. Since source carries no comments, this is the only place design
rationale and algorithm details (e.g. the RBJ biquad cookbook formulas, the
Schroeder/Freeverb reverb topology, the expression grammar) are written down -
keep it in sync with the code.

`documentation/` holds the end-user guides (`getting-started.md`, `editor.md`,
`nodes.md`, `sequencing.md`, `samples.md`, `exporting.md`), indexed by
`documentation/README.md`. These describe the shipped application, not the
code, and much of their material is sourced from `Gui.Docs` - the same
articles the editor shows from a node's "?" button - so when a node's
behaviour or its in-app article changes, check whether a guide names it too.

`README.md` at the repo root is user-facing and links to all three of
`documentation/`, `DIESEL.md` and `docs/internals/`. `DIESEL.md` is the
user-facing language guide, with a node reference table generated from
`NodeFactory`.
