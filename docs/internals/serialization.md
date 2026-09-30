# Serialization (`Petroleum.Serialization`)

Saves and loads a patch as a zip archive holding a JSON document plus binary
sample and song data. This is what makes a sound a **file** - the
precondition for presets, for shipping category generators, and for sharing a
sound with someone else.

## Why hand-rolled JSON

`Json` is a small reader/writer (parse, write, and typed accessors) rather than
a Jackson or Gson dependency. The project otherwise depends only on JavaFX, and
a first build from a cold `~/.m2` is the only one that must be online - adding a
runtime dependency would extend that. JSON itself was chosen over Java
serialization because a saved sound is meant to be readable, diffable, and
hand-editable, and because a binary format would pin the file to class names.
Since format version 6 the JSON sits inside a zip archive next to binary
sample and song entries (see "The format" below). It is still readable with
any unzip tool, but it is no longer a plain text file. That trade was taken
deliberately, for size, speed and lossless samples.

Output is indented, and `Float` values print through `Float.toString` rather
than widening to `double`, so a `0.005f` port value reads as `0.005` instead of
`0.004999999888241291`. Widened values round-trip exactly either way - this is
purely so the files stay legible.

`SerializationException` covers both malformed JSON and structurally invalid
patches, so a caller has one thing to catch.

## The format

```json
{
  "format": "petroleum-patch",
  "version": 6,
  "name": "Bell",
  "nodes": [
    { "id": "n0", "type": "Trigger", "x": 10, "y": 20 },
    { "id": "n1", "type": "Envelope", "x": 200, "y": 20,
      "portValues": { "attack": 0.005, "punch": 0.4 } },
    { "id": "n3", "type": "Constant", "x": 40, "y": 300,
      "state": { "value": "880.0" } }
  ],
  "connections": [
    { "from": "n0", "fromPort": "out", "to": "n1", "toPort": "trigger" }
  ],
  "macros": [
    { "name": "Pitch", "target": "n3", "min": 100, "max": 4000 }
  ]
}
```

That JSON is the `patch.json` entry of the file. The `.ptr` file itself is a
standard **zip** (`java.util.zip`, so still no dependency):

| Entry | Compression | Holds |
|---|---|---|
| `patch.json` | deflated | the document above |
| `samples/<id>` | stored (already compressed) | one `SampleCodec` sample, referenced from node JSON by id, e.g. `"sample": "s0"` |
| `data/<id>` | deflated | opaque bytes a node owns: a Tracker song (`"song": "d0"`), a MIDI Player's original file (`"midi": "d1"`) |

The container exists to remove base64 and the single multi-megabyte JSON
string. Base64 added a third to every binary payload, and building that string
made large songs slow to save. Entries are written with a fixed timestamp, so
saving an unchanged patch twice produces byte-identical files. The JSON stays
human-readable: `unzip -p Bell.ptr patch.json`. It is no longer
hand-editable in place, which is the trade accepted for size and speed.
Samples are encoded (in parallel, one task per sample) before the zip is
written, and decoded in parallel on load.

`name` is the patch's own name. `SynthApp` sets it from the filename on save,
and it is what a [module](modules.md) built from this file is labelled with.
`samples` is the file-wide sample pool, described below; it is written only when
the patch actually contains sample data.

An optional `origin` object records where a generated sound came from:

```json
"origin": { "category": "Laser", "seed": "-4821993017740827991" }
```

[`Generators`](generators.md) sets it, and it survives a round trip, so a saved
sound still knows its category and can be regenerated or re-rolled from the
same seed. It is written only when present and ignored when absent, so it needed
no format version bump - a hand-written patch simply omits it. The seed is
stored as a **string** because it is a full 64-bit `long`, which does not
survive JSON's double-precision numbers intact.

Loading first sniffs the zip signature (`PK\3\4`). A file without it is read
as a bare JSON text file, the version-5 layout, and goes through the
migration below. Loading then rejects a document whose `format` is not
`petroleum-patch` (legacy `petroleum-patch` files are still accepted). A `version`
newer than the current one is rejected outright, with a message naming both
versions, so a user opening a file from a future build is told why rather
than seeing a parse error. A `version` older than the current one is run
through `PatchCodec.MIGRATIONS` (see "Migrating old versions" below) before
loading. A missing `macros` key is accepted, so hand-written files stay
simple.

### `type` is a `NodeFactory` key

`typeOf(Node)` writes `nodeFactory.nameOf(node)` as the type key for any
ordinary node (see `graph.md`'s `NodeFactory.nameOf` section for the
`getName() == registry key` contract this relies on) - `OscillatorNode` and
`PatchNode` are the only two special-cased, since neither is factory-registered
(their identity comes from an `IOscillator`/embedded `PatchDocument`, not a
fixed type).

### Construction-time parameters

Some nodes take a parameter that determines their **port count**, which
`AbstractNode` fixes at construction. These cannot be restored by writing state
onto an already-built node, so `PatchCodec` constructs them directly from
fields saved alongside the type:

| Type | Field | Restored as |
|---|---|---|
| `Splitter` | `outputs` | `new SplitterNode(n)` |
| `Multiplexer` | `inputs` | `new MultiplexerNode(n)` |
| `Sequencer` | `steps` | `new SequencerNode(n)` |
| `Trance Gate` | `steps` | `new TranceGateNode(n)` |
| `Parametric EQ` | `bands` | `new ParametricEqNode(n)` |
| `Mix` | `ports` | `new MixNode(weights)` |
| `Oscillator` | `oscillator` | `new OscillatorNode(...)` - see below |
| `Patch` | `patch`, `name` | `PatchNode.create(...)` - see below |

Everything else goes through `NodeFactory.create(type)`.

`loadMix` requires `ports` to be present and throws (via `Json.asArray`'s own
"expected an array" message) if it is missing, rather than substituting a
default 4-input map - `saveNode` always writes `ports` for a `MixNode`, so a
version-2 file missing it is malformed, not merely old, and a silent
default would be a disguised compatibility branch in a codec that otherwise
has none.

### Migrating old versions

`FORMAT_VERSION` is currently 6. Version 2 added the sample pool, the patch
`name`, and the `Patch` node type. What versions 2->3 and 3->4 changed was
never written down before this build's git history begins, so no migration
can be reconstructed for them. A file older than version 5 is therefore
rejected on load with a message naming both versions, the same as a file from
a future build.

Every version bump **from here on** must add an entry to `MIGRATIONS` in the
same commit that bumps `FORMAT_VERSION`, not as follow-up work. Entries are
keyed by the version they upgrade *from*. Each one is a
`BiConsumer<Map<String, Object>, PatchArchive>` that rewrites the JSON root in
place into its shape one version later, and it may also add entries to the
archive: a JSON-to-JSON function alone could not express the move to a binary
container. `load()` walks a file's version up to `FORMAT_VERSION` by applying
each migration in turn before decoding. A version with no registered
migration is rejected the same way an unknown version always was.

**5 -> 6** (`FormatV5Migration`) is the container change described below. A
version-5 file is a bare JSON text file, which is how the reader tells it
apart from a zip. The migration decodes the old base64 16-bit `samples`
object into archive samples under the same ids, so node JSON referencing
`"s0"` is unchanged. It moves each `Tracker` node's base64 `state.pattern`
blob, and each `MIDI Player` node's base64 `state.midi`, into archive data
entries. It recurses into embedded `Patch` documents. Everything
else in a version-5 document already has its version-6 shape.

### `Stateful`

```java
public interface Stateful {
    void saveState(Map<String, String> state);
    void loadState(Map<String, String> state);
}
```

Lives in [`Graph`](graph.md), implemented by any node with mutable state that
survives construction. Right now that is only `ConstantNode` (its `value`), but
it is the extension point for future nodes - `PatchCodec` applies it generically
after construction, so a new `Stateful` node needs no codec change.

The map is `String -> String` and is read and written off the audio thread, so
allocation there is fine. This is deliberately not the mechanism for
construction-time parameters above, nor for **bulk binary state**. `TrackerNode`
and `MidiPlayerNode` used to push a base64 blob through this string map. They
are no longer `Stateful`: they expose raw bytes (`saveSongData`/`loadSongData`,
`getMidiData`/`loadMidi`), and `PatchCodec` special-cases them into `data/`
archive entries, the same way it already special-cases sample-carrying nodes.

## Oscillators (`OscillatorCodec`)

`IOscillator` is not a `Node`, and `OscillatorNode` is not registered in
`NodeFactory` (it needs an oscillator instance), so oscillators get their own
codec. It writes a `kind` discriminator:

- `factory` - anything with a working no-arg constructor, stored by
  `OscillatorFactory` name.
- `expression` - stores the formula source string.
- `wavetable` - stores the custom display name and a `sample` id into the
  file-wide pool.
- `drawn` - the hand-drawn counterpart to `wavetable`, same shape (a display
  name plus a `sample` id into the pool), for `DrawnWaveform` (see
  [Oscillators](oscillators.md)). Kept as its own `kind` rather than reusing
  `wavetable` because `DrawnWaveform` is a separate class, not a `Wavetable`
  subtype - loading a `drawn` entry always reconstructs a `DrawnWaveform`, so
  the Gui's "Draw Waveform" button keeps showing after a save/load round trip.
- `dual` / `wavefolder` - store their wrapped **base** oscillator recursively,
  so a Wavefolder wrapping a DualOscillator wrapping a Sine round-trips.

`Wavetable.getName()` is overridden to return the imported filename, so unlike
the other kinds it cannot be identified by name - hence the explicit `kind`
field rather than a name lookup.

### The archive (`PatchArchive`) and lossless samples (`SampleCodec`)

An imported sample is stored as **audio data, never as a path**. The file is
self-contained: it opens on another machine, after the source WAV is moved,
renamed, or deleted. `Wavetable` holds the decoded `float[]` and nothing else -
`WavetableLoader` reads the file once, at import, and the filename survives only
as a display label.

Samples live in one pool per file rather than inline at each oscillator, and
`PatchArchive.sampleId` **deduplicates by content**. Two nodes playing the same
sample, or two copies of the same instrument [module](modules.md), store one
copy between them. Because an embedded module is serialized with the *same*
archive as its parent, that dedup reaches through nesting.

Each sample is its own `samples/<id>` entry, encoded by `SampleCodec`.
Encoding is **bit-exact**, including `-0.0`, NaN and denormals. The encoder
picks from three methods and records the choice in a 7-byte header (`method`,
`count`, `shift`, `width`):

- **Integer detection.** `integerShift` finds the smallest `k` in 0..24 for
  which every `sample * 2^k` is an exact integer. Multiplying by a power of
  two is exact in float arithmetic, so storing those integers and dividing
  back by `2^k` reproduces every float bit for bit. Imported audio always
  qualifies: `WavetableLoader` produces `n / 32768` for mono and half-integers
  (`k` = 16) for stereo averaged down.
- **`INT_PLANES`**: the integers split into byte planes (all low bytes, then
  all high bytes, at the narrowest width that fits), then deflated. This
  wins on tonal and sparse material, because deflate finds the repeats a
  predictor would destroy.
- **`INT_RICE`**: a FLAC-style fixed polynomial predictor (order 0-3, chosen
  per 4096-sample block by smallest residual sum). The residuals are
  zigzagged and Rice-coded with a per-block parameter, and an escape codes
  any value whose quotient reaches 48 in raw 32 bits. This wins on dense,
  noisy material such as speech and applause.
- **`FLOAT_PLANES`**: the fallback for arbitrary floats (a 3-channel average,
  a gain-edited clip, a drawn waveform). Each value's raw bits are XORed with
  the previous value's, and the result is split into 4 byte planes and
  deflated.

Both integer encodings are computed and the smaller is kept. Measured on real
WAVs, each one wins on some files and neither wins on all of them, so
choosing per sample is what guarantees the smaller result. Imported 16-bit
audio lands at roughly 50-90% of its raw PCM size. That is smaller than the
old lossy format on every file measured, while being lossless. Arbitrary
floats are the honest cost: they come out at about 60% of raw float32, which
is larger than the old 16-bit quantization, but nothing is thrown away.

Earlier versions stored peak-normalized 16-bit PCM, which lost precision on
the first save. Nothing in the file is quantized any more.

### What is deliberately not saved

`ModulatableOscillator` parameters (`FMOscillator`'s mod ratio and index,
`Wavefolder`'s gain, `DualOscillator`'s offset) are **not** serialized. They
are derived state: `OscillatorNode.process` overwrites those fields every
sample from its parameter input ports, so the authoritative value lives in the
`ConstantNode` or wire driving that port, which is saved. Writing them too
would store a value that is discarded on the first rendered block.

Since a value there is never read back, `OscillatorCodec.load`'s `dual`/
`wavefolder` cases construct with a placeholder rather than a magic literal -
`DualOscillator.DEFAULT_OFFSET`/`Wavefolder.DEFAULT_GAIN`, the same constants
each class's own `getParameterPorts()` default uses and `Gui.NodeCatalog` reads
when adding one from the node menu, so the "what does a fresh one start at"
answer lives in one place instead of three copies of `Math.PI`/`2.0`.

## Port values, not shadow nodes

[`Gui.NodeView`](gui.md) creates an invisible "shadow" `ConstantNode` for every
unconnected input port on every node. These are real nodes in `GraphModel`, and
they hold the knob values, so they must be captured - but they cannot be saved
as ordinary nodes. On load, each rebuilt `NodeView` creates its own fresh
shadows, and the saved ones would arrive as duplicates driving the same ports.

So a shadow's value is saved on its **owning node** instead, as
`portValues: { portName: value }`. `PatchDocument` carries this map, and
`GraphEditor.loadDocument` applies it through `NodeView.applyShadowValues` after
the view is built. A loaded value outside a slider's default range widens that
slider rather than being clamped, since the default range is derived from
`Port.defaultValue()` and a saved patch may legitimately sit far outside it.

`GraphEditor.toDocument` correspondingly skips any connection whose source or
destination has no `NodeView`, which is exactly the shadow connections.

## Patches inside patches

A `Patch` node is a saved patch used as a node - see [Modules](modules.md) for
what it does at runtime. It is written with the whole inner document **embedded
by value**:

```json
{ "id": "n4", "type": "Patch", "x": 40, "y": 120, "name": "Bell",
  "patch": { "name": "Bell", "nodes": [ ... ], "connections": [ ... ],
             "macros": [ ... ] },
  "portValues": { "Pitch": 880.0 } }
```

`saveDocument`/`loadDocument` are the recursive half of `save`/`load`: they
handle one document's nodes, connections, macros, name and origin, while the
outer pair adds `format`, `version` and the archive. A nested document reuses
the parent's `PatchArchive`, so sample and data ids are unique file-wide and
identical samples are stored once however deep they sit.

Embedding rather than referencing a path is the same decision the sample
archive makes, for the same reason: a patch that names `../instruments/bell.ptr`
breaks as soon as the file moves, and cannot be sent to anyone. The cost is
duplication when one instrument is used in many patches - editing the original
file does not update the copies already embedded elsewhere. Re-drop the module
to pick up a new version.

A module's `portValues` on the parent node are the values of its input-port
knobs - ordinary port values, because a module's ports are ordinary ports.

### `PatchDocument.buildPatch()`

Because port values are metadata rather than nodes, `new Patch(doc.getNodes(),
doc.getConnections())` would silently drop every knob the user set in the
editor and fall back to `Port.defaultValue()`. `buildPatch()` closes that gap:
it materializes a `ConstantNode` for each stored port value whose port is not
already driven by a real connection, wires it in, and builds the `Patch` from
the combined lists.

A stored value for a port that *is* connected is ignored, so a real wire always
wins over a stale shadow value left behind when the wire was added. The
materialized nodes are throwaway - one set per build, off the audio thread -
and macros are unaffected because a macro always targets a `ConstantNode` that
is a genuine member of the document.

Generated patches carry no port values at all (every tunable input has an
explicit `ConstantNode`), so for them `buildPatch()` is just `new Patch(...)`.

`buildPatch(Map<Node, Node> substitutions)` is the same build with named nodes
swapped for replacements. [`Modules`](modules.md) uses it to replace each macro
target with the port bridge that feeds it from outside. Substitution is applied
to the node list, to both ends of every connection, and to the owner of every
materialized port value, so a replacement is wired exactly where the original
was. The document itself is never modified.

## `PatchDocument`

The in-memory form: a name, nodes, connections, per-node canvas positions,
per-node port values, and macros. It is what `PatchCodec` reads and writes, and
what `GraphEditor` converts to and from.

Deliberately separate from [`Patch`](patch.md). `Patch` is the immutable,
topologically sorted execution snapshot with no notion of canvas coordinates;
`PatchDocument` is the editable document, including presentation state that the
audio engine has no use for.

## `Macro`

```java
public record Macro(String name, ConstantNode target, float min, float max) {
}
```

A named binding onto a specific `ConstantNode` in the graph, with a display
range. This is the mechanism the quick-generation screen will use: a beginner
sees a handful of named sliders, each writing straight through to a real node
in a real graph. There is no separate parameter model to keep in sync, so
"open this generated sound in the graph editor" needs no conversion step - the
sound was always a graph.

Macros are held by `GraphEditor` and travel with the document. On load, each
macro's `target` is resolved to the live node instance that was just built, so
writing through a macro immediately affects the running patch. A macro pointing
at a non-`ConstantNode` is rejected at load with a message naming the macro.

A macro whose target is not among the document's nodes now makes `save` throw
`SerializationException` naming the macro, rather than skipping it. The
original code did `continue` there, and that silence hid a real bug for a
whole phase: promoting a *port knob* bound a macro to [`Gui.NodeView`](gui.md)'s
hidden shadow `ConstantNode`, which has no `NodeView` and so never reached the
node list - the macro vanished on save, vanished on returning to the quick
screen, and drove nothing in between. The editor no longer creates that state
(promotion materializes the shadow into a real node), and the exception is what
stops it coming back.

Macros are authored in two places and are the same thing in both:
[`Generators`](generators.md) attaches eight to every sound it emits, and
`GraphEditor.promoteToMacro(...)` adds one from the editor when the user
right-clicks a knob. `GraphEditor` rejects a blank name, a duplicate name
(case-insensitively), and a second macro on a knob that already has one; it
also widens a zero-width range to avoid a dead slider. This is what makes the
ramp bidirectional - a knob promoted deep in the graph becomes a top-level
slider on the quick screen without any conversion step.

## Gui integration

[`SynthApp`](gui.md) gains **Save Patch...** and **Open Patch...** buttons using
a `FileChooser` filtered to `*.ptr`. Saving stamps the document's `name` from
the chosen filename. Loading stops playback, rebuilds the canvas through
`GraphEditor.loadDocument`, and restarts playback if it was running. The node
menu's **Patches -> Load patch as node...** reads the same file type through the
same codec, but wraps it as a [module](modules.md) instead of replacing the
canvas. `GraphEditor.clear()` tears down every node view, wire, index, and
macro, and resets the spawn cursor.

## Diesel

The same document tree can also be read and written as Diesel text (`.dsl`)
- see [diesel.md](diesel.md). `PatchCodec.saveRoot`/`loadRoot` are the
shared entry points, and `PatchCodec.load(Path)`/`save(document, out, Path)`
pick the format from the file extension.
