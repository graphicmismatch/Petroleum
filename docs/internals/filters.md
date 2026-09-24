# Filters (`Petroleum.Filters`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Oscillators`, not nested under `Graph`.

- **OnePoleFilterNode** - a simple one-pole lowpass with a patchable cutoff
  (Hz); carries state across ticks. Ports: `in`, `cutoff`. The cutoff->alpha
  math (`rc = 1/(2*PI*cutoff)`, `dt = 1/sampleRate`, `alpha = dt/(rc+dt)`) is
  `Dsp.DspMath.onePoleLowpassAlpha`; the node still owns and compares its own
  `cachedCutoff`/`cachedSampleRate`, only the formula moved - see [Dsp](dsp.md).
- **BiquadFilterNode** - one node covering the common second-order IIR shapes
  via a `FilterType` enum (`LOWPASS`, `HIGHPASS`, `BANDPASS`, `NOTCH`,
  `ALLPASS`, `PEAKING`, `LOWSHELF`, `HIGHSHELF`), using Robert
  Bristow-Johnson's Audio EQ Cookbook formulas - the same formulas behind the
  Web Audio API's `BiquadFilterNode`. One node with a type instead of eight
  near-identical classes duplicating the same coefficient math. Ports: `in`,
  `cutoff`, `q`, `gain` (gain only affects `PEAKING`/`*SHELF`).
- **OnePoleHighpassNode** - the highpass complement to `OnePoleFilterNode`.
  Ports: `in`, `cutoff`. Uses `Dsp.DspMath.onePoleHighpassAlpha`
  (`alpha = rc/(rc+dt)`, the highpass variant of the same `rc`/`dt`) with the
  same cache-on-change pattern.
- **StateVariableFilterNode** - a Chamberlin state-variable filter; the one
  multi-output node in the catalog, exposing `lowpass`, `highpass`,
  `bandpass`, and `notch` simultaneously from a single pair of state
  variables. Ports: `in`, `cutoff`, `q`.
- **LadderFilterNode** - a simplified Moog-style 4-pole resonant lowpass:
  four cascaded one-pole stages with `tanh` saturation at each stage and a
  global feedback path for resonance (self-oscillates as `resonance`
  approaches 1). Ports: `in`, `cutoff`, `resonance`. All four stages share the
  single `alpha` from `Dsp.DspMath.onePoleLowpassAlpha`, cached the same way
  as `OnePoleFilterNode`.
- **CombFilterNode** - a general-purpose feedback comb filter (peaks/notches
  in the frequency response from a short audio-rate delay), distinct from
  `Effects.DelayNode` in that it has no dry/wet mix control - it always
  outputs `in + feedback * delayed`. Ports: `in`, `time`, `feedback`. The
  circular buffer/read/write logic is a `Dsp.DelayLine` field, lazily
  constructed on the first `process()` call; see [Dsp](dsp.md).
- **LowPassGateNode** - a Buchla-style vactrol-emulated low-pass gate: a
  filter and a VCA driven by one shared nonlinear decay envelope, giving the
  characteristic "West Coast pluck" (bright attack that darkens as it
  decays) rather than a filter and amplitude envelope moving independently
  as in an ordinary VCF+VCA+ADSR chain. Ports: `in`, `strike` (trigger, a
  rising-edge pulse detected via `Dsp.EdgeDetector` per convention 8),
  `decay` (seconds, the overall envelope length), `vactrolAmount` (0-1, the
  fast/slow blend weight described below).

  On `strike`'s rising edge the node restarts a dual-time-constant decay
  envelope approximating a real vactrol's two-stage response - a fast
  initial drop followed by a slower tail, rather than a single exponential:
  `env = a*exp(-t/tauFast) + (1-a)*exp(-t/tauSlow)`, where `t` is seconds
  elapsed since the strike, `tauFast = decay*0.15`, `tauSlow = decay`, and
  `a` is `vactrolAmount` (a high `vactrolAmount` weights the fast term more
  heavily, giving a shorter, snappier decay; a low `vactrolAmount` weights
  the slow term, giving the longer, characteristic vactrol tail). `env`
  starts at exactly 1 on the strike sample (both exponential terms are 1 at
  `t=0`, so their weighted sum is 1 regardless of `a`) and decays toward 0.

  `env` drives two things from the same value, which is the whole point of
  a vactrol-style LPG: the VCA gain (`out = filtered * env`) and a one-pole
  lowpass cutoff, mapped exponentially from `env` between a fixed 80 Hz
  floor and 8000 Hz ceiling in log-Hz space (`cutoff = 80 * (8000/80)^env`)
  and fed into `Dsp.DspMath.onePoleLowpassAlpha`. So right after a strike
  the signal is both loud and bright (`env` near 1: high cutoff, high gain);
  as `env` decays the same motion simultaneously quiets and darkens the
  sound, rather than a filter envelope and an amplitude envelope sweeping on
  independent schedules. Per convention 3, the one-pole coefficient is only
  recomputed when the mapped cutoff has actually moved past a small epsilon
  (or the sample rate changed) - a no-op while idle after the envelope has
  fully decayed, and effectively every sample while it's actively decaying,
  since `env` (and therefore the mapped cutoff) is continuously moving in
  that regime.
