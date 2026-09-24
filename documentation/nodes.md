# Nodes by purpose

What each category in the node menu is for, and when to reach for it. For the
exact ports and defaults of any individual node, use its **?** button in the
editor, or the node reference table in the
[Diesel language guide](../DIESEL.md).

Many nodes appear in the menu several times with a number after the name, such
as Mix 2 and Mix 8, or Tracker 4 and Tracker 16. These are the same node with a
different number of inputs or channels; pick the size you need.

## Sources & Sinks

Where signals come from and where they end up.

**Audio Out** is the one node every patch needs; it is what you hear, and a
patch must have exactly one. **Stereo Audio Out** is its two-channel
equivalent. **Recording End** stops a render when it fires, which lets a patch
decide its own exported length rather than being cut at a fixed duration.

**Phasor** turns a frequency into a continuously sweeping phase, which is what
drives an oscillator. Its variants cover the common cases: a **Period Phasor**
is set in seconds per cycle rather than cycles per second, a **Unit Phasor**
runs over 0 to 1 instead of a full turn, and **Ping Pong** sweeps up and back
down instead of restarting.

**Constant** holds a value you can change while the patch plays. **Note** gives
a pitch. **Random** produces a new value each time it is triggered. **Mic
Input** and **MIDI Input** bring in the outside world.

The **LFO** nodes are oscillators with a frequency built in, defaulting to a
slow 2 Hz. They are the convenient choice for modulating something rather than
for being heard.

## Oscillators

The waveforms themselves: sine, cosine, sawtooth, square and triangle, plus
noise generators, a **Sound Clip** for an imported audio file, and **Draw
Waveform** for one you draw by hand.

An oscillator takes a **phase** and returns the shape of the wave at that
point. It has no frequency of its own, so it needs a Phasor to drive it. This
is what lets any waveform be driven by any source of phase.

**Oscillator Wrappers** build on a waveform rather than replacing it: a **Dual
Oscillator** mixes it with a detuned copy of itself for thickness, and a
**Wavefolder** folds the peaks back on themselves for a harder, more complex
tone.

## Envelopes

Shape over the life of a note, as opposed to over one cycle of a wave.

**ADSR** is the conventional attack, decay, sustain and release, driven by a
gate. **Envelope** is the sharper attack, sustain, punch and decay shape used
for sound effects. **Ramp** is a single line from one value to another, the
building block for a pitch slide. **Trigger** emits the pulse that starts the
others. **Arpeggio** steps pitch up or down a ladder.

## Filters

Removing or emphasising part of the spectrum.

The **Biquad** family covers lowpass, highpass, bandpass, notch, allpass,
peaking, and low and high shelf. The **Ladder** filter and the **State
Variable** filter have characters of their own worth trying on the same
material. A **Comb Filter** is the basis of resonant and metallic sounds, and a
**Low-Pass Gate** combines filtering and level the way many acoustic
instruments behave, growing brighter as they grow louder.

## Effects

Changing a signal after it has been made.

Time and space: **Delay**, **Reverb**, **Spring Reverb**, **Chorus**,
**Flanger**, **Phaser**.

Level: the **Compressor** nodes (Peak, RMS and Feedback, which differ in how
they measure level), **Limiter**, **Noise Gate**, **Tremolo**, **Level
Follower**.

Colour and damage: the **Distortion** family (Soft Clip, Hard Clip, Overdrive,
Foldback, Asymmetric and Rectify), **Waveshaper**, **Bitcrusher**, **Tone
Stack**, **Graphic EQ**, **Parametric EQ**.

Modulation: **Ring Modulator**, **Amplitude Modulator**, **Frequency
Modulator**, **Vibrato**, **Frequency Shifter**, **Auto Wah**, **Trance
Gate**.

## Granular

Chopping audio into short grains and rebuilding it. This decouples pitch from
speed, and at the textural end turns any source into a cloud or a pad.

**Granular Player** grains an imported sample. **Granular Freeze** records what
you feed it live and grains that, so it can hold a moment indefinitely.
**Granular Delay** and **Granular Cloud** push the same engine towards shimmer
and texture.

## Instruments

**Instrument**, offered in 2, 4, 8 and 16-channel versions, is a multisample
instrument: several recordings laid out across a key range, each with its own
root pitch, plus a volume envelope. This is what you use to play a recorded
instrument rather than a synthesised one.

Pitch is carried in Hz the whole way through, never rounded to a note number,
so it is microtonal without any special handling.

Like every other pitch-and-gate node here it plays one voice. Polyphony comes
from wiring one instrument per channel of a Tracker or Piano Roll.

## Physical

**Resonator** models a plucked or struck object - a string, or a body with
modes - rather than sampling one. Excite it with a click or a burst of noise
and it rings the way the modelled object would. Worth reaching for when a
sampled instrument sounds static.

## Spectral

Working on the spectrum rather than the waveform.

**Spectral Freeze** holds a sound's spectrum indefinitely. **Spectral Gate**
keeps only the loud parts of the spectrum, which thins a sound out. **Phase
Vocoder Pitch Shift** changes pitch without changing speed. **Additive
Resynth** rebuilds a sound from the partials it is made of.

## Conditionals

Decisions and memory, for patches that do something other than run
continuously.

**Select** routes between signals, and **Multiplexer** and **Demultiplexer**
do the same over more channels. **Latch** remembers a value until told
otherwise, **Counter** counts triggers, **Toggle** flips between two states,
**Clock Divider** slows a pulse train down, and **Sequencer** steps through
values.

A trigger here is just an ordinary signal that goes to 1 for a single sample,
so anything can produce one.

## Tracker

Arranging notes over time.

**Tracker** is a step sequencer with 2, 4, 8 or 16 channels and per-note pitch
and timing effects. **Piano Roll** is polyphonic with velocity and has its own
editor screen. **MIDI Player** plays a MIDI file. MIDI files can be imported
and exported.

Each channel gives you a pitch and a gate. Wire a channel's pair into an
instrument to play it, and use one instrument per channel for polyphony.

## Mixers

Combining signals. **Mix** sums several inputs with a weight for each, and
**Stereo Mix** does the same across stereo pairs.

## Utility

Signal arithmetic, and the nodes that make a patch tidy.

**Amplifier** scales a signal, and is the usual way to control level.
**Splitter** fans one output out to several destinations. **Map Range** rescales
a value from one range to another, which is what you normally want when using
one thing to modulate another. **Clamp** keeps a value in bounds, **Slew**
stops it changing too abruptly, and the **Quantizer** nodes snap a pitch to a
scale, chromatic or otherwise.

The rest are arithmetic: **Multiplier**, **Difference**, **Invert**,
**Attenuverter**, **Floor**, **Ceil**, **Round**, and conversions between pitch
and time.

**Annotation** adds a note to the canvas for whoever opens the patch next.

## Patches

Any patch you have saved, available as a single node. Its Audio Out becomes the
node's output and each of its macros becomes an input port.

This is how an instrument or effect you have built gets reused. The patch is
embedded by value, so a patch that uses it still opens on a machine that has
never seen the original file.
