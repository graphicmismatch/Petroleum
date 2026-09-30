# Tracker (`Petroleum.Tracker`)

A sibling package to [Envelopes](envelopes.md)/[Conditionals](conditionals.md),
per convention 1. Holds three `Node` implementations. `MidiPlayerNode` plays a
Standard MIDI File as written (see "MIDI Player" below); `PianoRollNode` plays a
polyphonic, velocity-carrying note clip edited on its own piano-roll screen (see
"Piano Roll" below); `TrackerNode` is a step
sequencer holding a **song** - multiple reusable patterns arranged through an
order list, the same pattern/order split every OpenMPT-style tracker uses -
with sample-accurate internal timing and a Standard MIDI File importer and exporter. It
exists to let a patch act as a multi-channel song player - one
[`Modules.PatchNode`](modules.md) instrument per channel.

Channel count is a construction-time parameter (`new TrackerNode(int
channelCount)`), not a fixed constant - `NodeFactory` registers four palette
entries, `"Tracker 2"`/`"Tracker 4"`/`"Tracker 8"`/`"Tracker 16"`, each a
fixed-arity lambda (`() -> new TrackerNode(N)`), mirroring how
`BiquadFilterNode`'s `FilterType` variants are each a separate registration
of the same class rather than one registration with a runtime picker.
`Gui.GraphEditor.convertTrackerChannels` additionally lets a user swap an
already-placed Tracker for a different size in place, carrying over pattern
data and wiring on every channel that still exists (see "Channel-count
conversion" below) - something no other arity-parametric node in this
project (`SplitterNode`, `MultiplexerNode`, `SequencerNode`) supports; those
all fix their arity at placement time with no in-place resize.

`Tracker` has no dependency on [Gui](gui.md) or
[Serialization](serialization.md). The pattern-editing screen lives in `Gui`
and talks to `TrackerNode` only through its public getters/setters; state
persistence goes through the ordinary [`Stateful`](graph.md) interface, which
`PatchCodec` already handles generically for any node - `Tracker` needed no
changes to `Serialization` at all.

## Ports

Four inputs, all ordinary float ports per convention 8 (no event objects):

| Port | Default | Meaning |
|---|---|---|
| `bpm` | 120 | Tempo in beats per minute |
| `play` | 1 | 1 plays, 0 pauses in place - gates drop, but position is remembered |
| `reset` | 0 | Rising edge jumps every channel back to step 0 |
| `loop` | 1 | 1 repeats forever; 0 plays through once and holds silent |

`channelCount * 2` outputs, **interleaved** `pitch0, gate0, pitch1, gate1,
...` - not the flat `out0..outN` scheme `Utility.SplitterNode` uses.
`SplitterNode`'s outputs are interchangeable copies of one signal; a tracker
channel's pitch and gate are a semantically paired unit, and keeping them
adjacent means they land on adjacent rows in `Gui.NodeView`'s port layout,
so wiring one channel into one instrument stays visually local. `pitch` is
the currently-held note's frequency in Hz; `gate` is `1` for as long as a
note is sounding and `0` between notes, following the same rising-edge
trigger convention every other gate-driven node in this codebase uses -
`pitchPort(c)`/`gatePort(c)` (`c*2`/`c*2+1`) compute the interleaved index
for channel `c`, unchanged in shape across every channel-count variant.

## Pattern data model

Four classes, deliberately not one, because they serve different callers with
different constraints:

- **`TrackerSong`** is the song-level model both the GUI and the node speak:
  an ordered list of reusable `TrackerPattern`s plus an **order list** (a
  `List<Integer>` of pattern indices, which may repeat any pattern any number
  of times) - the pattern/order split every OpenMPT-style tracker uses to let
  a verse or chorus be authored once and replayed without duplicating its
  notes. A fresh `TrackerSong` starts with one empty pattern and an order
  list of `[0]`, so a brand-new `TrackerNode` behaves exactly like a
  single-pattern song. `channelCount` is a song-level property (every pattern
  in a song shares the same channel count); `addPattern`/`duplicatePattern`/
  `removePattern` manage the pattern list (`removePattern` also re-targets or
  drops order-list entries that pointed at the removed index, and refuses to
  drop the last remaining pattern), and `insertOrderEntry`/`removeOrderEntry`/
  `moveOrderEntry` manage the order list (which likewise refuses to shrink
  below one entry). `copy()` deep-copies every pattern and the order list -
  `Gui.TrackerEditorView`'s undo stack snapshots whole songs, not individual
  patterns, so adding/removing/reordering patterns is undoable exactly like a
  note edit. `withChannelCount(int)` maps `TrackerPattern.withChannelCount`
  across every pattern in the song - the song-level half of the
  channel-count conversion feature (see "Channel-count conversion" below).
- **`TrackerPattern`** is one pattern's note content - the same model as
  before multi-pattern support, now held inside a `TrackerSong` rather than
  directly by `TrackerNode`. A `record NoteEvent(int startStep, int
  lengthSteps, float pitch, NoteEffect effect)` per note (a 3-arg constructor
  overload defaults `effect` to `NoteEffect.NONE`, so every pre-existing call
  site that only cared about step/length/pitch still compiles unchanged),
  grouped into `channelCount()` per-channel lists (an instance field, set at
  construction), plus `totalSteps`/`stepsPerBeat`/`speed` and a per-channel
  display name. `speed` is ticks-per-row (default `6`, the ProTracker/OpenMPT
  convention), replacing what used to be a hardcoded `TICKS_PER_STEP`
  constant on `TrackerNode` - see "Effect scheduling" below for what it
  drives. `pitch` is a **continuous** semitone number - 69.0 is A4 exactly the
  way MIDI note 69 always was, but nothing rounds it to the nearest integer
  anywhere in the pipeline, so 69.5 (a quarter-tone sharp) or 69.07 (7 cents
  sharp) are both ordinary values, not a special case. `addNote` is the
  **only** place monophony is enforced: adding a note removes any existing
  note on that channel whose step range overlaps it. Hand-editing
  (`Gui.TrackerGridView`) and MIDI import both go
  through this one method, so there is exactly one code path for "a note
  lands in this pattern" - never two competing definitions of what counts as
  an overlap. Because a channel's list is therefore always sorted by
  `startStep` with no overlaps, `addNote` binary-searches for the insertion
  point and checks only the neighbours that could overlap, never rescanning
  or re-sorting the whole list. That matters because every bulk rebuild
  (`TrackerNode.getSong`, loading a saved song, changing the channel count,
  MIDI import) calls it once per note: the old scan-and-sort version was
  quadratic and froze the UI on large songs. `Sources.NoteName.format`/`.parse` (see [Sources](sources.md))
  is the one place a `float` pitch and a note name like `"A4+50c"` convert
  between each other, shared with the standalone `Sources.NoteNode` rather
  than duplicated as a tracker-only formatter. `withChannelCount(int)`
  returns a copy resized to a new channel count - channels that still exist
  keep their name and every note untouched; channels beyond the new count
  are dropped, growing adds empty new channels.
- **`NoteEffect`** (a nested record on `TrackerPattern`, alongside a
  `SlideMode` enum of `NONE`/`GLIDE`/`GLISSANDO`) bundles every per-note
  pitch/timing effect field into one value rather than telescoping
  `NoteEvent`'s constructor: `slideMode`/`slideSteps` (portamento into this
  note from whatever the channel was last sounding, `GLISSANDO` being the
  same glide quantized to whole semitones - ProTracker's `E3x` "glissando
  control" is literally this), `arpSemitone1`/`arpSemitone2`/`arpRateTicks`
  (fast cycling between the note's pitch and two offsets, classic tracker
  `0xy`), `delayFraction` (sub-step start offset, for groove/swing),
  `retriggerTicks` (re-fires the gate periodically within the note, for
  rolls), `cutTicks` (forces the gate off early, before the note's natural
  `lengthSteps` end). Deliberately does not include vibrato/tremolo - those
  don't need note-boundary awareness, so they're left to the existing
  generic `Envelopes`/`Effects` `VibratoNode`/`TremoloNode`, patched onto
  Tracker's own pitch/gate outputs, rather than duplicated inside
  `TrackerNode`. See "Effect scheduling" below for how `TrackerNode.process()`
  actually applies each field. A note's effect stack is drawn directly on it
  in the grid editor (`Gui.TrackerGridView`) using
  the exact text `NoteEffectText.format` produces - see "Editor screen" below.
- **`TrackTable`**/**`TrackSongTable`** (both package-private) are the
  immutable, primitive-array snapshots `TrackerNode.process()` actually
  reads, rebuilt whenever the song changes, never per sample. `TrackTable`
  is one pattern's snapshot (unchanged in shape from before multi-pattern
  support, plus the new `speed` field): `int[]` step/length arrays, a
  `float[] pitch` array, and a `NoteEffect[] effect` array per channel, plus
  a precomputed `float[] frequencyHz` (`440 * 2^((pitch-69)/12)`), so the hot
  path never calls `Math.pow` or walks a `List` for the common case of a
  flat, effect-free note (conventions 2 and 3); a sliding or arpeggiated note
  does call `Math.pow` per sample while active, since its target pitch is
  genuinely changing every sample - there is nothing to cache. `TrackSongTable`
  wraps a `TrackTable[]` (one per pattern) and an `int[] order` - see "Thread
  safety" below for why this is one object behind one `volatile` field rather
  than an array of independently-volatile patterns.

Velocity is deliberately **not** modeled, even as unused data on `NoteEvent`.
There is no output port for it and no product requirement for it yet; adding
a `velocity` field to `NoteEvent` later costs nothing today, so keeping it
out now is plain YAGNI rather than a forward-compatibility gap.

### Thread safety

`AudioEngine` runs `process()` on its own playback thread while the tracker
editor screen edits on the JavaFX Application Thread. `TrackerNode` holds one
`private volatile TrackSongTable snapshot`, replaced wholesale on every
edit - the same one-writer/one-reader idiom `Sources.ConstantNode` uses for
its single `volatile float`, generalized to a whole immutable object graph
instead of one primitive. Multi-pattern support deliberately kept this as
**one** object behind **one** volatile field (`TrackSongTable` bundling every
pattern's `TrackTable` plus the order array) rather than, say, a
`volatile TrackTable[]` of independently-swappable patterns - the latter
would let `process()` observe some patterns from before an edit and some
from after it within the same block, exactly the torn-read hazard this
single-object-swap idiom exists to prevent. `process()` reads the field
exactly once per block (`TrackSongTable song = this.snapshot;`) and only
touches `song`'s arrays for the rest of that block (switching which pattern
is "current" as playback crosses an order-list boundary mid-block still
reads from the same `song` snapshot), so a mid-block song edit from the GUI
thread can never produce a torn read.

This is also why `getSong()`/`setSong(TrackerSong)` copy rather than alias:
the GUI's working copy and the node's live snapshot are always two different
object graphs, so a GUI edit in progress can never be observed half-applied
by the audio thread, and vice versa.

## Scheduling (`TrackerNode.process()`)

Generalizes `Envelopes.TriggerNode`'s self-firing pattern (see
[envelopes.md](envelopes.md#triggernode)) from one pulse to `channelCount`
independently gated channels sharing one song position:

- **Session detection** follows convention 9 exactly: `blockStart =
  context.sampleIndex()` is compared against `expectedBlockStart`, the
  node's own previous block end - never against `0`, since `AudioEngine`
  builds a fresh `NodeContext` per Play and per Render-to-WAV, but reuses
  the same long-lived `Node` instances across both. On a new session, song
  position resets to order-list index 0/step 0 and every channel's note
  cursor and gate clear.
- **Tempo -> samples** is cached per convention 3: `samplesPerStep` is only
  recomputed when `bpm`, the current pattern's `stepsPerBeat`/`speed`, or the
  sample rate actually changed since the last sample.
- **Per-sample walk**: each sample writes the *current* held state for all
  `channelCount * 2` outputs, then advances the step position if playing. On
  every step boundary crossed, each channel's note cursor advances past any
  notes it has already stepped over and activates the note (if any) starting
  at the new step, holding its frequency and gate-high state until the step
  where that note ends. Output is written **before** the boundary-crossing
  advance within a sample, so a note occupies exactly
  `lengthSteps * samplesPerStep` samples, not one sample fewer - getting
  this order backwards was the one real bug the offline smoke test caught
  during development.
- **Crossing a pattern's last step advances the order list**, not just the
  step counter: `stepPosition >= table.totalSteps` increments `orderIndex`
  (wrapping to `0` when `loop` is set, exactly like `stepPosition` itself
  used to wrap within a single pattern before multi-pattern support), then
  looks up the next pattern via `song.patternAt(orderIndex)` and resets
  `stepPosition` to `0` for it. **Every channel's gate, note cursor, slide
  state, and pending-delay state are force-cleared at this transition** -
  the same per-channel reset `resetToStart()` already did on a `reset` edge,
  now shared through one `clearChannelGates()` helper. This is a deliberate
  fix, not incidental: before it existed, a note that was still sounding
  right up to a pattern's (or a looping pattern's) last step left `gateOn`
  stuck true across the boundary, because the old check
  (`gateOn[c] && step >= activeEnd[c]`) only ever ran against the *new*
  pattern's step numbering, and a small `activeEnd` from the *previous*
  pattern doesn't retroactively look expired once `stepPosition` has reset
  to `0`. A throwaway smoke test (see "Verification" - not checked into the
  repo, per this project's no-test-framework policy) confirmed the drop now
  happens at exactly the boundary sample. See "Legato / no-retrigger
  caveat" below for how this interacts with back-to-back notes.
- **`reset`** (rising edge) snaps every channel back to order-list index
  0/step 0 immediately, re-activating whatever note starts there. **`loop =
  0`** lets the song play through its order list once and then hold silent
  (all gates low) rather than wrapping back to the first order-list entry.

### Legato / no-retrigger caveat

Two notes placed back-to-back on the same channel with no gap - the second
note's `startStep` equal to the first note's `endStep` - never drop the gate
between them, because the gate only ever transitions low when a step has no
active note. A gate-edge-driven instrument (an `Envelopes.AdsrNode`-based
[`PatchNode`](modules.md)) reads this as one continuously held note, not two
separate ones. This is intentional and matches how a held analog gate/CV
pair behaves on real hardware, but it means an imported MIDI file that plays
legato will not retrigger the instrument's envelope between those notes -
see the MIDI import section below, since this is exactly the phrasing style
that produces adjacent, gap-free notes on import. This same back-to-back
condition is also exactly when a `slide`/`glissando` effect on the second
note engages (see "Effect scheduling" below) - a legato pair is precisely
the case where there is a previous held pitch to glide from, so slide and
the no-retrigger gate behavior are two sides of the same "these two notes
are really one continuous phrase" case.

**A pattern (or order-list loop) boundary is the one exception to this.**
Two notes placed back-to-back with no gap *within* a pattern stay legato as
described above, but a note ending exactly on a pattern's last step and a
note starting at step 0 of whatever comes next (a different pattern, or the
same one looping) always drops the gate between them - see "Scheduling"
above. A pattern boundary is treated as a full re-synchronization point, the
same as a `reset` edge, not a mid-phrase continuation; slide/glissando
likewise never engages across one, since `wasSounding` is force-cleared
there along with everything else.

## Effect scheduling (`TrackerNode.process()`/`activateStep()`)

All five `NoteEffect` fields are applied inline in `process()`'s existing
per-block/per-sample loop - no new ports, since effects are authored per
note in the pattern editor rather than driven by external CV, the same way
`lengthSteps`/`pitch` already work. Each pattern's `speed` (ticks-per-row,
default `6` - the ProTracker/OpenMPT convention, editable via the pattern
toolbar's Speed spinner) subdivides each step for
`arpRateTicks`/`retriggerTicks`/`cutTicks`; tick length in samples is
derived from `samplesPerStep` the same way `samplesPerStep` itself is
derived from `bpm` (cached, recomputed only when the inputs actually
change, per convention 3). `speed` used to be a fixed `TICKS_PER_STEP`
constant (`4`) on `TrackerNode` before multi-pattern support - promoting it
to a real, per-pattern value both matches real trackers (which allow a
speed change per pattern) and recalibrates every existing arp/retrigger/cut
effect to tracker-standard timing.

- **Slide/glide/glissando**: `activateStep` captures `wasSounding` (the
  channel's gate state) **before** the natural-end-of-previous-note check
  runs, not after - the two checks happen back to back for a legato pair,
  and reading `gateOn[c]` after the end check would always see `false` even
  for genuinely adjacent notes, silently disabling every slide (this was
  the first real bug the effects smoke-test suite caught: `wasSounding` has
  to be sampled at the top of `activateStep`, threaded through as a
  parameter into `activateNote`, never re-read from the field later). When
  a note with `slideMode != NONE` activates and `wasSounding` is true, the
  channel interpolates from `lastOutputSemitone[c]` (whatever pitch was
  actually sounding the instant before, which correctly captures mid-slide
  restarts, not just the previous note's flat target) to the new note's
  pitch over `slideSteps * samplesPerStep` samples, in semitone space
  (`Dsp.DspMath.lerp`, then the same `440*2^((semitone-69)/12)` conversion
  used everywhere else) - `GLISSANDO` rounds the interpolated semitone to
  the nearest integer before converting, `GLIDE` doesn't. Sliding from
  silence is deliberately not supported (there is no meaningful "from"
  pitch), so `wasSounding = false` skips straight to the flat, instant pitch
  assignment every note used before this feature existed.
- **Arpeggio**: while a note with `arpRateTicks > 0` is active and not
  currently sliding, the emitted pitch cycles through
  `{pitch, pitch+arpSemitone1, pitch+arpSemitone2}` at each tick boundary.
  Mutually exclusive with sliding per note (matching how real trackers treat
  these as alternative effect-column values, not combinable).
- **Note delay**: a note with `delayFraction > 0` is not activated
  immediately at its step boundary - `activateStep` instead records it as
  *pending* (`pendingActive[c]`/`pendingNoteIndex[c]`/
  `pendingDelayFraction[c]`) and the per-sample loop fires `activateNote`
  once `samplePosition` reaches `round(delayFraction * samplesPerStep)`,
  recomputed against the **current** `samplesPerStep` every sample rather
  than baked in at scheduling time - the first implementation baked the
  target sample count in when the delayed note was scheduled, using
  whatever `samplesPerStep` happened to hold at that moment, which is still
  its uninitialized default (`1`) the very first time `activateStep` runs
  (that initial call happens *before* the per-sample loop's own
  `bpm`-driven recompute), so every delayed note's first playback fired
  almost immediately instead of partway through the step - the effects
  smoke-test's delay case caught this exactly, and the fix was to store the
  fraction and re-derive the sample target from whatever `samplesPerStep`
  currently is at check time, not to precompute it once.
- **Retrigger**: at each tick boundary where `retriggerTicks > 0` and the
  elapsed tick count is a multiple of it, the gate is forced low for exactly
  one sample (`retriggerPulseSamplesLeft[c] = 1`) before returning high on
  the next sample - a real rising edge per convention 8, not just a
  continuously-high gate a downstream envelope would never re-trigger from.
- **Note cut**: once a note's elapsed tick count reaches `cutTicks` (if
  `cutTicks > 0`), the gate is forced low regardless of the note's remaining
  `lengthSteps` - checked every sample, a plain integer comparison, not
  something convention 3's coefficient-caching rule applies to.

## Song encoding (`TrackerStateCodec`)

`TrackerNode` is **not** `Stateful`. `saveSongData()`/`loadSongData(byte[])`
round-trip the whole song through `TrackerStateCodec.encode`/`decode`, a
custom binary layout. `Serialization.PatchCodec` stores the bytes as a
`data/<id>` zip entry of the `.ptr` archive and refers to them from the
node's JSON as `"song": "<id>"` (see [serialization.md](serialization.md)).
Compression is the archive's job, so the codec emits raw bytes and does no
deflating or base64 of its own. `Tracker` still does not depend on
`Serialization`: `PatchCodec` calls the node, never the reverse. That
dependency edge is documented as deliberately one-way except for `Modules`
(see [modules.md](modules.md)).

Layout, little-endian:

```
int32 channelCount
int32 patternCount
int32 orderLength
repeat orderLength times: int32 patternIndex
repeat patternCount times (pattern 0..patternCount-1):
    int32 totalSteps, int32 stepsPerBeat, int32 speed
    repeat channelCount times (channel 0..channelCount-1):
        int32 nameByteLength, then that many UTF-8 bytes (channel name)
        int32 noteCount
        repeat noteCount times:
            int32 startStep, int32 lengthSteps, float32 pitch
            int32 slideMode (SlideMode ordinal), int32 slideSteps
            int32 arpSemitone1, int32 arpSemitone2, int32 arpRateTicks
            float32 delayFraction, int32 retriggerTicks, int32 cutTicks
```

`channelCount` moved to the header once (every pattern in a song shares it,
per `TrackerSong`), and what used to be one pattern's worth of data is now
repeated `patternCount` times, each preceded by its own `totalSteps`/
`stepsPerBeat`/`speed` and preceded overall by the order list - this is the
song/order-list feature's format change. The per-note 44-byte record itself
is untouched. `pitch` was `int32` until microtonal support needed
fractional semitones; switching to `float32` kept every record at the same
byte width at the time, so no length field or version bump was needed for
that change alone - only the two `ByteBuffer` calls encoding/decoding that
one field changed. Both the original `channelCount`/`NoteEffect` addition
(44 bytes/note, up from 12) and this pattern/order-list restructuring are
genuine breaking format changes, unlike the `pitch` change - every existing
field's *meaning* stays the same, but the byte layout is no longer
compatible with what an older build wrote or reads, so each went through
`Serialization.PatchCodec.FORMAT_VERSION` (bumped to 3 for the original
`NoteEffect` addition; the song/order-list change bumps it again, to 5)
rather than staying a silent, in-place field change. Per this
project's serialization policy - "exactly one format version is read and
written; any other version is rejected on load, no compatibility branches
in the reader" - this means **every** existing `.ptr` file, not just ones
containing a Tracker node, needs re-saving with the new build before it will
load again; there is no fallback path that tolerates the pre-song single
flat pattern layout. This was a deliberate, confirmed tradeoff, matching how
`SplitterNode`/`MultiplexerNode`/`SequencerNode` already special-case their
own construction arguments in `PatchCodec` with no fallback branches -
`TrackerStateCodec` follows the same "no compatibility branch" discipline
`PatchCodec` already practices, just one level down, in its own binary
sub-format.

There is no nested version field inside the blob -
`PatchCodec.FORMAT_VERSION` is already the project's single global version
gate, and a private per-node version would duplicate that mechanism. Format
version 5 stored these same bytes deflated and base64-encoded in
`state["pattern"]`, with the length in `state["patternLength"]`.
`Serialization.FormatV5Migration` inflates that blob into a data entry
unchanged, because the byte layout itself did not change in version 6.

`TrackerNode`'s 4 inputs are structurally fixed regardless of channel count
(`bpm`/`play`/`reset`/`loop` never change), but its `channelCount * 2`
outputs are parametric on a construction-time argument, exactly like
`Conditionals.SequencerNode`'s step count - `NodeFactory` registers four
fixed-arity lambdas (`"Tracker 2"` through `"Tracker 16"`, each
`() -> new TrackerNode(N)`) rather than one plain no-arg constructor, and
`PatchCodec` needed a real `instanceof TrackerNode` branch: `saveNode`
writes `tracker.getChannelCount()` into a `"channels"` JSON field,
`loadNode` has an explicit `case TYPE_TRACKER -> new
TrackerNode((int) Json.numberOrDefault(json, "channels", 8))` rather than
falling through to the generic `default -> createFromFactory(type)` path -
the four palette names no longer share one `getName()` string
(`getName()` still always returns `"Tracker"`, so `typeOf`/`Docs.keyFor`
both special-case `TrackerNode` directly rather than relying on
`NodeFactory.nameOf`, the same treatment `OscillatorNode`/`PatchNode`/
`GranularPlayerNode` already get there). MIDI import still populates the
*active pattern* of an already-built node's song through
`Gui.TrackerEditorView.applyEdit` (the same commit path any other note edit
uses), not a constructor argument - `MidiImporter.importFile` itself is
still pattern-scoped (it returns one `TrackerPattern`, unaware of songs at
all) and takes the target channel count as an explicit parameter, rather
than reading a static constant, so it imports onto whatever size Tracker
the import was invoked from.

### Channel-count conversion (`Gui.GraphEditor.convertTrackerChannels`)

No existing utility in this codebase replaces one live node with another
while preserving wiring - `GraphEditor.materializeKnob` and
`Serialization.PatchDocument.buildPatch`'s `substitute` helper are both
close in shape but solve different problems (materializing a shadow knob;
rewriting `Connection`s at `Patch`-build time, not in the live editor).
`convertTrackerChannels` is new: it builds a replacement `TrackerNode` at
the target size, converts the whole song via `TrackerSong.withChannelCount`
(which maps `TrackerPattern.withChannelCount` across every pattern in it),
re-derives which of the old node's `Connection`s survive (the 4 input
connections always do; `pitchN`/`gateN` output connections survive when
`N` is still within the new channel count, dropped otherwise), and swaps
the node in as one undoable step.

Building this needed `GraphEditor.addNode`/`removeNode` split into a public,
history-**recording** half and a package-private, non-recording
`addNodeSilently`/`removeNodeSilently` half - the existing `restoreSnapshot`/
`deleteSnapshot` (used by ordinary add/remove undo) only ever run from
inside `EditHistory`'s own undo/redo replay, where a guard already suppresses
nested recording, so they could get away with calling the public, recording
`addNode`/`removeNode` directly. `convertTrackerChannels` needed to perform
its own multi-step swap **outside** of undo/redo replay (the first time the
user triggers it) while still recording exactly one composite
`EditCommand` for the whole operation - reusing the old recording methods
would have produced two separate undo entries (a "delete" and an "add")
instead of one atomic "convert channels" step, so both `addNode` and
`removeNode` now delegate to the silent primitives and only the public
wrapper records.

## MIDI import (`MidiImporter`)

Pure parsing, no JavaFX dependency - a sibling to how
`Oscillators.Implementations.WavetableLoader` sits outside `Gui` despite
only being used from there. Built on `javax.sound.midi`
(`MidiSystem.getSequence(File)`), a standard JDK API at the same tier as
`javax.sound.sampled` (already a project dependency via
[Audio](audio.md)) - no new external dependency, and no hand-rolled Standard
MIDI File byte parsing.

- **PPQ only.** A file using SMPTE time division is rejected with a clear
  error; supporting it is out of scope for v1.
- **Tempo** is read from the first `MetaMessage` of type `0x51`
  (set-tempo) found across any track; `120` BPM is assumed if none exists.
  Only the first tempo event is honored - mid-song tempo automation in the
  source file has no representation, since `TrackerNode`'s `bpm` is one live
  port, not a tempo track. The importer reports the detected BPM back to the
  caller so the user can set the node's `bpm` input to match; it does not
  reach into the graph and set it automatically, to avoid coupling the
  importer to `Gui.NodeView`'s knob/shadow-`ConstantNode` machinery for a
  convenience the user can do in one click.
- **Channel mapping is by MIDI channel number, not track index** - of the
  file's 16 possible channels, the first N (in ascending numeric order,
  where N is the target Tracker's channel count, passed into `importFile`
  explicitly rather than read from a static constant) that contain at least
  one `NOTE_ON` event map onto tracker channels 1-N. Track index was
  rejected because a Format 1 Standard MIDI File commonly spreads one MIDI
  channel's events across several tracks; channel number is the only stable
  identity across that split. Channels beyond the Nth with notes
  are dropped and counted in the import summary.
- **Truncate-on-retrigger.** Tracker channels are strictly monophonic (see
  `TrackerPattern.addNote` above), but a source MIDI channel can be
  polyphonic or chordal. When a new `NOTE_ON` arrives on a channel while a
  previous note on that same channel is still held, the importer ends the
  previous note at the new note's tick rather than overlapping them -
  last-note-priority collapse, the same choice a monophonic hardware voice
  makes. This is a real, audible lossiness for chordal input, and is
  reported in the import summary rather than silently dropped.
- **Quantization** divides each event's tick position by
  `sequence.getResolution() / stepsPerBeat` and rounds to the nearest step;
  `stepsPerBeat` comes from the pattern being imported into, so re-importing
  after changing that value re-quantizes at the new resolution. Every
  resulting note is added through `TrackerPattern.addNote` - the same entry
  point hand-editing uses, so an import can never produce an overlap that
  hand-editing would reject.

`MidiImporter.Result` also reports `mappedChannelCount` and
`skippedChannelCount`, both surfaced to the user in a summary dialog after
import.

## MIDI export (`MidiExporter`)

The inverse of the two importers, and like them pure `javax.sound.midi` with
no JavaFX dependency and no hand-rolled Standard MIDI File byte writing
(`MidiSystem.write(sequence, 1, file)`). One class with two static overloads
- `export(PianoRollClip, bpm, File)` and `export(TrackerSong, bpm, File)` -
because everything except walking the notes (building the sequence, the
set-tempo meta event, clamping, the trailing end-of-track event) is shared;
splitting it per editor would have duplicated that boilerplate for two loops
that differ by ten lines. Each editor screen exports on its own, from its own
"Export MIDI..." toolbar button, with no dependency on the other - a Tracker
node and a Piano Roll node are unrelated graph nodes, so there is no combined
"export the whole patch as MIDI" path and no shared export dialog.

- **One Format 1 track, resolution `PianoRollClip.TICKS_PER_BEAT` (96)** for
  both exporters. 96 is 2^5 * 3, so every `stepsPerBeat` a Tracker pattern
  can plausibly use (3, 4, 6, 8, 12, 16, 24, 32) converts to whole ticks;
  the Piano Roll's ticks are already in this unit, so its export is a
  straight copy with no rescaling at all.
- **Tempo** is written as the inverse of `MidiImporter.detectBpm`:
  `round(60_000_000 / bpm)` as three big-endian bytes in a `0x51`
  `MetaMessage` at tick 0. Microseconds-per-quarter is an integer, so a
  round trip returns the BPM to within one rounding step (140 comes back as
  140.00014), not bit-exactly - the editors' BPM controls are whole numbers,
  so this is never visible. The BPM comes from the editor, not the clip or
  song (neither stores one): the Piano Roll uses its preview-BPM spinner,
  and `TrackerEditorView` is handed the node's `bpm` knob value by
  `SynthApp.openTrackerEditor` (via `GraphEditor.knobValue`, the same call
  the Piano Roll editor already used), rather than the exporter reaching
  into the graph itself.
- **Channel** is `voice % 16` for the Piano Roll and the tracker channel
  index for a Tracker song, so re-importing lands notes back on the channel
  they came from. Voice/channel 9 reads as GM percussion in most players;
  that is accepted rather than remapped, since remapping would break the
  round trip.
- **A Tracker song exports its whole order list**, not just the pattern on
  screen, with each order entry's notes offset by the accumulated tick
  length of the entries before it. Ticks are accumulated in ticks rather
  than steps because patterns in one song may differ in `stepsPerBeat`. A
  note whose length overruns its pattern's `totalSteps` (which
  `TrackerPattern.setTotalSteps` allows, since it only drops notes that
  *start* past the end) is cut at that boundary, matching `TrackerNode`'s
  `clearChannelGates()` on every order advance - without the clamp the note
  would sustain into the next entry and its late `NOTE_OFF` could kill a
  same-pitch note that entry starts.
- **Each note's `NOTE_OFF` is added immediately after its own `NOTE_ON`**,
  iterating notes in start order. `Track.add` appends among events at equal
  ticks, so back-to-back same-pitch notes on one channel come out
  off-then-on and re-import as two notes. Collecting all the note-ons first
  and the note-offs afterwards would silently invert that at the boundary
  tick - keep the per-note pairing.
- **Velocity** is `round(velocity * 127)` clamped to 1-127, never 0 - a
  `NOTE_ON` with velocity 0 is a note-off by the MIDI spec, so a silent note
  would become a missing note. Tracker notes carry no velocity and export at
  127.
- **A trailing end-of-track event** is written at the clip/song length so a
  file whose last note stops early still reports its true length. Note that
  Petroleum's *own* importers derive length from the last note rather than
  this event, so trailing silence does not survive a round trip back into
  the editor; other DAWs read it.

Deliberately not exported, since MIDI has no lossless representation of them
and a lossy one would be worse than their absence: `TrackerPattern.NoteEffect`
(glide/glissando, arpeggio, note delay, retrigger, cut - these are per-tick
behaviours of `TrackerNode`, not note data) and `PianoRollClip`'s per-voice
pitch-bend curves. Microtonal pitches (both note records store pitch as a
`float`) round to the nearest semitone. An export is therefore a
notes-and-timing export, in the same spirit as the importer's documented
lossiness above - the `.ptr`/`.dsl` formats remain the lossless ones.

## MIDI Player (`MidiPlayerNode`)

A second, independent `Node` in this package for when a MIDI file should
play *as written* rather than be edited. The importer above is lossy by
design (quantized to the step grid, one note per channel, first tempo only);
`MidiPlayerNode` keeps all of that. It shares no code with `TrackerNode` -
the two scheduling models (step grid with tick effects vs. absolute seconds)
have nothing useful in common.

**Ports.** Inputs `play` (1), `reset` (rising edge, via `Dsp.EdgeDetector`),
`loop` (1). Outputs are three per voice, `pitchN`/`gateN`/`velocityN`
(`pitchPort`/`gatePort`/`velocityPort` = `N*3`, `N*3+1`, `N*3+2`), so a
voice pair wires straight into `InstrumentNode`'s `pitchN`/`gateN`. Voice
count is a constructor argument; `NodeFactory` registers
`"MIDI Player 2/4/8/16"` and `NodeCatalog` adds a custom-size entry, the
same arrangement as `Tracker`. Because `getName()` is plain `"MIDI Player"`
(not a registry key), `PatchCodec` special-cases it as `TYPE_MIDI_PLAYER`
with a `"channels"` field, and `Docs.keyFor` maps it to the one article.
Loading is a `Load` button on the node header (`Gui.NodeView`), which reads
the file, calls `loadMidi(bytes, name)` and shows a summary dialog.
There is no in-place voice-count conversion, unlike Tracker.

**Parsing happens once, at load, never in `process()`.** `parse` merges
every track's events into one list sorted by tick, with note-ons after
everything else at the same tick - so a set-tempo at tick T applies to
notes starting at T, and a note-off/note-on pair for the same key at the
same tick closes the old note before opening the new one. Walking that list
accumulates absolute seconds from `secondsPerTick`, which a set-tempo meta
event (`0x51`) updates - so every tempo change is honoured, not just the
first. SMPTE-division files are supported too (`1 / (fps * resolution)`
seconds per tick, tempo events ignored as the spec says). Note-ons pair with
offs FIFO per (channel, key); a velocity-0 note-on counts as an off; notes
never closed end at the last event; every note lasts at least 5 ms so a
zero-length drum hit still produces a gate.

**Voice allocation is also offline.** Notes are taken in start order; each
goes to the lowest-index voice whose previous note has ended. If none is
free, the voice whose note ends soonest is stolen and that note's end is
moved to the new note's start (`stolenNotes` counts these for the load
summary). The result is, per voice, a non-overlapping sorted
`double[] on/off` (seconds) plus `float[] hz/velocity` - an immutable `Song`
published through one `volatile` field. Doing this at load time is what
keeps `process()` allocation-free (convention 2): playback is just a cursor
per voice. Allocation depends only on the voice count, which is fixed per
node, so there's nothing to recompute at render time - including on a
sample-rate change, since times are in seconds and compared against
`position / sampleRate` per sample.

**Playback.** Session detection copies `TrackerNode`: a block whose
`context.sampleIndex()` isn't where the previous block ended - or a newly
published `Song` - restarts from zero (convention 9). Per sample, per
voice: end the current note once `time >= off`, then start every note whose
`on` has passed (a `while`, so notes shorter than a sample still advance the
cursor). When a note starts on a voice whose gate was high on the previous
sample, the gate is forced low for that one sample - the same one-sample
retrigger dip Tracker's retrigger effect uses - so back-to-back notes and
stolen voices re-fire a gate-driven envelope. The song length is the latest
note end or last event, whichever is later; at the end, `loop` > 0 restarts,
otherwise the node holds silent until `reset` or a new session.

**Persistence.** `PatchCodec` stores the original file bytes (`getMidiData()`)
as a `data/<id>` archive entry referenced by `"midi"`, plus the display name
under `"file"`, and re-parses them through `loadMidi` on load. Nothing is
saved by reference. A file that fails to parse
throws `IllegalArgumentException` from `loadMidi` before any field is
touched, so a bad load keeps the previous song.

## Piano Roll (`PianoRollNode`)

A polyphonic note clip - one linear timeline, no patterns or order list -
edited on its own screen (`Gui.PianoRollEditorView` + `Gui.PianoRollView`).
It exists alongside Tracker rather than as a second view of a Tracker
pattern (which is what it used to be) because the two models disagree on
the things a piano roll is for: a Tracker channel is strictly monophonic
and step-quantized, while a piano roll wants overlapping notes (chords),
off-grid timing, per-note velocity, and a hand-drawn pitch curve per voice.

**Data (`PianoRollClip`).** A clip length plus a list of
`Note(start, length, pitch, velocity)` records, times in **ticks** at a
fixed `TICKS_PER_BEAT = 96` (four beats to a bar). 96 divides evenly by 2,
3, 4, 6, 8, 12, 16, 24 and 32, so every grid the editor offers - down to
1/32 and both triplet grids - lands on whole ticks. The grid itself is an
editor setting only: a note's position never depends on the snap it was
placed with, and "Off" places at single-tick resolution. `pitch` is a
continuous semitone number like everywhere else in this package (69.0 is
A4); `velocity` is 0-1. Notes are kept sorted by start then pitch; `add`
replaces a note with the identical start and pitch (so dragging a note
exactly onto a twin merges them instead of stacking an invisible
duplicate) but otherwise allows any overlap. Notes starting at or past the
clip length are kept, drawn dimmed, and never played.

**Save format.** `PianoRollClip.encode`/`decode` - a format int (`1`), the
clip length, a note count, then 16 bytes per note (start, length as int;
pitch, velocity as float), little-endian. `PatchCodec` writes it as a
`PatchArchive` data entry under the node's `"clip"` key, with the voice
count under `"channels"`, exactly the way it treats Tracker's song blob -
so Diesel carries it as a base64 `data` block with no Diesel-side code.
The node's type string is `"Piano Roll"`; the palette entries are
`"Piano Roll 4"`/`"Piano Roll 8"`/`"Piano Roll 16"` plus a custom-size
entry, and `GraphEditor.convertPianoRollVoices` swaps a placed node for a
different voice count in place (no confirmation - the clip holds no
per-voice data, so nothing is lost by shrinking).

**Scheduling happens off the audio thread.** `setClip` turns the clip
into a `Schedule`: per voice, primitive `on`/`off` tick arrays plus
precomputed Hz and velocity arrays - the same shape `MidiPlayerNode` uses
(see "Explicit voices" below for how notes reach a voice). `process()` then only walks per-voice
cursors against a `double` tick position advanced by
`bpm / 60 * 96 / sampleRate` per sample, so a `bpm` input modulated
within a block is followed sample by sample with no allocation.

**Edits while playing do not restart the clip.** A new `Schedule` arriving
mid-session (every drag in the editor publishes one, for live preview)
triggers `resync()` instead of a restart: each voice's cursor is moved to
the first note starting after the current tick, and a note already
spanning the current tick keeps sounding without a new gate edge. Only a
new session (convention 9: `context.sampleIndex()` not where the last block
ended) or a rising `reset` restarts from tick 0; a new session also clears
the per-voice "gate was high" memory, so the first note of a fresh render
opens its gate on sample 0 rather than spending one sample on a retrigger
dip. Within a session, a voice that starts a note on the same sample its
previous note ended drops its gate for one sample, as `MidiPlayerNode`
does. At the clip end the tick wraps (`loop` > 0) and every voice rewinds,
or the node goes silent and reports playhead `-1` (`loop` = 0).

**Editor.** `PianoRollView` is one `Canvas` sized to its viewport with two
`ScrollBar`s, not a large canvas inside a `ScrollPane`: the keyboard gutter,
bar ruler and velocity lane stay pinned while the note grid scrolls, and a
long clip at a high zoom never asks for a canvas texture wider than the
screen. All layout goes through `tickToX`/`xToTick`/`pitchToY`/`rowAtY`/
`continuousPitchAtY`; `rowAtY` floors to a semitone row, while
`continuousPitchAtY` (used whenever Alt is held) reads the exact
fractional pitch for microtonal placement. Mouse gestures are a small mode
enum (`CREATE`, `MOVE`, `RESIZE`, `SELECT`, `VELOCITY`, `LENGTH`): each
press records the clip as `dragBase`, each drag rebuilds a `candidate`
from it and hands it to `PianoRollEditorView.previewEdit` (pushed straight
to the node for audible feedback, undo untouched), and release commits the
candidate through `applyEdit`. Moving snaps the grabbed note's start to the
grid and shifts the rest of the selection by the same amount, so notes that
were deliberately off-grid keep their offsets. `applyEdit` compares the
encoded clip before and after and skips the undo entry when nothing changed,
so a click that selects a note without moving it doesn't clutter undo. Undo
is a whole-clip snapshot stack local to the screen, like Tracker's.

**Explicit voices.** Every `Note` carries a `voice` - the index of the
pitch/gate/velocity output set that plays it - rather than being dealt out at
playback. That is what makes a per-voice pitch curve mean something: the user
has to be able to see which voice a note is on and aim at it. `buildSchedule`
therefore does no allocation any more; each voice plays its own notes in start
order, monophonically, and an overlap within one voice cuts the earlier note
at the later one's start (counted, together with notes on a voice `>=` the
node's voice count, in `stolenNotes`). The editor keeps chords easy anyway:
every placement path (create, move, resize, nudge, paste, duplicate) goes
through `PianoRollView.place`, which keeps a note's voice if it is still free
over the note's new span and otherwise reassigns it with
`PianoRollClip.freeVoice` - the lowest voice with the fewest overlapping
notes. `[`/`]` move the selection between voices explicitly with no
reassignment, since that is a deliberate choice. Notes are coloured by voice
(`ChannelColors`), and notes on voices the node lacks draw grey. MIDI import
assigns voices greedily with the same `freeVoice`, against the node's voice
count.

**Pitch curves (OpenUTAU-style pen), one per voice.** The clip holds a
`TreeMap<Integer, TreeMap<Integer, Float>>`: voice -> (tick -> cents), like
OpenUTAU's `PITD` curve but per voice, stored at a fixed `CURVE_STEP` of 4
ticks and linearly interpolated (0 where there is no point on either side). A
curve belongs to the voice's timeline, not to a note - it does not follow
moved notes. The pen draws relative to the nearest sounding note under the
cursor: the cents written are the cursor's continuous pitch minus that note's
centre (row centre is `pitch - 0.5` on screen), so the line drawn through each
note is its real output pitch. The first note a stroke lands on fixes the
stroke's voice (`penVoice`), and later points only look at notes on that
voice, so a stroke never jumps between the notes of a chord. The bend lane
edits the curve of `laneVoice` (the "Curve voice" selector, which also follows
the voice of the last stroke) on a fixed -1200 to +1200 cent scale.
`drawBend` fills every curve step between successive mouse positions; on
release `anchorBend` adds a 0 point one step past each end of the stroke if
nothing is there, so the edge doesn't interpolate across to the next stroke.
Erasing writes zeros and `pruneBend` drops zeros whose neighbours are zero or
missing (and the whole voice entry once it is empty).

The node bakes each voice's curve into `bendRatio[voice]`, a `float[]` of
`2^(cents/1200)` per curve step (`null` for a voice with no curve), and
`process()` interpolates one ratio per voice per sample - no `pow` on the
audio thread (convention 3). Save format 3 writes the voice after each note
and then a list of `(voice, point count, (tick, cents)...)` curves. Formats 1
and 2 still load: their notes get voices via `freeVoice` (up to 16), and a
format-2 clip-wide curve is copied onto every voice in use.

**Preview playback (`Gui.PianoRollPreview`).** The editor can play the clip by
itself - Play/Space, looping at a "Preview BPM" of its own (seeded from the
node's `bpm` knob via `GraphEditor.knobValue`) - and audition single notes as
they are placed, clicked or dragged to a new pitch. It never touches the real
graph: `PianoRollPreview` builds a standalone `Patch` of a private
`PianoRollNode` (fed the same clip on every `previewEdit`/`pushToNode`, so
edits are heard live), one voice instrument per voice, a `MixNode` at 0.25 per
voice, and an `AudioOutNode`. The voice instrument is a patch loaded as a
`Modules.PatchNode`, so its macros become input ports: `pitch` and `gate`
are required, `velocity` optional, and the roll's `pitchN`/`gateN`/
`velocityN` outputs are wired straight in. Each voice gets a fresh
`codec.load` of the bytes, because node instances are long-lived and must not
be shared between two patches.

The voice comes from `preview.ptr` in the per-OS config directory
(`PianoRollPreview.configDirectory()`: `$XDG_CONFIG_HOME/petroleum` or
`~/.config/petroleum`, `~/Library/Application Support/Petroleum`, or
`%APPDATA%\Petroleum`) and falls back to the bundled resource
`src/main/resources/preview.ptr` (Constant pitch -> Phasor -> Sine, Constant
gate -> ADSR, times a Constant velocity). The user file is re-read on each
Play so edits apply without a restart. If it fails to load or lacks the
macros, the editor says so once per session and uses the built-in voice.

Audio goes through `SynthApp`'s single `AudioEngine` via `setAudio(play, stop,
graphPlaying)`: starting a preview stops graph playback first, starting graph
playback stops the preview, leaving the screen stops it, and auditions are
skipped while either is playing. An audition plays a half-beat note with
`loop` 0 and stops the engine after 0.9 s. The preview has its own
`AnimationTimer` for the playhead, since `SynthApp`'s timer only runs during
graph playback.

The "Follow playhead" option pages the view sideways when the playhead
leaves it, and is ignored during a drag so the view never scrolls out from
under the mouse.

## Editor screen (`Gui.TrackerEditorView`)

A fourth `SynthApp` screen (`Screen.TRACKER`), not a modal dialog - matching
how `DocumentationView` is already `SynthApp`'s third screen rather than a
popup, and letting the tracker's live playhead reuse `SynthApp`'s existing
`waveformTimer` `AnimationTimer` instead of standing up a second redraw
loop. Reachable from an "Edit" button on a `TrackerNode`'s header in
`Gui.NodeView`, visible only for that node type, mirroring the existing
docs "?" button.

The screen holds one working `TrackerSong` (synced from the node on open,
pushed back into the node on every committed edit) and toggles between two
views over whichever pattern is currently active:

### Song, pattern, and order-list editing

A second toolbar row (`TrackerEditorView.buildPatternBar`) holds a
`ComboBox<Integer>` **pattern selector** ("Pattern 1", "Pattern 2", ...)
plus `+ Pattern`/`Duplicate Pattern`/`Delete Pattern` buttons, and a `Speed`
spinner alongside the existing `Pattern length`/`Steps/beat` spinners -
switching the selector re-renders `TrackerGridView`
against the newly-active pattern without touching the order list, exactly
like OpenMPT's own pattern-number field. A left-hand panel
(`buildOrderPanel`) holds the **order list** - a `ListView<Integer>` of
"step: Pattern N" entries with `+ Step`/`- Step`/`Up`/`Down` buttons and a
`Follow song` checkbox (on by default): clicking an order-list entry jumps
the pattern selector to that pattern for editing (without moving playback),
while `Follow song`, when checked, does the reverse during playback - the
pattern selector automatically follows whichever pattern
`TrackerNode.getCurrentOrderIndex()` reports as currently sounding, and the
currently-playing order-list row is highlighted (`orderListView.refresh()`
called only when that index actually changes, not on every playhead tick).

Pattern-level edits (add/duplicate/delete a pattern; insert/remove/reorder
an order-list entry) go through `TrackerEditorView.applySongEdit(Runnable)`,
a `TrackerSong`-level sibling of the note-level `applyEdit
(UnaryOperator<TrackerPattern>)` below - both push a `working.copy()` onto
the same undo stack first, so a note edit and a "add a pattern" edit undo
through the identical `Ctrl+Z` stack, one entry per action either way.
`applyEdit` itself now applies its `UnaryOperator<TrackerPattern>` against
`working.pattern(activePatternIndex)` - whichever pattern the grid
currently has open - and writes the result back into the song at that
same index, so every existing note-editing code path in
`TrackerGridView` needed no changes at all to become
pattern-aware; only `TrackerEditorView`'s own plumbing did.

- **`TrackerGridView`** - a step grid, rows = steps, one column per
  channel. It is a `Pane` sized to the whole pattern (the `ScrollPane`'s
  content), holding one unmanaged `Canvas` only as large as the visible
  viewport; `TrackerEditorView.syncGridViewport` repositions it on every
  scroll/resize and `draw` renders just the visible rows. A canvas sized to
  the full pattern exceeds the GPU's maximum texture size (16384px) on long
  songs - 32 channels by ~24k steps crashed the render thread. Click an empty cell to place a one-step note; drag downward to
  set its length; click-drag on an existing note's row re-sets where it
  ends (drag past its start to extend, release above its own start to
  shrink). Delete/Backspace removes the selected note; **right-click a note
  to delete it immediately**, no confirmation and no context menu - checked
  first thing in `onPressed` (`event.getButton() == MouseButton.SECONDARY`)
  so a right-click never falls through to the normal select/drag path, and a
  right-click on empty space is a no-op rather than placing a note, matching
  the rest of the app's convention that right-click means "remove this," not
  "act on this space" (`GraphEditor`'s node/wire delete works the same way).
  Mouse wheel over a selected note nudges its pitch by a semitone - coarse
  adjustment only, a
  `Shift`+wheel 10-cent variant was tried first for microtonal fine-adjust
  and dropped once **double-clicking a note** turned out to be both more
  precise and more discoverable than a modifier key nobody would find by
  accident. Both views log every semitone nudge (raw `deltaY`, the
  before/after note formatted through `Sources.NoteName`) to
  `Gui.InputDebugLog` alongside the graph editor's zoom events, for the same
  reason: a scroll-driven control is easy to misjudge from feel alone, hard
  to misjudge from the actual event stream.
- **Block selection, transpose, and copy/paste (`TrackerGridView` only)** -
  `Alt+Up`/`Alt+Down` extend the step range of the current selection from
  the cursor (`selectionAnchorStep`); `Alt+Left`/`Alt+Right` do the same
  across channels (`selectionAnchorChannel`), so a rectangular block of
  cells spanning any number of channels and steps is reachable from the
  keyboard alone, drawn as a translucent accent-colored rectangle in
  `draw()`. `Ctrl+C`/`Ctrl+X`/`Ctrl+V` copy/cut/paste the block (each note
  stored as a `channelOffset`/`stepOffset` pair relative to the block's
  corner, so paste re-anchors at whatever cell the cursor sits on when `V`
  is pressed); `Delete`/`Backspace` clears every note in the block instead
  of just the one under the cursor when a block is active. **Mouse wheel
  over an active block transposes every note in it** by one semitone
  (`Shift`+wheel: a full octave, `12` semitones) instead of requiring a
  single note under the cursor - the same `onScroll` handler, just
  generalized from "the note at the cursor" to "every note in the active
  selection." The clipboard itself lives on `TrackerEditorView`
  (`getClipboard`/`setClipboard`, a `List<ClipboardNote>`), not on the grid
  view, specifically so it survives switching which pattern is being
  edited - copy a phrase in pattern 0, switch the pattern selector, paste it
  into pattern 1, for free.
- **Double-click a note (or select it and press Tab) to retype its pitch and
  effect as one piece of text** - `TrackerEditorView` owns one shared inline
  `TextField` (`inlineEditor`), an unmanaged child layered into `viewStack`
  (the same `StackPane` the debug panel overlays, see [Gui](gui.md)) rather
  than a separate `TextInputDialog`/`Stage` - a popup window is a second
  top-level window a tiling window manager will place wherever it likes, not
  necessarily anywhere near the note it's editing, so the editor is embedded
  in the scene at the note's own position instead. `editInlineText(source,
  canvasX, canvasY, initialText, onCommit)` converts the note's canvas-local
  position to `viewStack`'s local space via `source.localToScene(...)` then
  `viewStack.sceneToLocal(...)` - the standard two-hop conversion for
  positioning one node relative to another that doesn't share its parent -
  so the field lands directly over the note however far the grid is
  scrolled. `TrackerGridView` computes that position from its own layout
  math (`GUTTER_WIDTH + channel * COLUMN_WIDTH`) and calls it through its
  `editNote(channel, existing)` helper.

  **One field, one text format, for both pitch and effect
  (`Petroleum.Tracker.NoteEntryText`).** `editNote` seeds the field with
  `NoteEntryText.format(pitch, effect)`: just the pitch
  (`Sources.NoteName.format`, e.g. `"C#3+25c"`) when the note carries no
  effect, or `"<pitch>|<effect>"` when it does, `<effect>` itself being
  `NoteEffectText.format`'s space-separated token run (see below). On
  commit, `NoteEntryText.parse(text, currentEffect)` splits on the first
  `|`: text before it is the pitch, read through
  `Sources.NoteName.parseFlexible` so either note-name notation or a bare
  semitone number (`"61.25"`) works; text after it (possibly empty) becomes
  the new effect via `NoteEffectText.parse`. **No `|` at all means "leave
  the effect exactly as it was"** - typing `C4` over an existing `A4|G4`
  note only retunes it, it doesn't silently strip the glide; typing `C4|`
  (an explicit, empty effect) is how you clear one. This is why `parse`
  takes the note's current effect as a fallback parameter rather than
  always defaulting to `NoteEffect.NONE` - a bare pitch retype is the common
  case and must not be destructive to whatever effect the note already had.

  **The focus grab is deferred through `Platform.runLater`, not called
  synchronously.** `editInlineText` runs inside the same `onPressed` handler
  that already called `requestFocus()` on the canvas itself (to give it
  mouse-click-to-focus behavior); calling `inlineEditor.requestFocus()`
  immediately afterward, in the same pulse as `setVisible(true)`, silently
  failed - a node's focus-eligibility isn't fully current until the scene
  graph has processed the visibility change, so the request was dropped and
  every keystroke kept going to the canvas underneath (which, for a
  piano-key keystroke, placed an unrelated new note instead of editing the
  one being double-clicked). Deferring both `requestFocus()` and
  `selectAll()` by one pulse is the same fix `restoreFocus()` (below)
  already used for the simpler canvas-to-canvas case; a headless test
  confirmed the field reports `isFocused() == false` immediately after
  `editInlineText` returns and only `true` one `Platform.runLater` pulse
  later.

  The callback returns `null` on success or an error message on failure,
  rather than applying the edit itself and reporting through a separate
  `Dialogs.showError` alert - a second popup would defeat the entire point
  of embedding the first one. On Enter, a failure keeps the field open with
  a red `invalid` border and the message as a `Tooltip`, the identical
  feedback `Gui.NodeView`'s inline note/formula fields already give (see
  [Gui](gui.md)) - deliberately reused rather than inventing a second visual
  language for "this text didn't parse." On focus-loss, a failure instead
  discards the edit silently: forcing focus back into a field the user is
  actively leaving would fight the platform, unlike Enter's explicit "I'm
  trying to submit this" intent. Escape always discards, valid or not.
  `commitInlineEditor`'s null-out-before-invoking-the-callback ordering
  matters because closing the field (`setVisible(false)`) itself triggers a
  focus-loss event synchronously, which would otherwise re-enter the same
  commit path mid-commit; nulling the pending callback first makes that
  reentrant call a no-op.

  Double-click is detected via `MouseEvent.getClickCount() == 2` inside each
  view's existing `onPressed` handler, ahead of the normal
  select-and-start-drag path - `dragChannel` is reset to `-1` first so the
  field doesn't leave a phantom drag armed for whatever mouse-up eventually
  follows it. Tab is checked in each view's `onKeyPressed`, against whatever
  note sits at the current selection/cursor, consuming the key so it doesn't
  fall through to JavaFX's default focus-traversal behavior. Both paths call
  the same `editNote`; the note's step position and length are read before
  the field opens and closed over, so the committed edit only ever changes
  `pitch`/`effect`, never nudging the note's timing as a side effect.

  **Double-clicking empty space places a one-step note first, then opens the
  same editor for it.** The first version only wired this up for the
  `existing != null` branch of `onPressed`, so double-clicking a cell with
  nothing in it yet did nothing at all - indistinguishable, from the user's
  side, from the editor being broken, since a double-click is the natural
  thing to try on an empty cell too. The `existing == null` branch now
  commits a `NoteEvent(step, 1, DEFAULT_NOTE)` through the same
  `applyEdit` a plain click-drag placement uses, then calls `editNote` on
  the note it just placed (its effect is `NoteEffect.NONE`, so the field
  opens with just the bare pitch, no `|`) - so a double-click always either
  edits what's there or creates something to edit, never a silent no-op.
### Keyboard editing (`TrackerGridView`)

The piano roll that used to be a second view of the same pattern moved out
into its own node (`PianoRollNode`, below) - the Tracker editor is now the
grid alone, keyboard-driven like a real tracker's pattern editor. The
key layout is the standard tracker piano-key mapping popularized by Impulse
Tracker and FastTracker 2 and still OpenMPT's default today, reusing the same
two-and-a-bit QWERTY rows every tracker musician already has muscle memory
for:

```
 2  3     5  6  7     9  0
Q  W  E  R  T  Y  U  I  O  P
   Z  X     V  B  N  M
```

`Z` is the root of the current octave; `S`, `X`, `D`, ... walk up the
chromatic scale exactly like the black/white key alternation on a real
piano, wrapping onto the `Q` row for the next octave up and continuing onto
`I`/`9`/`O`/`0`/`P` for a partial third octave, all without leaving the home
row cluster. The mapping is a static `Map<KeyCode, Integer>` of semitone
offsets in `TrackerGridView`; a key that presses a semitone `offset` on
octave `baseOctave` writes pitch `(baseOctave + 1) * 12 + offset` - an
integer-valued `float`, exactly like before microtonal support landed, since
keyboard entry only ever produces whole semitones. `Sources.NoteName` uses
the same `octave * 12` formula in reverse for the `C4`-style labels drawn on
notes (plus a cents suffix when the pitch isn't integer-valued), so a note
typed at octave 4 on `Z` reads back as `C4`. Reaching a fractional pitch
from the piano-key layout itself is impossible - it's already fully claimed
by the semitone mapping and the `Shift`/`Ctrl` modifiers it uses for length
and octave - so it needs the inline text editor instead: double-click a
placed note, or press **Tab** on the grid cursor to reach it without a mouse.

Everything a mouse gesture already commits through `TrackerEditorView.
applyEdit`, keyboard entry commits the same way - there is no second,
parallel edit path:

- **A mapped key** places a one-step note at the cursor (or, if a note
  already starts exactly there, keeps its existing length and only changes
  its pitch - typing over a note corrects it rather than truncating it back
  to one step).
- **Arrow keys** move the cursor: `Up`/`Down` by one step within the current
  channel, `Left`/`Right` to the adjacent channel. `Home`/`End` jump to the
  first/last step of the current channel; `Page Up`/`Page Down` jump by one
  bar (`stepsPerBeat * 4` steps), matching the grid's own bar shading.
- **`Delete`/`Backspace`** clears whatever note starts at the cursor, without
  moving it - the keyboard equivalent of the existing per-note Delete
  behavior, now reachable without a mouse click first.
- **`Shift+Up`/`Shift+Down`** grow or shrink the note starting at the cursor
  by one step, clamped to at least one step and to the pattern's length.
  Real trackers have no equivalent shortcut, because a classic tracker cell
  has no explicit duration - a note plays until the next event or an
  explicit note-off/cut row. `TrackerPattern.NoteEvent` stores an explicit
  `lengthSteps` instead (see above), so entering a note's *duration* from the
  keyboard needed a gesture no tracker keymap already defines; `Shift`+the
  same keys already used for cursor movement was the smallest addition that
  didn't collide with the piano-key or navigation bindings.
- **`Ctrl+Up`/`Ctrl+Down`** shift the base octave, clamped to `[-1, 8]`
  (the same MIDI octave range `Sources.NoteName` can label). The toolbar's
  `Octave -`/`Octave +` buttons and label call the identical
  `TrackerGridView.setBaseOctave`, so the displayed octave always agrees
  regardless of whether it changed from the keyboard or the buttons -
  `setBaseOctave` is the one place the value changes, and it fires the
  listener that updates the label every time, not just when the buttons are
  clicked.
- **A deliberate omission: no note-off/note-cut key.** Classic trackers need
  one because a cell with no explicit length must be told when to stop
  sounding. Every `TrackerPattern.NoteEvent` already carries an explicit
  `lengthSteps`, so there is no ambiguity a note-off marker would resolve -
  shortening a note (`Shift+Up`) or deleting it (`Delete`) already covers
  what a note-off key would do in a tracker with implicit note duration.

**Auto-advance ("edit step")**: a `Spinner<Integer>` in the toolbar
(0-16, default 1) sets how many steps the cursor moves down after each
mapped-key note entry - OpenMPT's own "edit step" field, under the same
name. `0` disables auto-advance entirely (typing repeatedly overwrites the
same cell, useful for auditioning a pitch); mouse actions, arrow-key
navigation, and `Delete` never consume edit-step - only a successful note
entry does, matching OpenMPT's behavior that only *data entry* advances the
cursor, not every keystroke.

The cursor is drawn as an outlined rectangle over whichever cell it currently
sits on (`TrackerGridView.draw`'s final pass, always on top of any note fill
so it stays visible even over a long note), independent of note selection
highlighting - the cursor can sit on an empty cell with nothing to select,
which the accent-colored note highlight alone could never show. Pressing any
mapped key or `Delete` for the first time in a fresh pattern (before any
mouse click has set a cursor) starts the cursor at channel 0, step 0, so the
keyboard is usable immediately after opening the editor - `TrackerEditorView.
open` also requests focus via `restoreFocus()` (below) so the first keypress
does not require a click first.

**Escape clears the accent highlight, not the cursor position.** The grid
track `selectedChannel`/`selectedStep` (the cursor/selection coordinate) and
a separate `noteHighlightVisible` flag (whether the note under that
coordinate, if any, gets drawn in `Theme.ACCENT`). A mouse click or any
cursor-moving keyboard action (`setCursor`) sets the flag back to `true`; `Escape` sets only the flag to
`false` and leaves the coordinate untouched, then redraws. Earlier, Escape
reset the coordinate itself to `(-1, -1)` - visually indistinguishable from
today's "highlight off," but functionally different: the grid view's
`ensureCursor()` (called at the top of every subsequent keypress) would then
snap the cursor to channel 0, step 0 on the very next key, silently
discarding the position mid-edit. Leaving the coordinate alone means Escape
now does exactly one thing - stop showing this note as selected - without
also relocating where the next keystroke lands, so `Delete`, the piano-key
bindings, arrow-key navigation, and the scroll-wheel pitch nudge (all of which read `selectedChannel`/`selectedStep` directly) keep working
against the same cell right after dismissing the highlight.

### `ScrollPane` steals focus back on every click

The grid lives inside a `ScrollPane` (`gridScroll` in `TrackerEditorView`). `ScrollPane`'s own `ScrollPaneBehavior` binds
`MOUSE_PRESSED` to `getNode().requestFocus()` on the `ScrollPane` itself, and
that binding fires on *any* `MOUSE_PRESSED` that bubbles up through it
unconsumed - which every click on the grid did, since its `onPressed` never
called `event.consume()`. The sequence
on a real click was: the view's own `onPressed` calls `requestFocus()` and
gives itself focus, then the same event keeps bubbling, reaches the
`ScrollPane`, and `ScrollPaneBehavior` immediately hands focus right back to
the `ScrollPane` - so by the time any key is pressed afterward, the
`ScrollPane` (not the view) is the actual focus owner and target. Its own
`InputMap` binds `UP`/`DOWN`/`LEFT`/`RIGHT`/`PAGE_UP`/`PAGE_DOWN`/`HOME`/`END`
to scrollbar-nudging, which is what "arrow keys just scroll the viewport"
actually was - not a bug in either view's own key handling (which was never
reached), and not fixed by anything done to `Escape` specifically, since
Escape was equally unreachable once focus had already moved. The fix is one
`event.consume()` at the top of each view's `onPressed`, right after its own
`requestFocus()` call, so the press never continues bubbling to the
`ScrollPane` in the first place. Confirmed against the actual OpenJFX 21.0.2
source (`ScrollPaneBehavior`/`ScrollPaneSkin`) rather than guessed, since
this environment has no display to click-test interactively.

### Keeping the cursor in view

`TrackerEditorView.scrollGridIntoView(x, y, w, h)` nudges the `ScrollPane`'s
`hvalue`/`vvalue` just enough to bring a cell's local-space rectangle back
inside the viewport - a no-op if it's already visible, a minimal scroll
(never re-centering) otherwise, the same `scrollable = max(0, content -
viewport)` / `value = offset / scrollable` math `SynthApp.zoomAt`/
`centerView` already use for the graph canvas (see [Gui](gui.md)).
`TrackerGridView.setCursor` calls it on every arrow-key/Home/End/Page
move (and therefore after piano-key note entry too, since
`enterNoteAtCursor` moves the cursor through `setCursor`). Mouse clicks and
in-progress drags don't call it: whatever was just clicked was, by
definition, already on screen, and following a live drag around would fight
the user's own mouse-driven scrolling instead of helping it.

### Focus restoration

JavaFX gives keyboard focus to whatever control was last clicked, so
clicking any toolbar button (`Import MIDI...`, `Export MIDI...`, `Undo edit`,
`Octave -`/`Octave +`) or leaving
the edit-step `Spinner` moves focus onto that control - and once focus is
there, typed keys go to it instead of the pattern view, silently breaking
keyboard note entry until the user clicks back into the grid. Every one of
those actions therefore ends by calling `TrackerEditorView.restoreFocus()`,
which requests focus back onto `gridView`, wrapped in `Platform.runLater` so it runs after - and
wins against - the button's own default click-to-focus behavior in the same
pulse.

**`TrackerGridView` only resizes** - dragging an existing note there
changes where it ends, never where it starts; moving one means deleting it
and placing a new one, to keep the grid's drag-gesture code to one unified
model.

**A note carrying a non-default effect renders its full effect stack
in-place, on the grid.** `TrackerGridView.draw()` draws
`NoteEffectText.format(note.effect())` - the identical token text
(`"G4 A3,-2 D0.25"`, etc.) the inline editor reads back through
`NoteEntryText.parse` - directly on the note, after its pitch, in
`Theme.TEXT_DIM` so it reads as secondary to the pitch (`Theme.TEXT_STRONG`).
Both texts are clipped to the note's own rounded rectangle via
`GraphicsContext.save()/beginPath()/rect()/clip()/restore()` so a long
effect stack truncates at the column edge instead of bleeding into the next
channel's column - `COLUMN_WIDTH` widened from `96` to `132` to give the
common one-or-two-token case room without truncating at all. What's drawn
is therefore always exactly what double-clicking the note (or pressing
**Tab**, see below) would show in the inline editor - no separate summary
representation to keep in sync.

**Typing effects as text (`Petroleum.Tracker.NoteEffectText`/`NoteEntryText`).**
The spinner panel is discoverable but slow for the phrase-level editing a
real tracker session is - dozens of notes each getting the same one or two
effects. Double-click a note, or select it and press **Tab** (the grid cursor
cell), to retype it as one string in
the shared inline editor described above: bare pitch (`"C4"`) to retune
without touching its effect, or `"<pitch>|<effect>"` to set both at once
(`"A4|G4 D0.25"`), or `"<pitch>|"` to explicitly clear the effect. The `|`
is the boundary `NoteEntryText.parse` splits on before handing the second
half to `NoteEffectText.parse` - see the note above for why a bare pitch
deliberately leaves the effect alone rather than resetting it.

`NoteEffectText`'s own syntax, the part after `|`, is a whitespace-separated
run of `<letter><value>` tokens, order-independent, letters matching the
panel's own field names so the two stay one mental model rather than two:
`G4`/`Q4` (glide/glissando, `slideSteps`), `A3,-2` or `A3,-2,2`
(`arpSemitone1,arpSemitone2[,arpRateTicks]`), `D0.25` (`delayFraction`),
`R4` (`retriggerTicks`), `C8` (`cutTicks`). Each token is range-checked
against the same bounds the panel's spinners already enforce
(`slideSteps`/`cutTicks` 0-64, `arpSemitone1`/`2` -48..48, `arpRateTicks`/
`retriggerTicks` 0-16, `delayFraction` 0-0.95); an out-of-range value or an
unrecognized letter rejects the whole edit with the same red-border/tooltip
feedback `editInlineText` already gives pitch typos, per its own doc
comment - `NoteEntryText` never partially applies a pitch with a rejected
effect. `NoteEffectText.format`/`.parse` and `NoteEntryText.format`/`.parse`
round-trip exactly, mirroring `Sources.NoteName`'s parse/format pair rather
than inventing a different shape for what is the same kind of problem.
Committing goes through the same `applyEdit` remove-then-add path every
other note edit uses, so undo and the sidebar panel (which reads `working`
after every `applyEdit`) stay in sync automatically - typing `A4|G4` in the
grid updates the panel's slide combo and spinner without any separate
wiring.

**Undo** is a small screen-local stack of whole-`TrackerSong` snapshots,
covering both note edits and song-structure edits (adding/duplicating/
deleting a pattern, inserting/removing/reordering an order-list entry) -
`applyEdit` and `applySongEdit` both push `working.copy()` before mutating,
so the two kinds of edit share one `Ctrl+Z` stack with no special-casing at
the undo/redo call sites. It is deliberately **not** wired into
`GraphEditor`'s existing topology undo/redo history (`EditHistory`/
`EditCommand`, see [gui.md](gui.md)) - folding potentially thousands of
individual note edits into that history would be a materially larger
integration for uncertain benefit, and the two histories serve different
scopes (graph structure vs. one node's internal data) that a single Ctrl+Z
stack would blur. Undoing past a pattern add/delete can move
`activePatternIndex` out of range for the restored song, so both `undo`/
`redo` clamp it (`Math.min(activePatternIndex, working.patternCount() -
1)`) immediately after restoring, before refreshing the pattern
selector/order list/grid.

During a drag, the grid calls `TrackerEditorView.previewEdit` with a
transient candidate pattern - this pushes a whole-song copy with just the
active pattern swapped for the candidate straight to the node's live
`TrackSongTable`, for audible feedback while dragging, without touching the
undo stack or the committed `working` song. Only mouse-release calls
`applyEdit`, which snapshots the pre-edit song for undo, commits the
change, and pushes it to the node.

## A worked patch

```
Tracker.bpm <- Constant(140)
Tracker.pitch0 --> Instrument 1 (patch).pitch
Tracker.gate0  --> Instrument 1 (patch).gate
Tracker.pitch1 --> Instrument 2 (patch).pitch
Tracker.gate1  --> Instrument 2 (patch).gate
...
Instrument 1 (patch).out --+
Instrument 2 (patch).out --+--> Mix --> Audio Out
```

Each `Instrument N (patch)` is a saved patch loaded as a node (see
[Modules](modules.md)) with `pitch` and `gate` macros exposed, so the
tracker's per-channel pitch/gate pair drives it exactly like a MIDI
keyboard would - up to as many instruments as the placed Tracker has
channels, playing one song from a single `TrackerNode`.
