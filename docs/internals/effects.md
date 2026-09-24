# Effects (`Petroleum.Effects`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Oscillators`, not nested under `Graph`.

- **DelayNode** - a feedback delay line (echo): interpolated fractional-sample
  read from a circular buffer, written back with feedback, dry/wet mixed.
  Ports: `in`, `time`, `feedback`, `mix`. The circular buffer/read/write logic
  is a `Dsp.DelayLine` field, lazily constructed on the first `process()` call
  once `context.sampleRate` is known; see [Dsp](dsp.md).
- **ChorusNode** - mixes the input with a copy read from a short delay line
  whose length is wobbled by a slow LFO (no feedback), thickening the sound.
  Ports: `in`, `rate`, `depth`, `mix`. Uses `Dsp.DelayLine` for the buffer and
  `Dsp.LfoPhase` for the modulation LFO. The `depth` clamp's upper bound is
  `min(CENTER_DELAY_MS, MAX_DELAY_MS - CENTER_DELAY_MS)` = 15ms - previously
  it was just `MAX_DELAY_MS - CENTER_DELAY_MS` = 35ms against a 15ms center,
  which let the modulated delay swing negative and crash with an
  `ArrayIndexOutOfBoundsException`; see [Dsp](dsp.md) for the full writeup.
- **FlangerNode** - like chorus but with a shorter LFO-wobbled delay and a
  feedback path, producing the sweeping comb-filter "jet" sound. Ports: `in`,
  `rate`, `depth`, `feedback`, `mix`. Uses `Dsp.DelayLine`/`Dsp.LfoPhase` the
  same way as `ChorusNode`, and had the identical negative-delay bug: the
  `depth` clamp's upper bound is now `min(CENTER_DELAY_MS, MAX_DELAY_MS -
  CENTER_DELAY_MS)` = 5ms (down from the old unguarded 10ms); see
  [Dsp](dsp.md).
- **TremoloNode** - amplitude modulation by an internal LFO (`Dsp.LfoPhase`).
  Ports: `in`, `rate`, `depth`.
- **TranceGateNode** - a stepped on/off (or partial-gain) gate, distinct from
  `TremoloNode`'s continuous sine LFO. Follows `Conditionals.SequencerNode`'s
  exact construction-time arity pattern: constructor takes a step count,
  `buildPorts(steps)` appends `step0..step(N-1)` after the fixed `in`/`rate`
  ports, `getSteps()` accessor for `PatchCodec`. Ports: `in`, `rate`
  (steps/second), `step0..step(N-1)` (each default `1`, unlike
  `SequencerNode`'s `0` default - this is an audio-shaping *effect* node, so
  full-through is the sane unpatched default). `NodeFactory` registers three
  preset sizes (`Trance Gate`/`Trance Gate 4`/`Trance Gate 16`, steps
  8/4/16); `Gui.NodeCatalog`'s "Trance Gate (Custom Size)..." entry prompts
  for any other step count and constructs one directly, unregistered - same
  treatment as [`SplitterNode`](utility.md)'s custom-size entry.

  A free-running `phase` in `[0,1)` advances by `rate/sampleRate` each
  sample (a hand-rolled accumulator like `Sources.LfoNode`'s, not
  `Dsp.LfoPhase`, since this needs a `while (phase >= 1f)` step-advance
  rather than a sinusoidal read); each wrap advances `currentStep` (mod
  `steps`). Output is `in * step{currentStep}`. No session-restart handling
  (per [convention 9](conventions.md)) - `phase`/`currentStep` are relative
  progress, not absolute sample numbers, the same non-issue `LfoNode`/
  `ChorusNode`/`Conditionals.RunglerNode` already accept without it.
- **DistortionNode** - one node covering five waveshaping curves via a
  `DistortionType` enum (`SOFT_CLIP`, `HARD_CLIP`, `FOLDBACK`, `OVERDRIVE`,
  `RECTIFY`), the same one-node-with-a-type-enum shape `Filters.
  BiquadFilterNode` established for its eight filter shapes - `getName()`
  returns `"Distortion " + type.label()`, and `NodeFactory` registers one
  key per type rather than one bare `"Distortion"` key. All five share the
  same `in`/`drive`/`mix` ports and the same `driven = in * (1 + drive)` then
  `out = in*(1-mix) + shape(driven)*mix` blend; only `shape(driven)` differs:
  - `SOFT_CLIP` - `tanh(driven)`, the original single-curve behavior. Smooth
    everywhere, no hard corners.
  - `HARD_CLIP` - `clamp(driven, -1, 1)`. A flat ceiling instead of a curved
    one - harsher, buzzier upper harmonics than `SOFT_CLIP` at the same
    `drive`.
  - `FOLDBACK` - reflects `driven` back down past a threshold instead of
    clipping it, via an analytic triangle-wave fold (`|wrap(driven-1, 4) -
    2| - 1`, `wrap` a floor-mod into `[0, 4)`) rather than an iterative
    reflect-until-in-range loop - closed-form and allocation-free, unlike a
    loop whose iteration count depends on `drive` and could in principle run
    long at extreme settings. The same folding principle
    `Oscillators.Implementations.Wavefolder` applies to a raw oscillator
    waveform, here applied to an arbitrary incoming signal instead.
  - `OVERDRIVE` - a cubic soft-clip (the classic three-region "quiet signal
    passes through mostly linear, a cubic curve rounds the knee, then a hard
    ceiling" shape used by many analog-modeled overdrive circuits): linear
    below `|driven| < 1/3`, a cubic knee between `1/3` and `2/3`, hard-clipped
    beyond. A different curve family from `SOFT_CLIP`'s tanh, not just a
    rescaled version of it.
  - `RECTIFY` - `min(1, |driven|)`, full-wave rectification. Folds the
    negative half-cycle onto the positive half, halving the fundamental
    period - heard as an octave-up buzz rather than a conventional clipped
    tone, the one type here that changes pitch content instead of only
    adding harmonics on top of the original.
  - `ASYMMETRIC` - `tanh(driven)` on the positive half, `tanh(driven *
    ASYMMETRY_RATIO)` (`1.6`) on the negative half. Both halves still
    saturate to the same `±1` bound (that's `tanh`'s own ceiling, independent
    of the input scale), so nothing needs rescaling - only the negative
    half's knee arrives sooner. A symmetric curve like `SOFT_CLIP` only
    produces odd-harmonic distortion; clipping the two halves differently
    also introduces even harmonics, the extra warmth/complexity an
    uneven-diode-pair circuit (a Tube Screamer-style overdrive) is known for.
- **WaveshaperNode** - a *generic* waveshaper: `outputs[OUT][s] =
  expression.evaluate(inputs[IN][s])`, where the formula is an arbitrary
  user-supplied [`Expression`](expression.md) with `x` bound to the incoming
  sample (not phase, unlike every oscillator's use of `MathExpression`) -
  `DistortionNode`'s fixed curves and `Wavefolder`'s fixed fold curve (see
  [Oscillators](oscillators.md)) are each one specific point on the space of
  curves this node can express; `"tanh(3*x)"` reproduces `DistortionNode`'s
  `SOFT_CLIP` shape directly, `"clamp(x,-0.3,0.3)"` is a hard clipper,
  `"x-x^3/3"` is a cheap odd-harmonic softener, and so on - one memoryless
  nonlinear transfer function, written down rather than picked from a fixed
  menu. One port, `in`; the default formula is `"x"` (identity - a freshly
  placed node passes its input through unchanged until edited). Implements
  [`Expression.FormulaSource`](expression.md), so it gets the same live
  `TextArea` editor `ExpressionOscillator` does - see [Gui](gui.md). Its
  formula persists through save/load the same way a construction-time
  parameter does (`Serialization.PatchCodec` writes/reads a `source` field),
  even though the node is also an ordinary `NodeFactory` entry with a sane
  default - the same dual treatment `Conditionals.SequencerNode`'s step
  count gets.
- **BitcrusherNode** - lo-fi effect: quantizes to a reduced bit depth (`bits`)
  and holds samples to simulate a lower sample rate (`rate`).
- **RingModulatorNode** - multiplies the input by an internal sine carrier
  (`frequency`, driven by a `Dsp.LfoPhase`), producing metallic/robotic
  tones.
- **AmplitudeModulatorNode** - the *generic* form `RingModulatorNode` is one
  fixed instance of: `outputs[OUT][s] = carrier * (1 + depth*modulator)`,
  where both `carrier` and `modulator` are ordinary patchable ports rather
  than one of them being a `Dsp.LfoPhase`-driven internal oscillator. At
  `depth = 0` the carrier passes through unchanged; at `depth = 1` a
  bipolar `[-1, 1]` modulator produces classic 100%-modulated AM (the
  carrier reaches exactly zero at the modulator's troughs). Patching
  `RingModulatorNode`'s internal carrier concept back in is one `LFO Sine`
  node (see [Sources](sources.md)) wired into `modulator`; patching in any
  other signal - a second voice, an envelope, noise - reaches sounds a
  fixed-internal-carrier ring modulator can't. Ports: `carrier`,
  `modulator`, `depth`.
- **FrequencyModulatorNode** - the *generic* form
  `Oscillators.Implementations.FMOscillator` (see
  [Oscillators](oscillators.md)) is one fixed instance of:
  `outputs[OUT][s] = carrier + depth*modulator`, a plain Hz value rather than
  audio - `carrier` and `modulator` are ordinary patchable ports instead of
  both being fixed internal sines only reachable through `mod ratio`/
  `mod index`. The output is not itself audio; it is meant to feed a
  `Sources.PhasorNode`'s `frequency` port (which in turn drives an
  oscillator), the same way `Envelopes.RampNode`/`ArpeggioNode` output a
  frequency value rather than a waveform. Deliberately addition rather than
  `AmplitudeModulatorNode`'s multiplication, since `depth` here is a Hz
  offset (how far the pitch swings), not a 0..1 fraction - there is no
  natural upper bound to clamp `depth` against the way `AmplitudeModulatorNode`
  clamps its own to `[0, 1]`. Patching an `LFO Sine` into `modulator` at a
  modest `depth` gives plain vibrato; any other audio-rate signal - noise, a
  second voice, an envelope - reaches modulation shapes the fixed-carrier FM
  oscillator can't, and unlike the FM oscillator, `carrier` itself can be
  fed from another modulating node (a `RampNode`, an `ArpeggioNode`) rather
  than only ever being a plain phase input. Ports: `carrier`, `modulator`,
  `depth`.
- **FrequencyShifterNode** - a Bode/single-sideband-style frequency shifter:
  moves every partial in the input by the same constant number of Hz, not
  the same ratio, which is what makes it distinct from every pitch-shifting
  or modulation node elsewhere in the project (including
  `RingModulatorNode` above) - a harmonic input (e.g. a sawtooth with
  partials at 100/200/300/400Hz) shifted by +50Hz comes out with partials at
  150/250/350/450Hz, no longer in an integer ratio to each other, which is
  what produces the technique's characteristic inharmonic, metallic,
  bell-like or "alien" timbres. `RingModulatorNode` multiplies by a fixed
  carrier and produces *two* symmetric sidebands (carrier±input); this node
  is deliberately single-sideband - only one sideband survives, so a pure
  sine in comes out as a single pure sine at input+shift (or input-shift),
  not a pair.
  - Ports: `in`, `shiftHz` (signed - a positive value shifts every partial
    up by that many Hz, a negative value shifts down; there is no ratio
    interpretation of this port, only an additive one).
  - Built from a `Dsp.HilbertTransformer` (see [Dsp](dsp.md)) producing an
    in-phase/quadrature pair from `in`, plus a `Dsp.LfoPhase` running at
    `abs(shiftHz)` as the shift carrier. Per sample:
    `out = inPhase*cos(carrierPhase) - sign*quadrature*sin(carrierPhase)`,
    `sign = +1` when `shiftHz >= 0` (upper sideband - the classic
    single-sideband "upshift"), `sign = -1` otherwise (lower sideband -
    "downshift"). This is exactly the analytic-signal derivation: treating
    `(inPhase, quadrature)` as one complex sample `z = inPhase + j*quadrature`
    (a signal with energy only at positive frequencies) and multiplying by
    `e^(+j*carrierPhase)` for an upshift or `e^(-j*carrierPhase)` for a
    downshift, then keeping only the real part.
  - Verified by smoke test: a 1000Hz sine in, `shiftHz = 200`, measures a
    single dominant output frequency at 1200Hz (not 1000Hz scaled by a
    ratio, and not a symmetric 800/1200Hz pair the way a ring modulator
    would produce) with the unwanted 800Hz sideband roughly 55dB down;
    `shiftHz = -200` on the same input measures 800Hz, same rejection.
- **CompressorNode** - dynamic range compressor, covering three detector
  topologies via a `CompressorType` enum (`PEAK`, `RMS`, `FEEDBACK`) - the
  same one-node-with-a-type-enum shape as `DistortionNode`/`BiquadFilterNode`.
  All three share the same `threshold`/`ratio`/`attack`/`release`/`makeup`
  ports and the same exponential attack/release envelope follower
  (`Dsp.EnvelopeFollower`) driving gain reduction above `threshold` (dB)
  according to `ratio`, plus optional `makeup` gain; only what feeds the
  envelope follower each sample differs:
  - `PEAK` - `abs(in)` (or `abs(sidechain)` with external keying, below), the
    original behavior. Reacts to the instantaneous peak, so it's fast and
    precise on sharp transients.
  - `RMS` - `in * in` fed through the same follower, then `sqrt(...)` taken
    afterward on the smoothed value rather than before - a one-pole
    (exponential) smoothing of the squared signal is a standard real-time
    approximation of true windowed RMS, reusing the existing
    `EnvelopeFollower` rather than adding a second smoother. Reacts to
    sustained loudness rather than brief peaks, reading as gentler/more
    "musical" than `PEAK` at the cost of letting sharp transients through a
    little further before gain reduction catches up.
  - `FEEDBACK` - `abs(previousOutput)`, a new per-instance field holding the
    node's own last output sample - the classic feedback (as opposed to
    feedforward) compressor topology vintage optical hardware compressors
    use. Because the detector sees the already-compressed signal, gain
    reduction self-moderates, reading smoother and more forgiving at high
    ratios than the feedforward `PEAK`/`RMS` types. `previousOutput` is
    ordinary per-node state, reset by nothing special - same treatment as the
    pre-existing `envelope` field, consistent with [convention 9](conventions.md)
    since neither is session-defined state, just a decaying value that
    happens to persist between renders like any other filter's memory.

  External (sidechain) keying is one more `sidechain` audio input plus an
  `externalKey` gate port, the same boolean-as-plain-signal idiom
  `GranularFreezeNode`'s `freeze` gate uses (convention 8): `externalKey > 0f`
  swaps the detector's source from the node's usual signal (`in`, or
  `previousOutput` for `FEEDBACK`) to `sidechain`, for all three detector
  types. There is deliberately no "is this port connected" check anywhere in
  `Graph`/`Patch` (an unconnected port is indistinguishable from one wired to
  a genuine 0), so a silent default couldn't tell "no sidechain patched" from
  "sidechain patched to a currently-silent signal" - the explicit gate is what
  keeps ordinary (non-keyed) compression byte-for-byte unchanged with the new
  ports left at their defaults, while still letting one signal (e.g. a kick
  drum on `sidechain`) duck a completely different one (`in`, e.g. a
  bassline) without the two ever being mixed together.

  The envelope/gain-from-dB math is genuinely signal-dependent every sample
  and isn't cached; the attack/release coefficients (which only depend on the
  knobs and sample rate) are, inside the `EnvelopeFollower` - see
  [Dsp](dsp.md).
- **EnvelopeFollowerNode** - `Dsp.EnvelopeFollower` exposed directly as a
  node, rather than buried inside `CompressorNode`/`LimiterNode`/
  `NoiseGateNode`'s own gain computation: `outputs[OUT][s] =
  envelopeFollower.followAttackRelease(envelope, abs(inputs[IN][s]))`, ports
  `in`, `attack` (ms), `release` (ms). Where the other three consumers use
  the tracked envelope internally to drive their own gain math and never
  expose it, this node's entire output *is* the envelope - "how loud is this
  signal right now, smoothed" as its own control-rate signal, patchable into
  any other node's port (an auto-wah's cutoff, an amplitude modulator's
  `depth`, anything). No other node in the codebase turns an audio signal
  into a general-purpose modulation source; this is the first analysis-only
  node, everything before it only ever transforms the audio it's given.
  Registered as `Level Follower` (not `Envelope Follower`, the class name's
  own reading) - the catalog name deliberately breaks from the class name to
  keep it from reading as a member of the `Envelopes` package's one-shot
  generator family (`Envelope`/`ADSR`/`Function Generator`), when this node
  is an analyzer, not a generator.
- **AutoWahNode** - exactly the "an auto-wah's cutoff" example above, built
  in as one node instead of hand-patched: an internal `Dsp.EnvelopeFollower`
  (the same helper `EnvelopeFollowerNode`/`CompressorNode`/`LimiterNode`/
  `NoiseGateNode` all use) tracks `abs(in)`, and that tracked level sweeps an
  RBJ bandpass biquad's cutoff between `minFreq` and `maxFreq`, scaled by
  `sensitivity`. The bandpass coefficient math is the exact `BANDPASS` case
  from `Filters.BiquadFilterNode.recomputeCoefficients` (`alpha =
  sin(w0)/(2*q)`, `b0=alpha, b1=0, b2=-alpha`, normalized by `a0=1+alpha`),
  duplicated locally rather than nesting a `BiquadFilterNode` instance -
  nodes don't currently hold other nodes as internal implementation details
  (`Modules.PatchNode` is the one deliberate exception, and that's a whole
  embedded `Patch`, not a single filter stage). Cutoff/`q`/sample-rate
  coefficient recomputation is guarded the same way `BiquadFilterNode`'s is
  (convention 3), even though the envelope-driven cutoff changes on most
  samples while the input is active - the guard still pays off during
  silence, when the envelope decays to a steady value and coefficient
  recomputation stops entirely. `Filters.LowPassGateNode` is not a
  substitute for this: it is strike-triggered (an event starts an
  exponential decay), not continuously driven by the input's own level.
  Ports: `in`, `sensitivity`, `attack`, `release`, `minFreq`, `maxFreq`, `q`,
  `mix`.
- **ReverbNode** - a simple Schroeder/Freeverb-style reverb: four parallel
  damped comb filters (`Comb`, a private inner class - a feedback delay with
  a one-pole lowpass in the loop for high-frequency damping) feed into two
  series allpass filters (`Allpass`, another private inner class - a
  fixed-feedback allpass delay that diffuses without coloring the spectrum).
  Comb/allpass delay lengths are tuned at 44.1kHz and scaled to whatever
  sample rate the graph actually runs at. Ports: `in`, `roomSize`, `damping`,
  `mix`.

  `Comb`/`Allpass` hand-roll their own ring buffers instead of using
  [`Dsp.DelayLine`](dsp.md) - deliberately, not an oversight. Each reads
  `buffer[index]` and writes `buffer[index]` in the same call, so the read is
  always exactly `length` samples old (one full trip around a `length`-slot
  ring). `DelayLine.readFractional` clamps its delay to `capacity - 1`
  specifically to avoid reading the slot a same-block write is about to land
  on, so a delay of exactly `length` can't be expressed through its public
  API without either breaking that guard or growing the ring by one slot and
  changing its period. `CombFilterNode` (see [Filters](filters.md)) is the
  node that actually fits `DelayLine`'s shape - lookback strictly less than
  capacity, write always trailing the read - which is a different access
  pattern from a same-slot feedback loop, not a smaller version of it.
- **SpringReverbNode** - a second, structurally distinct reverb algorithm
  from `ReverbNode`, aiming for a spring-tank "boing/chirp" character rather
  than `ReverbNode`'s smooth diffuse Schroeder/Freeverb tail. An honest
  approximation, not a validated circuit model. Ports: `in`, `decay`,
  `tone`, `boing`, `mix`.

  Six allpass stages in series, each built on `Dsp.DelayLine` (fractional
  reads, unlike `ReverbNode`'s own hand-rolled fixed-index `Allpass` inner
  class - required here because each stage's delay length is itself
  LFO-modulated, which a same-slot fixed-index ring buffer can't express).
  A private `ModulatedAllpass` inner class wraps the same `-FEEDBACK*input +
  buffered` / `write(input + buffered*FEEDBACK)` topology `ReverbNode.Allpass`
  uses, just parameterized per call instead of fixed at construction. Base
  delay lengths (`{397, 601, 773, 887, 1013, 1129}` at 44.1kHz, scaled to the
  actual sample rate on first `process()`, same lazy-init pattern
  `ReverbNode` uses) are spread and mutually non-multiple, like `ReverbNode`'s
  own comb/allpass lengths, so the six stages don't reinforce each other into
  an artificial resonance. A single `Dsp.LfoPhase` (`WOBBLE_RATE_HZ ≈ 0.6`)
  drives every stage's modulation, each stage offset by `i * (2*PI/STAGES)`
  radians so they wobble against rather than in lockstep with each other -
  that per-stage phase spread plus the dispersive (non-multiple) delay
  lengths together are what produce the "boing" rather than a uniform,
  mechanical vibrato. The chain's output feeds back into its own input
  scaled by `decay`, through a one-pole lowpass (`Dsp.DspMath.
  onePoleLowpassAlpha`, cutoff interpolated by `tone` between `MIN_TONE_HZ`
  and `MAX_TONE_HZ`, recomputed only when `tone`/sample rate change -
  convention 3) that models a spring's limited high-frequency reach.

  The chain's raw output is passed through `Math.tanh` before it becomes
  both the wet signal and the next sample's feedback input - load-bearing,
  not cosmetic saturation. A *time-varying* allpass chain (delay length
  changing every sample via the wobble) does not keep the fixed-length
  allpass's unity-magnitude guarantee `ReverbNode.Allpass` relies on: an
  early version of this node fed the chain's raw output back into itself
  and grew unboundedly within a fraction of a second at every default
  setting, confirmed by rendering ~200 blocks and watching the RMS climb
  past `10^16`, not merely a rare edge case at extreme `decay`/`boing`
  values. `LadderFilterNode` already saturates its own resonant feedback
  path the same way (`tanh(in - resonance*stage4)`) for the identical
  reason - a `tanh` inside a feedback loop is what keeps a physically
  plausible amount of energy circulating regardless of how much gain the
  loop's own filtering happens to inject, the digital equivalent of a real
  spring tank's mechanical saturation under a hard strike.
- **PhaserNode** - a 4-stage cascade of first-order allpass filters whose
  corner frequency is swept by an internal LFO (`Dsp.LfoPhase`), mixed with
  the dry signal; a feedback path around the whole cascade deepens the
  notches. Ports: `in`, `rate`, `depth`, `feedback`, `mix`.
- **LimiterNode** - a brick-wall peak limiter: envelope jumps up
  instantaneously (fast attack) and decays under an exponential release, and
  gain is pulled down whenever the envelope exceeds `ceiling`. Ports: `in`,
  `ceiling`, `release`. The instant-attack/exponential-release smoothing is
  `Dsp.EnvelopeFollower.followInstantAttack`, the one asymmetric shape among
  the three envelope-follower consumers; see [Dsp](dsp.md).
- **NoiseGateNode** - a dynamics-based auto-gate: an attack/release envelope
  follower (`Dsp.EnvelopeFollower`) on the input level drives a smoothed gain
  that opens above `threshold` (dB) and closes below it - the same
  `EnvelopeFollower` instance and cached coefficients smooth both the level
  envelope and the gate's gain. Distinct from `Conditionals.GateNode`, which
  gates one signal based on a *separate* control input rather than its own
  level. Ports: `in`, `threshold`, `attack`, `release`.
- **VibratoNode** - a 100%-wet LFO-modulated delay line (no dry mix), unlike
  `ChorusNode`/`FlangerNode` - the pure delay modulation reads as pitch
  vibrato rather than comb filtering. Ports: `in`, `rate`, `depth`. Uses
  `Dsp.DelayLine`/`Dsp.LfoPhase`; its depth clamp was already symmetric
  (7.5ms center against a 7.5ms max swing) and needed no bug fix, only the
  refactor - see [Dsp](dsp.md).
- **ToneStackNode** - guitar-amp-style Bass/Mid/Treble tone shaping: three
  `BiquadStage`s (a private inner class - state plus cached coefficients,
  same caching-on-change discipline as `BiquadFilterNode`) chained in series
  - a low shelf at `BASS_FREQ=100`Hz, a peak at `MID_FREQ=650`Hz (`Q=0.5`,
  deliberately wide - the "scooped mids" character a guitar tone stack is
  known for), a high shelf at `TREBLE_FREQ=2500`Hz - each stage's gain set
  by its own port (`bass`/`mid`/`treble`, dB). The three RBJ `LOWSHELF`/
  `PEAKING`/`HIGHSHELF` formulas are the exact ones in
  `Filters.BiquadFilterNode.recomputeCoefficients`, duplicated locally
  rather than nesting three `BiquadFilterNode` instances (see
  `AutoWahNode`'s entry above for why nodes don't currently hold other nodes
  as internal implementation details). Explicitly **not** a modeled passive
  circuit - a real Fender/Marshall tone stack has bass/mid/treble
  cross-coupling (turning one knob measurably shifts the others) this does
  not reproduce, since each `BiquadStage` here is independent. Ports: `in`,
  `bass`, `mid`, `treble`.
- **ParametricEqNode** - N independent peaking bands, each with its own
  `freq`/`gain`/`q`, contrasted with `ToneStackNode`'s fixed guitar-amp
  frequencies. Same construction-time arity pattern as `TranceGateNode`/
  `Conditionals.SequencerNode`: constructor takes a band count,
  `buildPorts(bands)` emits `freq{i}`/`gain{i}`/`q{i}` per band, `getBands()`
  accessor for `PatchCodec`. `NodeFactory` registers three preset sizes
  (`Parametric EQ`/`Parametric EQ 1`/`Parametric EQ 5`, bands 3/1/5);
  `Gui.NodeCatalog`'s "Parametric EQ (Custom Size)..." entry prompts for any
  other band count and constructs one directly, unregistered - same
  treatment as [`SplitterNode`](utility.md)'s custom-size
  entry. Each band is its own `PeakingStage` (a local inner
  class holding just the RBJ `PEAKING` formula - not shared with
  `ToneStackNode`'s three-`Shape` `BiquadStage`, the same limited
  duplication `ChorusNode`/`FlangerNode` already tolerate between sibling
  `Effects` files rather than factoring out a shared base class). `freq{i}`
  is clamped to `[1, sampleRate*0.499]` per sample, the same Nyquist guard
  `BiquadFilterNode`/`AutoWahNode` use, since (unlike `ToneStackNode`'s fixed
  constants) it's a patchable, unbounded port. Ports: `in`, then per band
  `freq{i}`, `gain{i}`, `q{i}`.
- **GraphicEqNode** - a fixed 7-band graphic EQ (`100, 200, 400, 800, 1600,
  3200, 6400`Hz, Boss GE-7-style octave spacing, fixed `Q=1.4` for the
  standard slight inter-band overlap), gain-only per band. No constructor
  argument and no `PatchCodec` special case - unlike `TranceGateNode`/
  `ParametricEqNode`, the port list never varies, so it restores exactly as
  simply as `BiquadFilterNode`/`DistortionNode` (through the generic
  `NodeFactory.create(type)` fallback). Same `PeakingStage` shape as
  `ParametricEqNode`, but a fixed `PeakingStage[7]` field reading the
  constant frequency/`Q` table instead of patchable ports - only `gain0..
  gain6` are ports. Ports: `in`, `gain0`..`gain6`.
