# Sources (`Petroleum.Sources`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Oscillators`, not nested under `Graph`. Nodes here originate
signals rather than transform an existing one.

- **ConstantNode** - no input ports, one output holding a settable
  `float value`. `getValue()`/`setValue(float)` are the GUI's handle for a
  live knob: the value is backed by a `volatile float`, so a GUI thread can
  call `setValue` while an audio thread reads it every block with no locking
  - safe because there's exactly one writer (the GUI) and one reader (the
  audio engine). Registered in `NodeFactory` as `Constant`, alongside
  `Phasor` and `Audio Out` below - this is also the node type
  [Gui.NodeView](gui.md) uses internally as an invisible "shadow" default
  for every unconnected input port on every other node. Since `process()`
  now fills a whole block at once, `value` is read exactly once per block
  (into a local, then `Arrays.fill`d across `outputs[OUT][0, frameCount)`)
  rather than once per sample - a GUI knob change is therefore only visible
  starting at the next block boundary, quantizing its audible effect to
  block granularity (e.g. ~23 ms at 44.1 kHz with 1024-sample blocks), not a
  functional change from the prior per-sample read since `value` was already
  read fresh every call, just a coarser update rate.
- **NoteNode** - `ConstantNode`'s shape (no input ports, one output, a
  `volatile` field read once per block and `Arrays.fill`d across it) with a
  musical-notation front end instead of a raw number: `setNoteText(String)`
  parses scientific pitch notation (`Sources.NoteName.parse`, e.g. `"A4"`,
  `"C#3"`, `"Bb5+25c"`) into a continuous semitone pitch and immediately
  converts that to a frequency in Hz (`440 * 2^((pitch-69)/12)`, cached in
  `frequency` rather than recomputed every block), which is what `process()`
  actually outputs - every "pitch" port elsewhere in the codebase (the
  tracker's `pitch0..pitch7`, `Utility.PitchToTimeNode`) is already a
  frequency in Hz by convention, so `NoteNode` outputs the same unit rather
  than a raw semitone number a downstream node would have to convert itself.
  Implements `NoteSource` (`getNoteText()`/`setNoteText(String)`, the latter
  throwing `IllegalArgumentException` on unparseable input) purely so
  `Gui.NodeView` can detect it generically - the same role
  `Expression.FormulaSource` plays for formula-editing nodes - and
  `Graph.Stateful`, saving/loading just the note text (`saveState` never
  persists the derived `frequency`; `loadState` re-parses it, the same
  derive-don't-duplicate choice `ExpressionOscillator` makes for its
  `MathExpression`). `getName()` returns `"Note (" + noteText + ")"`, so the
  node's header always shows the current note - dynamic `getName()` the same
  way `Wavefolder`/`ExpressionOscillator` report their live state (see
  [Oscillators](oscillators.md)), which is also why editing the text needs to
  call `Gui.NodeView.refreshTitle()` and not just `setNoteText` (see
  [Gui](gui.md)).
- **NoteName** (a plain utility class, not a `Node`) - `parse(String)`/
  `format(float)`, the one place a note name and a continuous semitone pitch
  convert between each other. `parse` reads a letter `A`-`G`, an optional `#`
  or `b`, a signed octave number (`C4` is MIDI 60/440's A4 minus 9 semitones,
  matching scientific pitch notation and every other octave-numbering
  convention already in this codebase - see `Tracker`'s keyboard mapping),
  and an optional trailing `+N`/`-N` (an optional trailing `c` is accepted
  and stripped, not required) read as a cents offset added to the semitone
  value - `"A4+50"` and `"A4+50c"` both parse to `69.5`, a quarter-tone sharp
  of A4. `format` is the inverse for display: rounds to the nearest semitone
  for the letter/octave, then shows whatever's left over as a signed cents
  suffix (`69.5` formats as `"A#4-50c"` - nearest-semitone rounding breaks
  the A4/A#4 tie upward, an arbitrary but consistent choice, not a bug - both
  spellings name the identical pitch), omitting the suffix entirely when the
  pitch is already an exact semitone. `Gui.TrackerGridView`'s note labels and
  `NoteNode`'s frequency conversion both go through this one class rather
  than each rolling their own note-name math - `Gui.NoteNames` (the
  tracker-only display formatter that predated microtonal support) was
  deleted once this could subsume it, rather than kept as a second,
  narrower implementation of the same formula.

  `parseFlexible(String)` is `parse`'s permissive sibling: tries
  `Float.parseFloat` on the trimmed text first and only falls back to
  `parse` if that fails, so `"61.25"` and `"C#3+25c"` both resolve to the
  same pitch. It exists for `Gui.TrackerGridView`/`PianoRollView`'s
  double-click-to-retype-a-note dialog (see [Tracker](tracker.md)), where a
  synthesist thinking in raw semitones shouldn't have to convert to a note
  name just to type a number - `NoteNode`'s own text field deliberately
  keeps using plain `parse`, since a bare number typed there would be
  ambiguous with nothing marking it as a semitone count rather than, say,
  Hz.
- **PhasorNode** - a single phase-accumulator class parameterized at
  construction along two independent axes, covering the eight phasor
  variants as eight `NodeFactory` registrations rather than eight classes:
  - **rate vs. period** (`periodDriven`): rate-driven phasors take one
    `frequency` input port (default 440) and increment by `range * frequency
    / sampleRate` each sample; period-driven phasors take one `period` input
    port (default 1, in seconds per cycle) and increment by `range / (period
    * sampleRate)`, skipping the increment entirely when `period` is exactly
    `0` (holding the phase rather than dividing by zero or producing `NaN`)
    and running backwards on a negative `period`, same as a negative
    `frequency`. `period = 1/f` and `frequency = f` always produce the same
    signal - the two are reciprocals of the same accumulator.
  - **radian vs. unit** (`range`): `TWO_PI` for the radian phasors that feed
    `OscillatorNode.phase` (whose `IOscillator` contract expects
    `[0, 2*PI)` - see [Oscillators](oscillators.md)); `UNIT_RANGE` (`1f`)
    for the unit phasors that feed everything else - a `[0, 1)` control
    signal is the natural shape for a `mix`/`gain`-style port, or for
    [`MapRangeNode`](utility.md) to sweep any other range without dividing
    radians away first.
  - **saw vs. ping-pong** (`pingPong`): a saw phasor emits the raw
    accumulated `phase`, wrapped with the project's no-`%` convention
    (`if (phase >= range) phase -= range;`). A ping-pong phasor folds that
    same phase on the way out instead - `phase < range/2 ? phase*2 :
    (range-phase)*2` - so it ramps up then back down (`0 -> range -> 0`)
    once per cycle instead of jumping from just under `range` back to `0`.
    The fold reads the phase before the increment, keeping the
    output-then-advance order, so a fresh node's first sample is always `0`;
    folding on output rather than in the accumulator keeps one cycle meaning
    one full period either way. A saw phasor's end-of-cycle jump is audible
    as a click once it drives a cutoff or gain port - a slow ping-pong
    phasor is a clean back-and-forth sweep instead, and (radian, ping-pong)
    feeding `OscillatorNode.phase` plays the wrapped waveform forward then
    backward within each cycle, one octave down with a mirrored second half.

  All eight combinations share one `process()`: a single input port at
  index `DRIVER` (named `"frequency"` or `"period"` depending on
  `periodDriven`), read per sample so it can be modulated at audio rate,
  and the same wrap/fold arithmetic scaled by `range`. `getName()` returns
  the constructor-supplied display name, which is also the exact
  `NodeFactory` registry key for that combination (`Phasor`, `Period
  Phasor`, `Unit Phasor`, `Unit Period Phasor`, `Ping Pong`, `Period Ping
  Pong`, `Unit Ping Pong`, `Unit Period Ping Pong`) - see `graph.md`'s
  `NodeFactory.nameOf` section for why that equality matters.
- **RandomNode** - draws a fresh random value on each rising edge of
  `trigger`, and holds it in between. Ports: `trigger`, `min` (default `0`),
  `max` (default `1`); the draw is uniform in `[min, max]`, read at the moment
  of the edge so both ends are modulatable.

  It emits a value immediately on the first block rather than waiting for an
  edge, so an untriggered `Random` is a fixed random constant rather than
  silence - useful on its own for per-instance variation.

  Deliberately separate from the noise oscillators in
  [Oscillators](oscillators.md), which produce a new value *every sample* to
  make a spectrum. `RandomNode` produces one value per event, for control
  rather than audio: a random pitch per shot, a random filter cutoff per hit.
  Pair it with [`Conditionals.LatchNode`](conditionals.md) when several
  parameters must be randomized off the same instant.
- **LfoNode** - a dedicated low-frequency-oscillator convenience: internally
  it is exactly a unit-radian `PhasorNode` (`frequency` port, default `2`
  Hz - modulation range rather than `PhasorNode`'s audio-range 440 default -
  the same `TWO_PI`-wrapped `phase` accumulator, output-then-advance,
  no-`%` wrap) with one of four waveform shapes read off that phase instead
  of the raw ramp, plus a `reset` port. It exists as its own node - rather
  than requiring a `Phasor` wired into an `OscillatorNode` every time a
  patch needs a modulation-rate wave - the same reasoning that gives
  sfxr-style one-shots `Envelopes.EnvelopeNode`/`AdsrNode` instead of making
  every patch build one from `Ramp`s by hand: a very common shape, made one
  node instead of several wired by hand.

  The four shapes are registered as four separate `NodeFactory` entries
  sharing one class (`LFO Sine`, `LFO Triangle`, `LFO Square`, `LFO
  Sawtooth`), the same collapse `PhasorNode` itself went through - a
  `Shape` enum carries a `label()` matching each registry key exactly
  (`getName()` returns `"LFO " + shape.label()`), the same
  `NodeFactory.nameOf`-safe pattern `BiquadFilterNode.FilterType` uses (see
  [Graph](graph.md)). Formulas, all as a function of `phase` in
  `[0, 2*PI)`: sine is `sin(phase)`; triangle is the exact identity
  `(2/PI)*asin(sin(phase))` (phase-aligned with sine - both are `0` and
  rising at `phase = 0` - rather than an independently-derived piecewise
  ramp); square is `phase < PI ? 1 : -1`; sawtooth is the plain bipolar ramp
  `2*(phase/TWO_PI) - 1`. All four are bipolar (`[-1, 1]`); a unipolar
  `[0, 1]` LFO is `MapRangeNode` downstream, the same way a unipolar phasor
  is a separate `PhasorNode` registration rather than a mode flag on this
  node.

  `reset` is a `Dsp.EdgeDetector`-driven rising-edge trigger (convention 8)
  that zeroes `phase` immediately, so the very next sample reads the shape
  at `phase = 0` - useful for syncing an LFO's cycle to a note-on or a
  sequencer step rather than letting it free-run out of phase with the
  rhythm. `PhasorNode` itself has no equivalent reset port; it was left
  alone rather than growing a new port that only this use case needs.
- **MidiInputNode** - live MIDI keyboard input: no input ports, three
  outputs (`pitch` in Hz, `gate`, `velocity` in `[0, 1]`). Built on
  `javax.sound.midi` (`MidiSystem`, `MidiDevice`, `Transmitter`, `Receiver`),
  the same JDK API [`Tracker.MidiImporter`](tracker.md) already uses for
  offline Standard MIDI File import - this is the realtime counterpart, no
  new dependency. Starts disconnected (`getDeviceName()` returns `null`) and
  outputs silence until a device is chosen; [Gui](gui.md)'s "MIDI..." header
  button (shown for this node type, alongside the existing `Edit`/`Draw`
  buttons `TrackerNode`/`InstrumentNode`/drawn-waveform oscillators get)
  lists `listAvailableDeviceNames()` in a `ChoiceDialog` and calls
  `connect(name)` on selection, then immediately opens a second
  `ChoiceDialog` over `channelNames()` (`"Omni"`, `"1"`-`"16"`) and calls
  `setChannelName(name)`. Multiple `MidiInputNode`s independently calling
  `MidiSystem.getMidiDevice(info)` for the same device name each get their
  own `MidiDevice`/`Transmitter` handle - `javax.sound.midi` hands out
  independent handles per call rather than one shared handle, so multi-device
  support already fell out of `connect`'s existing per-instance design with
  no registry needed; multi-channel support (below) is the piece that was
  actually missing.

  `channel` (`OMNI = -1`, or `0`-`15` matching `ShortMessage.getChannel()`'s
  own 0-indexed convention - the same convention `MidiImporter` already
  uses, not `MidiInputNode`'s own 1-indexed display strings) filters
  `NoteReceiver.send`: a non-`Omni` node ignores every message whose
  channel doesn't match before it ever looks at the command byte. This is
  what makes "one device, several instruments" work - point several
  `MidiInputNode`s at the same device, each on its own channel, instead of
  needing a separate physical or virtual device per instrument.

  A live `MidiDevice.Transmitter`'s `Receiver.send(MidiMessage, long)`
  callback runs on a JavaSound MIDI thread, not the audio render thread, so
  it never mutates the ports `process()` reads directly - it only ever
  replaces one `volatile NoteState` reference (a private
  `record NoteState(float pitch, float gate, float velocity)`), the same
  one-writer/one-reader idiom `Sources.ConstantNode`'s `volatile float`
  and [`Tracker.TrackerNode`](tracker.md)'s `volatile TrackTable` already
  use, generalized to three values that must update atomically together
  rather than independently (a MIDI thread swapping `pitch` and `gate`
  as two separate volatiles could let the audio thread observe a stale
  pairing of the two mid-update). `process()` reads that reference exactly
  once per block and `Arrays.fill`s all three output blocks from it -
  control-rate precision, not sample-accurate note timing, which matches
  how a live human player's timing is already far coarser than one audio
  block (~23 ms at 44.1 kHz/1024 samples).

  The receiver tracks currently-held notes in a `Deque<HeldNote>`
  (most-recently-pressed first) mutated only from the MIDI thread - a `NOTE_ON`
  pushes and republishes; a `NOTE_OFF` (or a zero-velocity `NOTE_ON`, the
  same running-status convention `MidiImporter` already handles) pops and
  republishes whatever note is now on top, or holds `gate` low at the last
  `pitch` (not resetting it to `0`) once nothing is held - the same
  "pitch holds its last value, only gate drops" convention
  [`Tracker.TrackerNode`](tracker.md)'s `pitch`/`gate` output pair follows,
  so a downstream `Slew`/glide effect has something meaningful to glide
  from. This makes a chorded MIDI input monophonic with last-note priority,
  the identical simplification `MidiImporter`'s "Truncate-on-retrigger"
  already makes for imported chords - one `MidiInputNode` is one voice, the
  same as every other pitch/gate-driven node in this codebase; play chords
  by pointing several `MidiInputNode`s at different MIDI channels and/or
  devices, the same way polyphony elsewhere comes from wiring multiple
  single-voice nodes rather than one node growing a channel count.

  A `PITCH_BEND` message updates a `volatile float bend` in `[-1, 1]`
  (the same MIDI-thread/audio-thread split as `NoteState`) and republishes
  whichever note is on top of `heldNotes`, recomputed from that note's raw
  MIDI number rather than from the already-bent `pitch` already in
  `NoteState` - bending twice in a row must not compound, so the bend is
  always applied fresh to the unbent frequency, never to a previous bend's
  result. The bend range is a hardcoded +/-2 semitones (`BEND_RANGE_SEMITONES`),
  the conventional default most controllers and synths ship with, not a
  port - convention 6 territory (a factory default, not a mode flag), and
  there was no port budget to spend on a value almost nobody changes from
  the default.

  `channel` is `Stateful` (saved as a single `"channel"` entry - see
  [Serialization](serialization.md)'s generic `Stateful` handling, no
  codec special-casing needed) since it is a patch-design choice, the
  instrument this node answers to on a shared device/cable - unlike the
  device connection itself, which stays deliberately un-saved: it is live
  session state, not part of a saved patch, the same way
  `Audio.AudioEngine`'s open `SourceDataLine` isn't saved either. Reopening
  a `.ptr` file with a `MidiInputNode` in it restores its channel but
  starts disconnected; the user picks a device again via the header button.

  A `MidiInputNode` inside a patch loaded as a node ([Modules](modules.md))
  is substituted with a silent stub before the nested `Patch` is built -
  see [Modules](modules.md) for why a live device connection made in the
  original standalone patch must not keep driving audio once that patch
  becomes one node inside a bigger one.
- **MicInputNode** - live microphone/line input: no input ports, one output
  (`out`, an audio signal). The realtime counterpart to `MidiInputNode`
  above, same shape: starts disconnected (`getDeviceName()` returns `null`)
  and outputs silence until a device is chosen; [Gui](gui.md)'s "Mic..."
  header button lists `listAvailableDeviceNames()` in a `ChoiceDialog` and
  calls `connect(name)` on selection, preselecting the app-wide default input
  device (Options > "Input Device...", see [Audio](audio.md)) when this node
  has none of its own. The node's device stays its own patch state - the
  default only seeds the dialog. Device enumeration and line-opening
  are factored into `Audio.AudioInputDevice` (mirroring
  `Sinks.VirtualLineOut.listAvailableMixerNames`, but scanning
  `Mixer.getTargetLineInfo()` for a `TargetDataLine` instead of a
  `SourceDataLine`), since [Gui](gui.md)'s Sound Clip record screen needs
  the exact same device list and open logic for its own, unrelated,
  record-to-a-static-buffer feature - one mixer-scanning implementation,
  two call sites.

  Unlike `MidiInputNode`'s single `NoteState` swap per message,
  `MicInputNode` has continuous data arriving on its own capture thread
  (a mono, 16-bit, 44100 Hz `TargetDataLine` read loop) that must reach
  `process()` sample-for-sample rather than as one control-rate snapshot.
  It reuses `Dsp.LiveGrainRecorder` for this - the same "one thread writes
  in, another reads a fixed offset behind the write head" buffer
  `Granular.GranularFreezeNode` already uses for its own live-recording
  input - rather than inventing a second ring buffer: the capture thread
  calls `recorder.write(sample)` per decoded sample, and `process()` reads
  `recorder.read(recorder.positionBehind(LATENCY_SECONDS, context.sampleRate))`
  per output sample, a fixed ~50ms behind the write head as a safety margin
  against ordinary thread-scheduling jitter.

  No `Stateful`: there is no `MicInputNode` equivalent of `MidiInputNode`'s
  `channel` (a patch-design choice worth saving) - the device connection
  itself stays deliberately un-saved, same rationale as `MidiInputNode`'s
  device.

  A `MicInputNode` inside a patch loaded as a node ([Modules](modules.md))
  is substituted with a silent stub before the nested `Patch` is built,
  the same rule and the same reason as `MidiInputNode` above.

  Every `ShortMessage` `NoteReceiver` sees - accepted or dropped by the
  channel filter - is also appended to `MidiDebugLog`, a hidden diagnostic
  tool with no visible entry point anywhere in the UI; see [Gui](gui.md)'s
  `MidiDebugLog`/`MidiDebugPanel` section for what it records and the
  undocumented shortcut that reveals it.
