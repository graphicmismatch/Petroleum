# Granular (`Petroleum.Granular`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Sources`/`Petroleum.Envelopes`, not nested under `Graph`. Granular
synthesis plays back many small, overlapping, windowed "grains" of a sample
buffer - each with its own read position, pitch, and duration - mixed
together, a fundamentally different shape from every other node family in
this codebase (which are all single-voice: one filter state, one oscillator
phase, one delay tap). Four nodes here delegate their actual grain
scheduling, windowing, and voice management to one shared engine,
[`Dsp.GrainPool`](dsp.md) - this package holds only the different ways of
feeding that engine a sample buffer and, for two of the four, shaping its
output further (feedback, per-grain filtering).

**Why grains at all, instead of `Oscillators.Implementations.Wavetable` or
`Utility`'s plain sample playback?** A single voice reading a buffer can only
be in one place, at one pitch, at a time - moving the read position and
changing pitch are the same knob (playback speed) fighting each other. Grains
decouple the two entirely: because many short, independent, overlapping
copies are being read and mixed at once, `pitch` can change without changing
how fast the read position advances through the buffer, and vice versa - the
same mechanism that lets a granular player stretch a one-second recording
into a minutes-long evolving drone with no pitch change, or transpose a
sound without speeding it up or slowing it down.

## Engine, in brief

The essentials of `Dsp.GrainPool` a `Granular` reader needs, without opening
`dsp.md`: grains are windowed with a raised-cosine (Hann) shape, so each one
fades in and out rather than clicking at its edges; the pool has a **fixed**
number of simultaneous voices (`GrainPool.DEFAULT_MAX_GRAINS = 32` for every
node here), and when a new grain needs to spawn with none free, it steals the
**single oldest** active grain rather than the newest - the oldest one is
already closest to the silent tail of its own Hann window, so stealing it
discards the least audible energy of any candidate. Every grain's read
position, duration, pitch (and, for `GranularCloudNode`, filter cutoff) are
randomized once at the moment it spawns and then held fixed for that grain's
whole lifetime - never re-rolled mid-grain. See [Dsp](dsp.md)'s `GrainPool`
entry for the exact formulas.

## Tuning `density` and `grainSize` together

These two ports interact more than any others, and the relationship is the
single most useful thing to understand before turning either knob: at
roughly `density * grainSize ≈ 2` (each new grain spawning halfway through
the previous one's life), overlapping Hann windows sum to a nearly constant
envelope - the classic smooth, self-leveling granular "wash." Push `density`
down relative to `grainSize` (fewer, longer grains with gaps between spawns)
and individual grains become audible as a stutter or a rhythmic
chop. Push `density` up relative to `grainSize` (many short grains
overlapping deeply) and the texture gets denser and louder - `GrainPool`
deliberately does not normalize its output against grain count (see
`dsp.md` for why), so a very dense setting benefits from a downstream
`Effects.LimiterNode` rather than expecting the node itself to cap the level.

## `GranularPlayerNode`

Plays grains from a static, fully-loaded sample buffer imported once at
construction (`GranularPlayerNode(float[] samples, String name)` - the same
shape as `Oscillators.Implementations.Wavetable(table, name)`).

Ports:

| Port | Units | Default | Meaning |
|---|---|---|---|
| `position` | seconds from the start of the sample | `0` | read anchor for new grains |
| `positionJitter` | `0..1` | `0` | random spread of the read anchor, scaled by each grain's own (jittered) size |
| `grainSize` | seconds | `0.08` | grain duration |
| `grainSizeJitter` | `0..1` | `0` | random spread of grain duration |
| `density` | Hz (grains/sec) | `20` | the internal free-running spawn clock's rate |
| `pitch` | ratio (`1` = unchanged) | `1` | per-grain playback-rate multiplier |
| `pitchJitter` | `0..1` | `0` | random spread of pitch |
| `amp` | linear gain | `0.6` | per-grain peak amplitude |
| `trigger` | edge-detected signal | `0` | spawns one additional grain per rising edge, independent of `density` |

One output, `out`. This node only wires its ports into one
`GrainPool.process(...)` call per sample - see "Engine, in brief" above.

**Reads clamp, they don't wrap.** The private `FixedBufferSource` indexes
`samples` by absolute position, clamping to `[0, length-1]` at both ends
(the same defensive clamp-then-lerp shape `Dsp.DelayLine.readFractional`
uses as its own second layer) rather than wrapping cyclically the way
`Wavetable.getSample` does. A grain that runs past the end of a one-shot
imported sample (a long `grainSize`, a high `pitch`, or a `position` near
the tail) should trail off holding the last sample, not audibly jump back to
the start of the file mid-grain - wrapping is the right choice for a
one-cycle waveform oscillator, but not for a finite recording.

**Deliberately unregistered in `NodeFactory`.** Like `Wavetable`,
`Oscillators.OscillatorNode`, `DualOscillator`, and `Wavefolder`, this node
needs a mandatory constructor argument with no sane universal default (a
sample buffer) - per convention 6 it is constructed directly instead
(`Gui.NodeCatalog`'s "Granular Player" entry, below) and given its own
`instanceof` branch in [`PatchCodec`](serialization.md) rather than a
factory registration.

**No convention-9 session handling needed.** Unlike `Envelopes.TriggerNode`,
this node does not compare `NodeContext.sampleIndex()` against a remembered
block start anywhere. `GrainPool`'s internal state (`active[]`/`age[]`/
`spawnPhase`) is ordinary free-running state that tolerates being carried
across two independent playback/render sessions of the same long-lived
`Node` instance with no correctness problem: a few grains mid-flight from a
previous session simply finish decaying into the new session's first block
(their fixed `duration` elapses normally regardless of which session it
started in), and `spawnPhase` has no dependency on absolute sample index at
all - it is exactly the same free-running accumulator `PhasorNode` already
carries across sessions unremarked.

## `Dsp.LiveGrainRecorder` - the shared live-input `Source`

`GranularFreezeNode`, `GranularDelayNode`, and `GranularCloudNode` all need
the same thing: continuously (or conditionally) record a live `in` signal
into a ring buffer and expose "how many seconds behind the current write
head" as `GrainPool.Source` read coordinates. That's `Dsp.LiveGrainRecorder`
(see `dsp.md`) - a `DelayLine` wrapped with a `long totalWritten` counter and
a `positionBehind(secondsBehind, sampleRate)` convenience method. It started
as private, duplicated logic inside `GranularFreezeNode` alone; once a second
node needed the identical live-recording pattern, it was extracted once
rather than copied a second time. Each node still owns *when* to call
`recorder.write(...)` (or skip it, for `GranularFreezeNode`'s `freeze` gate)
- the recorder itself has no opinion on that, only on how the ring buffer and
running write-count stay in lockstep.

## `GranularFreezeNode`

A real-time granular effect, not an imported-sample player: continuously
records its `in` port into a `LiveGrainRecorder` and grains that buffer
live, with a `freeze` gate that - while held - stops recording so grains
keep reading whatever was already captured (a "freeze"/shimmer effect). Has
no mandatory constructor argument, so unlike `GranularPlayerNode` it is an
entirely ordinary `NodeFactory`-registered node (`"Granular Freeze"`) with no
`PatchCodec` special-casing anywhere.

Ports:

| Port | Units | Default | Meaning |
|---|---|---|---|
| `in` | audio | `0` | signal continuously recorded into the ring buffer |
| `freeze` | gate, `>0` = frozen | `0` | while nonzero, stop writing new audio - grains keep reading the captured buffer |
| `position` | seconds **behind the live write head** | `0.5` | how far back into the recorded history grains read from |
| `positionJitter` | `0..1` | `0` | same meaning as `GranularPlayerNode` |
| `grainSize` | seconds | `0.08` | same |
| `grainSizeJitter` | `0..1` | `0` | same |
| `density` | Hz | `20` | same |
| `pitch` | ratio | `1` | same |
| `pitchJitter` | `0..1` | `0` | same |
| `amp` | linear gain | `0.6` | same |
| `trigger` | edge-detected | `0` | same |

**`position`'s meaning intentionally differs from `GranularPlayerNode`'s**
(seconds-behind-now instead of seconds-from-start) - a live, indefinitely
running recording has no fixed "start" to measure from, only a constantly
advancing "now." Every live-input node in this package (`GranularFreezeNode`,
`GranularDelayNode`, `GranularCloudNode`) shares this "seconds behind now"
convention; only the static-buffer `GranularPlayerNode` uses
seconds-from-start, and that's a deliberate asymmetry, not an inconsistency.

**`freeze` is a level gate, not an edge-detected trigger** - checked fresh
every sample (`inputs[FREEZE][s] > 0f`), matching the existing "sustained
condition" precedent (`Envelopes.AdsrNode`'s `gate` port) rather than the
"momentary pulse" one (convention 8), since "stop recording while held high"
is inherently a duration, not an instant.

**The `totalWritten`/freeze lockstep is the load-bearing correctness
detail**, now inside `LiveGrainRecorder.write(...)` rather than hand-rolled
per node: it advances the ring buffer's write index and the running
`totalWritten` count together, never independently. If `totalWritten`
advanced every sample regardless of `freeze`, a frozen grain's computed
`delaySamples = totalWritten - position` would keep growing even though the
physical write head has stopped moving, silently dragging every frozen
grain's read point backward toward the single oldest sample in the ring well
before a long freeze ends. `GranularFreezeNode` enforces the lockstep simply
by only calling `recorder.write(...)` inside its own `if (!frozen)` branch -
the recorder has no `freeze` concept of its own, it just guarantees that
*whenever* `write` is called, both counters move together.

The ring buffer is lazily allocated on the first `process()` call, once
`context.sampleRate` is known (`LiveGrainRecorder.ensureAllocated(...)`,
called at the top of `process()`) - the same pattern
`ChorusNode`/`FlangerNode`/`CombFilterNode`/`Effects.ReverbNode` already use
- sized to a fixed class constant, `MAX_RECORD_SECONDS = 3.0`, rather than a
constructor parameter. Keeping it a constant is deliberate: a configurable
record length would need the same construction-time-parameter treatment
(`PatchCodec` special-casing, `NodeFactory` default-supplying lambda) that
`GranularPlayerNode`'s sample buffer needs, for a node that otherwise
requires none at all.

## `GranularDelayNode`

A live granular delay with feedback - the mechanism behind granular
delay/shimmer effects (grain output is mixed back into what gets recorded,
so on `pitch != 1` a pitch-shifted copy is what gets re-granulated on the
*next* feedback pass, compounding into a rising or falling shimmer the more
passes it survives). Where `GranularFreezeNode` is a texture generator
(always 100% wet, no dry signal passes through untouched), this is a proper
delay *effect* with its own dry/wet `mix` - the input keeps passing through
even at `feedback = 0`.

Ports:

| Port | Units | Default | Meaning |
|---|---|---|---|
| `in` | audio | `0` | signal continuously recorded into the ring buffer |
| `time` | seconds **behind the live write head** | `0.3` | this node's delay time |
| `feedback` | `0..0.95` (clamped) | `0.3` | how much of the granulated output is mixed back into what gets recorded |
| `positionJitter` | `0..1` | `0` | same meaning as `GranularFreezeNode` |
| `grainSize` | seconds | `0.08` | same |
| `grainSizeJitter` | `0..1` | `0` | same |
| `density` | Hz | `20` | same |
| `pitch` | ratio | `1` | per-grain playback-rate multiplier - set away from `1` for shimmer, since feedback re-granulates the shift each pass |
| `pitchJitter` | `0..1` | `0` | same |
| `amp` | linear gain | `0.6` | same |
| `mix` | `0..1` | `0.5` | dry/wet blend of the final output - `0` is silent-except-dry, `1` is the granulated signal only |
| `trigger` | edge-detected | `0` | same |

**Read happens before write, every sample - never the reverse.** `time`'s
position is computed and `GrainPool.process(...)` is called against the
recorder's *current* `totalWritten` first; only after that does
`recorder.write(dry + grainOut * feedback)` run (which is what actually
advances `totalWritten`). This ordering is what keeps the feedback loop
causal: whatever a grain reads on a given sample can never include that same
sample's own feedback contribution, only whatever was written on a genuinely
earlier sample - feedback always has to pass through the real delay `time`
before it can affect a future grain, exactly like an ordinary delay line's
feedback, never an instant same-sample loop.

**Grain density matters more here than anywhere else in this package.**
Because feedback repeatedly re-granulates its own previous output, a sparse
setting (`density * grainSize` well under the ~2 "self-leveling" point above)
loses noticeably more energy per feedback pass than a dense one - each pass
is a lossy resampling of the previous pass, not a clean tap the way a plain
delay line's feedback is. For a shimmer/wash character that sustains audibly
across many feedback cycles, favor denser settings than `GranularFreezeNode`
or `GranularPlayerNode` typically need.

Registered in `NodeFactory` as `"Granular Delay"`, an ordinary no-arg
constructor - no `PatchCodec` special-casing, same as `GranularFreezeNode`.
Record buffer ceiling is `MAX_RECORD_SECONDS = 4.0`, longer than
`GranularFreezeNode`'s 3.0, since this node is meant to cover ordinary
delay-effect time ranges rather than just a freeze-point lookback.

## `GranularCloudNode`

Continuous live granulation, like `GranularFreezeNode` with no `freeze` gate
and no feedback, but every grain gets its own randomized low-pass filter
cutoff at spawn - so instead of every grain sharing one tone color, the
texture is a "cloud" of differently-filtered fragments, brighter and darker
grains overlapping at once. This is the technique behind 2026's Granite
plugin's headline feature (per-grain processing, rather than filtering the
mixed output of a granular engine the way every filter node elsewhere in
this codebase would if patched downstream of `GranularFreezeNode` instead).

Ports:

| Port | Units | Default | Meaning |
|---|---|---|---|
| `in` | audio | `0` | signal continuously recorded into the ring buffer |
| `position` | seconds behind the live write head | `0.3` | same meaning as `GranularFreezeNode` |
| `positionJitter` | `0..1` | `0` | same |
| `grainSize` | seconds | `0.08` | same |
| `grainSizeJitter` | `0..1` | `0` | same |
| `density` | Hz | `20` | same |
| `pitch` | ratio | `1` | same |
| `pitchJitter` | `0..1` | `0` | same |
| `filterCutoff` | Hz | `2000` | one-pole low-pass cutoff a grain is given at spawn |
| `filterCutoffJitter` | `0..1` | `0.5` | random spread of that cutoff, drawn once per grain - **the only jitter port in this whole package that defaults to nonzero**, since a `GranularCloudNode` with zero cutoff spread is just a plain filtered granulator and misses the entire point of the node |
| `amp` | linear gain | `0.6` | same |
| `trigger` | edge-detected | `0` | same |

Needs `Dsp.GrainPool`'s newer 13-argument `process(...)` overload (see
`dsp.md`) - `filterCutoff`/`filterCutoffJitter` are its two extra trailing
parameters. Registered in `NodeFactory` as `"Granular Cloud"`, an ordinary
no-arg constructor, same treatment as `GranularFreezeNode`/`GranularDelayNode`.

## Scope not attempted in v1

No reverse-direction ("negative pitch") grains - see `Dsp.GrainPool`'s note
in `dsp.md`. No stereo output - the engine is mono internally throughout
(`Patch` renders one buffer; see [Audio](audio.md)), so no node in this
package attempts a pan/spread port that would need a second output channel.
`GranularCloudNode`'s per-grain filtering is deliberately just one filter
type (one-pole low-pass) - per-grain resonant/high-pass/band-pass shaping,
or per-grain amplitude/pan envelopes beyond what jitter already provides,
are natural extensions of the same `GrainPool` mechanism this node
introduced, not attempted here.
