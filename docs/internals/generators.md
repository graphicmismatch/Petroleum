# Generators (`Petroleum.Generators`)

Builds a usable game sound from a category and a seed. This is the floor of the
product: someone who has never opened a synthesiser presses **Laser** and hears
a laser.

## The governing decision: a generated sound is a real graph

sfxr stores a sound as a fixed struct of ~24 parameters. Copying that shape
would leave Petroleum with two disconnected systems - a parameter model for the
quick screen and a node graph for the editor - plus a lossy conversion between
them, and a hard ceiling on what a generated sound is allowed to be.

`SoundGenerator.generate(...)` instead returns an ordinary
[`PatchDocument`](serialization.md): real nodes, real connections, real canvas
positions. "Open in graph editor" is therefore not a conversion, it is just
showing the user what was already there. Anything the editor can build, a
generator can emit, and anything a generator emits can be taken apart.

The quick screen's sliders are [`Macro`](serialization.md)s - named bindings
onto specific `ConstantNode`s inside that graph. A beginner sees eight named
knobs. An expert sees sixteen nodes. Both are editing the same object.

## The generated graph

Every category emits the same topology, and differs only in oscillator choice
and parameter ranges:

```
Trigger ─┬─> Envelope(attack, sustain, punch, decay) ─────────> Amplifier.gain
         └─> Ramp(from, to, time) ─> Phasor ─> Oscillator ─> Biquad Lowpass ─> Amplifier.in
                                                                               Amplifier ─> Audio Out
```

Eight `ConstantNode`s drive the tunable inputs, and each is exposed as a macro:

| Macro | Drives | Range |
|---|---|---|
| Pitch | `Ramp.from` | 20 - 12000 Hz |
| Slide | `Ramp.to` | 20 - 12000 Hz |
| Slide Time | `Ramp.time` | 0 - 2 s |
| Attack / Sustain / Decay | the matching `Envelope` inputs | 0 - 2 s |
| Punch | `Envelope.punch` | 0 - 1 |
| Bright | `Biquad.cutoff` | 100 - 16000 Hz |

Pitch and Slide are both absolute frequencies rather than a base plus a
multiplier, so the two sliders stay independent - dragging Pitch does not drag
the slide target with it.

Noise categories still contain the `Ramp` and `Phasor`, but the `Phasor` is
left unwired: a noise oscillator has a `gate` port in place of `phase` (see
[Oscillators](oscillators.md)), so wiring `phase` would throw when the patch
is built, and a phase ramp has no meaning as a gate. Keeping one set of nodes means the editor view of an explosion
looks like the editor view of a laser, and the `Bright` macro means the same
thing everywhere. Pitch, Slide and Slide Time drive nothing audible in a noise
sound; `Bright` and the envelope macros shape it.

## Categories

`SoundCategory` holds eight entries: Pickup, Laser, Explosion, Powerup, Hit,
Jump, Blip, and Random. `Random` picks one of the other seven, resolved
against `SoundGenerator.ROLLABLE` - the enum values filtered to exclude
`RANDOM` itself, computed once and independent of `RANDOM`'s position in the
declaration - so it is a shortcut rather than a ninth sound family.

`CategorySpec` is a package-private record grouping three smaller records -
`Pitch(frequency, slide, slideTime)`, `Envelope(attack, sustain, punch,
decay)`, and a bare `cutoff` - each field itself a `Range(min, max)`, plus the
oscillator names that suit that category. Generation is then uniform sampling
inside each `Range` (`between(random, range)`) - the whole per-category
character lives in one data table in `SoundGenerator.buildSpecs()`, so adding
a category means adding a row, not writing code. The grouping mirrors the
generated graph's own shape (one `Ramp` reads the `Pitch` fields, one
`Envelope` node reads the `Envelope` fields, one `Biquad` reads `cutoff`)
rather than being an arbitrary split of seventeen numbers.

Roughly what distinguishes them: Pickup and Jump slide up over a short window;
Laser slides sharply down; Explosion is noise with a low cutoff, heavy punch,
and a long decay; Powerup slides up much further over much longer; Hit is a
very short noise burst; Blip is short with almost no slide.

An envelope with `punch > 0` peaks above 1, so generated sounds routinely drive
the amplifier past unity and clip at the converter. That is sfxr behaviour and
part of the character - see [Envelopes](envelopes.md).

## Seeds

`generate(category, seed)` is fully deterministic: one `Random(seed)`, sampled
in a fixed order. The same category and seed always produce the same sound.

`encodeSeed`/`decodeSeed` render a `long` as unsigned base-36, so a sound is
shareable as a short code - seed `123456789` is `21I3V9`. `decodeSeed` uses
unsigned parsing so negative seeds round-trip, which matters because the quick
screen rolls with `Random.nextLong()`.

## Mutate

`mutate(document, seed, amount)` nudges each **macro value** by up to `amount`
of its range, clamped to that range. It never touches the graph - no node is
added, removed, or rewired, and the oscillator does not change.

That keeps mutate meaning "the same sound, slightly different", which is what
makes the sfxr roll-and-refine loop work. A mutation that could rewire would
just be another roll.

## Re-auditioning

Nothing here needs to re-arm anything. [`TriggerNode`](envelopes.md) fires from
the `NodeContext` timeline, so every fresh render or playback session triggers
the sound again on its own. An earlier version of this package carried a
`rearm(document)` helper that re-fired each trigger by hand; it was deleted
along with `TriggerNode.fire()` when the trigger became session-driven, because
every call site would have been "call this or get silence".

## `suggestedDuration`

Attack plus sustain plus decay, with a small tail and a 0.25 s floor. Used for
the offline preview render and as the export length, so exported files are as
long as the sound and no longer. Since the envelope closes on its own, a
slightly generous duration only adds silence rather than cutting the sound.

## Gui integration

[`Gui.QuickView`](gui.md) is the screen this package exists for: category
buttons, an offline-rendered preview, one slider per macro, Play / Mutate /
Export, a roll history of the last 20 sounds, and the code field. See
[Gui](gui.md) for how it and the graph editor share one window and one audio
engine.
