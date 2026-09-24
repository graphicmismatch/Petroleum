# Utility (`Petroleum.Utility`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Oscillators`, not nested under `Graph`. Mostly generic signal-math
nodes that don't belong in `Filters`/`Effects`/`Mixers`/`Conditionals` - none
of them shape a waveform, combine multiple signals, or branch on a
condition, they just transform one value - plus `AnnotationNode`, which
doesn't touch a signal at all and lives here for the same reason: it has no
other family to belong to.

- **SplitterNode** - one `in` input, N outputs (`out0`..`out(N-1)`), each a
  copy of `in`. Purely a visual fan-out convenience: a single output can
  already feed arbitrarily many destination inputs via separate
  `Connection`s without this node existing at all, so `SplitterNode` exists
  only to make that fan-out an explicit, visible graph node. Constructor
  takes the output count; the no-arg constructor defaults to 4 (same
  fixed-arity convention `Mixers.MixNode` and `Conditionals.MultiplexerNode` use),
  registered in `NodeFactory` under three preset sizes (`Splitter`/`Splitter
  2`/`Splitter 8`); `Gui.NodeCatalog`'s "Splitter (Custom Size)..." entry
  prompts for any other output count and constructs one directly, unregistered
  (same treatment as `AdditiveResynthNode`'s fidelity prompt). Any size is
  saved/loaded losslessly regardless of how it was created -
  `Serialization.PatchCodec` stores the actual output count, not the preset
  name. Unused outputs are simply left
  unwired. `process()` copies the block with `System.arraycopy` per output
  (`inputs`/`outputs` are `float[port][sample]`, `frameCount` samples
  valid) rather than a per-sample loop, since inputs must never be written
  and a whole-block copy is both simpler and faster than looping.
- **AmplifierNode** - `in` times `gain` (default `1.0`). `gain` can be
  greater than 1 to boost, between 0 and 1 to attenuate, or negative to
  invert. Registered as `Amplifier`.
- **AttenuverterNode** - functionally identical to `AmplifierNode` (`in`
  times `gain`, default `1.0`, negative inverts) - a deliberate near-duplicate
  class rather than a second `NodeFactory` registration for the same class,
  so the node menu shows an entry under the name a physical modular rack
  would use for this exact job. Registered as `Attenuverter`.
- **InvertNode** - one `in` input, output `-in`. The single-purpose case of
  `AmplifierNode`/`AttenuverterNode` with `gain` fixed at `-1` and no port
  to set, for a dedicated rack-style inverter utility that needs no
  configuration. Registered as `Invert`.
- **MapRangeNode** - linearly remaps `in` from `[inMin, inMax]` (default
  `[0, 1]`) to `[outMin, outMax]` (default `[0, 1]`). Guards `inMax ==
  inMin` by setting that sample's output to `outMin` instead of computing
  `0/0` - a stray `NaN` feeding a downstream IIR filter's persistent state
  (`BiquadFilterNode`, `CombFilterNode`, etc.) would latch permanently, with
  no way for the filter to self-recover. Registered as `Map Range`.
- **ClampNode** - clamps `in` to `[min, max]` (default `[-1, 1]`), reusing
  `AbstractNode`'s inherited `clamp(value, min, max)` helper as-is. If
  `min > max` at runtime, that helper's `Math.max(min, Math.min(max,
  value))` deterministically collapses to `min` - no crash, just a
  degenerate but well-defined range. Registered as `Clamp`.
- **RoundNode** / **CeilNode** / **FloorNode** - one `in` input each, rounded
  via `Math.round`/`Math.ceil`/`Math.floor` respectively. The standard way to
  turn a continuous control signal (a swept `Map Range`, a `Random` draw)
  into a whole-number step, e.g. a `Multiplexer`'s `select` index or a
  `Counter`'s `wrap`. Registered as `Round`, `Ceil`, `Floor`.
- **SlewNode** - rate-limits how fast a signal may change. Ports: `in`,
  `rise`, `fall` (seconds to travel one unit, default `0.05` each). Rising and
  falling are limited separately, so a fast attack with a slow release is one
  node rather than two.

  This is portamento/glide for control signals: a stepped
  [`SequencerNode`](conditionals.md) output through a `Slew` becomes a glide
  between notes, and a jumpy macro through one stops a knob drag from clicking.
  A `rise`/`fall` of `0` passes that direction through untouched, so the node
  is a no-op rather than a freeze when either is left at zero.

  The first sample **jumps** to the input rather than ramping up from zero -
  otherwise every patch would start with an audible sweep from silence that no
  one asked for. Per convention 3, the seconds-to-per-sample-step conversion is
  cached and recomputed only when `rise`, `fall`, or the sample rate changes.

  Distinct from [`Filters.OnePoleFilterNode`](filters.md), which is exponential
  smoothing: a one-pole approaches its target asymptotically and never quite
  arrives, while `Slew` moves at a constant rate and lands exactly. For a glide
  with a defined duration, that difference is the whole point.
- **MultiplierNode** - `a` times `b`, both defaulting to `0`. The one node in
  this package that combines two signals rather than transforming one, and the
  only ring-modulation-style multiply that isn't tucked inside an effect (see
  `Effects.RingModulatorNode`, which multiplies by an internal carrier LFO
  rather than a second patchable input) - wiring one signal into `a` and
  another into `b` is amplitude modulation with the modulator fully exposed.
  Registered as `Multiplier`. There is no `Petroleum.Math` package: a single
  two-input node didn't justify a sibling package of its own, so it lives here
  alongside the other generic signal-math nodes.
- **DifferenceNode** - `a` minus `b`, both defaulting to `0`. Same
  two-signal-in shape as `MultiplierNode`, registered as `Difference` and
  grouped alongside it in the "Signal Math" catalog submenu, but subtractive
  rather than multiplicative - the natural node for canceling one signal out
  of another (a slow drift, a DC offset, a second copy of the same waveform)
  rather than modulating it.
- **PitchToTimeNode** / **TimeToPitchNode** - a reciprocal pair: `out = 1 /
  pitch` (default `440`) and `out = 1 / time` (default `1`). Every other
  "frequency" port in the codebase (`Sources.PhasorNode.frequency`,
  `Envelopes.ArpeggioNode.frequency`, every oscillator) already speaks Hz, and
  every "time"/"delay" port (`Effects.DelayNode.time`,
  `Envelopes.RampNode.time`, `Utility.SlewNode.rise`/`fall`) already speaks
  seconds - these two nodes are the bridge between those two families rather
  than a third unit system of their own. `PitchToTimeNode` exists specifically
  for tuning a delay line to a musical pitch: a comb-filtered or
  feedback-delay effect only reads as a specific note when its `time` is set
  to that note's *period*, not its frequency, and `frequency = 1/time` is not
  a conversion most patches should have to spell out inline every time.
  `TimeToPitchNode` is the same relationship read the other way - turning a
  period (a clock interval, an LFO's cycle time) into the frequency an
  oscillator or Phasor can track directly.

  Both guard against a non-positive input the same way: `Math.max(epsilon,
  input)` before dividing, with `epsilon = 1e-6f`, so a `pitch` or `time` of
  zero or below produces a large-but-finite output rather than `Infinity` or
  `NaN` - the same reasoning `MapRangeNode`'s `inMax == inMin` guard documents
  above, since a stray `NaN` reaching a delay line's write index or a
  filter's persistent state would corrupt it permanently rather than
  self-recovering. Neither node caches per convention 3: a single division is
  not the `trig`/`pow`/`exp` class of cost that caching exists for, so both
  recompute every sample exactly like `AmplifierNode`/`ClampNode` do,
  unconditionally.
- **QuantizerNode** - snaps a pitch to the nearest note of a scale. Ports:
  `in` (default `440`, Hz - the pitch to quantize) and `root` (default
  `261.6256`, Hz - middle C, the scale's tonic). Both are Hz, matching every
  other pitch-carrying port in the codebase (`Sources.PhasorNode.frequency`,
  `Sources.NoteNode`'s output, `Tracker.TrackerNode.pitchN`,
  `PitchToTimeNode`/`TimeToPitchNode` above) - there is no "raw semitone"
  port type anywhere else to interoperate with, since the semitone/MIDI-note
  domain (`(octave+1)*12 + semitone`) only ever exists transiently inside
  `Sources.NoteName.parse` and `Tracker.TrackerNode`'s internal pattern data,
  never crossing a `Port` boundary. Internally, both `in` and `root` convert
  to a continuous semitone via `69 + 12*log2(hz/440)` (the inverse of the
  `440 * 2^((pitch-69)/12)` formula `NoteNode`/`TrackerNode` already use),
  `root` reduces to a pitch class (`floorMod(round(rootSemitone), 12)` - only
  which of the 12 note names it is matters, not the octave), the nearest
  scale degree is found by generating candidates across three octaves around
  `in` (enough to guarantee correctness near an octave boundary) and picking
  the minimum-distance one, then the result converts back to Hz. Recomputed
  only when `in` or `root` actually changed since the last sample (convention
  3 - `log`/`pow` are the expensive-enough class of operation caching exists
  for, same as `BiquadFilterNode`/`SlewNode`).

  The `Scale` enum and the quantize/Hz-semitone math actually live in
  `Dsp.PitchQuantizer`, not on `QuantizerNode` itself - `QuantizerNode` just
  calls `PitchQuantizer.quantize(scale, in, root)`. They moved there so
  `Spectral.PitchCorrectionNode` (see `spectral.md`) could reuse the same
  scale-snapping logic without a `Node`-to-`Node` import across packages,
  which nothing else in the codebase does (see `dsp.md` for the `Dsp`-side
  writeup).

  Follows governing convention 6 (a factory bakes in a fixed argument for a
  type that needs one, same as `Filters.BiquadFilterNode.FilterType` or
  `Effects.DistortionNode.DistortionType`): one class, a `Scale` enum with
  five variants (`CHROMATIC`, `MAJOR`, `MINOR`, `MAJOR_PENTATONIC`,
  `MINOR_PENTATONIC`), each its own semitone-interval set and its own
  `NodeFactory` registration - `Quantizer Chromatic`, `Quantizer Major`,
  `Quantizer Minor`, `Quantizer Major Pentatonic`, `Quantizer Minor
  Pentatonic`. `getName()` returns `"Quantizer " + scale.label()`, matching
  `BiquadFilterNode`'s `"Biquad " + type.label()` precedent so the registry
  key and the display name always agree.
- **AnnotationNode** - no inputs, no outputs, empty `process()` (the same
  shape `Sinks.AudioOutNode` and `Modules.MutedNode` use for a node that
  produces nothing). Holds one string, edited in `Gui.NodeView` as a resizable
  multi-line text box directly on the node body rather than through any port
  or knob; `Stateful` carries that string through save/load the same way
  `Sources.NoteNode` carries its note name. Exists purely so a patch can
  document itself inline - see [Modules](modules.md) for how its text
  surfaces once the patch containing it is used as a subpatch node rather
  than opened directly. Registered as `Annotation`; deliberately not called
  "Note" since `Sources.NoteNode` already claims that name for an unrelated
  pitch-to-frequency node.
