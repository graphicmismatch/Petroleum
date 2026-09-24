# Envelopes (`Petroleum.Envelopes`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Sources`/`Petroleum.Oscillators`, not nested under `Graph`. Nodes here
shape a signal over the life of one sound event rather than over one cycle.

Every node in [Sources](sources.md) is cyclic: phasors run forever, and a
patch built from them drones until playback stops. These nodes add the missing
idea of a sound that starts, peaks, and ends - the shape every sfxr-style
one-shot effect needs.

## Triggers are ordinary signals

A trigger is `1.0` for exactly one sample and `0.0` otherwise. A node that
consumes one detects a **rising edge** via a constructor-allocated
[`Dsp.EdgeDetector`](dsp.md) (`edge(trigger) > 0`), which implements exactly
`previous <= 0 && trigger > 0` and updates its own `previous` state - the
per-node boilerplate this used to require (a raw `previousTrigger` field plus
the inline comparison) is now one shared, allocation-free helper.

Nothing in `Node`, `Patch`, or `NodeContext` knows what a trigger is. This is
deliberate, and is recorded as [convention 8](conventions.md):

- The hot path is untouched. `process()` keeps its signature and its
  no-allocation guarantee (convention 2), because a trigger is just another
  `float` in the same `inputs[port][sample]` arrays.
- Triggers compose for free. A [`SelectNode`](conditionals.md) output, a
  phasor through a threshold, or a hand-wired [`ConstantNode`](sources.md) all
  work as trigger sources with no special-casing.
- Rising-edge detection means a trigger input held high for a whole block
  fires once, at the transition, rather than continuously.

The alternative - an event queue on `NodeContext` - was rejected. It would
need either allocation or a preallocated ring buffer in the realtime path, and
it would split signal data into two kinds that only some nodes understand.

## `Graph.OneShot`

```java
public interface OneShot {
    boolean isIdle();
}
```

Implemented by `EnvelopeNode`, `RampNode`, and `FunctionGeneratorNode`. It
lets [`Patch`](patch.md) answer "has this sound ended?" without depending on
this package - see `Patch.isFinished()`. `FunctionGeneratorNode` with `cycle`
held high never reports idle, same as `TriggerNode` with `interval > 0` below
- correct, since a free-running generator genuinely has no end.

`AdsrNode` deliberately does **not** implement it. An ADSR holds its sustain
level for as long as its gate stays high, so it has no defined end.

The backing field is a `volatile boolean` written only on a stage transition,
following the one-writer/one-reader pattern `ConstantNode` already uses. A
`volatile int stage` read once per sample would put a memory barrier in the
inner loop for no benefit.

## `TriggerNode`

Ports: `delay`, `interval`. One output. Emits single-sample pulses.

It fires **on its own**, from the [`NodeContext`](graph.md) timeline. There is
no button and no `fire()` method:

- `delay` seconds after a session starts, it emits one pulse.
- If `interval` is greater than zero, it keeps emitting one every `interval`
  seconds. At `interval = 0` (the default) it fires once per session.

### Why the timeline, and the bug it fixes

The first version held a `pending` flag on the node and fired once per node
*lifetime*. That was wrong, because a node outlives a playback session.
[`AudioEngine`](audio.md) builds a fresh `NodeContext` for each
`runPlaybackLoop` and each `renderToWav`, but the same `Node` instances are
reused across both - deliberately, so a graph edit during playback does not
reset delay lines or phase.

The visible symptom: press Play in the graph editor, then Render to WAV, and
the file was silent. Playback had consumed the only pulse the trigger would
ever emit. A Fire button papered over this in the editor and could not help
`renderToWav` at all.

A session is now detected by watching the context's own clock. Each block, the
node compares `context.sampleIndex()` against where the previous block ended;
when they disagree, the timeline restarted and the trigger re-arms. The
comparison is against the previous block's **end** rather than simply looking
for `sampleIndex == 0`, so that back-to-back single-block renders - which both
start and end at the same indices - are still seen as two sessions.

Every audition path gets correct behaviour for free: a new render, a new
playback thread, or a stop/start all produce a fresh context. Nothing has to
remember to re-arm anything, which is why `SoundGenerator.rearm(...)` was
deleted rather than kept.

### Notes

- `delay` is read once per session, from sample 0 of the re-arming block, since
  a delay that changed mid-countdown has no meaningful reading. `interval` is
  read per sample, so the repeat rate is modulatable while running.
- A negative `delay` clamps to zero. An `interval` shorter than one sample
  clamps to one sample, so it cannot lock up.
- `interval > 0` means a patch never reaches the idle state
  [`Patch.isFinished()`](patch.md) reports, which is correct - a repeating
  trigger genuinely has no end.
- No caching is needed here despite convention 3: the seconds-to-samples
  conversions happen once per session and once per pulse, not once per sample.

## `EnvelopeNode`

Ports: `trigger`, `attack`, `sustain`, `punch`, `decay`. One output. All times
are in seconds.

This is sfxr's envelope shape rather than a conventional ADSR:

| Stage | Output |
|---|---|
| idle | `0` |
| attack | ramps `0 -> 1` |
| sustain | starts at `1 + punch`, falls linearly to `1` |
| decay | ramps `1 -> 0`, then returns to idle |

`punch` is the overshoot at the start of the sustain stage. It is what makes
hits, coins, and explosions read as percussive, and it has no equivalent in a
standard ADSR - which is exactly why this node exists alongside `AdsrNode`
rather than being replaced by it. A negative `punch` is allowed and inverts the
shape into a soft swell into the sustain level.

An envelope with `punch > 0` peaks above `1`, so it will drive a
[`AmplifierNode`](utility.md) past unity and clip at the converter in
[Audio](audio.md). That is the intended sfxr behavior - the clipping is part of
the character - but insert a [`LimiterNode`](effects.md) before
[`AudioOutNode`](sinks.md) when it is not wanted.

Ramps are linear, matching `RampNode` and `AdsrNode`. Exponential stages would
be closer to analog hardware, but linear keeps every stage's arithmetic a
single multiply and makes the generated patches in later work predictable to
randomize.

Each ramp is exclusive of its endpoint - a 5-sample attack outputs
`0, 0.2, 0.4, 0.6, 0.8` and the sustain stage supplies the value at `1`. This
is the same sampled-ramp behavior as the phasors in [Sources](sources.md),
which never emit their maximum either.

Stage boundaries are resolved by a bounded `while` loop rather than a single
`if`, so zero-length stages are skipped in the same sample instead of costing
one sample each. An envelope with all three times at `0` therefore stays
silent, and one with only `attack = 0` starts straight at `1 + punch`.

Negative times clamp to `0` via `Math.max`. Per convention 3, the
seconds-to-samples conversion for all three stages is cached and recomputed
only when a time input or the sample rate actually changes.

## `AdsrNode`

Ports: `gate`, `attack`, `decay`, `sustain`, `release`. One output.

The conventional shape, for the deep path rather than the quick one. `sustain`
is a **level** (0-1), not a time - the stage holds that level for as long as
`gate` stays above `0`. Attack and decay times, and the release time, are in
seconds.

Gate-driven rather than trigger-driven: a rising edge starts the attack, and a
falling edge starts the release from wherever the envelope currently sits, so
releasing mid-attack does not jump. It is the one node that reads both signs
out of a single [`Dsp.EdgeDetector`](dsp.md) call (`edge > 0` / `edge < 0`)
rather than just the rising case, since its gate needs both transitions. This
is the node to reach for on sustained or held sounds - a pad, a siren, an
engine loop - where `EnvelopeNode`'s fixed one-shot length is the wrong model.

## `RampNode`

Ports: `trigger`, `from`, `to`, `time`. One output.

Ramps once from `from` to `to` across `time` seconds, then holds `to`. Before
the first trigger it outputs `from`.

This is the pitch-slide primitive. Wired into
[`PhasorNode.frequency`](sources.md) it produces sfxr's falling laser and
rising powerup, which the cyclic phasors cannot express on their own. It is
equally useful on a filter cutoff or any other parameter port.

`from` and `to` are read per sample, so both ends of the ramp stay modulatable
while it runs. `time` is cached per convention 3.

## `FunctionGeneratorNode`

Ports: `trigger`, `cycle`, `rise`, `fall`. Two outputs: `out`, `eoc`.

The West Coast/Buchla answer to "why do I need a separate envelope, LFO, *and*
oscillator": one rise/fall shape, and a `cycle` input that decides whether it
plays once or free-runs forever. `rise` and `fall` are independent, matching
`Ramp`'s `from`/`to` split rather than `EnvelopeNode`'s single symmetric
shape - unequal rise and fall is what gives the free-running form its
sawtooth-leaning brightness instead of a plain triangle.

With `cycle` at `0` it behaves like `Ramp(from 0, to 1)` immediately mirrored
by `Ramp(from 1, to 0)`: a rising edge on `trigger` starts the rise, it falls
once it peaks, then holds at `0` until triggered again - a one-shot hit, same
role as `EnvelopeNode`. With `cycle` held above `0`, the node restarts its own
rise the instant a fall completes instead of waiting for another trigger, with
no gap and no help from `TriggerNode` or any other clock source - at
seconds-scale `rise`/`fall` that's an LFO, at millisecond-scale it's a raw
audio-rate oscillator, and it is *the same node* either way, exactly the
Buchla notion that a function generator, an LFO, and an oscillator are one
circuit at different rates rather than three different modules.

`eoc` (end-of-cycle) is a single-sample pulse - the same trigger convention
described above - that fires whenever a fall completes, cycling or not. It
exists so one `FunctionGeneratorNode` can drive the next one's `trigger`
(building a multi-stage sequence the way Buchla's own function generators are
typically chained), or feed back into its own `cycle` input for irregular,
self-interrupting shapes - the kind of self-patched feedback [`Patch`](patch.md)
now supports as a cyclic graph.

Turning `cycle` on while idle starts the free run immediately, with no trigger
needed - patching a bare `Constant(1)` into `cycle` is enough to make it
self-start. Turning `cycle` off mid-run does not cut the shape short; the
generator finishes its current rise and fall and then goes idle, matching how
a physical Buchla cycle switch behaves. `rise`/`fall` are cached per
convention 3, same as every other timed stage in this package.

## `ArpeggioNode`

Ports: `frequency`, `trigger`, `delay`, `ratio`, `steps`. One output.

Passes `frequency` straight through, then every `delay` seconds multiplies it
by `ratio`, up to `steps` times. A rising edge on `trigger` restarts the ladder
from the original frequency.

This is sfxr's arpeggio generalized. sfxr applies a single frequency multiply
once, a fixed time after the note starts - that is this node with `steps = 1`,
its default. Allowing more steps turns the same mechanism into a rising or
falling run: `ratio = 2` doubles each step for octaves, `ratio = 0.5` falls,
and `steps = 0` means unlimited.

Distinct from [`RampNode`](#rampnode), which interpolates *continuously*
between two frequencies. Arpeggio jumps in discrete multiplicative steps and
holds between them, which is what makes it read as notes rather than a slide.
It is also distinct from [`Conditionals.SequencerNode`](conditionals.md), which
steps through arbitrary values from an external clock; `ArpeggioNode` carries
its own clock and derives every step from the incoming frequency, so it tracks
whatever pitch it is given.

`delay` clamps to a minimum of one sample, so a `delay` of `0` steps once per
sample rather than multiplying without bound inside a single sample. Per
convention 3, the seconds-to-samples conversion is cached and recomputed only
when `delay` or the sample rate changes; `ratio` and `steps` are read per
sample, so both stay modulatable while the ladder runs.

## Registration

All six are registered in `NodeFactory` as `Trigger`, `Envelope`, `ADSR`,
`Ramp`, `Function Generator`, and `Arpeggio`, and appear under an `Envelopes`
category in the [Gui.NodeCatalog](gui.md) menu, which derives itself from
`NodeFactory.names()`.

## A worked patch

A coin pickup, which is the shape this package exists to make possible:

```
Trigger ─┬─> Envelope(attack .005, sustain .04, punch .4, decay .15) ─> Amplifier.gain
         └─> Ramp(from 880, to 1320, time .03) ─> Phasor.frequency ─> Sine ─> Amplifier.in
                                                                              Amplifier ─> Audio Out
```

Rendered with `AudioEngine.renderToWav(patch, 1.0, file)`, this peaks about
5 ms in and is silent well before the end of the render.
