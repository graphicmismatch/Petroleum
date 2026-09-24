# Conditionals (`Petroleum.Conditionals`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Oscillators`, not nested under `Graph`.

- **SelectNode** - a patchable if/else: passes `ifTrue` through while
  `condition` is above `threshold` (default 0.5), `ifFalse` otherwise. Ports:
  `condition`, `ifTrue`, `ifFalse`, `threshold`.
- **GateNode** - passes `in` through unchanged when `control` is above
  `threshold`, otherwise outputs silence. Distinct from
  `Effects.NoiseGateNode`, which gates a signal based on its own level
  rather than a separate control input. Ports: `in`, `control`, `threshold`.
  Registered as `CV Gate`, not the bare `Gate` the class name suggests - the
  catalog already has `Effects.NoiseGateNode` ("Noise Gate") and
  `Filters.LowPassGateNode` ("Low-Pass Gate"); a bare "Gate" label sitting
  next to those two is the one most likely to be picked by mistake, since
  this node's own job (a level-vs-threshold switch) is the least
  self-explanatory of the three from its name alone.
- **MultiplexerNode** - an N-way select: picks `in0`..`in(N-1)` based on the
  (rounded, clamped) `select` index. Constructor takes `numInputs`; registered
  in `NodeFactory` under three preset sizes (`Multiplexer`/`Multiplexer
  2`/`Multiplexer 8`, arities 4/2/8) via lambdas, the same default-argument
  treatment `Mixers.MixNode` gets for its weights map. `Gui.NodeCatalog`'s
  "Multiplexer (Custom Size)..." entry prompts for any other input count and
  constructs one directly, unregistered - same treatment as
  [`SplitterNode`](utility.md)'s custom-size entry. Any size
  round-trips through `Serialization.PatchCodec` regardless of preset, since
  it saves the actual input count.
- **DemultiplexerNode** - the mirror of `MultiplexerNode`: routes `in` to
  whichever of `out0`..`out(N-1)` the (rounded, clamped) `select` index picks,
  the rest getting silence. Constructor takes `numOutputs`; registered in
  `NodeFactory` under three preset sizes (`Demultiplexer`/`Demultiplexer
  2`/`Demultiplexer 8`, arities 4/2/8) and given the same
  `Gui.NodeCatalog` "Demultiplexer (Custom Size)..." treatment as
  `MultiplexerNode`.
- **ToggleNode** - an edge-triggered flip-flop: flips its output between `0`
  and `1` each time `trigger` crosses above `threshold` from below. Ports:
  `trigger`, `threshold`.
- **LatchNode** - a sample-and-hold driven by an explicit trigger. Ports: `in`,
  `trigger`. On each **rising edge** of `trigger` (per governing convention 8,
  detected by a [`Dsp.EdgeDetector`](dsp.md)) it captures whatever `in` reads
  at that sample and holds it until the next edge; before the first edge it
  outputs `0`. Edge detection is what makes it a latch rather than a gate: a
  trigger that stays high for a whole block captures once, at the transition,
  not every sample.

  This is the missing partner to [`Oscillators.SampleAndHold`](oscillators.md),
  which resamples on a *phase* schedule of its own. A `LatchNode` samples when
  something else says so, so the same [`TriggerNode`](envelopes.md) that fires
  an envelope can also freeze a pitch, a filter cutoff, or a
  [`RandomNode`](sources.md) draw at exactly the same instant.
- **QueueQuantizerNode** - a FIFO quantizer for live playing (e.g.
  [`MidiInputNode`](sources.md)'s pitch/gate). Ports: `in`, `gate`,
  `trigger`; outputs `out`, `pulse`. A value is **pushed** when `in` differs
  from the previous sample *or* `gate` has a rising edge - the edge is what
  lets a repeated same-pitch note queue again, and a note-on that changes
  pitch and raises gate in the same sample pushes once, not twice. Each rising
  edge of `trigger` **pops** the oldest value into `out` (held until the next
  pop) and sets `pulse` to `1` for that one sample; popping an empty queue
  changes nothing. Push is evaluated before pop in the same sample, so a note
  landing exactly on a clock edge with nothing waiting goes straight out.

  The queue is a fixed 64-slot ring buffer (`head` + `size`, wraparound with
  `if (++i >= CAPACITY)`, per conventions 2 and 4); pushing into a full queue
  drops the oldest entry, since for live playing the most recent notes matter
  most. Why a change-driven push rather than every sample: popping one entry
  per clock against 44,100 pushes a second could never drain. The trade-off
  is that a continuously moving `in` (pitch bend, an LFO) floods the queue -
  that's inherent to "value changed" and documented for users.

  Per convention 9, a block whose `context.sampleIndex()` isn't where the last
  one ended starts a new session: the queue empties, `out` returns to `0`, and
  the first sample's `in`/`gate` become the "previous" values *without*
  pushing - otherwise MIDI Input's idle pitch of `0` would be queued at the
  start of every playback and come out as a spurious note on the first clock.
  `pulse` is a one-sample trigger (convention 8), so it pairs with the
  one-shot `EnvelopeNode`; an `AdsrNode` gated by it releases immediately.
- **CounterNode** - counts trigger edges. Ports: `trigger`, `wrap`, `reset`;
  outputs the running count as a float. A `wrap` above zero returns the count
  to `0` on reaching it, so `wrap = 8` cycles `0..7`; `wrap = 0` (the default)
  counts up without bound. A rising edge on `reset` returns to `0` immediately,
  and is applied before the trigger in the same sample so a simultaneous
  reset-and-tick lands on `0` rather than `1`. `trigger` and `reset` are
  independent edges, so the node holds two separate `Dsp.EdgeDetector`
  instances rather than one.

  Feed the output into [`MultiplexerNode`](conditionals.md)'s `select` or
  [`Utility.MapRangeNode`](utility.md) to turn a stream of pulses into a
  stepped control signal.
- **ClockDividerNode** - re-times an incoming clock, dividing and/or
  multiplying its rate. Ports: `clock`, `divide` (default `1`), `multiply`
  (default `1`). Outputs a re-timed pulse train.

  On each **rising edge** of `clock` (per governing convention 8, via a
  `Dsp.EdgeDetector`) it counts the edge and measures the sample distance
  from the previous one. `divide` keeps only every Nth edge (`round`ed,
  floored at `1`); when a kept edge lands, `multiply` (also `round`ed,
  floored at `1`) schedules that many evenly-spaced pulses across the
  interval the *previous* raw clock period predicts the next kept interval
  will span - the same absolute-sample scheduling style
  [`TriggerNode`](envelopes.md) uses (`context.sampleIndex()`-based, a
  `NEVER = Long.MAX_VALUE` sentinel, no allocation), reused here because
  this node also stores absolute sample numbers (`nextPulse`,
  `lastEdgeSample`) that go stale across a `NodeContext` reset. Per
  governing convention 9, it therefore also tracks `expectedBlockStart` and
  resets that scheduling state (not the `EdgeDetector`, which no other
  `Conditionals` node resets across sessions either) whenever
  `context.sampleIndex()` disagrees with where the previous block ended -
  without it, a node instance reused across a second Play/Render session
  would carry a stale `nextPulse` from the first session's (longer) sample
  range and never fire again, the exact class of bug `TriggerNode`'s own
  session-detection fixed.

  Multiplication is a prediction, not a measurement: the very first kept
  edge after the patch starts, or right after `clock`'s rate changes
  abruptly, has no prior period to subdivide, so it fires once with no
  subdivision - the same limitation every hardware clock multiplier has.
  At `divide = 1, multiply = 1` the node reduces to reproducing `clock`'s
  own edges as single-sample pulses.
- **SequencerNode** - steps through its own inputs. Ports: `trigger`, `reset`,
  then `step0`..`step(N-1)`; the output is whichever step port is current.
  Constructor takes the step count; `NodeFactory` registers three preset
  sizes (`Sequencer`/`Sequencer 4`/`Sequencer 16`, steps 8/4/16), the same
  default-argument treatment `Multiplexer` and `Mix` get. `Gui.NodeCatalog`'s
  "Sequencer (Custom Size)..." entry prompts for any other step count and
  constructs one directly, unregistered - same treatment as
  [`SplitterNode`](utility.md)'s custom-size entry; any size
  round-trips through `Serialization.PatchCodec` regardless of preset. Each
  rising edge on `trigger` advances one step and wraps at the
  end; `reset` returns to `step0`.

  Because every step is an ordinary input port, a step can be a constant, a
  macro, or another node's output entirely. Driving it from a
  [`TriggerNode`](envelopes.md) with a non-zero `interval` and feeding the
  output into `Phasor.frequency` is an arpeggio; feeding it into a filter
  cutoff is a stepped sweep.

  Step count is a construction-time parameter because it fixes the port list,
  so [`Serialization`](serialization.md) saves it explicitly and rebuilds the
  node from it rather than restoring it as state - the same treatment
  `Splitter`, `Multiplexer` and `Mix` get.
- **RunglerNode** - a Rob Hordijk/Benjolin-style 8-bit shift-register chaotic
  CV source. Everything else in `Conditionals` is deterministic/user-programmed
  (a `Counter` counts, a `Sequencer` steps through inputs someone wired); the
  Rungler is the package's one genuinely chaotic state machine - feedback
  through a shift register produces an emergent, non-repeating (within any
  practical timeframe) sequence rather than a value anyone chose. Ports:
  `clock`, `data`, `rate` (Hz, default 4). Outputs: `stepped`, `bit`.

  Internally it holds a single `int` used as an 8-bit shift register (masked
  to `0xFF` after every write). On each **rising edge** of `clock` (per
  governing convention 8, via a `Dsp.EdgeDetector`) the register shifts left
  by one and ORs in a new LSB: `1` if `data` is currently positive, `0`
  otherwise. `bit` outputs that LSB continuously as `0`/`1` - a chaotic gate
  or trigger source in its own right. `stepped` reads the register's top 3
  bits as a tiny weighted DAC and maps them to a bipolar voltage:
  `((topBits / 7.0) * 2) - 1`, so it always lands on one of exactly 8 evenly
  spaced steps between -1 and 1, and which step it lands on depends on the
  last several clock/data samples - the "havoc" CV the real Benjolin circuit
  is known for.

  `clock` and `data` both use the same trick to stay useful when left
  unpatched: each carries its own free-running `Dsp.LfoPhase`-driven square
  wave internally (`clock`'s at `rate` Hz, `data`'s at `rate * 1.37` Hz - a
  fixed, deliberately non-integer ratio so the two never lock into a simple
  repeating relationship), and the node simply **adds** that internal
  fallback to the port's actual value before thresholding at zero. An
  unpatched port sits at its `Port` default of `0`, so the sum is just the
  internal oscillator and the node self-clocks/self-shifts on its own; a
  patched signal rides on top of (and, at typical CV amplitudes, dominates)
  the fallback, so patching either input hands control to whatever's wired
  in without needing a separate "is this connected" flag anywhere - the node
  never needs to know, it just sums and thresholds either way.

  The canonical way to use a Rungler is the Benjolin self-patch: run two
  oscillators, clock the Rungler from one and feed the other's output into
  `data`, then feed `stepped` back into both oscillators' pitch (through a
  `Utility.MapRangeNode` or similar to scale it to a useful range first).
  Each oscillator perturbs its own and the other's pitch by way of the
  Rungler's memory of recent clock/data samples, so the whole loop settles
  into anything from a near-stable drone to full chaos depending on how far
  apart the two oscillators' frequencies sit - patch it, don't just read
  about it.
