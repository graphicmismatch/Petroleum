# Physical (`Petroleum.Physical`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Filters`/`Petroleum.Granular`, not nested under either. Mutable
Rings-inspired: an excitable resonator, not a filter you shape a
continuously-playing tone with. Feed it a short burst (a noise burst
through an envelope, or a single-sample impulse) on `in` and it rings on
its own, the way plucking a string or striking a bar does - `Filters
.CombFilterNode` is a bare single-tap comb with no damping filter in its
feedback loop and no modal-bank mode, so this is a genuinely new node
family, not a variant of an existing one.

## `ResonatorNode`

One node, two independent resonance algorithms computed every block and
crossfaded by `structure` - "selectable resonator model," Rings' headline
feature.

Ports:

| Port | Units | Default | Meaning |
|---|---|---|---|
| `in` | audio | `0` | excitation signal - a noise burst, an impulse, or any audio signal |
| `pitch` | Hz | `220` | tunes the fundamental of both algorithms |
| `structure` | `0..1` | `0.5` | crossfades string output (`0`) to modal output (`1`); also picks the modal harmonic-ratio table (see below) |
| `brightness` | `0..1` | `0.5` | string mode only - the feedback-loop damping filter's memory coefficient |
| `decay` | `0..1` | `0.5` | overall resonance duration - feedback gain in string mode, stage Q in modal mode |

One output, `out`.

### String mode - a lowpass-damped feedback comb

A `Dsp.DelayLine` tuned to the fundamental period, `1.0 / pitch` seconds,
converted to samples via the sample rate - the exact `1/pitch` convention
`Utility.TimeToPitchNode`/`PitchToTimeNode` already use, so a `pitchN`
output from a `Tracker` channel plugs straight into `pitch` with no
conversion node in between. Every sample, the delay line's current tap is
read, pushed through a one-pole lowpass in the classic Karplus-Strong form

```
y[n] = (1 - R) * x[n] + R * y[n-1]
```

and fed back into the delay line together with the live excitation. `R`
comes from `brightness` (higher `brightness` moves `R` closer to `1`, so
the damping stage retains more of its own memory each pass); the feedback
gain multiplying the damped tap before it re-enters the delay line comes
from `decay`. This is the standard "lowpass-feedback comb filter" shape
(the same topology `Effects.ReverbNode`'s Schroeder/Freeverb comb bank
uses, just single-tap and pitched rather than a fixed untuned bank) - not
a once-per-period damping step, since the damping filter and the delay
read/write both run every audio sample regardless of how long the tuned
period is. At `brightness`/`decay` extremes the string can range from a
short, dark thud to a long, bright sustained pluck.

### Modal mode - a bank of resonant bandpass stages

Six parallel biquad bandpass stages, each excited directly by the same
`in` signal (not chained), summed and divided by the stage count. Each
stage reuses the exact RBJ bandpass-cookbook coefficient formula
`Filters.BiquadFilterNode`'s `BANDPASS` type already implements (`alpha =
sin(w0) / (2*Q)`, `b0 = alpha`, `b1 = 0`, `b2 = -alpha`, `a1 = -2*cos(w0)`,
`a2 = 1 - alpha`, all divided through by `a0 = 1 + alpha`) - reimplemented
directly inside `ResonatorNode` rather than calling `BiquadFilterNode` as a
black box, so each stage can be driven to its own center frequency
independently. Per convention 3, each stage caches its own coefficients
and only recomputes them when its center frequency, the shared `Q`, or the
sample rate actually changed since the last sample.

Stage `i`'s center frequency is `pitch * ratio[i]`, `ratio` drawn from one
of two small preset tables selected by `structure`:

- `structure < 0.5`: `{1, 2, 3, 4, 5, 6}` - an exact harmonic series, a
  string/bar-like timbre.
- `structure >= 0.5`: `{1, 1.59, 2.14, 2.65, 3.02, 3.99}` - an inharmonic
  series, roughly bell/plate-like.

Every stage shares one `Q`, derived from `decay` (longer `decay` -> higher
`Q` -> a narrower, more resonant, longer-ringing stage - the direct modal
analogue of string mode's feedback gain). A high-`Q` bandpass has a small
peak gain for a broadband impulse by construction (the RBJ cookbook's
"constant skirt gain" bandpass, not "constant peak gain"), so modal mode's
output is naturally quieter than string mode's at the same excitation
level - expected, not a bug, and easy to compensate for downstream with an
`Amplifier`.

### `structure`'s two jobs

`structure` does double duty: a straightforward `stringOut*(1-structure) +
modalOut*structure` linear crossfade of the two algorithms' final output
signals, and (independently) the `0.5` threshold that picks the modal
harmonic-ratio table. This means the harmonic/inharmonic switch happens
exactly where modal mode is already at 50% blend, not at either extreme -
so sweeping `structure` from `0` to `1` is heard as "pure string" fading
into "pure harmonic-modal," then a discrete timbral character change right
as modal output is loudest, easing into "pure inharmonic-modal." Both
algorithms run on every block regardless of `structure`'s value (no branch
skips a mode's computation), matching convention 2's no-allocation,
predictable-cost requirement - the crossfade weight is the only place
`structure` cheaply gates the output.

### Scope not attempted in v1

No stereo spread across stages (the engine is mono throughout - see
[Granular](granular.md)'s note on the same constraint). No `structure`
morph within a single preset table (e.g. sweeping partial ratios rather
than crossfading two fixed tables) - Rings' actual firmware interpolates
between more than two models continuously; this node picks between exactly
two.
