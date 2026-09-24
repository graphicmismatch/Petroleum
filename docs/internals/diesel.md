# Diesel

The user-facing language guide (tutorial, field reference, and a generated
table of every node type's ports) is `DIESEL.md` at the repository root. This
file covers the implementation.

Diesel is Petroleum's C-like text format for patches (`.dsl`). It is a second
syntax for exactly the same data a `.ptr` archive holds, and conversion goes
both ways without loss: `Diesel.write` turns a `PatchDocument` into text, and
`Diesel.read` turns text back into a `PatchDocument`. Both live in
`Petroleum.Serialization.Diesel`.

## Why it is a syntax over `PatchCodec`'s tree

`PatchCodec.saveRoot` already turns a document into a plain tree of maps and
lists (`format`, `version`, `name`, `origin`, `nodes`, `connections`,
`macros`, with each node's per-type fields: `oscillator`, `steps`, `ports`,
`state`, a nested `patch`, and so on). `patch.json` inside a `.ptr` is that tree
written as JSON. Diesel prints and parses the same tree in C-like syntax, then
hands it to `PatchCodec.loadRoot`. The consequences:

- Every node type round-trips, including ones added later, with no Diesel
  change. Diesel has no per-node-type code and no name table of its own.
  Node types are the `NodeFactory`/`PatchCodec` names.
- A script goes through the same format-version check and migration chain as
  a `.ptr` (`version = 6;`, defaulting to the current `FORMAT_VERSION` when
  it's missing), and the same validation, such as a macro having to target a
  `Constant`.
- Sample and data references in the tree are archive ids (`"s0"`, `"d0"`).
  Diesel writes the matching `PatchArchive` entries at the end of the file as
  base64 `sample`/`data` blocks. Samples are encoded with `SampleCodec` first,
  just like in the zip, so they stay bit-exact and are only written once even
  when shared.

`PatchCodec.load(Path)` and `PatchCodec.save(document, out, Path)` choose Diesel
or the zip archive from the file extension (`Diesel.isDieselFile`). The GUI's
Open/Save and Load-patch-as-node, and the `petroleum render` CLI, all go through
them.

## Grammar

```
file        = { patchBlock | blob } ;            (exactly one top-level patchBlock)
patchBlock  = "patch" [ STRING ] "{" { stmt } "}" ;
stmt        = nodeDecl | connection | macro | assignment ;
nodeDecl    = TYPE IDENT ( ";" | "{" { nodeStmt } "}" [ ";" ] ) ;
nodeStmt    = "." KEY "=" NUMBER ";"             (unconnected input knob -> portValues)
            | KEY "=" value ";" ;                (construction field: x, y, oscillator, state, ...)
connection  = IDENT "." KEY "->" IDENT "." KEY ";" ;
macro       = "macro" STRING "->" IDENT "(" NUMBER "," NUMBER ")" ";" ;   (min, max)
assignment  = IDENT "=" value ";" ;              (document field: version, origin, ...)
blob        = ( "sample" | "data" ) IDENT "=" B64 ";" ;
value       = NUMBER | STRING | "true" | "false" | IDENT
            | "{" [ KEY "=" value { "," KEY "=" value } ] "}"
            | "[" [ value { "," value } ] "]"
            | patchBlock ;                       (a nested patch, e.g. a Patch node's `patch` field)
TYPE        = IDENT | STRING ;                   ("Audio Out" needs quotes, Envelope does not)
KEY         = IDENT | STRING ;
B64         = 'b64"' base64-with-any-whitespace '"' ;
```

Comments are `//` to end of line and `/* ... */`. Strings use C escapes
(`\" \\ \n \t \r \b \f \uXXXX`). A bare identifier used as a value is read as a
string.

Node names are local to their `patch` block. A node has to be declared before
a connection or macro uses it, and declaring the same name twice is an error.
Every parse error is reported as `Diesel line L:C: message`.

The writer derives node names from the type (`oscillator1`, `audio_out2`)
and always writes `x`/`y`. A hand-written node without them sits at `0, 0`.
Use the editor's Organize Layout to arrange it.

## Worked example

```c
// Petroleum Diesel script
patch "Laser" {
    version = 6;
    origin = { category = "Laser", seed = "42" };

    Trigger trigger1 { x = 40; y = 40; }
    Constant pitch { x = 40; y = 160; state = { value = "1500" }; }
    Envelope envelope1 { x = 280; y = 120; .decay = 0.17; }
    Phasor phasor1;
    Oscillator osc {
        oscillator = { kind = "factory", name = "Sawtooth" };
    }
    "Biquad Lowpass" lowpass { .cutoff = 6000; }
    Amplifier amp;
    "Audio Out" out;

    trigger1.out -> envelope1.trigger;
    pitch.out -> phasor1.frequency;
    phasor1.out -> osc.phase;
    osc.out -> lowpass.in;
    lowpass.out -> amp.in;
    envelope1.out -> amp.gain;
    amp.out -> out.in;

    macro "Pitch" -> pitch(20, 12000);
}
```

A patch holding an imported sample ends with a block like this:

```c
sample s0 = b64"
    AQAAAAAQ...
";
```

## Command line

`Petroleum.Main` is the packaged entry point. With no arguments it starts the
editor (`SynthApp`). With `render` it renders a `.dsl` (or `.ptr`) file straight
to WAV and never loads JavaFX, so it runs on a headless machine:

```
petroleum render song.dsl -o song.wav [--duration 5] [--max-duration 600]
                                      [--rate 44100] [--bits 16]
                                      [--channels 1|2] [--normalize] [--trim]
```

- The options map one-to-one onto `ExportSettings`, so the output matches
  Render to WAV... in the editor.
- If you leave out `--duration`, a patch with one-shots renders until
  `Patch.isFinished()`, measured in a first pass and capped at 60 s. A patch
  with a `"Recording End"` node instead renders unbounded until that node
  fires, capped by `--max-duration` (default 600 s, and the only thing that
  flag affects - see [Audio](audio.md)). A patch with neither is refused with
  a request for `--duration`.
- The measuring pass and the real render each use a fresh `NodeContext`.
  Convention 9 is what lets the second pass fire its triggers again.
- Errors print `petroleum: <message>` to stderr and exit with status 1.

Packaging: `app.mainClass` is `Petroleum.Main`. The Windows profile adds a
second console-attached launcher, `petroleum-cli` (via
`src/main/jpackage/petroleum-cli.properties`), because the main Windows
launcher has no console to print to. On Linux the installed launcher is
`/opt/petroleum/bin/Petroleum`. On macOS it is
`Petroleum.app/Contents/MacOS/Petroleum`. During development, run
`java -cp target/classes Petroleum.Main render ...`.
