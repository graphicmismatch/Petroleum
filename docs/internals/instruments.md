# Instruments (`Petroleum.Instruments`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Granular`/`Petroleum.Tracker`, not nested under `Graph`. One class,
`InstrumentNode`, plays an OpenMPT-style multisample instrument: several
imported WAV samples ("zones"), each with its own root pitch and key range,
picked and pitch-shifted at note-on, shaped by a breakpoint volume envelope.
`InstrumentNode` is `channelCount`-wide, the same construction-time-sized,
`NodeFactory`-registered-in-four-sizes paradigm `TrackerNode` uses (see
[Tracker](tracker.md)): every channel plays independently but shares one
`Instrument` definition.

## Why frequency, not a note number

Everywhere else in this codebase that ends up describing a pitch -
`TrackerNode`'s per-step `pitch` (a `float` semitone, fractional cents and
all), `NoteNode`/`Sources.NoteName`'s text field, the Piano Roll editor's
Alt-drag "true microtonal placement" - is already continuous, never
quantized to the nearest semitone. `InstrumentNode` follows the same rule at
the boundary that matters most: its `pitch` input port is a raw frequency in
Hz, and a zone's playback speed is `pitch / zone.rootHz` - a plain division,
computed once per note-on and then advanced every sample. There is no note
number anywhere in the hot path to round. Feed it a 19-EDO step, a
just-intonation ratio, or anything else an `Expression` node can compute, and
the sample comes out at exactly that pitch. Building a dedicated
alternate-tuning system on top would have been solving a problem the engine
doesn't have.

## `Instrument`

The mutable, GUI-facing data model - deliberately not a `Node` itself (no
`Port`s, no `process()`), the same separation `Dsp`'s helper classes keep
from the node packages that hold them.

- `SampleZone` (nested, mutable): `name`, `samples` (`float[]`, imported via
  `Oscillators.Implementations.WavetableLoader.loadMonoSamples`, the same
  loader `Granular.GranularPlayerNode`/`Oscillators.Implementations.Wavetable`
  use), `rootHz`, `minHz`, `maxHz` (the key range this zone answers to),
  `loopEnabled`/`loopStartFrame`/`loopEndFrame`.
- `EnvelopePoint` (nested record): `timeSeconds`, `level` (`0..1`) - one
  breakpoint of the volume envelope.
- A `sustainPointIndex` (`-1` = none): while `gate` is held and the envelope
  reaches this point, time freezes there; releasing `gate` lets it resume
  toward the remaining points, giving that tail its release shape - the same
  role a sustain point plays in a real OpenMPT instrument envelope, just
  addressed by index into a plain list instead of a tracker-tick position.

`InstrumentNode.getInstrument()`/`setInstrument(Instrument)` both defensively
copy (`Instrument.copy()`, deep for zones and the envelope list) rather than
handing out or taking a live reference - the same reason `TrackerNode` keeps
`getPattern()`/`setPattern()` copy-based instead of exposing its working
`TrackerPattern` directly: the editor screen mutates its own working copy on
the JavaFX thread while the audio thread may be mid-`process()` on a
different one, and a `volatile Instrument` field swapped by reference on
every edit is what keeps that safe without a lock.

## `InstrumentNode`

`channelCount` independent voices sharing one `Instrument`, the same
paradigm `TrackerNode` uses for its own channels: channel count is a
construction-time parameter (`new InstrumentNode(int channelCount)`), not a
fixed constant, and `NodeFactory` registers four fixed-arity palette
entries - `"Instrument 2"`/`"Instrument 4"`/`"Instrument 8"`/`"Instrument
16"`, each `() -> new InstrumentNode(N)` - mirroring `Tracker`'s own
`"Tracker 2"`.."Tracker 16"` registrations exactly, rather than one plain
no-arg constructor. `Gui.GraphEditor.convertInstrumentChannels` additionally
lets a user swap an already-placed Instrument for a different size in place,
carrying over the shared instrument and wiring on every channel that still
exists - no confirmation dialog is needed on shrink, unlike
`convertTrackerChannels`, since there is no per-channel data to lose (see
"Per-channel state, shared configuration" below).

Every channel reads and writes its own row of parallel primitive arrays
(`activeZone`, `position`, `envelopeTime`, `sustained`, `gateHeld`, one
`Dsp.EdgeDetector` each) - the same "arrays indexed by channel" shape
`TrackerNode` uses for its own per-channel state, and for the same
allocation-free reason (convention 2): `process()` iterates channels in an
outer loop and samples in an inner loop, indexing every array by the
channel's `int c`, never allocating per block or per sample.

### Per-channel state, shared configuration

The `Instrument` itself - its sample zones and volume envelope - is one
`volatile` field shared by every channel, exactly like `TrackerNode`'s
pattern is one `TrackTable` shared by every channel: editing it from
`Gui.InstrumentEditorView` changes what all channels play at once. What is
*not* shared is playback progress - each channel has its own active zone,
sample-read position, envelope time, sustain flag, and gate-edge detector,
so `channelCount` notes can be in flight simultaneously (a chord, or a
multi-channel `Tracker` driving several voices) without one channel's
envelope or loop position bleeding into another's.

Ports (per channel `N`, `0` to `channelCount - 1`):

| Port | Units | Default | Meaning |
|---|---|---|---|
| `pitchN` | Hz | `440` | the frequency to play channel `N`'s current note at - read fresh on every gate rising edge to pick a zone, and every sample after that to drive that channel's playback speed |
| `gateN` | `0` or `1` | `0` | a rising edge picks a zone for channel `N` (`Instrument`'s zone-selection below) and restarts its playback and envelope from the beginning; a falling edge starts its envelope's release |

`channelCount` outputs, `out0`..`out(channelCount - 1)`, one per channel -
the flat scheme `Utility.SplitterNode` uses, not `Tracker`'s interleaved
`pitch0, gate0, pitch1, ...`, since a channel's output here is a single
audio signal rather than a pitch/gate pair.

**Zone selection** happens once per channel's gate rising edge, not per
sample: the first zone whose `[minHz, maxHz]` contains that channel's
current `pitch` wins; if none matches, the zone whose `rootHz` is nearest in
log-frequency space is used instead, so a single-zone instrument still
answers any pitch rather than going silent outside its declared range. This
is the same "region" model SFZ/Kontakt use, and what OpenMPT's own sample
map degenerates to once you stop hand-painting every one of 120 keys
individually - a full per-key mapping table was deliberately not built (see
"Scope not attempted" below).

**Playback** reads the zone's `samples` with linear interpolation (the same
clamp-then-lerp shape `GranularPlayerNode.FixedBufferSource.read` uses,
minus the clamp - `InstrumentNode` goes silent past the buffer's end instead
of holding the last sample, since a finished one-shot zone should stop, not
hang) at `pitch / zone.rootHz` samples per sample, tracked in that channel's
own `position` entry. If `loopEnabled` and that channel's `gate` is still
held, crossing `loopEndFrame` wraps back to `loopStartFrame` by subtraction
(convention 4 - no `%`); once `gate` releases the zone plays its tail
through to the natural end instead of continuing to loop, the standard
sustain-loop convention.

**The envelope** is evaluated every sample, per channel: linear
interpolation between the two breakpoints surrounding that channel's current
envelope time, holding at the first point's level before it and the last
point's level after it - except that releasing `gate` after the envelope
has already run past its last point snaps that channel's output to silence
rather than hanging a note open forever with no defined release shape.
`advanceEnvelope` is where the per-channel sustain freeze lives: once a
channel's envelope time reaches `sustainPointIndex`'s time while its `gate`
is still held, that channel's time stops advancing until its `gate`
releases.

**No `OneShot`.** Like `AdsrNode`, this node has no self-declared "done" -
each channel just answers `0` when idle. It also does no convention-9
session-boundary detection: every channel is purely driven by its own
`gate`'s edges (`Dsp.EdgeDetector`), and a fresh `Patch`/`NodeContext` starts
with every unconnected `gateN` input at its port default of `0`, so a new
session naturally begins idle with no dedicated handling needed - the same
reasoning that lets `AdsrNode` skip it too.

## Editing (`Gui.InstrumentEditorView`)

Reached the same way `TrackerNode`'s pattern editor and `DrawnWaveform`'s
waveform-drawing screen are - an "Edit" button in `NodeView`'s header opens
a dedicated `SynthApp` screen, wired through
`GraphEditor.openInstrumentEditor`/`SynthApp.openInstrumentEditor`. Two
panels:

- A zone table: name, root/key-range-low/key-range-high (typed as note-name
  text, `Sources.NoteName.parse`/`format`, the same continuous
  semitone-plus-cents notation `NodeView.buildNoteControl` already uses for
  `NoteSource`s), and a loop checkbox. "Import Sample..." opens the same
  `WavetableLoader`-backed `FileChooser` flow `NodeCatalog`'s "Granular
  Player" entry and `Gui.SoundClipEditorView`'s "Import WAV..." button use.
- A breakpoint envelope canvas, modeled on `WaveformDrawView`'s
  `Canvas`/`GraphicsContext` drawing: click empty space to add a point, drag
  an existing one to move it (clamped between its neighbors' times so points
  can't cross and reorder mid-drag), double-click to mark/unmark it as the
  sustain point, right-click to delete it.

Every edit is pushed straight to the live node (`InstrumentNode.setInstrument`)
so changes are audible immediately if a patch is playing, mirroring
`TrackerEditorView.previewEdit`. Unlike `TrackerEditorView`, there is no
dedicated undo/redo stack here - the same tradeoff `WaveformDrawView` (the
closer precedent for a small, self-contained non-graph editing screen)
already makes.

## Serialization

`InstrumentNode` needs `PatchCodec` special-casing regardless of channel
count (an `instanceof InstrumentNode` branch in `saveNode`/`loadNode`/
`typeOf`, calling `Serialization.InstrumentCodec`) for the same reason
`GranularPlayerNode` and `Oscillators.Implementations.Wavetable` do: each
zone's `float[] samples` is embedded via the shared per-file `PatchArchive` sample pool
(`samples.idFor`/`samples.require`), exactly like `OscillatorCodec` embeds a
`Wavetable`'s table - "nothing external is ever saved by reference" (see
[Serialization](serialization.md)). Channel count itself is saved as a
plain `"channels"` integer field alongside the embedded instrument, read
back with `Json.numberOrDefault(json, "channels", 1)` on load and passed to
`InstrumentNode`'s constructor - the same shape `PatchCodec` already uses
for `TrackerNode`'s own `"channels"` field.

Channelizing `InstrumentNode` renamed its ports (`pitch`/`gate`/`out`
became `pitchN`/`gateN`/`outN`), so an old save's `"toPort"`/`"fromPort"`
connections into an `Instrument` node would no longer resolve. Per this
project's serialization policy - "exactly one format version is read and
written; any other version is rejected on load, no compatibility branches
in the reader" - this port rename is exactly the kind of breaking node-level
change that policy exists for, so `PatchCodec.FORMAT_VERSION` was bumped
(to 4) alongside it, the same way `TrackerNode`'s own multi-channel
conversion bumped it to 3 (see [Tracker](tracker.md)): every existing
`.ptr` file, not just ones with an Instrument node, needs re-saving with
the new build before it will load again.

## Scope not attempted in v1

No pan or pitch envelope - the whole engine is mono (`Patch` renders one
buffer throughout; see [Audio](audio.md) and `Granular`'s own "Scope not
attempted" note), so a pan envelope has nothing to act on; a pitch envelope
would work but wasn't the headline OpenMPT feature being reproduced here.
No New Note Action - each channel is still a single voice, one zone playing
at a time, retriggered from the beginning on every gate rising edge rather
than layering/stealing/cutting a second note on top; `channelCount` gives
polyphony across channels, not per-channel NNA behavior. No full 128-key
sample map grid - the zone/key-range model covers the
same ground with far less GUI, at the cost of not being able to remap one
single key independently of its neighbors without splitting a zone.
