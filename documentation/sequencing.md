# Sequencing

Playing a patch as music rather than as a single sound. Petroleum has three
nodes for this, all in the **Tracker** category.

| Node | Use it for |
|---|---|
| **Tracker** | A step grid, one note at a time per channel, with per-note effects |
| **Piano Roll** | Chords, free timing and velocity, drawn with the mouse |
| **MIDI Player** | Playing an existing MIDI file exactly as written |

All three work the same way once wired: each channel or voice gives you a
**pitch** in Hz and a **gate** that is 1 while a note sounds. Wire that pair
into whatever should play it.

An **Instrument** node has matching channels of its own, so one of them covers
a whole song. A patch loaded as a node plays a single voice, so those need one
per channel.

## Wiring a sequencer to an instrument

The usual arrangement sends each channel to its own instrument:

```
Tracker 4.pitch0 --> Bass.pitch
Tracker 4.gate0  --> Bass.gate
Tracker 4.pitch1 --> Lead.pitch
Tracker 4.gate1  --> Lead.gate
```

The instrument here is normally a patch you saved and loaded as a node, whose
`pitch` and `gate` macros become its input ports. You can equally wire a
channel into nodes directly:

```
Piano Roll 4.pitch0    --> Phasor.frequency --> Sawtooth.phase
Piano Roll 4.gate0     --> ADSR.gate
Piano Roll 4.velocity0 --> Amplifier.gain
ADSR.out               --> Amplifier.in
```

### Shared inputs

| Port | Meaning |
|---|---|
| `bpm` | Tempo in beats per minute. Tracker and Piano Roll only. |
| `play` | 1 plays, 0 pauses in place. Gates drop while paused, and playback resumes where it left off. |
| `reset` | A rising pulse jumps back to the start. |
| `loop` | 1 repeats forever; 0 plays through once and then stays silent. |

### Changing size

Every one of these nodes comes in fixed sizes: Tracker in 2, 4, 8 and 16
channels, Piano Roll in 4, 8 and 16 voices, MIDI Player in 2, 4, 8 and 16.
Piano Roll and MIDI Player also offer a custom size.

To change size after the fact, right-click the node and choose **Convert
channels**. Notes on channels that still exist are carried over, along with
every wire to a surviving pitch and gate pair. Shrinking asks for confirmation
first, because notes on the dropped channels are lost.

## Tracker

Click the node's **Edit** button to open the pattern editor, a full-screen step
grid. Notes can be typed or placed and resized with the mouse.

A Tracker channel plays one note at a time. Two notes placed back to back with
no gap do not retrigger a gate-driven instrument, because the gate never drops
between them. That is the same behaviour as real tracker hardware, and it is
worth knowing if a part sounds unexpectedly legato.

For chords, free timing or velocity, use a Piano Roll instead.

### Typing notes

The grid accepts notes from the keyboard, laid out like a piano across two
rows:

| Keys | Meaning |
|---|---|
| Z S X D C V G B H N J M | One octave of white and black keys |
| Q 2 W 3 E R 5 T 6 Y 7 U I 9 O 0 P | The octave above |
| Arrow keys | Move the cursor |
| Home / End | Jump to the first or last step |

### Per-note effects

Selecting a note opens an effect panel on the right holding every effect field
for that one note. Edit the fields and click **Apply**. A field left at 0, or
Slide left at None, means that effect is off.

Fields measured in **ticks** are finer than the step grid: there are 4 ticks to
a step, so 4 ticks is one full step and 1 tick is four times faster.

**Slide** (None, Glide or Glissando) makes a note's pitch arrive gradually
rather than instantly. It only applies when the note immediately follows
another on the same channel with no gap, since a glide needs a pitch to start
from; the first note on a channel, or one with a gap before it, ignores its
slide setting. Glide sweeps smoothly between the two pitches, and Glissando
sweeps the same distance snapped to whole semitones, so it sounds like a fast
chromatic run. **Slide steps** sets how many pattern steps the glide takes, in
musical time, so it is unaffected by tempo.

**Arp +semi 1** and **Arp +semi 2** are semitone offsets added to the note's
pitch, and **Arp rate (ticks)** is how long each step of the cycle holds. With
a nonzero rate the pitch cycles through root, root plus the first offset, root
plus the second, and back, for as long as the note is held.

Arpeggio and slide are mutually exclusive on one note. If a slide is actually
engaged, arpeggio is skipped even when its rate is set.

**Delay fraction** (0 to 1) starts the note late within its own step, so 0.5
starts it halfway to the next step. This is how you get swing or a looser feel.
Only the start moves; length and the other effects are unaffected.

**Retrigger (ticks)** drops the gate for a single sample and brings it straight
back every N ticks while the note is held, giving a gate-driven envelope a
fresh rising edge each time, for rolls and stutters. **Cut (ticks)** forces the
gate off for good once N ticks have passed since the note started, ending it
early however long the cell looks on the grid. Both combine freely with slide
and arpeggio.

Vibrato and tremolo are deliberately not here. Patch the **Vibrato** and
**Tremolo** nodes onto the Tracker's pitch and gate outputs instead.

### Importing MIDI

The editor can import a Standard MIDI File. The import reads the file's tempo
and maps the first channels that contain notes onto the Tracker's channels in
channel-number order, as many as the Tracker has.

Because a Tracker channel is strictly one note at a time, anything beyond that
number of channels is dropped, and so is a second overlapping note on a channel
that is already playing. If you need the file played faithfully, with its
chords and exact timing intact, use a MIDI Player.

## Piano Roll

Click **Edit** to open the piano roll screen. Time runs left to right and pitch
bottom to top, with a keyboard down the left edge, a bar ruler along the top
and a velocity lane along the bottom. Notes may overlap freely.

### Editing notes

| Action | How |
|---|---|
| Place a note | Click an empty spot, dragging right to set its length as you place it |
| Move or resize | Drag the note, or drag its right edge |
| Delete | Right-click the note |
| Type an exact pitch | Double-click it, or select it and press Enter |
| Ignore the grid | Hold Alt while placing, moving or resizing |

New notes inherit the length and velocity of the last note you touched. Typing
a pitch accepts either a note name with an optional cent offset, such as
`C#4+25c`, or a bare MIDI number such as `61.25`. Holding Alt puts pitches
between the semitone rows and times between grid lines, which is how you write
microtonal or deliberately unquantized parts.

### Selections

Shift-click adds or removes a note from the selection, and Shift-drag or
Ctrl-drag on empty space draws a selection box. Moving or resizing any selected
note applies to the whole selection.

| Shortcut | Action |
|---|---|
| Delete | Remove the selection |
| Up / Down | Transpose a semitone, or an octave with Shift |
| Left / Right | Nudge one grid step, or a bar with Shift |
| Q | Snap start times to the grid |
| Ctrl+D | Duplicate just after itself |
| Ctrl+C / Ctrl+X / Ctrl+V | Copy, cut, and paste at the mouse position |
| Ctrl+A | Select everything |
| Escape | Clear the selection |
| Ctrl+Z / Ctrl+Shift+Z | Undo and redo |
| [ and ] | Move the selection to the previous or next voice |

### Velocity

Drag up and down in the velocity lane to set how hard notes are played. Every
note whose start the mouse passes over changes, or only the selected notes when
there is a selection. Velocity leaves the node on its own port per voice, ready
to scale an amplifier or open a filter.

### Voices

Every note belongs to a voice, one of the node's pitch, gate and velocity
output sets, shown by its colour and a `v1`, `v2` label. A new, moved or pasted
note takes the lowest voice free for its whole length, so chords spread out on
their own.

A voice plays one note at a time, so two overlapping notes on the same voice
cut each other short. Notes on a voice the node no longer has, after converting
to fewer voices, are greyed out and silent. The toolbar counts both cases, and
converting to a larger Piano Roll fixes them.

When a voice starts a new note exactly as its previous one ends, the gate drops
for a single sample so an envelope hears a fresh note rather than one long one.

### Pitch curves

Each voice has its own pitch curve. Choose the **Pitch pen** tool and draw over
a note: the line through the note is the pitch it really plays, and drawing
above or below its centre bends it up or down by up to an octave. A stroke
writes to the curve of the voice belonging to the note you started on, so
bending one note of a chord leaves the rest alone. Right-drag with the pen
erases that voice's curve.

Setting the bottom lane to **Pitch bend** shows one voice's curve directly,
from -12 to +12 semitones, with **Curve voice** choosing which and **Clear
voice curve** removing it.

A curve belongs to the voice's timeline rather than to a note, so it stays put
when notes move, and a note that changes voice picks up that voice's curve.
Each stroke eases back to no bend just after its ends.

### Previewing

**Play**, or Space, previews the clip on its own without the rest of the patch,
looping at the **Preview BPM** tempo. Edits are heard as you make them. With
**Audition notes** on, placing, clicking or dragging a note plays a short blip.

Both use a preview voice patch, one copy per voice. The built-in one is a plain
sine with an ADSR. To hear your own sound instead, save a one-voice patch as
`preview.ptr` in Petroleum's config folder:

| Platform | Location |
|---|---|
| Linux | `~/.config/petroleum` |
| macOS | `~/Library/Application Support/Petroleum` |
| Windows | `%APPDATA%\Petroleum` |

The Play button's tooltip shows the exact path. The patch needs macros named
`pitch` in Hz and `gate`, and optionally `velocity` from 0 to 1; the piano
roll's outputs are wired into them exactly as when loading a patch as a node.
The file is re-read every time you press Play, so you can adjust it and try
again without restarting.

### Grid, length and navigation

The **Snap** box sets the grid, from a whole bar down to 1/32, including
triplets, or Off. The clip's length in bars is set in the toolbar or by
dragging the marker in the ruler; notes past the end are dimmed and do not
play.

Scroll to move around, Shift+scroll to scroll sideways, Ctrl+scroll to zoom
time and Alt+scroll to zoom pitch. **Fit** zooms to the whole clip.

**Import MIDI** replaces the clip with a Standard MIDI File's notes, keeping
their exact timing, chords and velocities.

## MIDI Player

Click **Load** to pick a `.mid` file. A summary reports how many notes it holds
and how long it runs. The file is stored inside the patch, so a saved patch
keeps playing it even if the original file is moved or deleted.

Nothing is snapped to a grid. Every note starts and stops at the time written
in the file, and tempo changes partway through are followed. Notes from every
MIDI channel are dealt across the voices, each new note taking the first free
one, so chords play in full.

When more notes sound at once than there are voices, the note that would have
ended soonest is cut short to make room. The load summary says how many notes
that affected, and a larger MIDI Player fixes it.

As with the Piano Roll, a voice starting a new note as its previous one ends
drops its gate for a single sample, so an envelope retriggers. Drum tracks on
MIDI channel 10 are played as ordinary pitched notes like any other channel.
