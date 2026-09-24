# Mixers (`Petroleum.Mixers`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Oscillators`, not nested under `Graph`.

- **MixNode** - sums any number of named inputs, each scaled by its own
  weight (constructor takes a `Map<String, Float>` for ergonomics - a
  one-time setup cost, not part of the per-sample path). Covers everything
  from a plain unity-gain mixer to a weighted crossfade. Registered in
  `NodeFactory` under three preset sizes (`Mix`/`Mix 2`/`Mix 8`) via lambdas
  that each bake in an equal-weight map (`in0`..`in(N-1)`, weight `1.0`
  each, N = 4/2/8) - the class itself is unchanged, the factory just
  supplies a sane default for the one constructor argument, the same
  treatment `BiquadFilterNode`'s filter type already got. `Gui.NodeCatalog`'s
  "Mix (Custom Size)..." entry prompts for any other input count and builds
  the same kind of equal-weight map before constructing one directly,
  unregistered - same treatment as
  [`SplitterNode`](utility.md#splitternode)'s custom-size entry. Placing one
  from the [Gui](gui.md) node menu gives you all inputs at once; unused ones
  are simply left unwired.
- **CrossfadeNode** - blends two inputs. Ports: `a`, `b`, `mix` (default
  `0.5`); the output is `a * (1 - mix) + b * mix`. `mix` is clamped to
  `[0, 1]`, so a control signal that overshoots saturates at one side instead
  of extrapolating past it and going louder than either input.

  This is a linear (equal-gain) crossfade, not equal-power. Equal-power is the
  right choice for blending uncorrelated material, where a linear fade dips in
  the middle; here the common use is morphing between two related sounds, where
  equal-gain keeps the arithmetic obvious and a correlated pair stays at
  constant level. Reach for two [`AmplifierNode`](utility.md)s and a `Mix` when
  an equal-power law is wanted.

  Distinct from `MixNode`, which sums N inputs at fixed weights. `Crossfade`
  takes exactly two and moves between them from a live, modulatable input -
  wire a macro, an envelope, or an LFO into `mix`.

### Stereo nodes

The two nodes that exist to feed a [`Sinks.StereoAudioOutNode`](sinks.md) -
everything else in this file is mono in, mono out.

- **PannerNode** - one `in` port and one `pan` port (`-1`..`1`, default `0`),
  two outputs `outL`/`outR`. A `PanLaw` enum picks the curve `pan` follows,
  the same construction-time-variant treatment `BiquadFilterNode.FilterType`
  gets (convention 6): `NodeFactory` registers `Panner Equal Power` and
  `Panner Linear` as two lambdas over the one class rather than a runtime
  port, and `getName()` returns `"Panner " + law.label()` so it matches its
  own registry key exactly - no `PatchCodec`/`Docs.keyFor` special-casing
  needed, the same reason `BiquadFilterNode`'s eight variants need none.
  - **Equal Power** - `pan` maps to an angle over a quarter cycle
    (`(pan + 1) * PI/4`, so `-1` is 0 radians and `+1` is `PI/2`), and
    `outL`/`outR` are `in` scaled by `cos`/`sin` of that angle. At center
    (`pan = 0`) both channels get `cos(PI/4) == sin(PI/4) ≈ 0.707`, i.e.
    -3 dB each side rather than -6 dB, which is the standard choice for
    placing one independent source across two speakers without it seeming
    to drop in level as it crosses center.
  - **Linear** - `pan` maps directly to a crossfade weight
    (`t = (pan + 1) / 2`), and `outL`/`outR` are `in` scaled by `1 - t`/`t`.
    At center both channels are at exactly half strength (-6 dB combined),
    the same center dip `Crossfade` has over an equal-weighted `Mix` (see
    above) - the right choice when panning two already-correlated signals
    (or when the predictable, purely-additive arithmetic matters more than
    constant perceived loudness), not when placing one independent source.

  Per convention 3, `gainL`/`gainR` (whichever law computed them) are only
  recomputed when `pan` actually changes since the previous sample, not
  every sample regardless - both laws share this cache, they just fill it
  differently.
- **StereoMixNode** - four `inL`i/`inR`i port pairs (`inL0`/`inR0` through
  `inL3`/`inR3`, interleaved for the same reason
  [`Tracker`](tracker.md)'s `pitchN`/`gateN` pairs are - a paired unit stays
  adjacent in [Gui](gui.md)'s port layout, so wiring one panned source's
  `outL`/`outR` into one pair stays visually local), summed unweighted into
  `outL`/`outR`. Lets several panned or otherwise-stereo signal chains
  combine onto one bus before a `StereoAudioOutNode`. Unlike `MixNode`,
  arity and per-input weights are not configurable - it is always exactly
  four pairs at unity gain, which needed no `PatchCodec` special-casing
  (`Serialization`'s existing generic `createFromFactory` path handles a
  plain no-arg constructor like any other simple node).

A worked patch:

```
Sine --> Panner Equal Power.in
Constant(-0.6) --> Panner Equal Power.pan
Panner Equal Power.outL --> Stereo Audio Out.inL
Panner Equal Power.outR --> Stereo Audio Out.inR
```
