# Working with samples

Getting recorded audio into a patch, and the three different things Petroleum
can do with it.

| Node | What it does with a file |
|---|---|
| **Sound Clip** | Plays the whole clip once per phase cycle, like any other waveform |
| **Instrument** | Spreads several samples across a keyboard and plays the right one per note |
| **Granular Player** | Reads many small overlapping pieces at once, as a texture |

Whichever you use, the audio is stored inside the patch rather than referenced,
so a saved patch still plays on a machine that has never seen the original
file.

## Sound Clip

**Oscillators > Sound Clip** places an oscillator that starts as a short
silence. The **Record** button on the node's header opens a small multitrack
recorder and editor.

### Recording and importing

Pick an input with the **Device** selector, which starts on the one chosen under
**Options > Input Device...**, refreshing the list if you plugged something in
after opening the screen, then press **Record** to capture a take.
**Play** auditions what you have. **Import WAV...** brings in an existing file
instead.

The screen holds more than one track: **+ Track** and **- Track** change how
many there are, and each has its own name, **Mute** and **Solo**, a
**Spectrogram** toggle for seeing it as frequency rather than waveform, and a
button to remove it. The status line reports the track count and total length.

### Editing

Drag across a track's waveform to select a range, then use the editing
buttons - cut, copy, paste and delete - or **Trim**, which discards everything
outside the selection. **Clear Track** empties a track without removing it.
Undo and redo step through your edits, and the zoom controls, including a fit
button, change how much of the timeline is visible.

**Export Mix WAV...** writes the mix of every track out to a file, separately
from the patch.

### How it plays back

A Sound Clip plays the same way every oscillator here does: once per phase
cycle. There is no separate playback-rate control, because phase and pitch are
the same thing.

Feed it a slow **Phasor** to hear it near its original speed, or a fast one to
loop it as a buzzy pitched texture:

```
Phasor(frequency 1) --> Sound Clip.phase --> Audio Out.in
```

Recording captures at 44100 Hz. An imported file keeps whatever rate it had,
but since playback is driven by phase rather than by a clock, the original rate
only affects the editor's own Play button, not how the clip sounds once wired
into a patch. Whatever the tracks hold is mixed down to the single waveform the
node plays.

This loops the whole clip every cycle rather than firing it once per hit. For
one-shot playback from a buffer, use Granular Player.

## Instrument

**Instrument** is the node for playing a real recorded instrument: several
samples laid out across a keyboard, so a piano uses a recording per few keys
rather than one recording stretched over the whole range.

It comes in 2, 4, 8 and 16-channel sizes. Unlike a patch loaded as a node,
which plays one voice, a single Instrument node covers as many simultaneous
notes as it has channels, so one node can serve a whole song rather than one
node per channel.

### Zones

Every channel shares one instrument definition: a list of **sample zones**,
each imported from its own WAV file with a root pitch and a low and high key
range. Ranges are typed as note names, the same `A4` or `C#3+37c` notation used
elsewhere.

On a rising edge of a channel's gate, the node picks the zone whose range
contains that channel's current pitch, or the nearest zone by root pitch if
none matches, and plays it back at the ratio of the pitch to that zone's root
frequency.

That ratio is the whole mechanism, and nothing in the signal path rounds to the
nearest semitone. Feed it a genuinely microtonal pitch - a 19-EDO step, a just
intonation ratio, anything an Expression node can compute - and you get exactly
that pitch.

### The instrument editor

The node's **Edit** button opens a dedicated screen with two halves.

The **Sample zones** table lists each zone with a **Name**, **Root**, **Key
range low**, **Key range high** and **Loop**. **Import Sample...** adds a zone
from a WAV file and **Remove Zone** deletes the selected one. A new zone is
named after the file, takes a root of A4, and is given a key range spanning
effectively the whole keyboard, so it sounds on any note until you narrow it.

Root and key range are typed as note names with an optional cent offset, in the
same `A4` or `D#0+49c` notation used elsewhere, so a zone can be placed between
semitones.

A zone's **Loop** checkbox sustains the whole sample for as long as that
channel's gate is held, then plays the tail once it releases.

The **Volume envelope** below is a breakpoint editor. Click empty space to add
a point, drag a point to move it, double-click to set or unset the sustain
point, and right-click to delete. Breakpoints are in seconds, and a default
shape is already in place.

Editing the instrument changes every channel at once, since they share one
definition. Only playback position, envelope time and active zone are per
channel.

### Wiring it up

Wire a sequencer channel's pitch and gate pair into the matching channel:

```
Tracker 4.pitch0 --> Instrument 4.pitch0
Tracker 4.gate0  --> Instrument 4.gate0
Tracker 4.pitch1 --> Instrument 4.pitch1
Tracker 4.gate1  --> Instrument 4.gate1
Instrument 4.out0 --> Mix.in0
Instrument 4.out1 --> Mix.in1
Mix.out --> Audio Out.in
```

Each channel has its own output, so they can be mixed, processed or panned
separately.

To change size later, right-click the node and choose **Convert channels**. The
shared instrument and every wire to a surviving channel are carried over, and
nothing is lost on the dropped channels, since there is no per-channel data
beyond playback state.

## Granular Player

**Granular > Granular Player** opens a file picker, imports a WAV file, and
grains it: many short overlapping windowed snippets are read from the buffer
and mixed, each with its own position, size and pitch.

This is what to reach for when you want to stretch a recording in time, smear
it into a pad, or break it into texture, without changing its overall pitch.

| Port | Meaning |
|---|---|
| `position` | How many seconds into the file grains start reading from |
| `positionJitter` | 0 to 1, how much each grain's read position varies randomly |
| `grainSize` | How many seconds long each grain is |
| `grainSizeJitter` | 0 to 1, how much each grain's size varies |
| `density` | How many grains spawn per second |
| `pitch` | A playback-speed multiplier per grain; 1 is unchanged |
| `pitchJitter` | 0 to 1, how much each grain's pitch varies |
| `amp` | The loudness of each grain |
| `trigger` | Spawns one extra grain immediately on a rising pulse |

The character comes almost entirely from the balance of `density` and
`grainSize`. High density with jitter on size and pitch gives a smooth ambient
wash; low density with a small grain size gives glitchy percussive granulation
where individual grains are audible as a stutter.

Sweeping `position` is how you move through the file. Driving it from a Ramp
plays the clip through in real time, and driving it from a phasor through a Map
Range loops a section:

```
Trigger.out --> Ramp(to 4.0, time 4.0).trigger
Ramp.out    --> Granular Player.position
```

The other granular nodes work the same way on different sources: **Granular
Freeze** grains what you feed it live rather than a file, and **Granular
Delay** and **Granular Cloud** push the same engine towards shimmer and
texture.

## Drawing a waveform instead

**Oscillators > Draw Waveform** places an oscillator that starts as a flat line
at zero and lets you draw its shape by hand. It behaves like Sound Clip when
played, once per phase cycle, but its shape is drawn rather than recorded.
