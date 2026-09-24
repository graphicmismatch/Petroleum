# Dsp (`Petroleum.Dsp`)

Pure-DSP helper classes shared across `Filters`, `Effects`, `Envelopes`,
`Conditionals`, `Sources`, and `Granular` - a sibling of `Petroleum.Graph`, not
`Node` implementations themselves. These classes hold no
`Port`/`process(inputs, outputs, context)` surface; they exist purely to
de-duplicate math and per-sample state that several `Node`s independently
reimplemented (delay-line interpolation, LFO phase accumulation, envelope
smoothing, one-pole coefficient math, rising/falling-edge detection, grain
scheduling and mixing).

- **DelayLine** - a circular float buffer with fractional-sample read and
  linear interpolation, factored out of `Effects.DelayNode`,
  `Effects.ChorusNode`, `Effects.FlangerNode`, `Effects.VibratoNode`, and
  `Filters.CombFilterNode`, which all copy-pasted the same buffer/wrap/
  interpolate pattern.
  - `DelayLine(int capacity)` allocates the backing buffer (`capacity`
    clamped to at least 1). Callers still decide the capacity themselves
    (`sampleRate * maxDelaySeconds`, ceil'd) and still lazily construct the
    `DelayLine` on the node's first `process()` call, once `context.sampleRate`
    is known - the class itself doesn't know about `NodeContext`.
  - `float readFractional(double delaySamples)` first clamps `delaySamples`
    to `[0, capacity - 1]` - this is a defensive second layer, independent of
    whatever clamping the caller does on its own knob inputs (see the Chorus/
    Flanger bug below). It then computes `readPos = writeIndex - delaySamples`
    and wraps it into range in **both** directions:
    `if (readPos < 0) readPos += capacity;` and
    `if (readPos >= capacity) readPos -= capacity;`. `i0 = (int) readPos`,
    `i1 = i0 + 1` with an if-wrap back to `0` when `i1 >= capacity`, and the
    two samples are linearly interpolated by `frac = readPos - floor(readPos)`
    via `DspMath.lerp`.
  - `void write(float sample)` stores at `writeIndex` and advances it with an
    if-wrap (`if (++writeIndex >= capacity) writeIndex = 0;`), never `%`, per
    the project's ring-buffer convention.
  - **Read-then-write ordering contract**: every caller calls
    `readFractional` *before* `write` in the same `process()` call, exactly
    matching the order the original inlined code used. This matters because
    several callers write `input + feedback * delayed` - `delayed` must come
    from the buffer's state *before* the current sample is written into it,
    or the node would read its own new sample as part of the delay.
  - `int capacity()` exposes the backing array's length - added for
    `Granular.GranularFreezeNode`, which needs to know how much recorded
    history is available (see `granular.md`) but has no other reason to reach
    past `DelayLine`'s existing read/write surface; every prior caller only
    ever needed `readFractional`/`write` and is unaffected.

- **LfoPhase** - the free-running phase accumulator duplicated in
  `Effects.TremoloNode`, `Effects.RingModulatorNode`, `Effects.PhaserNode`,
  `Effects.ChorusNode`, `Effects.FlangerNode`, and `Effects.VibratoNode`.
  `double next(double freqHz, double sampleRate)` advances
  `phase += 2*PI*freqHz/sampleRate` and wraps with `if (phase > 2*PI) phase -=
  2*PI;` (strictly `>`, not `>=`, to stay bit-identical with the code it
  replaced), then returns the new phase. One `LfoPhase` instance per
  modulation source - `RingModulatorNode`'s carrier and `ChorusNode`/
  `FlangerNode`/`VibratoNode`'s modulation LFO are all just separately-named
  `LfoPhase` fields.

- **EnvelopeFollower** - the attack/release exponential smoother duplicated
  in `Effects.CompressorNode`, `Effects.LimiterNode`, and
  `Effects.NoiseGateNode`. The three nodes don't all shape their envelope the
  same way, so this exposes two coefficient-update methods and two smoothing
  methods rather than forcing one shape:
  - `updateCoefficients(double attackMs, double releaseMs, double sampleRate)`
    recomputes `attackCoeff`/`releaseCoeff` via a shared private
    `coeff(timeMs, sampleRate) = exp(-1 / (sampleRate * timeMs / 1000))` only
    when `attackMs`, `releaseMs`, or `sampleRate` differ from the cached
    values (mirroring the `cachedAttackMs`/`cachedReleaseMs`/
    `cachedSampleRate` pattern the nodes used inline before the refactor).
    Used by `CompressorNode` and `NoiseGateNode`, both of which have real
    attack *and* release stages.
  - `updateReleaseCoefficient(double releaseMs, double sampleRate)` is the
    release-only counterpart, calling the same `coeff` helper, used by
    `LimiterNode`, which has no attack coefficient at all (see below).
  - `followAttackRelease(float currentValue, float targetValue)` implements
    the `coeff = targetValue > currentValue ? attackCoeff : releaseCoeff;
    return coeff*currentValue + (1-coeff)*targetValue;` shape.
    `CompressorNode` calls it once per sample for its level envelope;
    `NoiseGateNode` calls it *twice* per sample with the same
    `EnvelopeFollower` instance and its cached coefficients - once to smooth
    the input-level envelope, once to smooth the gate's open/close gain
    toward its 0/1 target - exactly reproducing the original code's reuse of
    one attack/release coefficient pair for two different smoothed
    quantities.
  - `followInstantAttack(float currentValue, float targetValue)` implements
    `LimiterNode`'s asymmetric shape: the envelope jumps to `targetValue`
    immediately when it's above the current envelope (instantaneous attack,
    no coefficient involved), and only the downward direction is smoothed
    with `releaseCoeff`.
  - `getAttackCoeff()`/`getReleaseCoeff()` are exposed for completeness but
    none of the three current nodes need them directly - they only ever go
    through the `follow*` methods.

- **DspMath** - static one-pole cutoff-to-alpha helpers, factored out of
  `Filters.OnePoleFilterNode`, `Filters.OnePoleHighpassNode`, and
  `Filters.LadderFilterNode` (which reuses the lowpass formula for each of
  its four cascaded stages). Each node still owns and compares its own
  `cachedCutoff`/`cachedSampleRate` - only the coefficient formula itself
  moved.
  - `static float onePoleLowpassAlpha(float cutoffHz, double sampleRate)` -
    `rc = 1/(2*PI*cutoffHz)`, `dt = 1/sampleRate`, returns `dt/(rc+dt)`.
  - `static float onePoleHighpassAlpha(float cutoffHz, double sampleRate)` -
    same `rc`/`dt`, returns `rc/(rc+dt)`.
  - `static float lerp(float a, float b, float t)` - `a*(1-t) + b*t`, the
    linear interpolation both `DelayLine.readFractional` and
    `Oscillators.Implementations.Wavetable.getSample` compute over their two
    neighboring samples; each still owns its own index/fraction math; only
    the blend itself is shared.

- **EdgeDetector** - the rising/falling-edge check that
  [convention 8](conventions.md) requires of every trigger-consuming node, factored out of
  `Envelopes.EnvelopeNode`, `Envelopes.RampNode`, `Envelopes.AdsrNode`,
  `Envelopes.ArpeggioNode`, `Conditionals.LatchNode`,
  `Conditionals.CounterNode` (two instances - `trigger` and `reset` are
  independent edges), `Conditionals.SequencerNode` (also two instances), and
  `Sources.RandomNode`. One `EdgeDetector` instance per tracked signal,
  allocated once at construction - `int edge(float value)` returns `+1` on a
  rising edge (`previous <= 0 && value > 0`), `-1` on a falling edge
  (`previous > 0 && value <= 0`), `0` otherwise, and updates `previous`
  unconditionally on every call regardless of which branch fired. Nodes that
  only care about the rising edge test `edge(v) > 0`; `AdsrNode` is the one
  consumer that reads both signs from a single call, since its gate drives
  attack on rising and release on falling.

  `Conditionals.ToggleNode` is deliberately **not** a consumer: it detects a
  crossing against a settable `threshold` port (`trigger > threshold &&
  lastTrigger <= threshold`) rather than a fixed edge at `0`, a different
  convention this refactor left alone rather than folding two conventions
  into one detector.

- **GrainPool** - a fixed-size, allocation-free pool of simultaneously-active
  granular-synthesis "grains," shared by all four `Granular` nodes -
  `GranularPlayerNode`, `GranularFreezeNode`, `GranularDelayNode`, and
  `GranularCloudNode` (see `granular.md` for all four). Unlike every
  other `Dsp` class, this one manages genuine polyphony - up to
  `DEFAULT_MAX_GRAINS` (32) overlapping grains at once - so it's the first
  class in this package to hold per-voice state, laid out as parallel
  primitive arrays (`position`/`increment`/`age`/`duration`/`amp`/`active`,
  plus `filterState`/`filterAlpha` for the per-grain filter extension below,
  each sized once in the constructor) rather than an array of small grain
  objects, keeping convention 2 (no allocation in `process()`) trivially true
  by construction.
  - **`Source`** is the one-method interface (`float read(double
    positionInSamples)`, plus a defaulted `length()`) that lets one pool
    implementation serve two unrelated buffer shapes: `GranularPlayerNode`'s
    fixed, fully-loaded `float[]` (indexed by absolute position, like
    `Oscillators.Implementations.Wavetable.getSample`) and
    `GranularFreezeNode`'s live `DelayLine` (indexed by a monotonically
    increasing elapsed-sample count that never wraps). `GrainPool` never
    clamps, wraps, or interprets `positionInSamples` itself - that coordinate
    space is entirely the `Source`'s business, which is what makes one grain
    engine usable against a static sample and a live recording alike.
  - **`float process(Source, sampleRate, positionSamples, positionJitter,
    grainSizeSeconds, grainSizeJitter, density, pitch, pitchJitter, amp,
    trigger)`** is the single per-sample entry point, called once per sample
    from each consuming node's own `process()` loop with nothing but
    primitives - no parameter object, no boxing.
  - **Spawning**: a free-running `spawnPhase` accumulator (`spawnPhase +=
    density/sampleRate`, single `if`-wrap, not a defensive `while` - the same
    single-wrap idiom `Sources.PhasorNode` uses, since a `density` high enough
    to overshoot a full period in one sample is already degenerate territory)
    spawns a grain every time it wraps. A `trigger` port, run through its own
    `EdgeDetector`, spawns an *additional* grain on every rising edge - so a
    `TriggerNode`/`SequencerNode` can drive grains directly, independent of
    (or with `density = 0`, instead of) the free-running clock.
  - **Window**: each active grain's amplitude follows a raised-cosine (Hann)
    envelope, `0.5 - 0.5*cos(2*pi*age/duration)`, recomputed from `age` every
    sample. This is not a convention-3 violation (cache coefficients when
    inputs are unchanged) - `age` is not a driving input that goes stale, it
    changes every single sample for every active grain by definition, so
    there is nothing stable to cache. A future table-lookup window is a
    possible optimization if profiling ever shows the per-sample `cos` calls
    matter, in the same "revisit if profiling ever shows it matters" spirit
    as this file's own denormal note below - not attempted here.
  - **Jitter** (position/grain-size/pitch, each a `0..1` spread fraction) is
    drawn once per grain, at spawn, from the pool's own `Random` (unseeded,
    matching `Sources.RandomNode`/`Oscillators.Implementations.SampleAndHold`)
    - never re-rolled mid-grain. Position jitter is scaled by the grain's own
    *already-jittered* size in samples rather than a separate seconds knob,
    so all three jitter ports share one "0..1 fraction" meaning.
  - **Voice stealing**: when every slot is busy at spawn time, the single
    *oldest* active grain is reused (linear scan for max `age` - no free
    list, appropriate for a pool this small). This is deliberately the
    simplest possible policy (a total order, no tie-breaking) and also the
    least audible one: a Hann window means the oldest grain is already
    closest to the silent tail of its envelope, so stealing it discards the
    least energy of any candidate - stealing the newest grain, by contrast,
    would routinely cut one still ramping up through its attack half.
  - **No internal gain normalization** - `process()` returns the raw sum of
    every active grain's windowed, amplitude-scaled sample, unlike
    `Effects.ReverbNode`'s `combSum /= combs.length`. Reverb's four combs are
    always all four active; a grain pool's active count is inherently
    dynamic and *is* the `density` knob's entire effect - dividing by it
    would fight the parameter and pump audibly on every spawn/expiry. Two
    correctly-phased Hann windows already sum to a constant envelope at the
    classic 50%-overlap setting (`density * grainSizeSeconds ≈ 2`), so
    sensible default settings are self-leveling without `process()` doing
    anything extra; a caller's own `amp` port is the intended level control,
    same as every other node controls its own output (chain a `LimiterNode`
    for a hard ceiling, as anywhere else in the graph).
  - **No reverse-direction grains in v1**: `pitch` is clamped to a positive
    floor (`MIN_PITCH_MAGNITUDE`) only, the same shape as the grain-size
    floor. `Source.read` would already tolerate a position moving either
    direction, so negative pitch is a small, self-contained future addition,
    not a design dead-end.
  - **Per-grain filtering (the 13-argument `process(...)` overload)**:
    `GranularCloudNode` needed each grain individually low-pass filtered at
    its own randomized cutoff, something no amount of patching a filter node
    *downstream* of a granular node can do (a downstream filter shapes the
    already-mixed sum of every active grain, not each one independently).
    Added as a second `process(...)` overload taking two extra trailing
    parameters, `filterCutoffHz`/`filterCutoffJitter` - the original
    11-argument signature is now a thin delegate to the 13-argument one with
    `filterCutoffHz = 0f`, so every existing caller (`GranularPlayerNode`,
    `GranularFreezeNode`) needed zero changes and produces bit-identical
    output, verified directly (not just reasoned about): a throwaway smoke
    test drove one `GrainPool` through both overloads with matching inputs
    and diffed every sample, `maxDiff == 0.0`. That exactness is a property
    of the math, not a coincidence - see below.
    - **Cutoff jitter, cached alpha, one-pole state**: at spawn, if
      `filterCutoffHz <= 0`, `filterAlpha[slot] = 1` exactly (the disabled
      case); otherwise a jittered cutoff (same "jitter once, hold for the
      grain's life" idiom as `pitch`/`position`/`grainSize`) is converted
      once via `Dsp.DspMath.onePoleLowpassAlpha` into `filterAlpha[slot]` -
      never recomputed per sample, per convention 3. `filterState[slot]`
      (the one-pole `y[n-1]`) resets to `0` at spawn. Per sample, every
      active grain runs `filterState[i] += filterAlpha[i] * (raw -
      filterState[i])` before windowing and summing - applied
      *unconditionally*, with no `if (filtering enabled)` branch, because
      `filterAlpha[i] = 1` makes that same line algebraically collapse to
      `filterState[i] = raw` every sample: exact pass-through, not an
      approximation of one. This is why the legacy overload's output is
      bit-identical rather than merely close.
    - **Why `filterCutoffHz <= 0` can't just be handed to
      `onePoleLowpassAlpha` directly**: that formula computes `rc = 1 / (2 *
      pi * cutoffHz)`, which is a division by zero at `cutoffHz = 0` and
      produces `alpha = 0` (a filter that never updates - frozen at its
      initial state, the *opposite* of pass-through) rather than `alpha = 1`.
      The explicit `filterCutoffHz <= 0` branch exists specifically to avoid
      ever calling the formula with a value it isn't defined for.

- **LiveGrainRecorder** - the shared `GrainPool.Source` for every
  live-input `Granular` node (`GranularFreezeNode`, `GranularDelayNode`,
  `GranularCloudNode`; `GranularPlayerNode`'s static-buffer `Source` is a
  private nested class local to that one node instead, since nothing else
  needs it). Wraps a lazily-allocated `DelayLine` (`ensureAllocated(sampleRate)`,
  called at the top of each consuming node's `process()`, same
  first-call-once-sample-rate-is-known pattern as everywhere else in this
  file) with a `long totalWritten` counter and a `write(float)` method that
  advances both together - the exact lockstep `GranularFreezeNode` used to
  hand-roll itself before this was a second caller's problem too.
  `positionBehind(secondsBehind, sampleRate)` is the one formula every
  live-input node needs (`totalWritten - secondsBehind * sampleRate`),
  factored out once rather than reproduced at each of the three call sites.
  `read(double)`/`length()` implement `Source` exactly as
  `GranularFreezeNode`'s original private `RingBufferSource` did - this
  extraction is a pure move, not a redesign, verified by rendering
  `GranularFreezeNode`'s existing burst-then-freeze smoke-test scenario
  before and after the refactor and confirming matching output.

- **HilbertTransformer** - a pair of cascaded first-order allpass IIR chains
  that split an input signal into an in-phase and a quadrature (~90°-shifted)
  component across the full audio band, the classic building block behind
  single-sideband frequency shifting (`Effects.FrequencyShifterNode`; see
  [Effects](effects.md)). Coefficients are the widely-published Bernie
  Hutchins/Olli Niemitalo allpass set (four stages per chain, raw values
  `{0.6923878, 0.9360654322959, 0.9882295226860, 0.9987488452737}` and
  `{0.4021921162426, 0.8561710882420, 0.9722909545651, 0.9952884791278}`, the
  same set reproduced verbatim in numerous open-source Hilbert-transform
  implementations) - cached as `private static final float[]` arrays holding
  each raw coefficient's *square*, since every stage's per-sample multiply
  works directly against `coeff = a*a`.
  - **The two-sample-memory allpass form**: each stage is *not* the usual
    one-`z^-1` first-order allpass; the derivation (`H(z) = (a^2 - z^-2) /
    (1 - a^2 z^-2)`) has a `z^-2` denominator, giving the one-multiply
    recurrence `out(t) = a^2*(in(t) + out(t-2)) - in(t-2)` - two samples of
    memory, not one. Implemented per stage as a 2-slot history for both the
    stage's input and output (`float[2]`, indexed by a `parity` bit that
    flips every call), rather than the single running state a plain
    one-`z^-1` allpass would need - this is what the class docstring's "one
    running state value per stage" simplification (from the original design
    sketch) turned out not to be sufficient for; the 2-slot form is still
    fixed-size and allocation-free per sample, just twice the state.
  - **The extra one-sample delay**: the in-phase chain (the `0.402...`
    coefficient set) is used as-is; the quadrature chain (the `0.692...`
    set) needs one *additional* plain sample of delay appended after its own
    four allpass stages before the two chains are ~90° apart at every
    frequency from 20Hz to 20kHz (verified empirically - without the extra
    delay, the measured phase difference drifts from about 2° at 1kHz up to
    68° at 18kHz; with it, every frequency from 50Hz to 20kHz lands within
    about 1° of 90°). This single delay is one more `float` field
    (`quadratureDelay`), updated after each `process()` call - not a full
    `Dsp.DelayLine`, since the delay is always exactly one sample.
  - **`void process(float input, float[] outInPhaseAndQuadrature)`** writes
    exactly two floats (`[0]` in-phase, `[1]` quadrature) into a
    caller-provided, reused array, rather than allocating or returning a new
    array each call - the caller (`FrequencyShifterNode`) owns one `float[2]`
    field for the lifetime of the node.
  - Measured single-sideband rejection with this coefficient set and
    architecture is about 55dB (the unwanted mirror sideband sits roughly
    550 times quieter than the wanted one) for a pure sine test input -
    consistent with the design's published performance, and easily good
    enough that the unwanted sideband is inaudible under normal use.

- **PitchQuantizer** - the `Scale` enum (`CHROMATIC`, `MAJOR`, `MINOR`,
  `MAJOR_PENTATONIC`, `MINOR_PENTATONIC`, each a semitone-interval array and
  a `label()`) and the "snap a Hz value to the nearest note of a scale" math,
  moved here from `Utility.QuantizerNode` (see `utility.md`) so
  `Spectral.PitchCorrectionNode` could reuse it too. This is the first time
  two different node packages have needed the same scale-snapping logic, and
  every other `Node` in the codebase only ever depends on `Graph`/`Dsp`,
  never on another concrete `Node` class - so rather than
  `PitchCorrectionNode` importing `QuantizerNode` directly (which would also
  have required making its `private quantize`/`hzToSemitone`/`semitoneToHz`
  public), the math moved to `Dsp` where both nodes can call it as ordinary
  static methods. `QuantizerNode` itself is now a thin wrapper: it still owns
  the `Scale` field and the `in`/`root` ports, and calls
  `PitchQuantizer.quantize(scale, in, root)` once per sample (cached exactly
  as before - see `utility.md`).

  **Bug found and fixed during the move: wrong octave anchors for restricted
  scales.** The original candidate search built its three octave anchors as
  `floor(pitch) - 12`, `floor(pitch)`, `floor(pitch) + 12` - each spaced a
  correct 12 semitones apart, but not themselves multiples of 12. Mod 12,
  `floor(pitch) - 12` reduces to `floor(pitch) mod 12`, not `0`, so the
  achievable candidate pitch classes (`anchor + rootClass + interval`) were
  silently shifted by the *input's own* pitch class instead of staying fixed
  by `root` and `scale` alone. For `Scale.CHROMATIC` this is invisible (its
  interval set is all 12 residues, so the shift lands on another value in
  the same complete set), which is almost certainly why it went unnoticed -
  but for every restricted scale (`MAJOR`, `MINOR`, both pentatonics) it
  meant `quantize` could return a note **outside the requested scale**,
  depending on where the input pitch happened to sit. Measured with a sweep
  across five octaves in semitone steps against `Scale.MAJOR`: 104 of the
  inputs tested produced an out-of-scale result - specifically, any input
  already sitting near a non-major pitch class (C#/D#/F#/G#/A#) passed
  through essentially unchanged instead of snapping to the nearest major
  note, exactly backwards from what a quantizer should do. The fix anchors
  the octave loop to true multiples of 12 instead -
  `12 * (floor(pitch / 12) - 1)` - so the candidate pitch-class set is fixed
  by `rootClass + interval` alone, independent of the input. The same sweep
  now measures zero out-of-scale results. This changes `QuantizerNode`
  Major/Minor/(both) Pentatonic's output for inputs that previously hit the
  bug - existing patches using those variants will hear a difference (a
  correctness fix, not a behavior preference); `Quantizer Chromatic` is
  unaffected, since the bug never had a visible effect there.

- **PitchDetector** - a monophonic fundamental-frequency (F0) tracker using
  the YIN algorithm (de Cheveigné & Kawahara), the one genuinely new piece
  `Spectral.PitchCorrectionNode` needed - nothing else in the codebase
  estimates a frequency *from* an audio signal (every other pitch-carrying
  port is a value the patch already knows, like `PhasorNode.frequency` or
  `TrackerNode`'s `pitchN`).
  - Sized from `sampleRate` and a fixed `minFrequencyHz` floor passed to the
    constructor: `tauMax = ceil(sampleRate / minFrequencyHz)`, backed by a
    `2*tauMax`-sample ring buffer. `PitchCorrectionNode` constructs it lazily
    on its first `process()` call, once `context.sampleRate` is known -
    exactly the same guarded-`if (x == null)` pattern `Effects.DelayNode`
    already uses for its `DelayLine`, so "never allocate in the hot path"
    holds the same way.
  - `void pushSample(float sample)` writes into the ring buffer (if-wrap, no
    `%`, per convention) and, every fixed `HOP_SIZE` (512) samples, copies
    the ring into a preallocated linear scratch buffer (same reason
    `StftEngine.processHop` linearizes its own ring buffer before an FFT -
    the difference function below needs ordinary indexing, not wraparound
    arithmetic) and runs one YIN analysis pass, entirely decoupled from
    `StftEngine`'s own window/hop - the detector reads raw time-domain
    samples directly, which the STFT engine's magnitude/phase frames can't
    provide.
  - The YIN pass: a difference function `d(tau) = sum (x[j]-x[j+tau])^2` for
    `tau` in `[1, tauMax)` over a `tauMax`-sample window (`O(tauMax^2)` per
    hop - negligible at audio rates for this frequency range, and what
    reference YIN implementations do by default; not worth an FFT-based
    autocorrelation trick here), cumulative-mean-normalized into `cmnd(tau)`,
    searched for the first dip below a fixed `ABSOLUTE_THRESHOLD` (0.15) -
    this is the whole point of YIN over plain autocorrelation, since plain
    autocorrelation's raw peak-picking is prone to snapping to the wrong
    (usually double or half) period - then refined to a fractional period by
    parabolic interpolation across the three `cmnd` values straddling the
    chosen `tau`.
  - Before running the difference function at all, the analysis window's
    energy is checked against a fixed `ENERGY_THRESHOLD` (`1e-6`); below it,
    the call reports `isVoiced() == false` immediately. Without this check,
    silence (all-zero samples) makes the difference function trivially zero
    everywhere, `cmnd` collapses to `0`, and the detector would report a
    perfect, maximally-confident pitch on pure silence - a degenerate case
    worth guarding explicitly rather than trusting the confidence score
    alone to catch it.
  - If no dip is found below the threshold, `isVoiced()` reports `false`
    (falling back to nothing, rather than the global minimum `cmnd` value,
    keeps `PitchCorrectionNode` from correcting onto a low-confidence guess).
    `getFrequencyHz()`/`getConfidence()` otherwise return the values from the
    most recent successful hop between analyses - the caller reads them
    every sample, not just once per hop.
  - **Known, accepted limitation**: like any monophonic pitch tracker,
    strongly inharmonic or heavily chorused/reverberant input can still make
    YIN lock onto the wrong octave - not solved further here, same as the
    bin-quantization limitation already accepted for
    `PhaseVocoderPitchShiftNode` in `spectral.md`.

## Bug fixed by this refactor: negative Chorus/Flanger delay

`ChorusNode`'s modulated delay is `delayMs = CENTER_DELAY_MS + depthMs *
sin(lfoPhase)`, and `depthMs` was clamped to `[0, MAX_DELAY_MS -
CENTER_DELAY_MS]` = `[0, 35]` while `CENTER_DELAY_MS` is only `15`. With
`depthMs` near its old maximum, `delayMs` could swing down to `15 - 35 =
-20`, producing a negative `delaySamples`. In the old inlined read code that
only wrapped the *negative* direction (`if (readPos < 0) readPos +=
length;`), a large-magnitude negative `delaySamples` pushed `readPos` past
`length` after the single wrap, and the unguarded `buffer[i0]`/`buffer[i1]`
access threw `ArrayIndexOutOfBoundsException`. `FlangerNode` had the
identical shape: `depthMs` clamped to `[0, MAX_DELAY_MS - CENTER_DELAY_MS]` =
`[0, 10]` against a `CENTER_DELAY_MS` of only `5`, so `delayMs` could reach
`5 - 10 = -5`.

Fix: the depth clamp's upper bound is now
`Math.min(CENTER_DELAY_MS, MAX_DELAY_MS - CENTER_DELAY_MS)` in both nodes -
`15` for Chorus (unchanged from its old symmetric-looking-but-wasn't bound,
now actually enforced) and `5` for Flanger (down from `10`) - which keeps
`delayMs` non-negative for any LFO phase. `VibratoNode` was already
symmetric (`CENTER_DELAY_MS = 7.5`, `MAX_DELAY_MS - CENTER_DELAY_MS = 7.5`,
`min(7.5, 7.5) = 7.5`) and needed no change; verified rather than "fixed."

`DelayLine.readFractional`'s own `[0, capacity - 1]` clamp and its added
upper-direction wrap are the second, defensive layer described above: even
if some future caller reintroduces a similar out-of-range `delaySamples` bug
upstream, `DelayLine` itself can no longer be walked out of bounds.

## Deliberate non-change: no denormal flush-to-zero

None of the `Dsp` helpers (or the filter/effect nodes that use them) flush
denormalized floats to zero. One-pole and comb/delay feedback loops can decay
into the denormal range during a silence tail, which is measurably slower on
some CPUs than normal float arithmetic. This is a known, deliberate
non-change for now: it's a performance concern, not a correctness one (no
crashes, no audible artifacts, just a possible tail of drastically-slower
per-sample math on already-near-silent audio), and adding flush-to-zero
handling (e.g. `if (Math.abs(x) < 1e-15f) x = 0f;` in every feedback path)
means touching every recursive filter/effect node's hot path for a cost this
project hasn't profiled. Revisit if profiling ever shows it matters.
