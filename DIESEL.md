# Diesel language guide

Diesel is Petroleum's text language for patches. A `.dsl` file describes the
same thing a `.ptr` patch does: the nodes, the wires between them, knob values
and macros. The two formats convert both ways without losing anything. You can:

- write a patch by hand in a text editor, or generate one from a script;
- keep patches in version control and review changes as plain diffs;
- open a `.dsl` in the Petroleum editor, tweak it visually, and save it back
  as `.dsl`;
- render a `.dsl` straight to a WAV file from the command line, with no editor
  and no display.

The syntax is C-like: `//` and `/* */` comments, statements ending in `;`,
blocks in `{ }`, and declarations written as `Type name`.

## Quick start

Save this as `drone.dsl`:

```c
// A 220 Hz sine drone.
patch "Drone" {
    "LFO Sine" lfo { .frequency = 220; }
    "Audio Out" out;

    lfo.out -> out.in;
}
```

Render three seconds of it:

```
petroleum render drone.dsl -o drone.wav --duration 3
```

Or open it in the editor with **Open Patch...** (pick the *Diesel scripts*
filter or just type the file name).

## File layout

A file holds exactly one top-level `patch` block, optionally followed by
`sample`/`data` blocks that carry embedded audio (see
[Samples and embedded data](#samples-and-embedded-data)):

```c
patch "Optional name" {
    // document fields, nodes, connections, macros - in any order,
    // as long as a node is declared before anything refers to it
}
```

Two document fields may appear inside the block. You never need to write
either of them:

| Field | Meaning |
|---|---|
| `version = 6;` | The patch format version. If you leave it out, the current version is assumed. Older versions are upgraded on load, exactly like old `.ptr` files. |
| `origin = { category = "Laser", seed = "42" };` | Written by the Quick Sounds generator to record where a sound came from. |

## Nodes

A node declaration is a node **type**, then a **name** you choose, then either
`;` or a body in braces:

```c
Envelope env;                       // all defaults
"Biquad Lowpass" filter {           // type names with spaces are quoted
    x = 400; y = 120;               // position on the editor canvas
    .cutoff = 1200;                 // knob value for the 'cutoff' input
    .q = 0.9;
}
```

- **Type**: one of the names listed in the [node reference](#node-reference).
  These are the same names as the editor's add-node menu. Quote the name when
  it contains a space or other punctuation.
- **Name**: a C identifier (letters, digits, `_`, not starting with a digit),
  unique within its `patch` block. Names only matter inside the file; the
  editor shows the node's type.
- **Body statements**:
  - `.port = number;` sets the knob of an input port. The knob is only used
    while nothing is wired into that port, same as in the editor. Quote port
    names that contain spaces: `."mod index" = 4;`.
  - `field = value;` sets a construction field, such as a position, which
    waveform an oscillator plays, or how many steps a sequencer has. See
    [Node fields](#node-fields).

A node with no `x`/`y` is placed at `0, 0`. When a hand-written script with no
positions opens in the editor, press **Organize** to lay it out.

## Connections

```c
source.outputPort -> destination.inputPort;
```

```c
osc.out -> filter.in;
lfo.out -> filter.cutoff;          // a wired port overrides that port's knob
tracker.pitch0 -> voice.pitch0;
```

Both nodes must already be declared above the connection. An output can feed
any number of inputs, but each input takes at most one wire.

## Macros

A macro puts a named slider for a `Constant` node on the Macros panel and the
Quick Sounds screen. When the patch is used as a node inside another patch,
each macro becomes one of that node's input ports.

```c
Constant cutoffKnob { state = { value = 1200 }; }
cutoffKnob.out -> filter.cutoff;

macro "Cutoff" -> cutoffKnob(100, 8000);    // name -> target(min, max)
```

The target must be a `Constant` node declared earlier in the same patch.

## Values

Fields take ordinary values:

| Kind | Examples |
|---|---|
| Number | `440`, `-3`, `0.25`, `.5`, `1e-3` |
| String | `"Sawtooth"`, `"tanh(3*x)"`, escapes `\" \\ \n \t \uXXXX` |
| Boolean | `true`, `false` |
| Bare word | `Sawtooth`, read as the string `"Sawtooth"` |
| Object | `{ kind = "factory", name = "Sine" }` |
| List | `["a", "b", "c"]`, `[1, 2, 3]` |
| Patch | `patch "Name" { ... }`, a whole nested patch (see below) |

Commas separate object entries and list items. A trailing comma is allowed.

## Node fields

Most nodes need no fields besides `x`/`y`. These are the exceptions.

### Required fields

| Type | Field | Example |
|---|---|---|
| `Oscillator` | `oscillator`: which waveform (see below) | `oscillator = { kind = "factory", name = "Square" };` |
| `Patch` | `name` and `patch`: a nested patch used as one node | see [Patches inside patches](#patches-inside-patches) |

Leaving a required field out gives an error that names the node, e.g.
`Node 'osc' (Oscillator): Expected an object, found nothing (is a required
field missing?)`.

### Optional size fields

| Type | Field | Default |
|---|---|---|
| `Splitter` | `outputs = 2;` | 4 |
| `Multiplexer` | `inputs = 8;` | 4 |
| `Sequencer`, `"Trance Gate"` | `steps = 16;` | 8 |
| `"Parametric EQ"` | `bands = 5;` | 3 |
| `Waveshaper` | `source = "tanh(3*x)";` | `"x"` |
| `Mix` | `ports = ["a", "b", "c"];`: names of the inputs to sum | `in0` ... `in3` |

The numbered variants in the menu (`"Multiplexer 2"`, `"Tracker 8"`,
`"Instrument 4"`, ...) are plain type names with the size built in. They need
no fields.

### Node state

A few nodes keep a value that isn't an input port. It goes in a `state`
object:

| Type | State | Example |
|---|---|---|
| `Constant` | `value` | `Constant gain { state = { value = 0.8 }; }` |
| `Note` | `note`, a note name | `Note root { state = { note = "C#4" }; }` |
| `"MIDI Input"` | `channel` (-1 = all channels) | `"MIDI Input" midi { state = { channel = 0 }; }` |
| `Annotation` | `text` | `Annotation n { state = { text = "Lead voice" }; }` |

Files the editor writes store state values as strings (`value = "0.8"`).
Plain numbers work too.

### Oscillators

An `Oscillator` node plays the waveform given by its `oscillator` object:

| `kind` | Other keys | Meaning |
|---|---|---|
| `"factory"` | `name` | A built-in waveform: `Sine`, `Cosine`, `Sawtooth`, `Square`, `Triangle`, `White Noise`, `Pink Noise`, `Brown Noise`, `Blue Noise`, `Violet Noise`, `Grey Noise`, `Sample & Hold`, `Chaotic`, `SuperSaw`, `FM` |
| `"expression"` | `source` | A math expression of `x`, e.g. `"sin(x) + 0.3*sin(3*x)"` (grammar in the in-app docs) |
| `"dual"` | `base` | Another oscillator object mixed with a phase-shifted copy of itself |
| `"wavefolder"` | `base` | Another oscillator object, wavefolded |
| `"wavetable"`, `"drawn"` | `name`, `sample` | A single-cycle table stored as an embedded sample |

An oscillator node's inputs depend on the waveform. Most have one input,
`phase`. `Square` adds `width`, `Sample & Hold` adds `steps`, and `FM` adds
`"mod ratio"` and `"mod index"`. Noise waveforms take `gate` instead of
`phase`. `phase` expects a value in radians, which is what a `Phasor` node
produces:

```c
Phasor ph { .frequency = 110; }
Oscillator saw { oscillator = { kind = "factory", name = "Sawtooth" }; }
ph.out -> saw.phase;
```

## Patches inside patches

A `Patch` node holds a whole patch, embedded by value. Each of the inner
patch's macros becomes an input port on the node, and its single `Audio Out`
becomes the node's `out` port:

```c
patch "Two voices" {
    Patch voice {
        name = "Voice";
        patch = patch "Voice" {
            Constant pitch { state = { value = 220 }; }
            Phasor ph;
            Oscillator osc { oscillator = { kind = "factory", name = "Triangle" }; }
            "Audio Out" out;
            pitch.out -> ph.frequency;
            ph.out -> osc.phase;
            osc.out -> out.in;
            macro "Pitch" -> pitch(20, 2000);
        };
        .Pitch = 330;               // the inner macro, now an input port
    }
    "Audio Out" out;
    voice.out -> out.in;
}
```

Node names inside the nested block are separate from the outer block's names.

## Samples and embedded data

Nodes that hold imported audio or other binary data refer to it by an id, such
as `sample = "s0";`. The data itself follows the `patch` block as base64:

```c
sample s0 = b64"
    AQAAAAAQAAAA...
";

data d0 = b64"
    TVRoZAAAAAYA...
";
```

- `sample` blocks hold audio: Granular Player, Additive Resynth, Instrument
  zones, and wavetable/drawn oscillators. A sample shared by several nodes is
  stored once.
- `data` blocks hold other binary state, such as a Tracker song or a MIDI
  Player's MIDI file.

These blocks are meant for machines, not for hand-editing. Build such nodes
in the editor and save as `.dsl`. The script still opens on its own with no
external files, just like a `.ptr`.

## Comments and whitespace

```c
// to end of line
/* block
   comment */
```

Line breaks and indentation don't matter. Every statement ends with `;`, and a
node body may be followed by an optional `;`.

## Errors

Syntax errors give the line and column:

```
Diesel line 3:1: Expected ';' or '{' after node 'out', found '}'
Diesel line 7:5: Unknown node 'filtr' (declare a node before referring to it)
Diesel line 4:12: Node 'osc' is already declared in this patch
```

Errors found while building the patch name the node:

```
Node 'x' (Oscilator): Unknown node type 'Oscilator'
```

## Command line

```
petroleum render <file.dsl|file.ptr> -o <out.wav> [options]

  -o, --output <file>   WAV file to write (required)
  -d, --duration <sec>  length in seconds
  --rate <hz>           sample rate (default 44100)
  --bits <8|16|24|32>   bit depth (default 16; 32 = float)
  --channels <1|2>      channel count (default 2)
  --normalize           scale the peak to just under full scale
  --trim                cut leading and trailing silence
```

- **Duration**: a one-shot patch (one driven by an envelope) renders until it
  finishes, up to 60 seconds, when `--duration` is left out. A patch that plays
  forever needs `--duration`.
- **Where the command lives**: the installed launcher is
  `/opt/petroleum/bin/Petroleum` on Linux,
  `Petroleum.app/Contents/MacOS/Petroleum` on macOS, and `petroleum-cli.exe` on
  Windows (the console version of the launcher). From a source checkout, use
  `java -cp target/classes Petroleum.Main render ...` after `mvn compile`.
- **Headless**: rendering never opens a window, so it works over SSH and in CI.
- **Errors**: a failed render prints `petroleum: <message>` and exits with
  status 1.

## Complete example

A one-shot laser zap: a trigger fires an envelope, and a falling pitch ramp
drives a filtered sawtooth.

```c
patch "Laser" {
    Trigger fire;
    Envelope env { .attack = 0.001; .sustain = 0.03; .punch = 0.4; .decay = 0.17; }
    Ramp sweep { .from = 1500; .to = 480; .time = 0.1; }
    Phasor ph;
    Oscillator saw { oscillator = { kind = "factory", name = "Sawtooth" }; }
    "Biquad Lowpass" lp { .cutoff = 6000; }
    Amplifier amp;
    "Audio Out" out;

    fire.out  -> env.trigger;
    fire.out  -> sweep.trigger;
    sweep.out -> ph.frequency;
    ph.out    -> saw.phase;
    saw.out   -> lp.in;
    lp.out    -> amp.in;
    env.out   -> amp.gain;
    amp.out   -> out.in;
}
```

```
petroleum render laser.dsl -o laser.wav --normalize
```

## Node reference

Every type below can be declared with no fields at all. For each one the
table lists its input ports with their default knob values, and its output
ports. `Oscillator` and `Patch` aren't listed; they need the fields described
above. Nodes built around imported data (`"Granular Player"`,
`"Additive Resynth"`, instrument samples) are easiest to create in the editor.
To see what a node does, click its **?** button in the editor.

### Conditionals

| Type | Inputs (default) | Outputs |
|---|---|---|
| `Select` | `condition` 0, `ifTrue` 0, `ifFalse` 0, `threshold` 0.5 | `out` |
| `"CV Gate"` | `in` 0, `control` 0, `threshold` 0.5 | `out` |
| `Toggle` | `trigger` 0, `threshold` 0.5 | `out` |
| `Multiplexer` | `select` 0, `in0` 0, `in1` 0, `in2` 0, `in3` 0 | `out` |
| `"Multiplexer 2"` | `select` 0, `in0` 0, `in1` 0 | `out` |
| `"Multiplexer 8"` | `select` 0, `in0` 0, `in1` 0, `in2` 0, `in3` 0, `in4` 0, `in5` 0, `in6` 0, `in7` 0 | `out` |
| `Demultiplexer` | `select` 0, `in` 0 | `out0`, `out1`, `out2`, `out3` |
| `"Demultiplexer 2"` | `select` 0, `in` 0 | `out0`, `out1` |
| `"Demultiplexer 8"` | `select` 0, `in` 0 | `out0`, `out1`, `out2`, `out3`, `out4`, `out5`, `out6`, `out7` |
| `Latch` | `in` 0, `trigger` 0 | `out` |
| `"Queue Quantizer"` | `in` 0, `gate` 0, `trigger` 0 | `out`, `pulse` |
| `Counter` | `trigger` 0, `wrap` 0, `reset` 0 | `out`, `ovfl` |
| `Rungler` | `clock` 0, `data` 0, `rate` 4 | `stepped`, `bit` |
| `"Clock Divider"` | `clock` 0, `divide` 1, `multiply` 1 | `out` |
| `Sequencer` | `trigger` 0, `reset` 0, `step0` 0, `step1` 0, `step2` 0, `step3` 0, `step4` 0, `step5` 0, `step6` 0, `step7` 0 | `out` |
| `"Sequencer 4"` | `trigger` 0, `reset` 0, `step0` 0, `step1` 0, `step2` 0, `step3` 0 | `out` |
| `"Sequencer 16"` | `trigger` 0, `reset` 0, `step0` 0, `step1` 0, `step2` 0, `step3` 0, `step4` 0, `step5` 0, `step6` 0, `step7` 0, `step8` 0, `step9` 0, `step10` 0, `step11` 0, `step12` 0, `step13` 0, `step14` 0, `step15` 0 | `out` |

### Effects

| Type | Inputs (default) | Outputs |
|---|---|---|
| `Delay` | `in` 0, `time` 0.3, `feedback` 0.3, `mix` 0.5 | `out` |
| `Chorus` | `in` 0, `rate` 0.5, `depth` 3, `mix` 0.5 | `out` |
| `Flanger` | `in` 0, `rate` 0.25, `depth` 3, `feedback` 0.5, `mix` 0.5 | `out` |
| `Tremolo` | `in` 0, `rate` 5, `depth` 0.5 | `out` |
| `"Trance Gate"` | `in` 0, `rate` 4, `step0` 1, `step1` 1, `step2` 1, `step3` 1, `step4` 1, `step5` 1, `step6` 1, `step7` 1 | `out` |
| `"Trance Gate 4"` | `in` 0, `rate` 4, `step0` 1, `step1` 1, `step2` 1, `step3` 1 | `out` |
| `"Trance Gate 16"` | `in` 0, `rate` 4, `step0` 1, `step1` 1, `step2` 1, `step3` 1, `step4` 1, `step5` 1, `step6` 1, `step7` 1, `step8` 1, `step9` 1, `step10` 1, `step11` 1, `step12` 1, `step13` 1, `step14` 1, `step15` 1 | `out` |
| `"Distortion Soft Clip"` | `in` 0, `drive` 5, `mix` 1 | `out` |
| `"Distortion Hard Clip"` | `in` 0, `drive` 5, `mix` 1 | `out` |
| `"Distortion Foldback"` | `in` 0, `drive` 5, `mix` 1 | `out` |
| `"Distortion Overdrive"` | `in` 0, `drive` 5, `mix` 1 | `out` |
| `"Distortion Rectify"` | `in` 0, `drive` 5, `mix` 1 | `out` |
| `"Distortion Asymmetric"` | `in` 0, `drive` 5, `mix` 1 | `out` |
| `Bitcrusher` | `in` 0, `bits` 8, `rate` 8000 | `out` |
| `"Ring Modulator"` | `in` 0, `frequency` 30, `mix` 1 | `out` |
| `"Frequency Shifter"` | `in` 0, `shiftHz` 100 | `out` |
| `"Compressor Peak"` | `in` 0, `sidechain` 0, `externalKey` 0, `threshold` -18, `ratio` 4, `attack` 10, `release` 100, `makeup` 0 | `out` |
| `"Compressor RMS"` | `in` 0, `sidechain` 0, `externalKey` 0, `threshold` -18, `ratio` 4, `attack` 10, `release` 100, `makeup` 0 | `out` |
| `"Compressor Feedback"` | `in` 0, `sidechain` 0, `externalKey` 0, `threshold` -18, `ratio` 4, `attack` 10, `release` 100, `makeup` 0 | `out` |
| `Reverb` | `in` 0, `roomSize` 0.5, `damping` 0.5, `mix` 0.3 | `out` |
| `"Spring Reverb"` | `in` 0, `decay` 0.6, `tone` 0.5, `boing` 0.3, `mix` 0.3 | `out` |
| `Phaser` | `in` 0, `rate` 0.5, `depth` 1, `feedback` 0.3, `mix` 0.5 | `out` |
| `Limiter` | `in` 0, `ceiling` 0.9, `release` 50 | `out` |
| `"Noise Gate"` | `in` 0, `threshold` -40, `attack` 2, `release` 100 | `out` |
| `Vibrato` | `in` 0, `rate` 5, `depth` 3 | `out` |
| `Waveshaper` | `in` 0 | `out` |
| `"Level Follower"` | `in` 0, `attack` 10, `release` 100 | `out` |
| `"Auto Wah"` | `in` 0, `sensitivity` 1, `attack` 5, `release` 80, `minFreq` 300, `maxFreq` 3000, `q` 5, `mix` 1 | `out` |
| `"Amplitude Modulator"` | `carrier` 0, `modulator` 0, `depth` 1 | `out` |
| `"Frequency Modulator"` | `carrier` 440, `modulator` 0, `depth` 50 | `out` |
| `"Tone Stack"` | `in` 0, `bass` 0, `mid` 0, `treble` 0 | `out` |
| `"Parametric EQ"` | `in` 0, `freq0` 1000, `gain0` 0, `q0` 1, `freq1` 1000, `gain1` 0, `q1` 1, `freq2` 1000, `gain2` 0, `q2` 1 | `out` |
| `"Parametric EQ 1"` | `in` 0, `freq0` 1000, `gain0` 0, `q0` 1 | `out` |
| `"Parametric EQ 5"` | `in` 0, `freq0` 1000, `gain0` 0, `q0` 1, `freq1` 1000, `gain1` 0, `q1` 1, `freq2` 1000, `gain2` 0, `q2` 1, `freq3` 1000, `gain3` 0, `q3` 1, `freq4` 1000, `gain4` 0, `q4` 1 | `out` |
| `"Graphic EQ"` | `in` 0, `gain0` 0, `gain1` 0, `gain2` 0, `gain3` 0, `gain4` 0, `gain5` 0, `gain6` 0 | `out` |

### Envelopes

| Type | Inputs (default) | Outputs |
|---|---|---|
| `Trigger` | `delay` 0, `interval` 0 | `out` |
| `Envelope` | `trigger` 0, `attack` 0.01, `sustain` 0.1, `punch` 0.3, `decay` 0.2 | `out` |
| `ADSR` | `gate` 0, `attack` 0.01, `decay` 0.1, `sustain` 0.7, `release` 0.2 | `out` |
| `Ramp` | `trigger` 0, `from` 0, `to` 1, `time` 0.1 | `out` |
| `"Function Generator"` | `trigger` 0, `cycle` 0, `rise` 0.1, `fall` 0.3 | `out`, `eoc` |
| `Arpeggio` | `frequency` 440, `trigger` 0, `delay` 0.08, `ratio` 1.5, `steps` 1 | `out` |

### Filters

| Type | Inputs (default) | Outputs |
|---|---|---|
| `"One-Pole Filter"` | `in` 0, `cutoff` 1000 | `out` |
| `"One-Pole Highpass"` | `in` 0, `cutoff` 1000 | `out` |
| `"Biquad Lowpass"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"Biquad Highpass"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"Biquad Bandpass"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"Biquad Notch"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"Biquad Allpass"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"Biquad Peaking"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"Biquad Low Shelf"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"Biquad High Shelf"` | `in` 0, `cutoff` 1000, `q` 0.7071, `gain` 0 | `out` |
| `"State Variable Filter"` | `in` 0, `cutoff` 1000, `q` 0.7071 | `lowpass`, `highpass`, `bandpass`, `notch` |
| `"Ladder Filter"` | `in` 0, `cutoff` 1000, `resonance` 0.3 | `out` |
| `"Comb Filter"` | `in` 0, `time` 10, `feedback` 0.5 | `out` |
| `"Low-Pass Gate"` | `in` 0, `strike` 0, `decay` 0.3, `vactrolAmount` 0.5 | `out` |

### Granular

| Type | Inputs (default) | Outputs |
|---|---|---|
| `"Granular Freeze"` | `in` 0, `freeze` 0, `position` 0.5, `positionJitter` 0, `grainSize` 0.08, `grainSizeJitter` 0, `density` 20, `pitch` 1, `pitchJitter` 0, `amp` 0.6, `trigger` 0 | `out` |
| `"Granular Delay"` | `in` 0, `time` 0.3, `feedback` 0.3, `positionJitter` 0, `grainSize` 0.08, `grainSizeJitter` 0, `density` 20, `pitch` 1, `pitchJitter` 0, `amp` 0.6, `mix` 0.5, `trigger` 0 | `out` |
| `"Granular Cloud"` | `in` 0, `position` 0.3, `positionJitter` 0, `grainSize` 0.08, `grainSizeJitter` 0, `density` 20, `pitch` 1, `pitchJitter` 0, `filterCutoff` 2000, `filterCutoffJitter` 0.5, `amp` 0.6, `trigger` 0 | `out` |

### Instruments

| Type | Inputs (default) | Outputs |
|---|---|---|
| `"Instrument 2"` | `pitch0` 440, `gate0` 0, `pitch1` 440, `gate1` 0 | `out0`, `out1` |
| `"Instrument 4"` | `pitch0` 440, `gate0` 0, `pitch1` 440, `gate1` 0, `pitch2` 440, `gate2` 0, `pitch3` 440, `gate3` 0 | `out0`, `out1`, `out2`, `out3` |
| `"Instrument 8"` | `pitch0` 440, `gate0` 0, `pitch1` 440, `gate1` 0, `pitch2` 440, `gate2` 0, `pitch3` 440, `gate3` 0, `pitch4` 440, `gate4` 0, `pitch5` 440, `gate5` 0, `pitch6` 440, `gate6` 0, `pitch7` 440, `gate7` 0 | `out0`, `out1`, `out2`, `out3`, `out4`, `out5`, `out6`, `out7` |
| `"Instrument 16"` | `pitch0` 440, `gate0` 0, `pitch1` 440, `gate1` 0, `pitch2` 440, `gate2` 0, `pitch3` 440, `gate3` 0, `pitch4` 440, `gate4` 0, `pitch5` 440, `gate5` 0, `pitch6` 440, `gate6` 0, `pitch7` 440, `gate7` 0, `pitch8` 440, `gate8` 0, `pitch9` 440, `gate9` 0, `pitch10` 440, `gate10` 0, `pitch11` 440, `gate11` 0, `pitch12` 440, `gate12` 0, `pitch13` 440, `gate13` 0, `pitch14` 440, `gate14` 0, `pitch15` 440, `gate15` 0 | `out0`, `out1`, `out2`, `out3`, `out4`, `out5`, `out6`, `out7`, `out8`, `out9`, `out10`, `out11`, `out12`, `out13`, `out14`, `out15` |

### Mixers

| Type | Inputs (default) | Outputs |
|---|---|---|
| `Mix` | `in0` 0, `in1` 0, `in2` 0, `in3` 0 | `out` |
| `"Mix 2"` | `in0` 0, `in1` 0 | `out` |
| `"Mix 8"` | `in0` 0, `in1` 0, `in2` 0, `in3` 0, `in4` 0, `in5` 0, `in6` 0, `in7` 0 | `out` |
| `Crossfade` | `a` 0, `b` 0, `mix` 0.5 | `out` |
| `"Panner Linear"` | `in` 0, `pan` 0 | `outL`, `outR` |
| `"Panner Equal Power"` | `in` 0, `pan` 0 | `outL`, `outR` |
| `"Stereo Mix"` | `inL0` 0, `inR0` 0, `inL1` 0, `inR1` 0, `inL2` 0, `inR2` 0, `inL3` 0, `inR3` 0 | `outL`, `outR` |

### Physical

| Type | Inputs (default) | Outputs |
|---|---|---|
| `Resonator` | `in` 0, `pitch` 220, `structure` 0.5, `brightness` 0.5, `decay` 0.5 | `out` |

### Sinks

| Type | Inputs (default) | Outputs |
|---|---|---|
| `"Audio Out"` | `in` 0 |  |
| `"Stereo Audio Out"` | `inL` 0, `inR` 0 |  |
| `"Virtual Line Out"` | `in` 0 |  |
| `"Stereo Virtual Line Out"` | `inL` 0, `inR` 0 |  |

### Sources

| Type | Inputs (default) | Outputs |
|---|---|---|
| `Constant` | - | `out` |
| `Note` | - | `out` |
| `"MIDI Input"` | - | `pitch`, `gate`, `velocity` |
| `"Mic Input"` | - | `out` |
| `Phasor` | `frequency` 440 | `out` |
| `"Period Phasor"` | `period` 1 | `out` |
| `"Unit Phasor"` | `frequency` 440 | `out` |
| `"Unit Period Phasor"` | `period` 1 | `out` |
| `Random` | `trigger` 0, `min` 0, `max` 1 | `out` |
| `"Ping Pong"` | `frequency` 440 | `out` |
| `"Period Ping Pong"` | `period` 1 | `out` |
| `"Unit Ping Pong"` | `frequency` 440 | `out` |
| `"Unit Period Ping Pong"` | `period` 1 | `out` |
| `"LFO Sine"` | `frequency` 2, `reset` 0 | `out` |
| `"LFO Triangle"` | `frequency` 2, `reset` 0 | `out` |
| `"LFO Square"` | `frequency` 2, `reset` 0 | `out` |
| `"LFO Sawtooth"` | `frequency` 2, `reset` 0 | `out` |

### Spectral

| Type | Inputs (default) | Outputs |
|---|---|---|
| `"Spectral Freeze"` | `in` 0, `freeze` 0 | `out` |
| `"Spectral Gate"` | `in` 0, `threshold` 0.1, `blur` 0 | `out` |
| `"Phase Vocoder Pitch Shift"` | `in` 0, `pitchRatio` 1 | `out` |
| `"Pitch Correction Chromatic"` | `in` 0, `root` 261.6256, `retuneSpeed` 20 | `out` |
| `"Pitch Correction Major"` | `in` 0, `root` 261.6256, `retuneSpeed` 20 | `out` |
| `"Pitch Correction Minor"` | `in` 0, `root` 261.6256, `retuneSpeed` 20 | `out` |
| `"Pitch Correction Major Pentatonic"` | `in` 0, `root` 261.6256, `retuneSpeed` 20 | `out` |
| `"Pitch Correction Minor Pentatonic"` | `in` 0, `root` 261.6256, `retuneSpeed` 20 | `out` |

### Tracker

| Type | Inputs (default) | Outputs |
|---|---|---|
| `"Tracker 2"` | `bpm` 120, `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `pitch1`, `gate1` |
| `"Tracker 4"` | `bpm` 120, `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `pitch1`, `gate1`, `pitch2`, `gate2`, `pitch3`, `gate3` |
| `"Tracker 8"` | `bpm` 120, `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `pitch1`, `gate1`, `pitch2`, `gate2`, `pitch3`, `gate3`, `pitch4`, `gate4`, `pitch5`, `gate5`, `pitch6`, `gate6`, `pitch7`, `gate7` |
| `"Tracker 16"` | `bpm` 120, `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `pitch1`, `gate1`, `pitch2`, `gate2`, `pitch3`, `gate3`, `pitch4`, `gate4`, `pitch5`, `gate5`, `pitch6`, `gate6`, `pitch7`, `gate7`, `pitch8`, `gate8`, `pitch9`, `gate9`, `pitch10`, `gate10`, `pitch11`, `gate11`, `pitch12`, `gate12`, `pitch13`, `gate13`, `pitch14`, `gate14`, `pitch15`, `gate15` |
| `"MIDI Player 2"` | `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `velocity0`, `pitch1`, `gate1`, `velocity1` |
| `"MIDI Player 4"` | `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `velocity0`, `pitch1`, `gate1`, `velocity1`, `pitch2`, `gate2`, `velocity2`, `pitch3`, `gate3`, `velocity3` |
| `"MIDI Player 8"` | `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `velocity0`, `pitch1`, `gate1`, `velocity1`, `pitch2`, `gate2`, `velocity2`, `pitch3`, `gate3`, `velocity3`, `pitch4`, `gate4`, `velocity4`, `pitch5`, `gate5`, `velocity5`, `pitch6`, `gate6`, `velocity6`, `pitch7`, `gate7`, `velocity7` |
| `"MIDI Player 16"` | `play` 1, `reset` 0, `loop` 1 | `pitch0`, `gate0`, `velocity0`, `pitch1`, `gate1`, `velocity1`, `pitch2`, `gate2`, `velocity2`, `pitch3`, `gate3`, `velocity3`, `pitch4`, `gate4`, `velocity4`, `pitch5`, `gate5`, `velocity5`, `pitch6`, `gate6`, `velocity6`, `pitch7`, `gate7`, `velocity7`, `pitch8`, `gate8`, `velocity8`, `pitch9`, `gate9`, `velocity9`, `pitch10`, `gate10`, `velocity10`, `pitch11`, `gate11`, `velocity11`, `pitch12`, `gate12`, `velocity12`, `pitch13`, `gate13`, `velocity13`, `pitch14`, `gate14`, `velocity14`, `pitch15`, `gate15`, `velocity15` |

### Utility

| Type | Inputs (default) | Outputs |
|---|---|---|
| `Multiplier` | `a` 0, `b` 0 | `out` |
| `Difference` | `a` 0, `b` 0 | `out` |
| `Attenuverter` | `in` 0, `gain` 1 | `out` |
| `Invert` | `in` 0 | `out` |
| `"Quantizer Chromatic"` | `in` 440, `root` 261.6256 | `out` |
| `"Quantizer Major"` | `in` 440, `root` 261.6256 | `out` |
| `"Quantizer Minor"` | `in` 440, `root` 261.6256 | `out` |
| `"Quantizer Major Pentatonic"` | `in` 440, `root` 261.6256 | `out` |
| `"Quantizer Minor Pentatonic"` | `in` 440, `root` 261.6256 | `out` |
| `Splitter` | `in` 0 | `out0`, `out1`, `out2`, `out3` |
| `"Splitter 2"` | `in` 0 | `out0`, `out1` |
| `"Splitter 8"` | `in` 0 | `out0`, `out1`, `out2`, `out3`, `out4`, `out5`, `out6`, `out7` |
| `Amplifier` | `in` 0, `gain` 1 | `out` |
| `"Map Range"` | `in` 0, `inMin` 0, `inMax` 1, `outMin` 0, `outMax` 1 | `out` |
| `Clamp` | `in` 0, `min` -1, `max` 1 | `out` |
| `Round` | `in` 0 | `out` |
| `Ceil` | `in` 0 | `out` |
| `Floor` | `in` 0 | `out` |
| `Slew` | `in` 0, `rise` 0.05, `fall` 0.05 | `out` |
| `"Pitch to Time"` | `pitch` 440 | `out` |
| `"Time to Pitch"` | `time` 1 | `out` |
| `Annotation` | - |  |

