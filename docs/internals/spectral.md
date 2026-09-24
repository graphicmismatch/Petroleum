# Spectral (`Petroleum.Spectral`)

Frequency-domain infrastructure - a sibling of `Petroleum.Graph`, not a
`Node`-implementation package itself (same relationship `Petroleum.Dsp` has to
the rest of the codebase, and the same "new family -> new sibling package"
call as `Granular`). Nothing in this package has a `Port` or a `process(
inputs, outputs, frameCount, context)` method; concrete spectral effect nodes
(spectral freeze, spectral gate/blur, phase-vocoder pitch shift - a later
addition) will live in their own node packages and *drive* `StftEngine`, the
same way `Granular.GranularPlayerNode`/`GranularFreezeNode` drive
`Dsp.GrainPool` rather than reimplementing grain scheduling themselves.

This package depends on `Dsp` (specifically `Dsp.Fft`, described below) and
nothing else.

## `Dsp.Fft`

`Dsp.Fft` itself lives in `Petroleum.Dsp`, not `Petroleum.Spectral` - it's a
general-purpose numeric primitive with no audio-specific meaning, exactly
like `DelayLine` or `EnvelopeFollower`, so it belongs alongside them rather
than in this package. It's documented here (not `dsp.md`) because
`StftEngine` is its only consumer so far and the two are easiest to
understand together; `dsp.md` cross-references this section rather than
duplicating it.

An in-place, iterative radix-2 Cooley-Tukey FFT over preallocated `float[]`
real/imaginary arrays. No external FFT library is used: the project hand-rolls
everything outside the JavaFX dependency set, its own JSON parser being the
standing precedent, and an FFT is no exception.

- `Fft(int size)` - `size` must be a power of 2 (`IllegalArgumentException`
  otherwise). The constructor precomputes and caches two tables as instance
  fields, per convention 3 (cache expensive coefficients - nothing in
  `forward`/`inverse` may call `Math.sin`/`Math.cos`):
  - `cosTable`/`sinTable`, each of length `size/2`, holding
    `cos(2*pi*k/size)`/`sin(2*pi*k/size)` for `k` in `[0, size/2)` - the full
    set of twiddle factors any stage of the transform needs, indexed by a
    per-stage step (`tableStep = size/len`) rather than recomputed per stage.
  - `bitReversal`, length `size`, the bit-reversal permutation table used to
    reorder samples in place before the butterfly stages run.
- `void forward(float[] re, float[] im)` / `void inverse(float[] re, float[]
  im)` - both in-place, both `IllegalArgumentException` if either array's
  length isn't exactly `size`. Neither allocates: the bit-reversal reorder is
  done with in-place index swaps (`if (i < j) swap(...)`, using the
  precomputed `bitReversal` table, so each pair swaps exactly once), and the
  butterfly stages read only `cosTable`/`sinTable`/loop indices.
- **Sign/normalization convention**: `forward` computes the standard
  unnormalized DFT, `X[k] = sum_n x[n] * exp(-2*pi*i*k*n/N)`. `inverse`
  applies the conjugate-and-forward trick (negate the imaginary part, run the
  same `forward` butterfly network, negate imaginary again, divide both real
  and imaginary parts by `size`) rather than a second hand-written butterfly
  network - this halves the amount of trigonometric-butterfly code to get
  right, at the cost of two full passes over the array to flip signs, which
  is negligible next to the `O(N log N)` transform itself. **`inverse`
  normalizes internally** - a caller never divides by `size` itself, and
  `forward` followed by `inverse` on the same data reproduces the original
  signal directly (verified to ~`3e-7` max absolute error at `size=1024`,
  i.e. float32 epsilon, in the smoke test described below).

## `StftEngine`

The shared, allocation-free STFT analysis / ISTFT overlap-add machinery a
concrete spectral effect node builds on. Not a `Node` - no `Port`s, no
`process()`. Everything is preallocated in the constructor from a window size
`N` and a hop size `H`; `pushSample`/`popSample`/the `processFrame` hook that
runs once per hop allocate nothing.

### Construction

`StftEngine(int windowSize, int hopSize)` requires `hopSize` to evenly divide
`windowSize` (`IllegalArgumentException` otherwise) - the overlap-add
normalization below depends on the hop grid tiling the window exactly, and
every concrete use in this project is expected to pick a hop that's a clean
fraction of the window (`windowSize/4` is the recommended default - see
below).

Preallocated in the constructor:

- `inputAccum`, a circular `float[windowSize]` fed one sample at a time by
  `pushSample`.
- `hannWindow`, `float[windowSize]`, `0.5 - 0.5*cos(2*pi*n/(windowSize-1))` -
  used at *both* the analysis and synthesis stage of every hop (see below).
- `normSumWindow`, `float[windowSize]` - the per-position overlap-add
  normalization envelope (see "Windowing and normalization" below); computed
  once from `hannWindow` and never touched again.
- `analysisBuffer`, `fftRe`, `fftIm`, `magnitude`, `phase` - all
  `float[windowSize]`, the scratch space one hop's worth of analysis/
  resynthesis needs, reused every hop.
- `outputAccum`, a circular `float[2*windowSize]` (see "Why the output
  accumulator is `2*windowSize`, not `windowSize`" below) that
  `popSample` drains from.
- One `Dsp.Fft` instance, sized `windowSize`.

### `pushSample(float inputSample)` / hop scheduling

Writes into `inputAccum` at a circular write index, advancing an
`if (++index >= capacity) index = 0;` guard per convention 4 (no `%` in
ring-buffer indexing - every index in this class follows that rule, not just
`inputAccum`'s). A `samplesSinceHop` counter also advances every call; once it
reaches `hopSize` it resets to `0` and a hop (`processHop()`, below) runs.
Because `inputAccum` is zero-initialized, the very first few hops run against
a buffer that's mostly still zero - this self-corrects as real samples arrive
and does not need special-casing (a windowed all-zero segment just
contributes zero energy).

### One hop (`processHop`, private)

1. **Analysis window**: copy the last `windowSize` samples out of
   `inputAccum` in time-order (oldest first), multiplying each by
   `hannWindow[i]` into `analysisBuffer`.
2. Copy `analysisBuffer` into `fftRe`, zero `fftIm`, and run `fft.forward`.
3. Convert to magnitude/phase into the preallocated `magnitude`/`phase`
   arrays (`sqrt(re^2+im^2)`, `atan2(im, re)`), covering the full
   `windowSize` bins (not just the `N/2+1` non-redundant ones) - simpler to
   reason about and still trivially allocation-free.
4. Call `processFrame(magnitude, phase)` - see below. The default
   implementation is a no-op, making `StftEngine` usable standalone as a pure
   passthrough (exactly the configuration the smoke test below exercises).
5. Convert the (possibly now-modified) magnitude/phase back to `fftRe`/
   `fftIm` and run `fft.inverse`.
6. **Synthesis window**: multiply the inverse-transformed frame by
   `hannWindow` *again* (the same array, not a separate "synthesis window" -
   see below) and overlap-add it into `outputAccum`.

### `protected void processFrame(float[] magnitude, float[] phase)`

The one extension point. Called once per hop, after step 3 and before step
5 above, with both arrays already converted from `Dsp.Fft`'s real/imaginary
form. A subclass overrides it and mutates `magnitude`/`phase` in place -
whatever it leaves in those arrays is what gets resynthesized. `StftEngine`
itself supplies a no-op body so the class is usable and testable
standalone. A concrete node (spectral freeze, spectral gate, phase-vocoder
pitch shift) is expected to subclass `StftEngine`, hold it as - or extend it
as - a field/superclass, and override only this method; everything about
buffering, windowing, FFT plumbing, and overlap-add stays exactly as
described here regardless of what the override does.

### `popSample()`

Reads one sample per call from `outputAccum` at a circular read index (same
`if (++index >= capacity)` idiom), dividing by the matching
`normSumWindow` entry and clearing the slot to `0` before advancing.
Returns `0` for the first `getLatencySamples()` calls - see "Latency" below
- with no attempt to hide or interpolate through that startup period; the
class's contract is to be honest about it rather than to paper over it.

**Contract: call `pushSample` and `popSample` once each, in lockstep, per
audio sample.** This isn't just a convenience - it's load-bearing. The class
tracks exactly one "calls so far" counter per side (advancing `inputAccum`'s
and `outputAccum`'s circular indices once per call), and the fixed spacing
between the write side and the read side that makes overlap-add correctness
(see below) hold is only guaranteed under a strict one-call-per-sample,
push-then-pop (or pop-then-push - order between the two doesn't matter,
only the 1:1 *rate* does) calling pattern. Pushing many samples with no
interleaved pops (e.g. "push a whole file, then pop it all") will silently
corrupt `outputAccum`, because the write side keeps advancing past where an
un-drained read side has left old data sitting in slots that are about to be
reused. A concrete `Node` subclass naturally satisfies this contract simply
by calling both once per sample inside its own `process()` loop, which is
exactly how every smoke test below drives it too.

### Windowing and normalization

`hannWindow` is applied at *both* the analysis and synthesis stage of every
hop - not a `sqrt(Hann)` split across the two stages the way some phase
vocoder implementations do it. That means a passthrough (no-op
`processFrame`) signal is effectively multiplied by `hannWindow^2` by the
time it reaches `outputAccum`, and the classic "Hann + 75% overlap = perfect
constant-overlap-add" identity most references quote is about the window
applied *once*, not squared.

Rather than lean on that identity (which also assumes the *periodic*/DFT-even
Hann definition, denominator `N`, and this class deliberately uses the
*symmetric* one from the task spec, denominator `N-1`, whose endpoints are
exactly zero - the two are subtly different and the symmetric one does not
sum to a clean constant when squared), `StftEngine` computes its own exact
normalization: `normSumWindow[p]`, for every output position `p` in
`[0, windowSize)`, is the sum of `hannWindow[idx]^2` over the
`windowSize/hopSize` positions in the window that actually land on `p`
through the circular indexing `processHop` and `popSample` use (computed by
literally replaying that same wraparound arithmetic once, in the
constructor, rather than trusting a closed-form identity). Dividing each
popped sample by the matching `normSumWindow` entry is what makes the
passthrough smoke test below reconstruct to `~3e-7` (float32 epsilon) instead
of the `~10%` steady-state ripple an unnormalized (or center-sampled-constant
normalized) version of this exact class produced during development - the
symmetric Hann's non-ideal squared-COLA sum turned out to matter in
practice, not just in theory, which is why this class computes the exact
per-position table instead of assuming a single scalar constant.

### Latency

**`getLatencySamples()` returns `windowSize`, not `windowSize - hopSize`.**
This is a deliberate, verified departure from the `windowSize - hopSize`
figure commonly quoted for STFT/OLA processing latency, arrived at by
building and testing the smaller figure first and finding it insufficient:

A sample pushed at absolute time `t` is only *fully* reconstructed - all
`windowSize/hopSize` overlapping frames that cover it have all been
analyzed and resynthesized - once the *last* covering frame has run. That
last frame's hop occurs, in the worst case (a sample landing right at a hop
boundary), `windowSize - 1` samples after `t`, not `windowSize - hopSize`
samples after it. Popping at exactly `windowSize - hopSize` (verified via an
impulse test: pushing a single unit impulse and popping `popSample()` once
per sample) measurably returns only a fraction of the impulse's energy
(observed: `~92%` at `windowSize=1024`/`hopSize=256`) at the "expected"
position, with the missing `~8%` reappearing a full `outputAccum` cycle
later, since the last contributing hop's overlap-add write lands in a slot
that popping has already read and cleared. Using `windowSize` as the
latency instead - one extra `hopSize`'s worth of delay versus the commonly
quoted figure - guarantees the read always happens strictly after the last
possible contributing write, which the same impulse test then confirms
lands a clean, single-sample-wide unit impulse (`0.99999994`, everything
else at `~1e-8`, float noise) at exactly the expected position with no
delayed leftover.

At `windowSize=1024`, `sampleRate=44100`, this is `1024` samples (`~23.2ms`)
of one-way latency before the first real output sample appears -
acceptable for a patched effect node, not for a live-monitoring path (same
caveat the plan that introduced this package already flagged).

### Why the output accumulator is `2*windowSize`, not `windowSize`

A circular buffer exactly `windowSize` long is enough to hold every *live*
overlap-add contribution at once, but is not enough slack for the
`windowSize`-latency popping scheme above: the last write for a given
output position can legally land up to `windowSize - 1` samples after that
position was pushed, i.e. one sample *before* it gets read, which is a tight
enough margin that a same-size buffer's slot-reuse cycle (every
`windowSize` calls) can start overwriting a position with the *next*
cycle's unrelated data before the current cycle's last write has safely
landed and been drained. Doubling the accumulator to `2*windowSize` pushes
the next reuse of any given slot a full `windowSize` calls further out,
which is enough margin (verified, not just asserted, by the same impulse
test - the very first attempt at this class used a `windowSize`-length
accumulator with the smaller `windowSize - hopSize` latency and exhibited
exactly this corruption, which is what led to fixing both the latency
value and the accumulator size together). The extra memory is `windowSize`
floats - four kilobytes at `windowSize=1024` - which is immaterial next to
the FFT scratch space already held.

## Smoke-test results (throwaway, not checked in)

Both classes were verified with throwaway smoke-test classes compiled
against `target/classes` and run directly, the project's standard verification
approach in the absence of a test framework:

- **`Fft`**, `size=1024`: a real sine at a bin-exact frequency (bin 23 of
  1024, `sampleRate=44100`) puts `99.9999...%` of the spectrum's energy in
  that bin and its mirror image (bin `1001`) after `forward` - `0%` smeared
  across the remaining bins. `forward` followed by `inverse` on
  1024 samples of random noise reproduces the original signal to a maximum
  absolute error of `~3e-7` (float32 epsilon).
- **`StftEngine`**, `windowSize=1024`/`hopSize=256`, default no-op
  `processFrame`: pushing 2 seconds of a 440Hz sine and popping the same
  number of samples (one `pushSample`/`popSample` pair per sample) reproduces
  the input delayed by exactly `windowSize` (`1024`) samples, to a maximum
  absolute error of `~2.4e-7` and an RMS error `~1.1e-7` relative to the
  signal's own RMS - float32 epsilon, not an approximation. A separate
  single-impulse test (see "Latency" above) confirms the same for a signal
  shape a smooth sine can't distinguish from a small phase/alignment error.

## Concrete nodes built on `StftEngine`

Each of the three registered `Spectral` nodes composes a private, anonymous
`StftEngine` subclass as a field (rather than the node class itself
extending `StftEngine`), since every other node in the project extends
`AbstractNode` for its `Port`-list boilerplate and Java has no multiple
inheritance - `StftEngine`'s `processFrame` hook is `protected`, so the
anonymous subclass overrides it and closes over the outer node's fields to
read the current per-sample port values (threaded in as plain fields,
updated once per sample in `process()` immediately before
`engine.pushSample(...)`, since `processFrame` fires synchronously inside
that same call at hop boundaries - no `NodeContext`/port array is available
inside the hook itself). All three use `windowSize=1024`, `hopSize=256`,
matching the values `StftEngine`/`Fft` were verified against above.

- **`SpectralFreezeNode`** (`in`, `freeze`): while `freeze > 0`, the first
  frame after crossing above 0 snapshots the current magnitude and phase;
  every subsequent frame holds that magnitude and re-emits phase advanced by
  a small random increment per bin per frame (a fixed `PHASE_DRIFT` constant
  scaling a `java.util.Random` draw) so the sustain doesn't lock into an
  audible periodic loop. `freeze <= 0` passes the live spectrum through
  unmodified.
- **`SpectralGateNode`** (`in`, `threshold`, `blur`): one-pole-smooths each
  bin's magnitude across frames by `blur` (0 = no smoothing, up to a fixed
  `MAX_BLUR_COEFF` of 0.98), normalizes each smoothed bin against the
  frame's own peak magnitude, and multiplies any bin below `threshold` by a
  fixed small `BELOW_THRESHOLD_GAIN` (0.05) rather than hard-muting it -
  keeps the effect from producing sharp gating artifacts at the
  threshold boundary.
- **`PhaseVocoderPitchShiftNode`** (`in`, `pitchRatio`): for each analysis
  bin `k` in `[0, windowSize/2]`, recovers the bin's true instantaneous
  phase advance per hop from the measured phase difference against the
  previous frame (the standard phase-vocoder trick: subtract the bin's
  *nominal* advance `2*PI*k*hopSize/windowSize`, unwrap the remainder to
  `(-PI, PI]`, add it back - this works entirely in "phase advance per hop"
  units and never needs the sample rate, since it cancels out
  algebraically). The first working version scaled each bin's phase advance
  *in place* without moving its magnitude to a new bin index, which
  measured as a wrong, not-simply-related output frequency in testing
  (verified wrong, not just suspected - see below); the fix was the
  standard "identity phase vocoder" bin-shift technique instead: each source
  bin's magnitude is redistributed into `round(k * pitchRatio)`, and a
  *separate*, persistent per-target-bin accumulated output phase is advanced
  by the source bin's true phase advance scaled by `pitchRatio`. Bins above
  `windowSize/2` are filled in as the conjugate mirror of the lower half
  (`magnitude[N-k] = magnitude[k]`, `phase[N-k] = -phase[k]`) to keep the
  inverse transform's real part a faithful reconstruction rather than
  relying on `Fft.inverse` happening to discard the imaginary part cleanly
  for an asymmetric spectrum. `pitchRatio < 1` (compressing bins toward
  lower indices) measures a few percent off the mathematically exact target
  frequency in testing - expected, not a bug: multiple source bins can round
  to the same target bin, and `round(k*pitchRatio)` has up to half a bin's
  worth of quantization error at `windowSize=1024`'s bin spacing, a known,
  accepted limitation of the naive per-bin-rounding approach (the standard
  fix, sub-bin interpolation across neighboring target bins, is future work,
  not implemented here).

## `PitchCorrectionNode` - self-contained autotune

Unlike the three nodes above, `PitchCorrectionNode` doesn't just compose an
`StftEngine` subclass - it embeds the *exact same* anonymous-subclass
`processFrame` body as `PhaseVocoderPitchShiftNode` (the same
identity-phase-vocoder bin-shift algorithm, just a larger
`windowSize=2048`/`hopSize=512` - see "Why a bigger window than
`PhaseVocoderPitchShiftNode`" below) as its correction backend, but computes
the `pitchRatio` field itself instead of reading it from a port. Registered
five times, once per
`Dsp.PitchQuantizer.Scale` value (`Pitch Correction Chromatic/Major/Minor/
Major Pentatonic/Minor Pentatonic`), the same variant-registration pattern
`Utility.QuantizerNode` already uses (see `utility.md`/`dsp.md`).

Ports: `in` (audio), `root` (Hz, default `261.6256` = middle C, matching
`QuantizerNode`'s default), `retuneSpeed` (milliseconds). Deliberately no
dry/wet `mix` port - `PhaseVocoderPitchShiftNode`, its closest sibling,
doesn't have one either.

Per sample, `process()`:
1. Feeds the raw input sample to a `Dsp.PitchDetector` (see `dsp.md`),
   lazily constructed on first use with a fixed `minFrequencyHz=60`
   floor - low enough to cover vocal/guitar/piano low notes without needing
   an upper-bound parameter (YIN's own first-dip search finds the true
   period without one).
2. If the detector currently reports `isVoiced()`, quantizes its detected
   frequency to the node's `scale` via `PitchQuantizer.quantize` and sets
   `targetRatio = quantizedHz / detectedHz`; otherwise `targetRatio = 1`
   (silence or unpitched/noisy input passes through uncorrected rather than
   being forced onto a wrong note).
3. Glides the actual `pitchRatio` toward `targetRatio` with an
   `EnvelopeFollower` (see `dsp.md`) driven by the `retuneSpeed` port fed as
   *both* its attack and release time - the same knob governs snapping up
   and down, unlike the asymmetric attack/release nodes (`Compressor`,
   `NoiseGate`) that need different speeds each direction. `retuneSpeed=0`
   falls out of `EnvelopeFollower`'s own coefficient math
   (`exp(-1/0) = 0`) as the instant, hard "robotic" snap real autotune's
   hardest setting produces - no special-casing needed for that case.
   This one glide mechanism does double duty: it's both the "retune speed"
   knob and what makes the unvoiced-passthrough transition smooth rather
   than a discontinuous jump back to `pitchRatio=1`.
4. Feeds the same raw sample into the shared `StftEngine`, exactly like
   `PhaseVocoderPitchShiftNode` does.

**Why a bigger window than `PhaseVocoderPitchShiftNode`**: at
`windowSize=1024` (43Hz/bin at 44.1kHz), the identity-phase-vocoder's
`targetBin = round(k * pitchRatio)` rounding step is coarse relative to a
typical vocal fundamental - bin spacing is a meaningful fraction of the
fundamental itself down in the 200-350Hz range, not a small fraction of it
the way it is for a higher-frequency signal. Measured directly against
`StftEngine` with a fixed ratio (349.23Hz shifted by a ratio of `0.9439`,
the exact major-third-down correction a Pitch Correction Major Pentatonic
would apply to an off-scale F4): `windowSize=1024` measured `315.16Hz`
against an exact target of `329.63Hz` - a `4.4%` error, big enough to land
on a clearly wrong note, not just slightly flat. `windowSize=2048` measured
`329.86Hz` (`0.07%` error); `windowSize=4096` measured `329.63Hz`
(effectively exact); `windowSize=8192` measured `330.08Hz` (`0.14%`) - no
further improvement past `2048`, so `2048`/`512` (`windowSize`/`hopSize`,
keeping the same `4:1` ratio `PhaseVocoderPitchShiftNode` uses) was chosen
as the smallest window that fixes the problem, rather than `4096`'s
marginally better number at double the latency. `PhaseVocoderPitchShiftNode`
itself doesn't need this: its `pitchRatio` comes from a knob the user is
actively listening to sweep, where a few percent of frequency error is
imperceptible drift, not a wrong note - `PitchCorrectionNode`'s entire
purpose is landing exactly on a specific note, so the same error that's
fine there isn't fine here.

**Two independent kinds of latency**: the STFT path adds the same
`windowSize`-sample output latency `PhaseVocoderPitchShiftNode` has, just
at the larger `windowSize=2048` (~46ms at 44.1kHz) chosen above rather than
`1024`'s ~23ms. Separately, `PitchDetector`'s own analysis window
(`2*tauMax`, up to ~33ms at 44.1kHz for the 60Hz floor) makes its
*reported pitch* up to that much stale relative to the live input - not a
delay in the audio path itself (only the STFT path touches audio samples on
their way to the output), just a lag in how quickly a pitch change is
noticed. Pitch doesn't change fast enough for that staleness to matter
audibly in practice.

Explicitly not attempted: true polyphonic (multi-note-at-once) pitch
correction. Fed a chord or other simultaneously-voiced material, this node -
like every real hardware/software autotune - just tracks whichever pitch is
dominant and corrects that; separating and correcting multiple simultaneous
pitches is a fundamentally different, much harder problem (multi-pitch
estimation/source separation) that was never in scope here.

## Smoke-test results (throwaway, not checked in)

All classes were verified with throwaway smoke-test classes compiled
against `target/classes` and run directly, the project's standard verification
approach in the absence of a test framework:

- **`Fft`**, `size=1024`: a real sine at a bin-exact frequency (bin 23 of
  1024, `sampleRate=44100`) puts `99.9999...%` of the spectrum's energy in
  that bin and its mirror image (bin `1001`) after `forward` - `0%` smeared
  across the remaining bins. `forward` followed by `inverse` on
  1024 samples of random noise reproduces the original signal to a maximum
  absolute error of `~3e-7` (float32 epsilon).
- **`StftEngine`**, `windowSize=1024`/`hopSize=256`, default no-op
  `processFrame`: pushing 2 seconds of a 440Hz sine and popping the same
  number of samples (one `pushSample`/`popSample` pair per sample) reproduces
  the input delayed by exactly `windowSize` (`1024`) samples, to a maximum
  absolute error of `~2.4e-7` and an RMS error `~1.1e-7` relative to the
  signal's own RMS - float32 epsilon, not an approximation. A separate
  single-impulse test (see "Latency" above) confirms the same for a signal
  shape a smooth sine can't distinguish from a small phase/alignment error.
- **`SpectralFreezeNode`**: a 1-second 440Hz burst followed by 2 seconds of
  silence, frozen halfway through the burst, still measures non-trivial
  output energy (mean squared amplitude `~0.09`) in the final 20,000 samples
  - long after an unfrozen render of the same input would have gone
  completely silent.
- **`SpectralGateNode`**: a 1000Hz sine mixed with broadband noise, rendered
  once with `threshold=0` (bypass) and once with `threshold=0.3`, measured
  by a Goertzel single-bin DFT at both 1000Hz (the sine) and 5000Hz (pure
  noise territory). The sine bin survives gating at `~95%` of its bypassed
  strength; the 5000Hz noise-region bin is knocked down to `~7%` - clear
  evidence the gate suppresses weak frequencies independently of a strong
  one nearby, not just attenuating the whole signal uniformly.
- **`PhaseVocoderPitchShiftNode`**: a pure sine, measured by zero-crossing
  rate after the engine's `windowSize`-sample latency has elapsed.
  `pitchRatio=1.0` on a 300Hz input measures exactly 300Hz. `pitchRatio=2.0`
  on a 440Hz input measures exactly 880Hz. `pitchRatio=0.5` on an 800Hz
  input measures 425Hz against an exact target of 400Hz (~6% error, the
  bin-quantization limitation described above).
- **`AdditiveResynthNode`**: a synthetic 3-second buffer built from three
  known sine partials (220/440/660Hz at amplitudes 0.5/0.3/0.15) at
  `fidelity=1.0` recovers all three frequencies exactly (within 15Hz) via a
  post-render Goertzel spectral scan. `pitchRatio=1.0` on a 300Hz test
  signal measures exactly 300Hz; `pitchRatio=2.0` measures exactly 600Hz.
  Halving `speed` on the same signal measures the identical 300Hz dominant
  frequency as full speed - confirms speed and pitch are genuinely
  decoupled, not just approximately so.
- **`Dsp.PitchDetector`**: 2 seconds of a clean 220Hz sine measures
  `220.0009Hz` with `confidence≈0.9999` and `isVoiced()==true`; 1 second of
  silence measures `isVoiced()==false`.
- **`PitchCorrectionNode`** (measured by zero-crossing rate over the last
  second of a 3-second run, after the STFT's own latency and the detector's
  settling time have both elapsed - a coarse Goertzel bin scan was tried
  first and rejected, since at these short window lengths its own bin
  spacing is too wide to reliably distinguish frequencies this close
  together): a 226Hz input (`Chromatic`, `root=261.6256`) corrects to
  `220.17Hz` against an exact target of 220Hz (A3). A 349.23Hz input (F4,
  deliberately excluded from `Major Pentatonic`) corrects to `329.63Hz`,
  matching the nearest pentatonic note (E4) to within `0.001%` - confirms
  the scale restriction actually changes behavior end-to-end, not just in
  `PitchQuantizer` isolation. Comparing `retuneSpeed=0` against
  `retuneSpeed=400` on the same off-pitch input, sampled 0.15 seconds in:
  the fast setting has already reached `220.20Hz` while the slow setting is
  still at `231.77Hz` (a 226Hz input barely nudged so far) - confirms the
  glide actually glides, not just that both settings eventually converge.
  Feeding silence throughout holds the output at exactly `0.0`, confirming
  the unvoiced bypass doesn't produce pitch-shifted noise artifacts.

## `AdditiveResynthNode` - offline analysis + real-time sum-of-sines resynthesis

A different technique from the three `StftEngine`-based nodes above: this
analyzes an *imported* sample once, offline, at construction (not
streaming, not allocation-free - construction is not `process()`), into a
fixed set of sinusoidal partial trajectories, then plays it back in real
time as literal summed sine oscillators following those trajectories -
never touching the original waveform at playback time at all. Lives in
`Petroleum.Spectral` alongside the streaming nodes since it shares `Dsp.Fft`
for its analysis pass, but does not use `StftEngine` (a one-shot batch scan
over a fixed buffer doesn't need `StftEngine`'s streaming push/pop
machinery - duplicating its windowing loop here as a simple `for` loop is
clearer than forcing a batch use case through a streaming API).

Construction-only-buffer treatment, same as `Granular.GranularPlayerNode`:
unregistered in `NodeFactory` (needs sample data at construction, per
convention 6) - placed via a dedicated `Gui.NodeCatalog` "Additive Resynth"
entry that opens a file picker, mirroring `loadGranularPlayerSample` almost
exactly except it also probes the WAV file's own sample rate
(`AudioSystem.getAudioFileFormat(file).getFormat().getSampleRate()`) before
calling `WavetableLoader.loadMonoSamples`, since `loadMonoSamples` itself
only returns the decoded samples and silently discards the source rate -
`AdditiveResynthNode`'s analysis needs that rate to convert FFT bins to Hz
correctly, unlike `GranularPlayerNode`, which never needs to reason about
the buffer's content in absolute Hz at all.

Since `fidelity` only takes effect at construction (it drives the one-time
analysis pass, not a live port - see "Resynthesis" below), `NodeCatalog`
prompts for it with a `TextInputDialog` ("Fidelity (0-100%)") right after
the file is chosen, defaulting to 50; cancelling that second dialog aborts
the whole import, matching how a cancelled `ExportDialog` aborts a WAV
render elsewhere in `Gui`. The chosen percentage is baked directly into the
node's display name (`"drums.wav (50% fidelity)"`), passed as the
constructor's `name` argument - this is a deliberate simplification over
adding a second display-only field: `NodeView`'s title label already just
renders `node.getName()` with no `AdditiveResynthNode`-specific case, and
`Serialization.PatchCodec.saveNode` already round-trips `getName()`
verbatim through the `"name"` JSON field, so baking the fidelity into the
name gets it into both places for free. There is currently no way to
re-analyze an already-placed node at a different fidelity short of removing
it and re-importing - `fidelity` intentionally has no setter, since changing
it invalidates every `Partial` the resynthesis loop depends on.
`Serialization.PatchCodec` gets a `TYPE_ADDITIVE_RESYNTH` branch mirroring
`GranularPlayerNode`'s exactly (`instanceof` branch in `saveNode`/`typeOf`,
explicit `case` in `loadNode`), storing the raw sample bytes via the
existing `PatchArchive` sample mechanism plus `sampleRate`/`fidelity`/`name` (already
carrying the fidelity suffix) as ordinary JSON fields - analysis re-runs
deterministically from the stored sample on load rather than caching
trajectories in a second bespoke binary format.

**Analysis** (`windowSize=2048`, `hopSize=512`, a plain Hann-windowed
sliding scan, no `StftEngine`): per window, run `Dsp.Fft.forward` and find
magnitude local maxima (`magnitude[k] > magnitude[k-1] && magnitude[k] >
magnitude[k+1]`), refine each peak's frequency via parabolic interpolation
across the 3 surrounding log-magnitude values (`p = 0.5*(alpha-gamma)/
(alpha-2*beta+gamma)`, the standard McAulay-Quatieri peak-refinement
formula - more accurate than the raw `sampleRate/windowSize` bin
resolution), and keep the strongest `partialCount` peaks (`fidelity` maps
linearly to `partialCount` between 4 and 64). Peaks are tracked into
continuous trajectories across windows by nearest-frequency matching
(within `2.5` bins' tolerance) - an unmatched active trajectory "dies" (its
frame range ends), an unmatched peak "is born" (a new trajectory starts);
trajectories shorter than 2 frames are discarded as noise. Each surviving
trajectory is stored as a `startFrame` plus parallel `float[]
frequencies`/`amplitudes` arrays - contiguous by construction, since a
trajectory only ever gains one entry per window it's alive for, so no
explicit frame-index array is needed to look up "frame N" within a
trajectory's own local range.

**Resynthesis** (`process()`, real-time, allocation-free): one phase
accumulator per *distinct trajectory found during analysis* - not a fixed
pool of reused voice slots the way the design was originally sketched,
since slot reuse across different trajectories over time would need
careful crossfading to avoid an audible phase jump whenever a slot's
occupant changes, and a plain "one accumulator per trajectory, however many
there turn out to be" design sidesteps that complexity entirely while still
satisfying the allocation-free requirement (the accumulator array is sized
once, at construction, from however large `partials.length` turns out to
be - never resized in `process()`). A fractional `playheadFrames` position
(in analysis-window units) advances every sample by `speed / hopSize`;
`trigger`'s rising edge jumps it to `position * totalFrames`. Per sample,
each partial whose stored frame range covers the current `playheadFrames`
contributes `amplitude * sin(phase)` to the output (linearly interpolating
frequency/amplitude between the two nearest analyzed frames via
`Dsp.DspMath.lerp`, advancing that partial's own phase by
`2*PI*frequency*pitchRatio/sampleRate` each sample); partials outside their
frame range at the current position simply contribute nothing, which is
what naturally makes a trajectory's birth and death within the file
audible as the corresponding partial fading in/out of the mix rather than
needing separate envelope logic.
