# Petroleum internals

Design and implementation documentation for the code under
`src/main/java/Petroleum`, written for people working on the engine itself.
There is one document per package, plus the conventions that govern all of
them.

For using Petroleum rather than modifying it, see the
[user documentation](../../documentation/README.md).

- [Engineering conventions](conventions.md) - the rules the engine is built on:
  the no-allocation processing path, coefficient caching, ring-buffer indexing,
  triggers as plain signals, and per-session state

## Core abstractions

- [Graph](graph.md) - the realtime-safe node graph abstraction: `Node`, `Port`,
  `NodeContext`, `NodeFactory`, and the `OneShot`/`Stateful` markers
- [Patch](patch.md) - the wiring and execution engine: topological sort,
  validation, and allocation-free block rendering
- [Dsp](dsp.md) - pure-DSP helpers shared across the node packages: delay
  lines, LFO phase, envelope following, one-pole coefficients, edge detection,
  grain pooling, FFT

## Sound generation

- [Oscillators](oscillators.md) - waveform and noise generators, plus the
  `OscillatorNode` graph bridge
- [Expression](expression.md) - the math-expression parser and evaluator behind
  user-defined waveforms
- [Sources](sources.md) - signal-originating nodes: `ConstantNode`, `PhasorNode`
- [Granular](granular.md) - grain-based sample playback, live freeze, granular
  delay and cloud textures
- [Instruments](instruments.md) - multisample instruments with keyed sample
  zones, a breakpoint volume envelope, and Hz-based microtonal pitch
- [Physical](physical.md) - physically modelled excitable string and modal
  resonators
- [Spectral](spectral.md) - frequency-domain infrastructure and the nodes built
  on it: spectral freeze, spectral gate, phase-vocoder pitch shift

## Sound shaping

- [Filters](filters.md) - filter nodes
- [Effects](effects.md) - effect nodes
- [Envelopes](envelopes.md) - one-shot shaping over a sound event:
  `TriggerNode`, `EnvelopeNode`, `AdsrNode`, `RampNode`
- [Mixers](mixers.md) - mixer nodes
- [Conditionals](conditionals.md) - conditional and trigger-driven state nodes
- [Utility](utility.md) - generic signal-math nodes: splitter, amplifier, map
  range, clamp, quantizer, slew
- [Sinks](sinks.md) - patch-terminating nodes: `AudioOutNode`

## Composition and reuse

- [Tracker](tracker.md) - step sequencer with per-note pitch and timing
  effects, polyphonic piano roll, and MIDI import, export and playback
- [Modules](modules.md) - a saved patch used as a node
- [Generators](generators.md) - seeded category generators that emit a patch
  document

## Storage and output

- [Serialization](serialization.md) - saving and loading patches as a zip
  archive of a JSON document plus lossless sample data, plus macro bindings
- [Diesel](diesel.md) - the implementation of the `.dsl` text format and the
  `petroleum render` command line
- [Audio](audio.md) - cross-platform realtime playback and offline WAV
  rendering
- [Gui](gui.md) - the JavaFX visual node-graph editor

## Package layout

Every package listed above is a direct sibling under `Petroleum`. The grouping
in this index is editorial and does not imply nesting in the source tree.

`Graph` holds only the node abstraction itself; every concrete node type, the
execution engine, and the audio and GUI layers live in their own packages.

`Dsp` is not a node package. Its classes have no `Port`s and no `process()`
method - they are plain helper objects that the node implementations hold as
fields.

## Building and verifying

`mvn compile` builds the project; `mvn javafx:run` launches the editor and
needs a real display. The first build after a fresh `~/.m2` cache must run
online so the per-platform JavaFX artifacts download, after which `mvn -o
compile` works offline. `mvn -o package` does not work, because the jar and
surefire plugins have transitive dependencies that are not in a fresh cache.

No test framework and no linter are configured. Changes are verified by
compiling a throwaway class against `target/classes` and running it directly:

```
javac -cp target/classes -d /path/to/scratch YourSmoke.java
java -cp target/classes:/path/to/scratch YourSmoke
```

`AudioEngine.renderToWav` needs neither a display nor an audio device, which
makes it the usual way to exercise the engine end to end. Playback and the
editor need a real audio device and display respectively.
