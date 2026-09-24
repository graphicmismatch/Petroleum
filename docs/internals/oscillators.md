# Oscillators (`Petroleum.Oscillators`)

## `IOscillator`

```java
public interface IOscillator {
    float getSample(double phase, double dt);
    String getName();
}
```

`getSample(phase, dt)` returns one waveform sample for a given phase. By
convention, callers pass phase pre-wrapped to a single cycle in `[0, 2*PI)`
radians - implementations don't wrap it themselves (e.g. `Sawtooth` and
`Triangle` divide by `2*PI` and assume the result already lands in `[0, 1)`).
Implementations are free to hold internal state across calls (noise
generators, `Wavetable`, `SampleAndHold`, etc. all do).

`dt` is the current phase increment expressed as a fraction of a cycle
(`frequency / sampleRate`), computed once per output sample by
`OscillatorNode` from consecutive phase-port values (it has no other way to
learn the frequency - phase is the only thing that crosses the
`Oscillators`/`Graph` boundary, per governing convention 5) and passed down
unchanged. `dt` is a best-effort estimate, not a guarantee: `OscillatorNode`
only accepts a delta between `0` and half a cycle as plausible and falls
back to `0` otherwise (a paused/reversed phase, an external signal wired
into `phase` that isn't a monotonic ramp, or the very first sample after
construction). Implementations that band-limit a discontinuity (`Sawtooth`,
`Square`, `SuperSaw`) treat `dt <= 0` as "unknown - skip correction" rather
than dividing by it. `dt` must be computed exactly once, at the single
real per-sample call site (`OscillatorNode`), and threaded through
unchanged by anything that calls another oscillator's `getSample` more than
once per output sample (`DualOscillator`, `Wavefolder`) - inferring it
independently inside a stateful leaf oscillator instead breaks the moment
that instance is called from two different phases in the same sample, which
is exactly what `DualOscillator` does.

### Anti-aliasing (PolyBLEP)

`Sawtooth`, `Square`, and `SuperSaw` correct their hard discontinuities with
[polyBLEP](https://www.kvraudio.com/forum/viewtopic.php?t=375517)
(`Petroleum.Dsp.DspMath.polyBlep(t, dt)`, `t` and `dt` both normalized to
`[0, 1)` cycle units): a small polynomial correction subtracted/added for
the sample or two straddling an edge, approximating the ideal band-limited
step and cancelling most of the energy that would otherwise fold back below
Nyquist. It's applied only when `dt > 0` (see above). `Triangle` is left
naive - its discontinuity is in the slope, not the value, so it already
rolls off much faster (-12 dB/octave vs. -6 dB/octave) and aliases far less
audibly; band-limiting it properly needs the integrated-square (polyBLAMP)
technique, which doesn't retrofit onto the current closed-form
`4*|t-0.5|-1` implementation without risking a mis-scaled correction.
`Wavetable`/`DrawnWaveform` don't need it (already table-limited by their
content); `SampleAndHold`'s stepped jumps are the deliberate lo-fi
character of the node, not a defect to smooth away.

## `ModulatableOscillator`

```java
public interface ModulatableOscillator extends IOscillator {
    List<Port> getParameterPorts();
    void setParameter(int index, float value);
}
```

An optional extension for oscillators that have extra tunable parameters
beyond phase (FM's `modRatio`/`modIndex`, `DualOscillator`'s `offset`,
`Wavefolder`'s `gain`, `SampleAndHold`'s `stepsPerCycle`). `getSample(phase,
dt)` itself stays a fixed two-argument call per `IOscillator`'s contract -
these parameters are ordinary mutable fields on the implementation, set
directly (each has a plain getter/setter too, e.g. `FMOscillator.setModIndex`)
or in bulk via `setParameter(index, value)`, where `index` lines up
positionally with `getParameterPorts()`.

The point of this interface is that [`OscillatorNode`](#oscillatornode)
checks for it and, when present, turns each declared parameter into a real
graph input port - so these become ordinary wireable connections (a
`ConstantNode` for a fixed knob, or any other node's output for live
modulation) rather than a GUI-specific special case. See [Patch](patch.md)
and [Gui](gui.md).

## `WrapperOscillator`

```java
public interface WrapperOscillator extends ModulatableOscillator {
    IOscillator getBase();
    void setBase(IOscillator base);
}
```

A further optional extension, for oscillators that don't just have extra
parameters but wrap another `IOscillator` entirely, swappable at runtime -
`DualOscillator` and `Wavefolder` both implement it. Pulling `getBase()`/
`setBase(IOscillator)` out of those two classes into a shared interface
lets [Gui.NodeView](gui.md) detect "does this oscillator wrap a swappable
base?" with one `instanceof WrapperOscillator` check instead of two
separate `instanceof DualOscillator` / `instanceof Wavefolder` checks -
without changing either class's existing public API, since both already
had exactly this `getBase`/`setBase` shape beforehand.

## `OscillatorFactory`

A name -> `Supplier<IOscillator>` registry (`register` / `create` /
`names()`, the last used by [Gui.NodeCatalog](gui.md) to build the
node-creation menu without a hand-maintained parallel name list). Only
oscillators with a meaningful no-arg constructor are registered by default;
oscillators that require constructor arguments (`Wavetable`, `DualOscillator`,
`Wavefolder`) are meant to be constructed directly instead.

Registered names: `Sine`, `Cosine`, `Sawtooth`, `Square`, `Triangle`,
`White Noise`, `Pink Noise`, `Brown Noise`, `Blue Noise`, `Violet Noise`,
`Grey Noise`, `Sample & Hold`, `Chaotic`, `SuperSaw`, `FM`. `Square` is
registered with its no-arg constructor and so defaults to a 50% duty; the
`Square(double width)` constructor exists for building one directly.

## Implementations (`Petroleum.Oscillators.Implementations`)

### Basic waveforms
- **Sine** / **Cosine** - `Math.sin`/`Math.cos` of the raw phase.
- **Sawtooth** - linear ramp from -1 to 1 over the cycle, polyBLEP-corrected
  at the wrap (see [Anti-aliasing](#anti-aliasing-polyblep) above).
- **Square** - a pulse wave with a settable duty cycle, polyBLEP-corrected at
  both edges. `width` is a live
  `ModulatableOscillator` parameter (default `0.5`): the output is `+1` while
  the normalized phase is below `width` and `-1` above it, so `width` *is* the
  duty cycle. At `0.5` that is an ordinary square; sweeping it is PWM, and
  because it is a real graph port an LFO or envelope can drive it directly.

  Reformulating from the previous `sin(phase) >= 0` test changes exactly one
  sample of the default waveform, at phase exactly `PI`: `Math.sin(PI)` is a
  tiny *positive* double, so the old form returned `+1` there while `t < 0.5`
  returns `-1`. Every other phase is identical, and floating-point phase
  accumulation essentially never lands on `PI` exactly - but it is a real
  difference rather than a bit-identical refactor.

  `width` clamps to `[0, 1]`, so `0` is a constant `-1` and `1` a constant
  `+1` rather than anything undefined.
- **Triangle** - linear ramp up then down over the cycle.

### Composition
- **Wavetable** - looks up a sample from a user-supplied `float[]` table with
  linear interpolation between neighboring entries. `getSample(phase)` maps
  one full phase cycle across the *entire* table regardless of the table's
  length, so it always plays back as a single-cycle waveform - there's no
  concept of sample rate or one-shot playback here. Has two constructors:
  the original `Wavetable(float[] table)` (`getName()` returns the generic
  `"Wavetable"`), and an additive `Wavetable(float[] table, String name)`
  that overrides the display name - the "Sound Clip" Gui entry below always
  passes the fixed name `"Sound Clip"`, since a clip's content changes
  live (recording, trimming, importing) without ever becoming a new node,
  so there is no stable per-file name to show the way `GranularPlayerNode`
  shows an imported filename. The backing array is a private field with
  `getTable()`/`setTable(float[])` accessors, not a public field - table
  index wraparound in `getSample` (both the base sample index and its
  neighbor used for interpolation) uses `if (index >= table.length) index = 0;`
  guards rather than `%`, per the project's ring-buffer-indexing convention,
  with wrap semantics identical to the old modulo arithmetic.
- **WavetableLoader** (`Oscillators.Implementations.WavetableLoader`) - a
  static `loadMonoSamples(File)` helper that decodes a `.wav` file straight
  into the `float[]` a `Wavetable` wants: `AudioSystem.getAudioInputStream`
  to read the container, an explicit conversion to canonical 16-bit
  `PCM_SIGNED` (handles 24-bit and IEEE-float WAVs, which aren't already in
  that form), downmix to mono by averaging channels, normalize by
  `32768f`. Self-contained - deliberately has no dependency on
  `Audio.AudioEngine` even though both touch `javax.sound.sampled`, since
  `Audio` is a higher-level package built on top of `Patch`/`Graph`, above
  `Oscillators` in the dependency direction; taking a dependency the other
  way would invert that layering. This backs one half of the Gui's "Sound
  Clip" node (see [Gui](gui.md)): its "Import WAV..." button, inside
  `Gui.SoundClipEditorView`, calls `loadMonoSamples` and pushes the result
  into whichever track is active - never straight into `Wavetable`.
  Recording likewise lands in a track, not the node directly - `Wavetable`'s
  table is always the editor's live mixdown of every unmuted
  `Gui.SoundClipTrack`, recomputed and pushed with `setTable` after any
  track content change, not something a single import/record call writes
  to on its own anymore. The other half, recording, does not go through
  this class at all - `SoundClipEditorView` decodes its own live
  `TargetDataLine` capture directly, since `WavetableLoader` only reads
  finished files, not a live stream. Either way, whatever ends up in the
  table plays back with `Wavetable`'s existing single-cycle semantics: a
  3-second clip played through a `Phasor` at 440 Hz replays the entire
  buffer 440 times a second, not a sample-accurate one-shot trigger - a
  direct consequence of reusing `Wavetable` rather than building a separate
  sample-playback oscillator. A recording is captured at a fixed 44100 Hz;
  an imported file keeps whatever rate `loadMonoSamples` read it at. Neither
  rate is stored anywhere once the samples land in the table - `getSample`
  has never taken a sample rate, only a phase - so the only place either
  rate matters at all is `SoundClipEditorView`'s own Play button, which
  always assumes 44100 Hz for audition; a WAV recorded at a different rate
  will audition very slightly off-pitch there even though it's completely
  unaffected once wired into a patch through a `Phasor`. Nothing keeps a
  file path once imported: the decoded `float[]` is the only copy, and
  saving the patch writes the audio data itself (see
  [Serialization](serialization.md)).
- **DrawnWaveform** - the same table-lookup-with-lerp `getSample(phase)` as
  `Wavetable`, deliberately duplicated rather than inherited (a distinct
  concept - hand-drawn, not imported - kept as a flat, separate class per the
  "no unnecessary abstraction" project convention). Holds a fixed-size
  `float[DrawnWaveform.TABLE_SIZE]` table (512 samples) supplied at
  construction; unlike `Wavetable`'s table, this one is meant to be mutated
  in place after construction - `getTable()` returns the live backing array,
  and the Gui's waveform-drawing screen (`Gui.WaveformDrawView`, see
  [Gui](gui.md)) writes samples directly into it while the user drags the
  mouse, so playback updates live with no push-back-to-the-node step. The
  Gui's "Draw Waveform" node menu entry constructs one with an all-zero
  (silent) table, the drawing-screen equivalent of a blank canvas.
- **DualOscillator** - wraps a base oscillator and averages it with a
  phase-shifted copy of itself (constructor takes the base oscillator and the
  phase offset; the offset is a live `ModulatableOscillator` parameter named
  `offset`). The base itself is a `volatile IOscillator` field with
  `getBase()`/`setBase(IOscillator)` - swappable at any time, which is what
  lets [Gui.NodeView](gui.md) offer a live `ComboBox` for the base oscillator
  instead of a one-time construction-time choice.
- **Wavefolder** - wraps another oscillator, multiplies its output by a gain,
  then reflects (folds) any excursion outside `[-1, 1]` back into range,
  adding harmonics the harder it's driven (gain is a live
  `ModulatableOscillator` parameter named `gain`). Like `DualOscillator`, its
  base is a `volatile IOscillator` field with `getBase()`/`setBase(...)`.

### Noise colors
All of these extend the shared abstract **NoiseOscillator** base class, which
holds the `java.util.Random` and a `protected final float white()` helper
(one uniform sample in `[-1, 1]`) - each subclass only adds whatever filter
state it needs to shape the spectrum, generating raw white noise via
`white()` instead of each carrying its own duplicate `Random` field. Noise
has no fundamental frequency, so `OscillatorNode` gives every
`NoiseOscillator` a `gate` port in place of the usual `phase` port (`1` = pass
noise through, `0` = silence, default `1` so an unpatched noise node still
plays exactly as before) instead of a no-op phase input - each subclass's
filter state keeps updating every sample even while gated closed, so
reopening the gate never clicks:
- **WhiteNoise** - flat spectrum, uniform random samples in `[-1, 1]`.
- **PinkNoise** - ~1/f spectrum via Paul Kellet's refined pink-noise filter
  (seven feedback registers `b0`-`b6` low-pass-filtering white noise at
  different rates).
- **BrownNoise** (a.k.a. red/Brownian) - ~1/f^2 spectrum: white noise run
  through a leaky integrator (`brown = (lastOutput + 0.02*white) / 1.02`) so
  it can't wander off to +-infinity, then clamped to `[-1, 1]`.
- **BlueNoise** (azure) - ~f spectrum: first difference of white noise
  (`(white - lastWhite) * 0.5`).
- **VioletNoise** (purple) - ~f^2 spectrum: second difference of white noise
  (differentiated twice), clamped to `[-1, 1]`.
- **GreyNoise** - a rough approximation of perceptually "flat" noise: splits
  white noise into a low band (one-pole lowpass) and a complementary high
  band, then boosts both, leaving the middle - where the ear is most
  sensitive - relatively attenuated.

### Random / unusual oscillators
- **SampleAndHold** - the classic "random" oscillator: draws a fresh random
  value at each of `stepsPerCycle` evenly spaced points within a cycle and
  holds it, like a clocked sample-and-hold circuit (`stepsPerCycle` is a live
  `ModulatableOscillator` parameter named `steps`, rounded to the nearest
  int on each update). Resampling is driven by a step index
  `(int) (t * stepsPerCycle)` (`t` = phase / 2π), but that index alone is
  ambiguous at the default `stepsPerCycle == 1`: `t` in `[0, 1)` always maps
  to step `0`, so comparing only against the previous step's index would
  freeze the output after the first sample forever. `getSample` therefore
  also tracks `lastT` and treats a phase-wrap - the normalized phase falling
  back below where it was last call (`t < lastT`), or there being no previous
  call at all (`lastT` is `NaN`) - as an implicit new step, in addition to the
  step index actually changing. That gives one fresh random value per cycle
  when `stepsPerCycle == 1`, and leaves behavior at `stepsPerCycle >= 2`
  unchanged, since a step-index change there always happens before the next
  wrap is reached.
- **ChaoticOscillator** - a sine oscillator whose phase offset is nudged once
  per cycle by the logistic map `x -> r*x*(1-x)`. Near `r = 4` the map is
  chaotic: deterministic, but sensitive enough to its own state that the
  drifting phase sounds unpredictable. The once-per-cycle nudge fires on
  phase wraparound, detected as `lastPhase - phase > Math.PI` - a large
  backward jump, not merely `phase < lastPhase`, since any non-monotonic
  phase (a render restarting near zero, jitter from an upstream modulator)
  would otherwise misfire as a spurious extra logistic-map step; a genuine
  wrap from just under 2π back to just above 0 is always well over a π
  backward jump, so this still catches every real wrap.
- **SuperSaw** - a stack of detuned sawtooth waves (default 7 voices) summed
  and averaged, in the style of the classic "supersaw" patch. Detune is
  expressed as a fraction of a cycle spread evenly across the voices (a
  static phase offset per voice, not a per-voice frequency offset - cheaper,
  and at typical detune amounts the audible result is the same chorused
  thickness). Each voice gets its own polyBLEP correction at its own wrapped
  phase, all sharing the single `dt` passed in (every voice is the same
  underlying pitch, just phase-shifted).
- **FMOscillator** - two-operator frequency modulation: a sine carrier
  phase-modulated by a sine modulator running at `modRatio` times the
  carrier's rate, with modulation depth `modIndex`. Both are live
  `ModulatableOscillator` parameters (`mod ratio`, `mod index`). Both
  carrier and modulator are fixed internal sines here; see
  `Effects.FrequencyModulatorNode` (in [Effects](effects.md)) for the
  generic form with both exposed as ordinary patchable ports, the same
  relationship `RingModulatorNode`/`AmplitudeModulatorNode` have.

### Expression-driven
- **ExpressionOscillator** - wraps a [`MathExpression`](expression.md) so a
  waveform can be defined directly as a formula string, e.g.
  `"sin(x) + 0.3*sin(3*x)"` or `"2*abs(2*t - 1) - 1"` for a triangle wave.
  Needs a formula string at construction time, so like `Wavetable`,
  `DualOscillator`, and `Wavefolder` it isn't registered in
  `OscillatorFactory` - reachable from the GUI as the hand-written
  "Expression" [Gui.NodeCatalog](gui.md) entry instead, which places it
  immediately with a hardcoded default formula (`"sin(x)"`), no dialog. The
  formula itself isn't fixed at construction: `getSource()`/`setSource(...)`
  let it be replaced live (re-parsing into a fresh `MathExpression` each
  time), which is what lets [Gui.NodeView](gui.md) offer an inline, resizable
  text editor directly on the node instead of a one-time construction-time
  prompt - a `setSource` call that fails to parse throws
  `ExpressionException` and leaves the previous, still-valid expression in
  place rather than clobbering it, so a syntax error mid-edit can't silently
  break a patch that's already playing.

## `OscillatorNode`

Lives directly in this package (not `Implementations`, since it implements
the [Graph](graph.md) `Node` interface, not `IOscillator`) - it's the bridge
that lets any `IOscillator` sit in the graph. Not registered in
`NodeFactory`, since it needs an `IOscillator` instance at construction
time.

`getOscillator()` exposes the wrapped `IOscillator` so [Gui](gui.md) can
introspect what a placed `OscillatorNode` wraps - specifically, whether it's
a `DualOscillator`/`Wavefolder` that should get a live base-oscillator
`ComboBox`.

Its input port list isn't fixed: `phase` is always port 0, and if the
wrapped oscillator implements `ModulatableOscillator`, its
`getParameterPorts()` are appended after `phase` - so an `OscillatorNode`
wrapping a plain `Sine` has one input port, but one wrapping an
`FMOscillator` has three (`phase`, `mod ratio`, `mod index`). `process()`
takes a block of `frameCount` samples at once (`inputs`/`outputs` are
`float[port][sample]`); for each sample position it pushes every port after
`phase` into the oscillator via `setParameter` before calling `getSample` -
so these parameters are ordinary graph connections like any other node's
inputs, not a special case the [Gui](gui.md) or [Patch](patch.md) packages
need to know about, and per-sample parameter modulation still works exactly
as before, just resolved one sample at a time within the block.

Per convention 3, `setParameter` is only actually called when a parameter's
value has changed since the previous sample: a `lastParams` array, one slot
per parameter port, sized once at construction and seeded with `NaN` (so the
very first sample always differs and always applies), is compared against
each incoming sample before the call. A steady knob - the overwhelmingly
common case, since a parameter only moves during an active modulation or a
live GUI drag - costs one float comparison per sample instead of a call into
the wrapped oscillator's setter every time. This is the same cache-and-compare
idiom [Dsp](dsp.md) and the filter/effect nodes use for their own coefficients,
applied at the `OscillatorNode` boundary instead of inside each
`ModulatableOscillator`, since all five implementations
(`FMOscillator`, `Square`, `Wavefolder`, `DualOscillator`, `SampleAndHold`)
share the same "did this one float change" question and would otherwise each
answer it separately. Each implementation's `setParameter` silently ignores
an out-of-range `index` rather than throwing: the index always comes from
`OscillatorNode`'s own loop over exactly the ports `getParameterPorts()`
declared, so an out-of-range value cannot occur in practice, and a `throw`
reachable only by a bug that cannot currently happen has no business sitting
in the audio thread's hot path.
