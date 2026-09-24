# Graph (`Petroleum.Graph`)

A realtime-safe signal graph abstraction. A `Node` has a fixed set of input
and output ports; what it does with the former to produce the latter is
entirely up to the implementation. This package holds only the abstraction
itself - `Node`, `AbstractNode`, `NodeContext`, `Port`, `OneShot`, and the
`NodeFactory` registry. Concrete node types (oscillator bridge, filters,
effects, mixers, conditionals, sources, envelopes, sinks, generic signal-math
utilities) live in their own sibling packages, the same way
`Petroleum.Oscillators` is a sibling of this package - see
[Filters](filters.md), [Effects](effects.md),
[Mixers](mixers.md), [Conditionals](conditionals.md),
[Sources](sources.md), [Envelopes](envelopes.md), [Sinks](sinks.md),
[Utility](utility.md), and `OscillatorNode` in [Oscillators](oscillators.md).

## `OneShot`

```java
public interface OneShot {
    boolean isIdle();
}
```

An optional marker a node implements when it has a defined end - it runs when
triggered and eventually goes quiet. [`EnvelopeNode` and
`RampNode`](envelopes.md) implement it.

It lives here rather than in `Envelopes` so that [`Patch`](patch.md) can
implement `isFinished()` against an abstraction instead of importing a
node-implementation package, which would invert the dependency direction the
rest of the layout maintains. Nothing in the block-processing path touches it:
`Patch` collects the implementors once, at construction.

## `Stateful`

```java
public interface Stateful {
    void saveState(Map<String, String> state);
    void loadState(Map<String, String> state);
}
```

The other optional marker: a node implements it when it holds mutable state
that outlives construction and must survive a save/load cycle.
[`ConstantNode`](sources.md) implements it for its `value`;
[`Serialization.PatchCodec`](serialization.md) applies it generically, so a new
`Stateful` node needs no codec change.

Same placement reasoning as `OneShot` - the interface is an abstraction, so it
belongs here, while the code that uses it lives in `Serialization`.

It is deliberately **not** the mechanism for parameters fixed at construction
(`SplitterNode`'s output count, `MixNode`'s port set, `BiquadFilterNode`'s
type). `AbstractNode` copies its port lists once in the constructor, so no
amount of post-construction state can change a node's shape - the codec
reconstructs those types directly instead.

## `NodeFactory.nameOf(Node)`

The reverse of `create(String)`: given a node, the registered name that would
build one. Used by `Serialization` to write a stable type key. The contract is
that **a factory-registered node's `getName()` returns exactly its registry
key** - `nameOf` is therefore just `registry.containsKey(node.getName()) ?
node.getName() : null`, an O(1) lookup that never instantiates anything.

This is why `BiquadFilterNode`, registered under eight names (one per
`FilterType`), carries a `label()` on the enum and builds `getName()` as
`"Biquad " + type.label()` to match its key exactly - the same pattern any
other multi-registration type (see `MixNode`'s default weights,
`MultiplexerNode`/`SplitterNode`'s default arity) must follow if it ever needs
more than one registry entry.

## Realtime design

The graph processes audio in blocks, not one sample at a time. A node's
`process` is called once per block, not once per sample, and is
allocation-free by construction:

```java
void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context);
```

- `inputs`/`outputs` are arrays-of-arrays indexed first by port position
  (index `i` corresponds to `getInputPorts().get(i)` /
  `getOutputPorts().get(i)`), then by sample index within the block:
  `inputs[portIndex][sampleIndex]`, `outputs[portIndex][sampleIndex]` - no
  boxing, no map lookups, no allocation inside `process`.
- The per-port arrays are **capacity-sized**, not `frameCount`-sized: a
  `Patch` allocates every block once, at construction, sized to its
  `blockCapacity` (see [Patch](patch.md)), and reuses those same arrays for
  every `renderBlock` call regardless of how many frames are actually
  requested. Only the `[0, frameCount)` prefix of each array holds valid
  data for the current block - a node must never read or write past
  `frameCount`, and must never assume the array's `.length` equals the
  number of frames being processed.
- **A node must never write into its `inputs` arrays.** An input port's
  backing array may be aliased directly to the producing node's output
  array (see [Patch](patch.md)'s zero-copy wiring) - mutating it would
  corrupt the producer's own output and any other consumer sharing that
  same array. Nodes only ever read `inputs` and write `outputs`.
- A graph executor is expected to resolve port names to indices **once**,
  when a patch is wired up, via `Node.inputIndexOf(name)` /
  `Node.outputIndexOf(name)` - never inside `process`.
- Unconnected input ports must already be filled with that port's
  `Port.defaultValue()` (across the full capacity, not just `frameCount`) by
  whoever built the `inputs` array; `process()` never sees "missing" data,
  only values.
- `NodeContext` (sample rate + sample index) is mutable and meant to be
  reused: create one instance for an entire run and call `advance(frames)`
  once per block (not once per sample) rather than allocating a fresh
  context per block.
- Nodes whose coefficients are expensive to derive (trig, `pow`, `exp`) cache
  them and only recompute when the relevant parameters (or the sample rate)
  actually change - see `BiquadFilterNode`, `OnePoleFilterNode`,
  `CompressorNode`, and `BitcrusherNode`. A static knob position is the
  common case; there's no reason to pay for transcendental math every sample
  when nothing has moved.
- Circular-buffer indexing (delay lines, comb/allpass filters) uses
  `if (++index >= length) index = 0;` instead of `index = (index + 1) %
  length` - integer division is one of the more expensive ALU ops, and the
  branch is cheap and highly predictable in a ring buffer.

## Core types

- **Node** - the interface described above (`getInputPorts()`,
  `getOutputPorts()`, `process(...)`, `getName()`). `Node.OUT = 0` is the
  conventional array index for a node's sole output, for single-output nodes.
  It also carries two `default` methods for name -> index resolution:
  `inputIndexOf(name)` scans `getInputPorts()` for a `Port` whose `name()`
  matches, and `outputIndexOf(name)` scans `getOutputPorts()` (a
  `List<String>`, not `Port`s - output ports carry no metadata) for an equal
  `String`. Both throw `IllegalArgumentException` (naming the node and the
  requested port) if nothing matches. Living on the interface rather than
  `AbstractNode` means any `Node` implementation gets symmetric,
  construction-time-only port resolution for free, without having to extend
  `AbstractNode` to get it.
- **Port** - metadata for an input port: `name()` (for wiring/UI) and
  `defaultValue()` (used when nothing is connected). Output ports don't need
  this since a node always produces its own outputs.
- **NodeContext** - per-tick info a node may need but that isn't part of the
  signal graph itself: `sampleRate` and the current `sampleIndex()`
  (`timeSeconds()` derives elapsed time from the two).
- **AbstractNode** - base class handling port-list bookkeeping for the (near
  universal) case of a node whose ports are fixed at construction. Also
  provides `clamp(value, min, max)`. It used to carry its own `inputIndexOf`
  implementation; that's now the `Node` interface default described above,
  so `AbstractNode` no longer needs to override it.
- **NodeFactory** - a name -> `Supplier<Node>` registry (`register` /
  `create`/`names()`), the graph-level counterpart to `OscillatorFactory`.
  Every entry is a fully self-contained `Supplier<Node>`: most wrap a bare
  no-arg constructor, but some bake in a fixed default argument for a type
  that technically needs one - `BiquadFilterNode`'s filter type, `MixNode`'s
  default 4-input equal-weight map, `MultiplexerNode`'s default 4-way arity. Only
  `OscillatorNode` (needs an arbitrary `IOscillator`) and the two mutable-base
  oscillator wrappers exposed through the [Gui](gui.md) layer stay outside
  any factory entirely, since they need a value with no sane universal
  default. The backing map is a `LinkedHashMap` so `names()` (used by
  [Gui.NodeCatalog](gui.md) to build the node-creation menu) returns a
  stable, deliberately-ordered list rather than shuffling every run.

  Registered names: `Constant`, `Phasor`, `Audio Out`, `Select`,
  `CV Gate`, `Toggle`, `Multiplexer`, `One-Pole Filter`,
  `One-Pole Highpass`, `Biquad Lowpass`, `Biquad Highpass`,
  `Biquad Bandpass`, `Biquad Notch`, `Biquad Allpass`, `Biquad Peaking`,
  `Biquad Low Shelf`, `Biquad High Shelf`, `State Variable Filter`,
  `Ladder Filter`, `Comb Filter`, `Delay`, `Chorus`, `Flanger`, `Tremolo`,
  `Distortion`, `Bitcrusher`, `Ring Modulator`, `Compressor`, `Reverb`,
  `Phaser`, `Limiter`, `Noise Gate`, `Vibrato`, `Mix`, `Splitter`,
  `Amplifier`, `Map Range`, `Clamp`, `Round`, `Ceil`, `Floor`.
