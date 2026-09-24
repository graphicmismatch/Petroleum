package Petroleum.Gui;

import Petroleum.Graph.Node;
import Petroleum.Graph.NodeFactory;
import Petroleum.Granular.GranularPlayerNode;
import Petroleum.Instruments.InstrumentNode;
import Petroleum.Modules.PatchNode;
import Petroleum.Oscillators.IOscillator;
import Petroleum.Oscillators.OscillatorNode;
import Petroleum.Oscillators.Implementations.DrawnWaveform;
import Petroleum.Oscillators.Implementations.DualOscillator;
import Petroleum.Oscillators.Implementations.ExpressionOscillator;
import Petroleum.Oscillators.Implementations.Wavefolder;
import Petroleum.Oscillators.Implementations.Wavetable;
import Petroleum.Sources.NoteNode;
import Petroleum.Spectral.AdditiveResynthNode;
import Petroleum.Tracker.MidiPlayerNode;
import Petroleum.Tracker.PianoRollNode;
import Petroleum.Tracker.TrackerNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Docs {
    public static final String GUIDE_CATEGORY = "Guide";

    public record PortInfo(String name, String description) {
    }

    public record Article(String category, String title, String summary, String body,
                           List<PortInfo> inputs, String output, String example) {
    }

    private static PortInfo port(String name, String description) {
        return new PortInfo(name, description);
    }

    private static final String AUDIO_WAVE = "An audio signal, roughly between -1 and 1 - the actual "
            + "sound wave you hear once it reaches Audio Out.";
    private static final String AUDIO_SHAPED = "An audio signal - the same kind of signal that came in, "
            + "with its tone or shape changed.";
    private static final String CONTROL_NOT_AUDIO = "A number that changes over time, not an audio wave - "
            + "meant to be wired into another node's port rather than straight into Audio Out.";

    private static final List<Article> NODE_ARTICLE_LIST = List.of(
            new Article("Sources & Sinks", "Constant",
                    "A fixed value you can drag with a knob.",
                    """
                    Constant has no inputs, just one output holding a single number you set with its \
                    slider or text field. It is what every unconnected input port is quietly wired to \
                    behind the scenes, so placing one explicitly is only needed when you want a knob \
                    that several nodes share, or one you plan to promote to a macro.

                    Dragging the slider changes the sound live, on the next audio block - there is no \
                    separate "apply" step.""",
                    List.of(),
                    "Whatever number you set with the slider or text field - not audio, just a steady "
                            + "value for another node to use.",
                    """
                    Constant(value 0.5) --> Amplifier.gain
                    Constant(value 880) --> Phasor.frequency --> Sine.phase --> Audio Out.in"""),
            new Article("Sources & Sinks", "Note",
                    "Type a note name like \"A4\" or \"C#3\" and get its frequency in Hz.",
                    """
                    Note has no inputs, just one output holding the frequency (in Hz) of whatever note \
                    name you type into its text field - scientific pitch notation, letter A-G, an \
                    optional # or b, then an octave number (middle C is C4, matching a standard piano \
                    keyboard/DAW convention). It is the same fixed-value role Constant plays, except the \
                    knob is spelled in music theory instead of a raw number, so "wire the pitch straight \
                    into a Phasor" doesn't require converting a note to Hz by hand first.

                    A trailing +N or -N (optionally followed by "c") detunes by that many cents - 100 \
                    cents is a semitone, so "A4+50" or "A4+50c" is a quarter-tone sharp of A4, and any \
                    fractional cents value is fair game for genuinely microtonal tuning, not just the \
                    nearest semitone. An unparseable note name turns the field's border red and shows why \
                    in a tooltip, leaving the last valid note - and its frequency - in effect, the same \
                    convention Expression's formula box uses for a syntax error.""",
                    List.of(),
                    "The parsed note's frequency in Hz, steady until the text changes - not audio, meant "
                            + "to feed a Phasor or any other pitch-taking port.",
                    """
                    Note("C#4") --> Phasor.frequency --> Sine.phase --> Audio Out.in
                    Note("E2") --> Phasor.frequency --> Sawtooth.phase --> Ladder Filter(cutoff 500).in"""),
            new Article("Sources & Sinks", "Phasor",
                    "A ramp from 0 to 2*PI, the clock every oscillator rides on.",
                    """
                    Phasor counts up from 0 to 2*PI at whatever frequency you give it, then snaps back \
                    to 0 and repeats. Feed its output into an oscillator's phase input and you get that \
                    oscillator's waveform at the phasor's frequency.

                    A negative frequency runs the phasor backwards, which plays most waveforms in \
                    reverse. There are several phasor variants in this menu (Period, Unit, Ping Pong, \
                    and combinations) - see their own entries for what each variant changes.""",
                    List.of(port("frequency", "how many times per second the ramp repeats, in Hz - "
                            + "this becomes the pitch once wired into an oscillator")),
                    "A steadily rising ramp between 0 and 2*PI (radians), snapping back to 0 at the top "
                            + "of each cycle - not audio by itself, meant to feed an oscillator's phase input.",
                    """
                    Phasor(frequency 440) --> Sine.phase --> Audio Out.in
                    Phasor(frequency 110) --> Square.phase --> Biquad Lowpass(cutoff 800).in"""),
            new Article("Sources & Sinks", "Period Phasor",
                    "Like Phasor, but you set the cycle length in seconds instead of Hz.",
                    """
                    Identical to Phasor except its input is called period and is read in seconds per \
                    cycle rather than cycles per second. period = 1 / frequency, so use whichever one \
                    is more natural for the effect you are building - period is often easier when you \
                    want a cycle to line up with a fixed duration, like one bar of a beat.

                    A period of exactly 0 holds the phase still instead of dividing by zero.""",
                    List.of(port("period", "how many seconds one full cycle takes - the reciprocal of "
                            + "frequency")),
                    "Same shape as Phasor's output - a ramp between 0 and 2*PI - just paced by a "
                            + "seconds-per-cycle setting instead of a Hz setting.",
                    """
                    Period Phasor(period 0.5) --> Sine.phase --> Audio Out.in
                    Period Phasor(period 2) --> Triangle.phase --> Delay(time 0.3).in"""),
            new Article("Sources & Sinks", "Unit Phasor",
                    "Like Phasor, but ramps from 0 to 1 instead of 0 to 2*PI.",
                    """
                    Unit Phasor is the same rate-driven ramp as Phasor, just scaled to a plain 0..1 \
                    range instead of radians. That makes it the natural driver for anything that isn't \
                    an oscillator's phase input - a Mix weight, a Crossfade, a Map Range - since those \
                    ports expect an ordinary 0..1 signal rather than radians.

                    Wire a Unit Phasor into Map Range to sweep any other range at a steady rate.""",
                    List.of(port("frequency", "how many times per second the ramp repeats, in Hz")),
                    "A steadily rising ramp between 0 and 1, snapping back to 0 at the top of each cycle "
                            + "- a plain 0..1 control signal, not radians and not audio.",
                    """
                    Unit Phasor(frequency 0.25) --> Map Range(outMin 200, outMax 2000) --> Biquad Lowpass.cutoff
                    Unit Phasor(frequency 0.5) --> Crossfade.mix"""),
            new Article("Sources & Sinks", "Unit Period Phasor",
                    "Unit Phasor's frequency input, expressed as a period in seconds instead.",
                    """
                    Combines Unit Phasor's 0..1 output range with Period Phasor's seconds-per-cycle \
                    input. Reach for this one when you want a slow, steady 0..1 sweep and it's easier \
                    to think in "how many seconds per cycle" than "how many Hz".""",
                    List.of(port("period", "how many seconds one full cycle takes")),
                    "A steadily rising 0..1 ramp, the same shape as Unit Phasor, just paced in seconds "
                            + "per cycle instead of Hz.",
                    """
                    Unit Period Phasor(period 4) --> Crossfade.mix
                    Unit Period Phasor(period 2) --> Square.width"""),
            new Article("Sources & Sinks", "Random",
                    "Draws a fresh random value each time it is triggered, and holds it.",
                    """
                    Random outputs a uniform random number between min and max. It draws a new value on \
                    every rising edge of its trigger input and holds that value steady until the next \
                    edge - so it behaves like a "roll the dice" button rather than noise.

                    Left untriggered, it still draws one value on the very first block, so an \
                    unconnected Random gives you a fixed random constant - handy for giving several \
                    copies of the same patch their own fixed variation. For a per-hit random pitch or \
                    cutoff, trigger it from the same signal that starts the sound's envelope.""",
                    List.of(
                            port("trigger", "a pulse (1 for an instant, 0 otherwise) that draws a new "
                                    + "random value the moment it rises"),
                            port("min", "the lowest value the random draw can land on"),
                            port("max", "the highest value the random draw can land on")),
                    "A single number between min and max that stays fixed until the next trigger - not "
                            + "audio, a control value.",
                    """
                    Trigger.out --> Random(min 220, max 880).trigger
                    Random.out --> Phasor.frequency
                    Trigger.out --> Random(min 0.05, max 0.5).trigger
                    Random.out --> Delay.time"""),
            new Article("Sources & Sinks", "MIDI Input",
                    "Play the patch live from a connected MIDI keyboard or controller.",
                    """
                    MIDI Input has no inputs and three outputs - pitch, gate, and velocity - driven by a \
                    real MIDI device instead of anything wired in the graph. Click its MIDI... button \
                    (next to the documentation button) to choose which connected device to listen to and, \
                    right after, which MIDI channel to answer to (Omni, or 1-16); until a device is chosen \
                    it just outputs silence.

                    Holding a key sets pitch to that note's frequency in Hz and gate to 1; releasing it \
                    drops gate back to 0 (pitch holds its last value, so a Slew or portamento effect \
                    downstream has something to glide from). velocity is how hard the key was struck, \
                    scaled to 0-1. A pitch bend wheel bends pitch up or down by up to 2 semitones, applied \
                    on top of whichever note is currently sounding - it never compounds across repeated \
                    bend messages, since it is always recomputed from the raw note rather than from the \
                    already-bent frequency. Playing a chord is last-note priority, same as everywhere else \
                    in this project a pitch/gate pair is one voice - for real polyphony, point several MIDI \
                    Input nodes at separate instruments, each picking its own device and/or channel, so one \
                    physical controller split across MIDI channels can drive several instruments from a \
                    single cable.

                    Which device is chosen is not saved with the patch, the same way the audio output \
                    device itself isn't - reopening a saved patch needs the device picked again. The \
                    channel, unlike the device, is a patch-design choice (which instrument answers to which \
                    channel) and is saved with the patch.

                    A MIDI Input node inside a patch loaded as a node (see Load patch as node...) is muted \
                    - it always outputs silence, since a live device connection made in the original \
                    standalone patch shouldn't keep driving audio once that patch becomes one node inside \
                    a bigger one.""",
                    List.of(),
                    "pitch (Hz), gate (1 while a key is held), and velocity (0-1, how hard it was struck) "
                            + "- three separate control signals, not audio.",
                    """
                    MIDI Input.pitch --> Sine.phase
                    MIDI Input.gate --> ADSR.gate
                    MIDI Input.velocity --> Map Range(outMin 400, outMax 4000) --> Ladder Filter.cutoff
                    ADSR.out --> Amplifier.gain
                    Sine --> Amplifier.in --> Ladder Filter.in --> Audio Out.in"""),
            new Article("Sources & Sinks", "Mic Input",
                    "Stream a live microphone or line input straight into the patch.",
                    """
                    Mic Input has no inputs and one output - a live audio signal read straight off a \
                    hardware input device instead of anything wired in the graph. Click its Mic... button \
                    (next to the documentation button) to choose which connected input device to capture \
                    from; until a device is chosen it just outputs silence.

                    Capture runs on its own background thread and is read a short, fixed distance (about \
                    50 milliseconds) behind where it's being written, the same "read a bit behind the write \
                    head" trick Granular Freeze uses for its live-recording input - the safety margin \
                    absorbs ordinary thread-scheduling jitter so the output doesn't stutter.

                    This is for wiring your live voice or an instrument through the patch's own effects in \
                    real time (a live filter, a live distortion, a live reverb) - it is not for capturing a \
                    take to keep. To record something and keep it, place a Sound Clip node instead and use \
                    its own Record button.

                    Which device is chosen is not saved with the patch, the same way MIDI Input's device \
                    isn't - reopening a saved patch needs the device picked again.

                    A Mic Input node inside a patch loaded as a node (see Load patch as node...) is muted \
                    - it always outputs silence, since a live device connection made in the original \
                    standalone patch shouldn't keep driving audio once that patch becomes one node inside \
                    a bigger one, the same rule MIDI Input follows.""",
                    List.of(),
                    "A live audio signal read from the chosen input device, roughly between -1 and 1 - "
                            + "the same kind of signal Audio Out expects.",
                    """
                    Mic Input.out --> Reverb.in --> Audio Out.in
                    Mic Input.out --> Ladder Filter.in --> Audio Out.in"""),
            new Article("Sources & Sinks", "Ping Pong",
                    "Like Phasor, but sweeps up then back down instead of jumping at the end of a cycle.",
                    """
                    Ping Pong is a phasor whose output ramps from 0 up to 2*PI and then back down to 0 \
                    within a single cycle, instead of jumping straight back to 0 the way Phasor does. \
                    Feeding it into an oscillator plays that oscillator's waveform forward then backward \
                    each cycle - one octave lower than the same frequency would sound through a plain \
                    Phasor, since the round trip takes twice as long.

                    Useful anywhere a phasor's end-of-cycle jump would be audible as a click, such as \
                    driving a filter cutoff or gain directly.""",
                    List.of(port("frequency", "how many up-and-down round trips happen per second, in Hz")),
                    "A ramp that rises from 0 to 2*PI and then eases back down to 0, with no sudden jump "
                            + "- not audio by itself, meant to feed an oscillator's phase input.",
                    """
                    Ping Pong(frequency 220) --> Sine.phase --> Audio Out.in
                    Ping Pong(frequency 110) --> Triangle.phase --> Delay(feedback 0.5).in"""),
            new Article("Sources & Sinks", "Period Ping Pong",
                    "Ping Pong with a seconds-per-cycle input instead of Hz.",
                    "Same up-then-down sweep as Ping Pong, just driven by a period input (seconds per " +
                    "cycle) instead of a frequency input, exactly like Period Phasor relates to Phasor.",
                    List.of(port("period", "how many seconds one up-and-down round trip takes")),
                    "The same up-then-down 0 to 2*PI ramp as Ping Pong, just paced in seconds per cycle.",
                    """
                    Period Ping Pong(period 0.01) --> Triangle.phase --> Audio Out.in
                    Period Ping Pong(period 1) --> Sawtooth.phase --> Biquad Lowpass(cutoff 1000).in"""),
            new Article("Sources & Sinks", "Unit Ping Pong",
                    "Ping Pong scaled to a 0..1 range instead of 0..2*PI.",
                    "The up-then-down sweep of Ping Pong, rescaled to 0..1 so it can drive a mix " +
                    "weight, a Map Range, or any other control-style port instead of an oscillator's " +
                    "phase input.",
                    List.of(port("frequency", "how many up-and-down round trips happen per second, in Hz")),
                    "A ramp that rises from 0 to 1 and eases back down to 0, with no sudden jump - a "
                            + "plain 0..1 control signal.",
                    """
                    Unit Ping Pong(frequency 1) --> Map Range(outMin 0.05, outMax 0.4) --> Delay.time
                    Unit Ping Pong(frequency 2) --> Crossfade.mix"""),
            new Article("Sources & Sinks", "Unit Period Ping Pong",
                    "Unit Ping Pong with a seconds-per-cycle input instead of Hz.",
                    "Combines Unit Ping Pong's 0..1 output with a period (seconds per cycle) input " +
                    "instead of a frequency input - the slowest, smoothest of the eight phasor " +
                    "variants to dial in by ear.",
                    List.of(port("period", "how many seconds one up-and-down round trip takes")),
                    "The same up-then-down 0 to 1 ramp as Unit Ping Pong, just paced in seconds per cycle.",
                    """
                    Unit Period Ping Pong(period 8) --> Map Range(outMin 0.1, outMax 2) --> Chorus.rate
                    Unit Period Ping Pong(period 6) --> Amplifier.gain"""),
            new Article("Sources & Sinks", "LFO Sine",
                    "A ready-made low-frequency sine wave for modulation.",
                    """
                    LFO Sine is a sine oscillator tuned for modulation rather than audio - its frequency \
                    default is 2 Hz instead of 440 Hz. Wire its output into any other node's port (a \
                    filter cutoff, an amplitude, a pan) to get a smooth wobble at that rate.

                    Its output swings between -1 and 1. If you need a 0..1 range instead, follow it with \
                    a Map Range. The reset input snaps its cycle back to the start on a rising edge, \
                    useful for syncing the wobble to a note or a sequencer step.""",
                    List.of(
                            port("frequency", "how many wobbles per second, in Hz"),
                            port("reset", "a pulse that snaps the wobble's cycle back to its start the "
                                    + "moment it rises")),
                    CONTROL_NOT_AUDIO + " Swings smoothly between -1 and 1.",
                    """
                    LFO Sine(frequency 4) --> Map Range(outMin 200, outMax 1200) --> Biquad Lowpass.cutoff
                    LFO Sine(frequency 0.5) --> Amplifier.gain"""),
            new Article("Sources & Sinks", "LFO Triangle",
                    "Like LFO Sine, but a triangle wave.",
                    "The same modulation-rate oscillator as LFO Sine, with a linear up-then-down " +
                    "triangle shape instead of a sine curve - a straighter-edged wobble, useful when " +
                    "you want a modulation that ramps at a constant rate rather than easing in and out.",
                    List.of(
                            port("frequency", "how many wobbles per second, in Hz"),
                            port("reset", "a pulse that snaps the wobble's cycle back to its start")),
                    CONTROL_NOT_AUDIO + " Ramps linearly between -1 and 1.",
                    """
                    LFO Triangle(frequency 3) --> Amplifier.gain
                    LFO Triangle(frequency 2) --> Map Range(outMin 400, outMax 2000) --> Biquad Lowpass.cutoff"""),
            new Article("Sources & Sinks", "LFO Square",
                    "Like LFO Sine, but flips between -1 and 1 with no in-between.",
                    "The same modulation-rate oscillator as LFO Sine, but its output snaps between -1 " +
                    "and 1 instead of curving smoothly - useful for on/off style modulation, like " +
                    "switching a filter between two cutoffs on a beat.",
                    List.of(
                            port("frequency", "how many flips per second, in Hz"),
                            port("reset", "a pulse that snaps the wobble's cycle back to its start")),
                    CONTROL_NOT_AUDIO + " Snaps instantly between -1 and 1.",
                    """
                    LFO Square(frequency 2) --> CV Gate.control
                    LFO Square(frequency 1) --> Crossfade.mix"""),
            new Article("Sources & Sinks", "LFO Sawtooth",
                    "Like LFO Sine, but a rising ramp that snaps back down each cycle.",
                    "The same modulation-rate oscillator as LFO Sine, with a linear rising-ramp shape " +
                    "that jumps back to the start at the end of each cycle - good for a repeating " +
                    "rising sweep, like a filter cutoff that climbs and resets.",
                    List.of(
                            port("frequency", "how many ramps per second, in Hz"),
                            port("reset", "a pulse that snaps the wobble's cycle back to its start")),
                    CONTROL_NOT_AUDIO + " Rises from -1 to 1, then jumps back down.",
                    """
                    LFO Sawtooth(frequency 0.5) --> Map Range(outMin 200, outMax 4000) --> Ladder Filter.cutoff
                    LFO Sawtooth(frequency 1) --> Map Range(outMin 0.2, outMax 0.8) --> Square.width"""),
            new Article("Sources & Sinks", "Audio Out",
                    "The exit point of the patch - every patch needs exactly one.",
                    """
                    Audio Out has one input, in, and no outputs. Whatever is wired into it is what you \
                    actually hear when you press Play, and what gets written to a file by Render to \
                    WAV.... A patch must have exactly one Audio Out node to play or render at all - \
                    zero or more than one is reported as an error rather than silently guessing which \
                    one you meant.""",
                    List.of(port("in", "the final audio signal for the whole patch - whatever is wired "
                            + "here is literally what comes out of the speakers")),
                    "None - this node has no output port at all. It is the end of the line.",
                    """
                    Amplifier.out --> Audio Out.in
                    Mix.out --> Audio Out.in"""),
            new Article("Sources & Sinks", "Stereo Audio Out",
                    "The stereo exit point of a patch - use this instead of Audio Out for real left/right sound.",
                    """
                    Stereo Audio Out has two inputs, inL and inR, and no outputs. Whatever is wired into \
                    them is what actually comes out of the left and right speakers when you press Play, \
                    and what gets written to a file by Render to WAV. A patch can have exactly one Audio \
                    Out or Stereo Audio Out node, never both - use Panner and Stereo Mix (see Mixers) to \
                    build up a left/right signal to feed it.""",
                    List.of(
                            port("inL", "the left channel's final audio signal"),
                            port("inR", "the right channel's final audio signal")),
                    "None - this node has no output port at all. It is the end of the line.",
                    """
                    Panner Equal Power.outL --> Stereo Audio Out.inL
                    Panner Equal Power.outR --> Stereo Audio Out.inR"""),
            new Article("Sources & Sinks", "Recording End",
                    "Stops a render the moment it is triggered, however long the requested length was.",
                    """
                    Recording End has one input, trigger, and no outputs. During a render (Render to WAV \
                    or the petroleum render command) the first rising edge on trigger ends the file right \
                    there, on that exact sample - so the length you typed in the export dialog becomes a \
                    ceiling rather than the actual length. Wire a Trigger, a Sequencer step, a comparison \
                    on an envelope, or anything else that produces a 1 for one sample into it, exactly as \
                    with any other trigger input.

                    It also lets a patch be rendered with no length at all: leave the duration blank in \
                    the export dialog (or drop --duration from the command line) and the render runs \
                    until this node fires, instead of being rejected as a patch that plays forever. That \
                    still has a safety ceiling, so a trigger that never arrives stops the render rather \
                    than filling the disk - the "Render Limit..." button in the header sets how long that \
                    is.

                    The node does nothing during live playback - Play is not a recording, so there is \
                    nothing to end - and it only ends the render when it sits in the top-level patch, \
                    not inside a Patch-as-node module.""",
                    List.of(port("trigger", "a rising edge (a 1 for one sample) ends the render here")),
                    "None - this node has no output port at all. It is the end of the recording.",
                    """
                    Trigger(delay 8) --> Recording End.trigger
                    Sequencer.out --> Recording End.trigger"""),
            new Article("Sources & Sinks", "Virtual Line Out",
                    "A second, mono exit point that feeds a virtual audio cable instead of the speakers.",
                    """
                    Virtual Line Out has one input, in, and no outputs, same shape as Audio Out - but \
                    instead of feeding the speakers it feeds a virtual audio device already installed on \
                    this machine (such as a loopback/virtual-cable device many operating systems or \
                    third-party drivers provide), so another application can record or stream it as if it \
                    were a real line input. Click its Line... button to choose which virtual device to \
                    write to; until one is chosen it writes nothing.

                    It exists alongside whichever of Audio Out or Stereo Audio Out the patch already uses \
                    - a patch has exactly one of those as its real output, plus as many Virtual Line Out \
                    or Stereo Virtual Line Out nodes as you like feeding separate virtual cables. It only \
                    ever writes during live Play, never during Render to WAV....

                    Which device is chosen is session-only, the same as MIDI Input's device and the audio \
                    output device itself - reopening a saved patch needs it picked again. Inside a patch \
                    loaded as a node it is muted, same as MIDI Input, so an embedded patch never blasts \
                    audio into a virtual cable behind the scenes.

                    Petroleum only opens a device that already exists - it does not create one - so the Line... \
                    dropdown is empty until a virtual/loopback device is installed on the machine, a \
                    one-time setup step that differs by OS.

                    On Linux, this dropdown can only ever list real ALSA sound cards - a PulseAudio or \
                    PipeWire virtual sink made with "pactl load-module module-null-sink" does not show up \
                    here, no matter how it's created, since it lives above ALSA rather than as an ALSA card \
                    (this was tried and confirmed absent). Use the ALSA loopback kernel module instead: run \
                    "sudo modprobe snd-aloop" to create a small virtual sound card wired straight to itself. \
                    Pick its first subdevice here (listed as something like "Loopback, Loopback PCM \
                    [hw:Loopback,0]"), and have the other application record from the paired second \
                    subdevice (index 1) - if that application talks to ALSA directly this works exactly \
                    like a hardware loopback cable. Add "snd-aloop" to /etc/modules-load.d/ to have it load \
                    automatically at boot instead of running modprobe by hand every time. A PulseAudio/\
                    PipeWire null-sink is still useful between two Pulse-aware applications - for example \
                    routing into Audacity with its own Host preference set to PulseAudio rather than ALSA - \
                    just never for Petroleum itself, since it only ever talks to ALSA.

                    On macOS, install the free, open-source BlackHole driver - "brew install \
                    blackhole-2ch" via Homebrew, or its installer from the BlackHole project page. Once \
                    installed, "BlackHole 2ch" appears as an ordinary device everywhere: pick it here, and \
                    have the other application record from "BlackHole 2ch" too. If silent live monitoring \
                    while routing is also wanted, combine BlackHole with the real output into a Multi-Output \
                    Device in Audio MIDI Setup.

                    On Windows, install the free VB-Audio Virtual Cable driver from vb-audio.com (a reboot \
                    is typically required afterward). It adds two devices: "CABLE Input", which is what \
                    shows up here, and "CABLE Output", which the other application should select as its \
                    recording device.""",
                    List.of(port("in", "the audio signal to send to the chosen virtual device")),
                    "None - this node has no output port at all. It is the end of a line, just not the "
                            + "main one.",
                    """
                    Mix.out --> Audio Out.in
                    Mix.out --> Virtual Line Out.in"""),
            new Article("Sources & Sinks", "Stereo Virtual Line Out",
                    "Virtual Line Out with left/right inputs, for a stereo virtual cable.",
                    "The same virtual-cable idea as Virtual Line Out, with two inputs (inL, inR) instead " +
                    "of one, exactly the way Stereo Audio Out relates to Audio Out - use it to send a " +
                    "stereo mix to another application instead of a mono one. See Virtual Line Out's own " +
                    "article for how to install a virtual/loopback device in the first place - the same " +
                    "device works for both.",
                    List.of(
                            port("inL", "the left channel to send to the chosen virtual device"),
                            port("inR", "the right channel to send to the chosen virtual device")),
                    "None - this node has no output port at all.",
                    """
                    Panner Equal Power.outL --> Stereo Audio Out.inL
                    Panner Equal Power.outL --> Stereo Virtual Line Out.inL
                    Panner Equal Power.outR --> Stereo Audio Out.inR
                    Panner Equal Power.outR --> Stereo Virtual Line Out.inR"""),
            new Article("Envelopes", "Trigger",
                    "Fires a single pulse on its own, without a button.",
                    """
                    Trigger emits one single-sample pulse delay seconds after playback or rendering \
                    starts, with no manual "fire" button anywhere - it fires itself off the clock. If \
                    interval is greater than zero it keeps firing again every interval seconds after \
                    that; left at its default of zero it fires exactly once per session.

                    Wire its output into an Envelope's trigger input to start a one-shot sound, or into \
                    a Sequencer's trigger to start stepping through a pattern. Because it re-arms itself \
                    at the start of every new play or render, pressing Play twice in a row always fires \
                    it again.""",
                    List.of(
                            port("delay", "how many seconds to wait, after Play starts, before firing "
                                    + "the first pulse"),
                            port("interval", "how many seconds between repeats after that first pulse; "
                                    + "0 means it only ever fires once")),
                    "A single-sample pulse (1 for an instant, 0 the rest of the time) - not audio, just "
                            + "a starting signal for other nodes to react to.",
                    """
                    Trigger(delay 0).out --> Envelope.trigger
                    Trigger(delay 0).out --> Ramp.trigger
                    Trigger(delay 0, interval 1).out --> Counter.trigger (self-firing retriggered pulse every second)"""),
            new Article("Envelopes", "Envelope",
                    "A one-shot loudness shape: attack, punch, decay - the classic \"pew\" or \"boop.\"",
                    """
                    Envelope ramps from 0 up to 1 over attack seconds, briefly overshoots to 1 + punch, \
                    settles back down to 1, then ramps down to 0 over decay seconds and stays there \
                    until the next trigger. It fires on a rising edge of its trigger input.

                    punch is what gives a hit, coin, or explosion its percussive snap - there is no \
                    equivalent in a conventional ADSR, which is why this node exists alongside ADSR \
                    rather than replacing it. A negative punch softens the shape into a gentle swell \
                    instead. Multiply this envelope's output into an Amplifier's gain to shape any \
                    sustained sound into a one-shot hit.""",
                    List.of(
                            port("trigger", "a pulse that starts the shape from the beginning"),
                            port("attack", "seconds to rise from 0 up to 1"),
                            port("sustain", "seconds to hold near the peak before decaying - a time, "
                                    + "not a level (unlike ADSR's sustain)"),
                            port("punch", "how far above 1 the peak briefly overshoots - what gives a "
                                    + "hit its percussive snap; can be negative for a gentle swell instead"),
                            port("decay", "seconds to fall from 1 back down to 0")),
                    "A loudness curve over time, 0 at rest and briefly above 1 right after the peak if "
                            + "punch is positive - not audio by itself, multiply it into an Amplifier's "
                            + "gain to actually hear it shape a sound.",
                    """
                    Trigger.out --> Envelope(attack .005, sustain .04, punch .4, decay .15).trigger
                    Envelope.out --> Amplifier.gain
                    White Noise --> Amplifier.in
                    Trigger.out --> Envelope(attack .1, sustain .05, punch -.2, decay .2).out --> Amplifier.gain --> Audio Out.in (soft swell with negative punch)"""),
            new Article("Envelopes", "ADSR",
                    "The classic sustained envelope: Attack, Decay, Sustain, Release.",
                    """
                    ADSR follows its gate input rather than firing once from a trigger: while gate is \
                    held high it ramps up over attack, eases down to the sustain level over decay, and \
                    holds sustain for as long as the gate stays high. Releasing the gate ramps back down \
                    to 0 over release seconds, starting from wherever the envelope currently sits - so \
                    releasing mid-attack never causes a jump.

                    Reach for ADSR over Envelope for anything that should keep sounding for as long as a \
                    note is held - a pad, a siren, an engine loop - rather than a fixed-length one-shot.""",
                    List.of(
                            port("gate", "held above 0 for as long as the note is \"on\"; releasing it "
                                    + "(dropping to 0) starts the release stage"),
                            port("attack", "seconds to rise from 0 up to 1"),
                            port("decay", "seconds to ease down from 1 to the sustain level"),
                            port("sustain", "the level (0 to 1) held for as long as gate stays on - a "
                                    + "level, not a time (unlike Envelope's sustain)"),
                            port("release", "seconds to fall back to 0 once gate is released")),
                    "A loudness curve between 0 and 1 that rises while gate is held and falls once it's "
                            + "released - not audio by itself, multiply it into an Amplifier's gain.",
                    """
                    CV Gate.out --> ADSR(attack .01, decay .1, sustain .6, release .3).gate
                    ADSR.out --> Amplifier.gain
                    Phasor --> Sawtooth.phase
                    CV Gate.out --> ADSR(attack .5, decay .2, sustain .85, release .8).gate
                    ADSR.out --> Amplifier.gain --> Audio Out.in (slow pad with long attack and release)"""),
            new Article("Envelopes", "Ramp",
                    "Slides smoothly from one value to another once, on trigger.",
                    """
                    Ramp moves linearly from from to to over time seconds after a rising trigger edge, \
                    then holds at to. Before the first trigger it outputs from.

                    This is the pitch-slide primitive: wire it into a Phasor's frequency input for a \
                    falling laser or a rising powerup sound, or into a filter's cutoff for a swept \
                    effect. Both from and to can themselves be modulated while the ramp is running.""",
                    List.of(
                            port("trigger", "a pulse that starts the slide"),
                            port("from", "the starting number, held here until the first trigger"),
                            port("to", "the ending number, held here once the slide finishes"),
                            port("time", "how many seconds the slide from from to to takes")),
                    "A single number that slides from from to to and then holds - not audio, typically "
                            + "wired into a pitch or cutoff port.",
                    """
                    Trigger.out --> Ramp(from 880, to 220, time .3).trigger
                    Ramp.out --> Phasor.frequency --> Sine.phase --> Audio Out.in
                    Trigger.out --> Ramp(from 100, to 3000, time .4).trigger
                    Sawtooth --> Biquad Lowpass.in
                    Ramp.out --> Biquad Lowpass.cutoff --> Audio Out.in (sweeping filter cutoff)"""),
            new Article("Envelopes", "Function Generator",
                    "A rise/fall envelope that can retrigger itself - one shape for both a "
                            + "percussive hit and a free-running LFO or audio-rate oscillator.",
                    """
                    Function Generator rises linearly from 0 to 1 over rise seconds, then falls back to \
                    0 over fall seconds, on a rising edge of trigger - exactly like Ramp(from 0, to 1) \
                    chained with its own mirror image. What makes it different is cycle: held above 0, \
                    the generator restarts its own rise the instant a fall completes, with no trigger \
                    needed, turning the same shape into a free-running LFO at slow rise/fall times or a \
                    raw audio-rate oscillator at fast ones - unequal rise and fall times give it a \
                    sawtooth-leaning brightness a symmetric triangle can't. eoc fires a single-sample \
                    pulse at the end of every fall, cycling or not, so one Function Generator can drive \
                    the next one's trigger to build a chain of dependent stages, or feed back into its \
                    own cycle input for irregular, self-interrupting shapes.

                    This is the one generator to reach for instead of separately picking Envelope, ADSR, \
                    or an LFO node: leave cycle at 0 for a one-shot hit, wire a gate into it for a \
                    sustained shape, or hold cycle high for a self-clocking LFO/oscillator - the same \
                    node covers all three the way a Buchla-style function generator does.""",
                    List.of(
                            port("trigger", "a pulse that restarts the rise from the beginning"),
                            port("cycle", "held above 0, restarts the rise automatically at the end of "
                                    + "every fall instead of waiting for another trigger - the free-run "
                                    + "switch"),
                            port("rise", "seconds to climb from 0 up to 1"),
                            port("fall", "seconds to fall back from 1 to 0")),
                    "out is the 0-to-1 shape; eoc is a single-sample pulse at the end of every fall, "
                            + "useful for triggering the next stage in a chain.",
                    """
                    Function Generator(rise .3, fall .3, cycle 1).out --> Sine.phase (via a Phasor) - a \
                    slow triangle LFO
                    Function Generator(rise .001, fall .001, cycle 1).out --> Audio Out.in - the same \
                    node as a buzzy audio-rate oscillator
                    Function Generator A.eoc --> Function Generator B.trigger - chaining stages
                    CV Gate.out --> Function Generator(rise .15, fall .15, cycle 1).cycle
                    Function Generator.out --> Amplifier.gain (gate-controlled sustained shape, like ADSR)"""),
            new Article("Envelopes", "Arpeggio",
                    "Steps a frequency up or down by a fixed ratio, a set number of times.",
                    """
                    Arpeggio passes frequency straight through, then every delay seconds multiplies its \
                    output by ratio, repeating up to steps times (0 means unlimited). A rising trigger \
                    edge restarts the sequence from the original frequency.

                    ratio = 2 with a few steps gives a rising octave run; ratio = 0.5 gives a falling \
                    one. Unlike Ramp, which slides continuously between two values, Arpeggio jumps in \
                    discrete steps and holds between them, which is what makes it read as separate notes \
                    rather than a slide.""",
                    List.of(
                            port("frequency", "the starting pitch in Hz, before any steps are applied"),
                            port("trigger", "a pulse that restarts the step ladder from frequency"),
                            port("delay", "seconds between each step"),
                            port("ratio", "what each step multiplies the frequency by - 2 doubles it "
                                    + "(an octave up), 0.5 halves it (an octave down)"),
                            port("steps", "how many times to multiply before stopping; 0 means unlimited")),
                    "A stepped frequency value in Hz - not audio by itself, feed it into a Phasor's "
                            + "frequency to actually hear the pitch change.",
                    """
                    Constant(440) --> Arpeggio(delay .08, ratio 2, steps 3).frequency
                    Trigger --> Arpeggio.trigger
                    Arpeggio.out --> Phasor.frequency --> Sine.phase --> Audio Out.in
                    Constant(2000) --> Arpeggio(delay .1, ratio 0.5, steps 4).frequency
                    Trigger.out --> Arpeggio.trigger
                    Arpeggio.out --> Phasor.frequency --> Sine.phase --> Audio Out.in (descending octave pattern)"""),
            new Article("Oscillators", "Sine",
                    "The purest tone - a single smooth wave with no extra harmonics.",
                    "Sine is the simplest waveform, a plain sine curve with no harmonics beyond the " +
                    "fundamental. Wire a Phasor into its phase input to set the pitch.",
                    List.of(port("phase", "the position within one wave cycle (0 to 2*PI radians) - "
                            + "wire a Phasor here to set the pitch; you rarely set this by hand")),
                    AUDIO_WAVE,
                    """
                    Phasor(frequency 440) --> Sine.phase --> Audio Out.in
                    LFO Sine(frequency 3) --> Map Range(outMin 200, outMax 800) --> Phasor.frequency --> Sine.phase --> Amplifier.gain"""),
            new Article("Oscillators", "Cosine",
                    "The same tone as Sine, just a quarter-cycle ahead in phase.",
                    "Identical to Sine except its wave is shifted a quarter cycle earlier, so it starts " +
                    "at its peak instead of at 0. Sounds identical to Sine on its own; the difference " +
                    "only matters when you are combining it with other signals that care about phase " +
                    "alignment.",
                    List.of(port("phase", "the position within one wave cycle - wire a Phasor here")),
                    AUDIO_WAVE,
                    """
                    Phasor --> Cosine.phase --> Audio Out.in
                    Phasor --> Sine.phase --> Mix.in0
                    Phasor --> Cosine.phase --> Mix.in1
                    Mix.out --> Amplifier(gain 0.5) --> Audio Out.in (sine and cosine summed for richer tone)"""),
            new Article("Oscillators", "Sawtooth",
                    "A bright, buzzy tone that ramps up and snaps back down each cycle.",
                    "A linear ramp from -1 to 1 across each cycle, rich in both even and odd harmonics " +
                    "- the classic bright, buzzy synth-lead and bass waveform.",
                    List.of(port("phase", "the position within one wave cycle - wire a Phasor here")),
                    AUDIO_WAVE,
                    """
                    Phasor(frequency 110) --> Sawtooth.phase --> Ladder Filter.in --> Audio Out.in
                    Trigger --> Ramp(from 880, to 220, time .5).trigger
                    Ramp.out --> Phasor.frequency --> Sawtooth.phase --> Audio Out.in (sliding sawtooth laser)"""),
            new Article("Oscillators", "Square",
                    "A hollow, reedy tone with a settable duty cycle for pulse-width modulation.",
                    """
                    Square flips between +1 and -1, with a width input controlling the duty cycle - at \
                    the default 0.5 it is an even square wave, and sweeping width away from 0.5 is \
                    classic pulse-width modulation, thinning the tone out. Since width is an ordinary \
                    patchable port, wire an LFO into it for a moving, animated PWM effect.""",
                    List.of(
                            port("phase", "the position within one wave cycle - wire a Phasor here"),
                            port("width", "the fraction of each cycle spent at +1 before flipping to "
                                    + "-1; 0.5 is an even square, other values thin the tone out (PWM)")),
                    AUDIO_WAVE,
                    """
                    Phasor --> Square.phase
                    LFO Triangle(frequency 0.2) --> Map Range(outMin 0.1, outMax 0.9) --> Square.width
                    Constant(0.5) --> Square.width
                    Phasor(frequency 55) --> Square.phase --> Biquad Lowpass(cutoff 2000, q 3) --> Audio Out.in (even square bass)"""),
            new Article("Oscillators", "Triangle",
                    "A softer, rounder tone than Sawtooth with fewer harmonics.",
                    "A linear ramp up and back down each cycle, giving a mellower tone than Sawtooth " +
                    "with only odd harmonics that fall off quickly - closer to a flute than a buzz.",
                    List.of(port("phase", "the position within one wave cycle - wire a Phasor here")),
                    AUDIO_WAVE,
                    """
                    Phasor --> Triangle.phase --> Audio Out.in
                    LFO Triangle(frequency 0.5) --> Map Range(outMin 55, outMax 220) --> Phasor.frequency --> Triangle.phase --> Audio Out.in"""),
            new Article("Oscillators", "White Noise",
                    "Flat-spectrum hiss - every frequency at equal strength.",
                    "Uniform random noise with an even spectrum across all frequencies - the harshest, " +
                    "brightest of the noise colors, good raw material for hi-hats, snares, and wind.",
                    List.of(port("gate", "1 passes noise through, 0 silences it - there is no pitch to "
                            + "set here, only on/off")),
                    AUDIO_WAVE + " Random, hissy, and unpitched.",
                    """
                    White Noise --> Biquad Bandpass(cutoff 6000, q 2) --> Audio Out.in
                    Trigger --> Envelope(attack .005, sustain .08, decay .1).out --> Amplifier.gain
                    White Noise --> Amplifier.in (percussive hi-hat from white noise)"""),
            new Article("Oscillators", "Pink Noise",
                    "Noise weighted toward low frequencies - a softer hiss than White Noise.",
                    "Noise shaped to roughly 1/f - each octave carries about the same energy, so it " +
                    "sounds noticeably darker and less harsh than White Noise. A common choice for " +
                    "background texture or a softer wind/rain effect.",
                    List.of(port("gate", "1 passes noise through, 0 silences it - noise has no pitch, "
                            + "only on/off")),
                    AUDIO_WAVE + " Random and unpitched, darker-sounding than White Noise.",
                    """
                    Pink Noise --> Amplifier(gain 0.4) --> Audio Out.in
                    Pink Noise --> Delay(time 0.3) --> Mix.in0
                    Pink Noise --> Mix.in1
                    Mix.out --> Amplifier(gain 0.3) --> Audio Out.in (rhythmic pink noise texture)"""),
            new Article("Oscillators", "Brown Noise",
                    "Deep, rumbling noise - even darker than Pink Noise.",
                    "Noise shaped to roughly 1/f-squared, weighted even further toward low frequencies " +
                    "than Pink Noise - a deep rumble, good for thunder, surf, or low rumbling textures.",
                    List.of(port("gate", "1 passes noise through, 0 silences it - noise has no pitch, "
                            + "only on/off")),
                    AUDIO_WAVE + " Random and unpitched, a deep rumble.",
                    """
                    Brown Noise --> Audio Out.in
                    Brown Noise --> Reverb.in --> Amplifier(gain 0.5) --> Audio Out.in (deep resonant texture)"""),
            new Article("Oscillators", "Blue Noise",
                    "Noise weighted toward high frequencies - the opposite of Pink Noise.",
                    "Noise shaped to rise roughly with frequency, the mirror image of Pink Noise - a " +
                    "thin, hissy texture with the low end thinned out.",
                    List.of(port("gate", "1 passes noise through, 0 silences it - noise has no pitch, "
                            + "only on/off")),
                    AUDIO_WAVE + " Random and unpitched, thin and hissy.",
                    """
                    Blue Noise --> Audio Out.in
                    Blue Noise --> Distortion Soft Clip.in --> Amplifier(gain 0.3) --> Audio Out.in (edgy high-end texture)"""),
            new Article("Oscillators", "Violet Noise",
                    "Even brighter than Blue Noise - strongly weighted toward high frequencies.",
                    "Noise shaped to rise even more steeply with frequency than Blue Noise - a very " +
                    "thin, sharp hiss, useful as a bright top-end texture layered under other sounds.",
                    List.of(port("gate", "1 passes noise through, 0 silences it - noise has no pitch, "
                            + "only on/off")),
                    AUDIO_WAVE + " Random and unpitched, a very thin, sharp hiss.",
                    """
                    Violet Noise --> Amplifier(gain 0.2) --> Audio Out.in
                    Phasor(frequency 220) --> Sine.phase --> Mix.in0
                    Violet Noise --> Amplifier(gain 0.15) --> Mix.in1
                    Mix.out --> Audio Out.in (sine with crisp top shimmer)"""),
            new Article("Oscillators", "Grey Noise",
                    "Noise shaped to sound roughly equally loud across the range the ear is sensitive to.",
                    "White Noise's flat spectrum is not perceived as flat, since the ear is more " +
                    "sensitive in the middle of its range. Grey Noise boosts the low and high ends and " +
                    "leaves the middle alone, aiming for a more evenly-loud-sounding hiss.",
                    List.of(port("gate", "1 passes noise through, 0 silences it - noise has no pitch, "
                            + "only on/off")),
                    AUDIO_WAVE + " Random and unpitched.",
                    """
                    Grey Noise --> Audio Out.in
                    Grey Noise --> Compressor Peak(threshold 0.5, ratio 4, attack .01, release .1).in --> Amplifier(gain 0.6) --> Audio Out.in"""),
            new Article("Oscillators", "Sample & Hold",
                    "A stepped random waveform - resamples itself at a fixed number of points per cycle.",
                    """
                    Sample & Hold draws a fresh random value at steps evenly-spaced points within each \
                    cycle and holds each one flat until the next step - the classic "random stepped" \
                    sound. At the default of one step per cycle it draws a new random value once per \
                    cycle; raise steps for a faster, choppier random texture.""",
                    List.of(
                            port("phase", "wire a Phasor here - its frequency sets how fast the steps "
                                    + "cycle through"),
                            port("steps", "how many random values are drawn within each cycle")),
                    AUDIO_WAVE + " A choppy, stepped random texture rather than a smooth wave.",
                    """
                    Phasor(frequency 8) --> Sample & Hold(steps 4).phase --> Ladder Filter.cutoff
                    Phasor(frequency 220) --> Sample & Hold(steps 8).phase --> Audio Out.in (stepped random tone)"""),
            new Article("Oscillators", "Chaotic",
                    "An unpredictable, drifting tone driven by a mathematical chaos equation.",
                    "A sine wave whose phase is nudged once per cycle by a chaotic equation (the " +
                    "logistic map) - the result is deterministic but drifts unpredictably over time, " +
                    "giving an organic, never-quite-repeating wobble.",
                    List.of(port("phase", "wire a Phasor here to set the base pitch")),
                    AUDIO_WAVE + " A sine-like tone that drifts unpredictably rather than repeating exactly.",
                    """
                    Phasor(frequency 220) --> Chaotic.phase --> Audio Out.in
                    Phasor(frequency 110) --> Chaotic.phase --> Biquad Lowpass(cutoff 800, q 4) --> Audio Out.in (organic evolving bass)"""),
            new Article("Oscillators", "SuperSaw",
                    "A thick, detuned stack of sawtooth waves - the classic \"supersaw\" lead/pad sound.",
                    "Several detuned sawtooth waves (seven by default) summed together, in the style " +
                    "of the classic trance/EDM \"supersaw\" patch - a much thicker, wider tone than a " +
                    "single Sawtooth on its own.",
                    List.of(port("phase", "wire a Phasor here to set the pitch")),
                    AUDIO_WAVE + " A thick, wide tone made of several slightly-detuned sawtooth waves.",
                    """
                    Phasor(frequency 220) --> SuperSaw.phase --> Ladder Filter.in --> Audio Out.in
                    Trigger --> ADSR(attack .1, decay .2, sustain .7, release .5).gate
                    ADSR.out --> Amplifier.gain
                    Phasor(frequency 110) --> SuperSaw.phase --> Amplifier.in --> Audio Out.in (wide pad with envelope)"""),
            new Article("Oscillators", "FM",
                    "A metallic, bell-like tone made by modulating one sine wave's pitch with another.",
                    """
                    Two-operator frequency modulation: a carrier sine wave whose pitch is wobbled by a \
                    second sine (the modulator), running at mod ratio times the carrier's frequency, at \
                    a strength of mod index. Small mod index values give a subtle vibrato-like \
                    thickening; larger values produce metallic, bell-like, or harsh tones depending on \
                    mod ratio. Both parameters are ordinary patchable ports, so an envelope on mod index \
                    gives the classic FM "bright attack, mellow sustain" bell/electric-piano character.""",
                    List.of(
                            port("phase", "wire a Phasor here to set the carrier's base pitch"),
                            port("mod ratio", "the modulator's frequency as a multiple of the carrier's "
                                    + "- whole numbers (1, 2, 3...) sound more musical/harmonic"),
                            port("mod index", "how strongly the modulator bends the carrier's pitch - "
                                    + "higher values sound more metallic or harsh")),
                    AUDIO_WAVE + " Ranges from a subtle vibrato-like thickening to harsh, bell-like, "
                            + "metallic tones depending on mod ratio/mod index.",
                    """
                    Phasor(frequency 220) --> FM(mod ratio 3.5, mod index 2).phase
                    Trigger --> Envelope(decay .8).out --> FM.mod index (bright attack, mellow sustain)
                    Phasor(frequency 110) --> FM(mod ratio 1.5, mod index 0.5).phase --> Audio Out.in (subtle harmonic thickening)"""),
            new Article("Oscillators", "Sound Clip",
                    "Record live audio or import a WAV file, edit it, and play it back as a looping waveform.",
                    """
                    Sound Clip places an oscillator that starts as a short silence and opens, via the \
                    "Record" button on the node's header, a dedicated record/edit screen: choose an input \
                    device and click Record to capture a live take, or Import WAV... to bring in an \
                    existing file. Once there's audio, drag across the waveform to select a range, then \
                    Trim to Selection or Delete Selection to edit it, Play to audition, Undo to step back \
                    one edit, and Export WAV... to save the result outside the patch.

                    However it got there, the clip plays back the same way every oscillator here does: \
                    once per phase cycle. Feed it a slow Phasor to hear it back close to its original \
                    speed, or a fast one to loop it as a buzzy, pitched texture - there is no separate \
                    playback-rate concept, phase and pitch are the same knob. Recording captures at a \
                    fixed 44100 Hz; an imported file keeps whatever rate it already had, but since \
                    playback here is phase-driven rather than clock-driven, the original rate only matters \
                    for the editor's own Play button, not for how the clip sounds once wired into a patch.

                    This loops the whole clip every cycle rather than triggering it once per hit; for \
                    one-shot granular playback from a static buffer, see Granular Player instead.""",
                    List.of(port("phase", "wire a Phasor here - its frequency controls how fast the "
                            + "whole clip is replayed, which is also its pitch")),
                    AUDIO_WAVE + " The recorded or imported clip, looped once per phase cycle.",
                    """
                    Phasor(frequency 1) --> Sound Clip.phase --> Audio Out.in
                    LFO Sine(frequency 2) --> Map Range(outMin 0.5, outMax 2) --> Phasor.frequency --> Sound Clip.phase --> Audio Out.in (wobbling clip playback)"""),
            new Article("Oscillators", "Expression",
                    "Define a waveform with a typed-in math formula instead of picking one from a list.",
                    """
                    Expression places an oscillator with an empty ports list and one editable formula \
                    box on the node itself, defaulting to sin(x). Type any expression using x (phase in \
                    radians) or t (phase normalized to one cycle, 0..1), the usual operators, and \
                    built-in functions like sin, cos, abs, pow, clamp, and more.

                    Every keystroke updates the sound live - a formula that doesn't parse yet just \
                    shows a red border and keeps playing whatever the last valid formula was, so a typo \
                    mid-edit never breaks a patch that's already running.""",
                    List.of(port("phase", "wire a Phasor here - inside the formula box it's available "
                            + "as x (radians) or t (0..1)")),
                    AUDIO_WAVE + " Whatever shape your formula computes for each phase value.",
                    """
                    Phasor --> Expression(\"sin(x) + 0.3*sin(3*x)\").phase --> Audio Out.in
                    Phasor(frequency 55) --> Expression(\"abs(sin(x))\").phase --> Ladder Filter.in --> Audio Out.in (rectified sine bass)"""),
            new Article("Oscillators", "Draw Waveform",
                    "Hand-draw a custom waveform shape on its own screen instead of picking or importing one.",
                    """
                    Draw Waveform places an oscillator that starts silent (a flat line at zero) and \
                    opens, via the "Draw" button on the node's header, a dedicated drawing screen with a \
                    click-and-drag canvas. Painting a stroke there writes straight into the same 512-sample \
                    table the oscillator reads, so the sound updates live while you draw - no separate \
                    save step. "Clear" resets the table back to silence.

                    Unlike Sound Clip, which records or imports real audio, Draw Waveform's shape comes \
                    entirely from what you draw - useful for a hand-crafted, non-natural timbre rather \
                    than a recorded sound.""",
                    List.of(port("phase", "wire a Phasor here - its frequency controls the pitch of the "
                            + "drawn waveform")),
                    AUDIO_WAVE + " Whatever shape you drew, looped once per phase cycle.",
                    """
                    Phasor(frequency 220) --> Draw Waveform.phase --> Audio Out.in
                    Unit Phasor(frequency 0.5) --> Map Range(outMin 110, outMax 440) --> Phasor.frequency --> Draw Waveform.phase --> Audio Out.in (sweeping drawn tone)"""),
            new Article("Oscillator Wrappers", "Dual Oscillator",
                    "Layers a phase-shifted copy of an oscillator with itself for a thicker tone.",
                    """
                    Dual Oscillator wraps another oscillator (Sine by default, swappable from a dropdown \
                    on the node) and averages it with a copy of itself offset in phase by the offset \
                    parameter. Small offsets add subtle thickness; larger ones can hollow the tone out or \
                    add movement, depending on the base waveform and how offset is modulated.""",
                    List.of(
                            port("phase", "wire a Phasor here to set the pitch"),
                            port("offset", "how far out of phase (in radians) the second copy is shifted "
                                    + "from the first - small values thicken the tone, larger ones "
                                    + "hollow it out")),
                    AUDIO_WAVE + " A thicker version of the chosen base waveform.",
                    """
                    Phasor --> Dual Oscillator(base Sawtooth, offset 0.4).phase --> Audio Out.in
                    LFO Triangle(frequency 1) --> Map Range(outMin 0, outMax 3.14159) --> Dual Oscillator(base Square).offset
                    Phasor(frequency 110) --> Dual Oscillator(base Square).phase --> Audio Out.in (modulated offset chorus)"""),
            new Article("Oscillator Wrappers", "Wavefolder",
                    "Adds harmonics by folding a wave back into range instead of clipping it.",
                    """
                    Wavefolder wraps another oscillator (Sine by default, swappable from a dropdown on \
                    the node), multiplies its output by gain, and reflects ("folds") any part that \
                    would exceed the -1..1 range back into it instead of clipping flat. Higher gain \
                    values drive harder folding and add more harmonics - a classic West Coast-style \
                    wavefolding tone that gets buzzier the harder it's pushed.""",
                    List.of(
                            port("phase", "wire a Phasor here to set the pitch"),
                            port("gain", "how hard the wave is driven before folding - higher values "
                                    + "add more harmonics and a buzzier tone")),
                    AUDIO_WAVE + " The base waveform folded back into range, sounding buzzier as gain "
                            + "increases.",
                    """
                    Phasor --> Wavefolder(base Sine, gain 4).phase --> Audio Out.in
                    Trigger --> Envelope(attack .01, sustain .05, decay .1).out --> Wavefolder.gain
                    Phasor(frequency 220) --> Wavefolder(base Triangle).phase --> Audio Out.in (dynamic harmonic richness)"""),
            new Article("Filters", "One-Pole Filter",
                    "A gentle lowpass - softens high frequencies above cutoff.",
                    "The simplest lowpass filter: everything above cutoff (in Hz) is smoothly rolled " +
                    "off. A subtle, gentle darkening rather than a sharp cut - reach for Biquad Lowpass " +
                    "or Ladder Filter when a steeper, more resonant cut is wanted.",
                    List.of(
                            port("in", "the audio signal to darken"),
                            port("cutoff", "the frequency (in Hz) above which the sound starts to fade")),
                    AUDIO_SHAPED + " Frequencies above cutoff are gently quieter; below it, unchanged.",
                    """
                    Sawtooth --> One-Pole Filter(cutoff 800) --> Audio Out.in
                    LFO Sine(frequency 1) --> Map Range(outMin 200, outMax 3000) --> One-Pole Filter.cutoff"""),
            new Article("Filters", "One-Pole Highpass",
                    "A gentle highpass - softens low frequencies below cutoff.",
                    "The highpass complement to One-Pole Filter: everything below cutoff (in Hz) is " +
                    "smoothly rolled off, thinning out the low end gently rather than cutting it sharply.",
                    List.of(
                            port("in", "the audio signal to thin out"),
                            port("cutoff", "the frequency (in Hz) below which the sound starts to fade")),
                    AUDIO_SHAPED + " Frequencies below cutoff are gently quieter; above it, unchanged.",
                    """
                    White Noise --> One-Pole Highpass(cutoff 4000) --> Audio Out.in
                    LFO Triangle(frequency 0.5) --> Map Range(outMin 500, outMax 2000) --> One-Pole Highpass.cutoff"""),
            new Article("Filters", "Biquad Lowpass",
                    "A sharper, resonant lowpass with an adjustable resonance peak.",
                    """
                    A steeper lowpass than One-Pole Filter, with a q input controlling resonance - how \
                    much the response peaks right at cutoff before rolling off. High q values give a \
                    pronounced whistling emphasis at the cutoff frequency, the classic squelchy filter- \
                    sweep sound; low q values give a cleaner, gentler roll-off.""",
                    List.of(
                            port("in", "the audio signal to darken"),
                            port("cutoff", "the frequency (in Hz) above which the sound is cut"),
                            port("q", "how much the response peaks (resonates) right at cutoff - higher "
                                    + "is a more pronounced, whistling emphasis"),
                            port("gain", "unused by this filter type - only Biquad Peaking and the two "
                                    + "Shelf filters read it")),
                    AUDIO_SHAPED + " Frequencies above cutoff are steeply quieter, with an optional "
                            + "resonant emphasis right at cutoff.",
                    """
                    Sawtooth --> Biquad Lowpass(cutoff 600, q 6).in
                    LFO Sawtooth(frequency 0.3) --> Map Range(outMin 200, outMax 3000) --> Biquad Lowpass.cutoff
                    White Noise --> Biquad Lowpass(cutoff 1000, q 3) --> Audio Out.in (gentler resonance on noise)"""),
            new Article("Filters", "Biquad Highpass",
                    "A sharper, resonant highpass - the mirror image of Biquad Lowpass.",
                    "Same steep, resonant response as Biquad Lowpass, but cutting below cutoff instead " +
                    "of above it - thins out the low end sharply, with q controlling how much the " +
                    "response peaks right at the cutoff frequency.",
                    List.of(
                            port("in", "the audio signal to thin out"),
                            port("cutoff", "the frequency (in Hz) below which the sound is cut"),
                            port("q", "how much the response peaks right at cutoff"),
                            port("gain", "unused by this filter type")),
                    AUDIO_SHAPED + " Frequencies below cutoff are steeply quieter.",
                    """
                    Sawtooth --> Biquad Highpass(cutoff 2000, q 2) --> Audio Out.in
                    LFO Triangle(frequency 0.4) --> Map Range(outMin 1000, outMax 4000) --> Biquad Highpass.cutoff"""),
            new Article("Filters", "Biquad Bandpass",
                    "Passes only a narrow band of frequencies around cutoff, cutting both above and below.",
                    "Lets through only a band of frequencies centered on cutoff and cuts everything " +
                    "else, both above and below - q controls how narrow that band is. Useful for " +
                    "carving out a specific resonant frequency, like a vowel-ish formant or a telephone " +
                    "effect.",
                    List.of(
                            port("in", "the audio signal to filter"),
                            port("cutoff", "the center frequency (in Hz) of the band that's allowed "
                                    + "through"),
                            port("q", "how narrow the passed band is - higher is narrower"),
                            port("gain", "unused by this filter type")),
                    AUDIO_SHAPED + " Only frequencies near cutoff remain; everything else is quiet.",
                    """
                    White Noise --> Biquad Bandpass(cutoff 1000, q 8) --> Audio Out.in
                    LFO Sine(frequency 0.25) --> Map Range(outMin 500, outMax 4000) --> Biquad Bandpass.cutoff"""),
            new Article("Filters", "Biquad Notch",
                    "Cuts a narrow band of frequencies around cutoff, passing everything else.",
                    "The opposite of Biquad Bandpass: removes a narrow band around cutoff and leaves " +
                    "everything else untouched - useful for surgically removing an unwanted resonance " +
                    "or a specific tone.",
                    List.of(
                            port("in", "the audio signal to filter"),
                            port("cutoff", "the center frequency (in Hz) of the band that's removed"),
                            port("q", "how narrow the removed band is - higher is narrower"),
                            port("gain", "unused by this filter type")),
                    AUDIO_SHAPED + " Frequencies near cutoff are quiet; everything else passes normally.",
                    """
                    Sawtooth --> Biquad Notch(cutoff 1000, q 6) --> Audio Out.in
                    LFO Sine(frequency 0.5) --> Map Range(outMin 2000, outMax 8000) --> Biquad Notch.cutoff (sweep up to remove high tones)"""),
            new Article("Filters", "Biquad Allpass",
                    "Passes every frequency at the same level but shifts their phase around cutoff.",
                    "An allpass filter changes no frequency's loudness at all - it only shifts phase " +
                    "around cutoff. On its own it's inaudible on a single voice; it becomes useful when " +
                    "combined with a dry copy of the same signal (as Phaser does internally), where the " +
                    "phase shift creates moving notches.",
                    List.of(
                            port("in", "the audio signal to phase-shift"),
                            port("cutoff", "the frequency (in Hz) around which the phase shift happens"),
                            port("q", "how sharp the phase transition is around cutoff"),
                            port("gain", "unused by this filter type")),
                    AUDIO_SHAPED + " Same loudness as in at every frequency - only audible once mixed "
                            + "back with a dry copy of the original signal.",
                    """
                    Sawtooth --> Splitter --> out0 to Biquad Allpass.in, out1 to Mix.in1
                    Biquad Allpass.out --> Mix.in0 (the moving phase difference creates notches)
                    Sine --> Biquad Allpass(cutoff 2000, q 1) --> Mix.in0, Sine --> Mix.in1 (30/70 blend)"""),
            new Article("Filters", "Biquad Peaking",
                    "Boosts or cuts a band of frequencies around cutoff - a parametric EQ band.",
                    "Boosts or cuts frequencies near cutoff by gain (in dB) while leaving frequencies " +
                    "far from it alone, with q controlling how wide or narrow that band is - a single " +
                    "parametric EQ band.",
                    List.of(
                            port("in", "the audio signal to equalize"),
                            port("cutoff", "the center frequency (in Hz) of the band being boosted or "
                                    + "cut"),
                            port("q", "how wide or narrow the affected band is"),
                            port("gain", "how many decibels (dB) to boost (positive) or cut (negative) "
                                    + "that band")),
                    AUDIO_SHAPED + " Frequencies near cutoff are louder or quieter depending on gain; "
                            + "everything else is unchanged.",
                    """
                    Sawtooth --> Biquad Peaking(cutoff 1000, gain 6, q 2) --> Audio Out.in
                    Sawtooth --> Biquad Peaking(cutoff 3000, gain -8, q 3) --> Audio Out.in (cut harsh highs)"""),
            new Article("Filters", "Biquad Low Shelf",
                    "Boosts or cuts everything below cutoff by a fixed amount - a bass EQ shelf.",
                    "Raises or lowers every frequency below cutoff by gain (in dB), leaving frequencies " +
                    "above it unchanged - the classic \"bass\" tone-control shelf.",
                    List.of(
                            port("in", "the audio signal to equalize"),
                            port("cutoff", "the frequency (in Hz) below which the shelf applies"),
                            port("q", "how sharp the transition into the shelf is"),
                            port("gain", "how many decibels (dB) to boost (positive) or cut (negative) "
                                    + "everything below cutoff")),
                    AUDIO_SHAPED + " Bass frequencies (below cutoff) are louder or quieter; the rest is "
                            + "unchanged.",
                    """
                    Sawtooth --> Biquad Low Shelf(cutoff 200, gain 6) --> Audio Out.in
                    Pink Noise --> Biquad Low Shelf(cutoff 300, gain -12) --> Audio Out.in (remove deep rumble)"""),
            new Article("Filters", "Biquad High Shelf",
                    "Boosts or cuts everything above cutoff by a fixed amount - a treble EQ shelf.",
                    "Raises or lowers every frequency above cutoff by gain (in dB), leaving frequencies " +
                    "below it unchanged - the classic \"treble\" tone-control shelf.",
                    List.of(
                            port("in", "the audio signal to equalize"),
                            port("cutoff", "the frequency (in Hz) above which the shelf applies"),
                            port("q", "how sharp the transition into the shelf is"),
                            port("gain", "how many decibels (dB) to boost (positive) or cut (negative) "
                                    + "everything above cutoff")),
                    AUDIO_SHAPED + " Treble frequencies (above cutoff) are louder or quieter; the rest "
                            + "is unchanged.",
                    """
                    Sawtooth --> Biquad High Shelf(cutoff 4000, gain -6) --> Audio Out.in
                    Square --> Biquad High Shelf(cutoff 5000, gain 8) --> Audio Out.in (brighten the pulse)"""),
            new Article("Filters", "State Variable Filter",
                    "One filter, four outputs at once: lowpass, highpass, bandpass, and notch.",
                    """
                    State Variable Filter computes lowpass, highpass, bandpass, and notch responses \
                    simultaneously from the same cutoff and q, and exposes all four as separate outputs \
                    - wire whichever one (or several) you need, or crossfade between two of its outputs \
                    for a filter-type sweep that a single-response filter can't do on its own.""",
                    List.of(
                            port("in", "the audio signal to filter"),
                            port("cutoff", "the frequency (in Hz) all four responses are centered on"),
                            port("q", "how resonant/narrow each of the four responses is")),
                    "Four separate audio outputs at once - lowpass, highpass, bandpass, and notch - each "
                            + "a differently-filtered version of in. Wire whichever one you actually want "
                            + "downstream; the others simply go unused if left unwired.",
                    """
                    Sawtooth --> State Variable Filter(cutoff 800, q 3).in
                    State Variable Filter.lowpass --> Crossfade.a
                    State Variable Filter.highpass --> Crossfade.b
                    White Noise --> State Variable Filter.in, State Variable Filter.bandpass --> Audio Out.in"""),
            new Article("Filters", "Ladder Filter",
                    "A warm, resonant lowpass modeled on classic analog synthesizer filters.",
                    """
                    A four-stage lowpass with a soft-saturating character and a feedback path that adds \
                    resonance, in the style of the ladder filters found in classic analog synthesizers. \
                    Pushing resonance close to its maximum makes the filter self-oscillate, producing a \
                    pure tone of its own even with no input signal - useful as a synth voice in its own \
                    right, not just a tone-shaping tool.""",
                    List.of(
                            port("in", "the audio signal to darken"),
                            port("cutoff", "the frequency (in Hz) above which the sound is cut"),
                            port("resonance", "how much the filter emphasizes cutoff - close to its "
                                    + "maximum, the filter can self-oscillate and produce a tone of its "
                                    + "own with no input")),
                    AUDIO_SHAPED + " A warm, steeply-darkened tone; at high resonance it can produce "
                            + "sound on its own even with silence at in.",
                    """
                    Sawtooth --> Ladder Filter(cutoff 400, resonance 0.8) --> Audio Out.in
                    LFO Sine(frequency 0.5) --> Map Range(outMin 200, outMax 2000) --> Ladder Filter.cutoff"""),
            new Article("Filters", "Comb Filter",
                    "A short feedback delay that carves peaks and notches into the tone.",
                    """
                    Comb Filter mixes the input with a delayed, fed-back copy of itself at a very short \
                    delay time, which reinforces some frequencies and cancels others, producing a \
                    "combed" ridged spectrum - the metallic tone heard when a very short echo builds up. \
                    Unlike Delay, it has no dry/wet control - it always outputs the mix. Sweeping time \
                    slowly gives a classic flanging/jet-plane texture.""",
                    List.of(
                            port("in", "the audio signal to color"),
                            port("time", "the delay length in milliseconds - short values (a few ms) "
                                    + "give the metallic \"combed\" tone; note this is milliseconds, "
                                    + "not seconds like Delay's time"),
                            port("feedback", "how much of the delayed copy feeds back in - higher values "
                                    + "give a stronger, more ringing comb effect")),
                    AUDIO_SHAPED + " A metallic, ridged tone from the combination of in and its very "
                            + "short, fed-back echo - always the combined signal, no separate dry level.",
                    """
                    Sawtooth --> Comb Filter(time 10, feedback 0.6) --> Audio Out.in
                    LFO Triangle(frequency 0.3) --> Map Range(outMin 2, outMax 20) --> Comb Filter.time (jet plane sweep)"""),
            new Article("Filters", "Low-Pass Gate",
                    "A Buchla-style vactrol gate - one envelope that darkens and quiets a sound together.",
                    """
                    Low-Pass Gate emulates a real Buchla vactrol: instead of a separate filter envelope \
                    and amplitude envelope running on independent schedules, one shared decay envelope \
                    drives both a low-pass filter's cutoff and the output's volume at once. That's what \
                    gives it the classic "West Coast pluck" - a sound that's bright and loud right after \
                    the strike, then darkens and quiets down together as it decays, rather than a filter \
                    sweep and a volume fade happening on their own timers.

                    A strike is a pulse (1 for an instant, 0 otherwise) on the strike input - the moment \
                    it rises, the envelope restarts from full brightness/volume and begins decaying. \
                    Under the hood the decay is actually two exponential decays blended together: a fast \
                    one and a slow one, approximating how a real vactrol's light-dependent resistor \
                    settles in two stages rather than one smooth curve. vactrolAmount controls the blend \
                    - turn it up for a snappier, more percussive decay dominated by the fast stage; turn \
                    it down for the long, glowing tail vactrols are known for. decay sets the overall \
                    length in seconds.

                    Re-striking mid-decay restarts the envelope from the top, so it can be played \
                    rhythmically like a plucked or struck instrument.""",
                    List.of(
                            port("in", "the audio signal to be gated and filtered"),
                            port("strike", "a pulse (1 for an instant, 0 otherwise) that restarts the "
                                    + "decay envelope the moment it rises"),
                            port("decay", "how many seconds the envelope takes to die away - shorter for "
                                    + "a plucky hit, longer for a slow fade"),
                            port("vactrolAmount", "blends the envelope's fast and slow decay stages - "
                                    + "higher favors the fast, snappier stage; lower favors the slow, "
                                    + "glowing vactrol tail")),
                    AUDIO_SHAPED + " already gated and filtered by the shared vactrol envelope.",
                    """
                    Trigger.out --> Low-Pass Gate.strike
                    Noise.out --> Low-Pass Gate.in --> Audio Out.in
                    Trigger.out --> Low-Pass Gate.strike
                    Phasor(frequency 220) --> Sine.phase --> Low-Pass Gate.in --> Audio Out.in (plucked sine)"""),
            new Article("Effects", "Delay",
                    "A classic echo effect with adjustable time, feedback, and dry/wet mix.",
                    "A feedback delay line: the input is echoed back after time seconds, each repeat " +
                    "quieter by feedback, blended with the dry signal by mix. Raise feedback toward 1 " +
                    "for a long trail of repeats; lower it for a single slap-back echo.",
                    List.of(
                            port("in", "the audio signal to echo"),
                            port("time", "seconds between each echo"),
                            port("feedback", "how much quieter each repeat gets - closer to 1 means "
                                    + "more repeats before it dies out"),
                            port("mix", "0 is fully dry (no echo heard), 1 is fully wet (only the echo)")),
                    AUDIO_SHAPED + " The original signal blended with one or more delayed echoes of "
                            + "itself.",
                    """
                    Sine --> Delay(time .3, feedback .4, mix .5) --> Audio Out.in
                    Sawtooth --> Delay(time .5, feedback .8, mix .7) --> Audio Out.in"""),
            new Article("Effects", "Chorus",
                    "Thickens a sound by blending it with a wobbling, delayed copy of itself.",
                    "Mixes the input with a copy read from a short delay line whose length is slowly " +
                    "wobbled by an internal LFO (rate, depth), with no feedback. The wobble creates " +
                    "subtle pitch variation that thickens and widens the sound, the classic chorus " +
                    "effect used to fatten guitars, synths, and vocals.",
                    List.of(
                            port("in", "the audio signal to thicken"),
                            port("rate", "how fast (in Hz) the internal wobble cycles"),
                            port("depth", "how far (in milliseconds) the delay length wobbles - more "
                                    + "depth means a more noticeable, wavering thickness"),
                            port("mix", "0 is fully dry, 1 is fully the wobbling copy")),
                    AUDIO_SHAPED + " A thicker, subtly wavering version of the original.",
                    """
                    Sawtooth --> Chorus(rate .8, depth .5, mix .5) --> Audio Out.in
                    Sine --> Chorus(rate .3, depth .2, mix .8) --> Audio Out.in"""),
            new Article("Effects", "Flanger",
                    "Like Chorus but with feedback, giving a sweeping, metallic \"jet\" sound.",
                    "Similar to Chorus - a short, LFO-wobbled delay - but with a feedback path added, " +
                    "which produces the sweeping comb-filter \"jet plane\" sound flangers are known for. " +
                    "feedback controls how pronounced that sweeping resonance is.",
                    List.of(
                            port("in", "the audio signal to sweep"),
                            port("rate", "how fast (in Hz) the sweep cycles"),
                            port("depth", "how far (in milliseconds) the delay length sweeps"),
                            port("feedback", "how much of the swept signal feeds back in - higher "
                                    + "values give a deeper, more resonant sweep"),
                            port("mix", "0 is fully dry, 1 is fully the swept copy")),
                    AUDIO_SHAPED + " A sweeping, metallic \"jet plane\" texture layered over the "
                            + "original.",
                    """
                    Sawtooth --> Flanger(rate .2, depth .6, feedback .5, mix .5) --> Audio Out.in
                    Sine --> Flanger(rate 1, depth .8, feedback .9, mix 1) --> Audio Out.in"""),
            new Article("Effects", "Tremolo",
                    "Rhythmic volume pulsing, driven by an internal LFO.",
                    "Continuously varies the input's volume up and down at rate Hz, by depth amount - " +
                    "the classic tremolo effect, a rhythmic pulsing loudness rather than a pitch or tone " +
                    "change.",
                    List.of(
                            port("in", "the audio signal to pulse"),
                            port("rate", "how many volume pulses per second, in Hz"),
                            port("depth", "how deep each pulse dips - 0 is no effect, 1 dips all the "
                                    + "way to silence")),
                    AUDIO_SHAPED + " The same tone as in, with its volume rhythmically rising and "
                            + "falling.",
                    """
                    Sine --> Tremolo(rate 5, depth .8) --> Audio Out.in
                    Sawtooth --> Tremolo(rate 0.5, depth 1) --> Audio Out.in"""),
            new Article("Effects", "Trance Gate",
                    "Chops a signal into a rhythmic on/off (or partial-volume) pattern, driven by its "
                            + "own clock.",
                    """
                    Trance Gate has one input port per step (step0, step1, ...) and multiplies in by \
                    whichever step is currently active. rate sets how many steps happen per second, and \
                    the gate advances through its steps on its own - no external trigger needed.

                    Unlike Tremolo's smooth sine pulsing, every step is a distinct value you set \
                    yourself, so the classic use is a hard on/off chop (step0 1, step1 0, step2 1, step3 \
                    0, ...) for the rhythmic gated-synth sound trance/EDM production is named after - but \
                    dialing in-between values on some steps works just as well for a softer, syncopated \
                    volume pattern instead of a hard chop.""",
                    List.of(
                            port("in", "the audio signal to gate"),
                            port("rate", "how many steps advance per second"),
                            port("step0, step1, ... (one per step)", "the gain applied while that step "
                                    + "is active - 1 is full volume, 0 is silent, eight by default when "
                                    + "placed from the menu")),
                    AUDIO_SHAPED + " in, multiplied by whichever step is currently active.",
                    """
                    Sine --> Trance Gate(rate 8, step0 1, step1 0).in --> Audio Out.in
                    Sawtooth --> Trance Gate(rate 16, step0 1, step1 .5, step2 0, step3 .7).in --> Audio Out.in"""),
            new Article("Effects", "Distortion Soft Clip",
                    "A smooth, tanh-curve overdrive/fuzz with adjustable drive and dry/wet mix.",
                    "Pushes the signal through a soft-clipping curve (tanh) scaled by drive, then " +
                    "blends the result with the dry signal by mix - the classic overdrive/fuzz " +
                    "character, getting harsher and more compressed-sounding as drive increases, with " +
                    "no hard corners anywhere in the curve. The gentlest-sounding of the five Distortion " +
                    "types - see Distortion Hard Clip for the same idea with an abrupt ceiling instead of " +
                    "a smooth one, or Waveshaper for a version where you define the clipping curve " +
                    "yourself.",
                    List.of(
                            port("in", "the audio signal to distort"),
                            port("drive", "how hard the signal is pushed into the clipping curve - "
                                    + "higher values sound more overdriven/fuzzy"),
                            port("mix", "0 is fully dry (clean), 1 is fully distorted")),
                    AUDIO_SHAPED + " An overdriven, fuzzier version of the original.",
                    """
                    Sine --> Distortion Soft Clip(drive 6, mix .8) --> Audio Out.in
                    Sawtooth --> Distortion Soft Clip(drive 2, mix .5) --> Audio Out.in"""),
            new Article("Effects", "Distortion Hard Clip",
                    "Slices the signal flat above a threshold - a sharper, buzzier fuzz than Soft Clip.",
                    "Same drive/mix shape as Distortion Soft Clip, but the clipping curve is a flat " +
                    "line at +-1 instead of a smooth tanh curve - anything driven past that ceiling is " +
                    "sliced off rather than rounded over, adding harsher, buzzier upper harmonics. " +
                    "Reads as more aggressive and \"digital\"-sounding than Soft Clip at the same drive " +
                    "setting.",
                    List.of(
                            port("in", "the audio signal to distort"),
                            port("drive", "how hard the signal is pushed into the +-1 ceiling before "
                                    + "it's sliced flat"),
                            port("mix", "0 is fully dry (clean), 1 is fully distorted")),
                    AUDIO_SHAPED + " A harsh, buzzy, flat-topped version of the original.",
                    """
                    Sawtooth --> Distortion Hard Clip(drive 4, mix 1) --> Audio Out.in
                    Sine --> Distortion Hard Clip(drive 8, mix .6) --> Audio Out.in"""),
            new Article("Effects", "Distortion Foldback",
                    "Reflects the signal back down instead of clipping it - a wavefolder-style distortion.",
                    "Instead of flattening the signal once it passes a ceiling, foldback reflects it back " +
                    "down (and back up again, repeatedly, for high enough drive) - the same folding " +
                    "principle Wavefolder applies to a raw oscillator waveform, here applied to whatever " +
                    "audio signal is patched in. The result is a lot more inharmonic and metallic than " +
                    "Soft Clip or Hard Clip, and changes character dramatically as drive sweeps through " +
                    "each extra fold rather than just getting steadily harsher.",
                    List.of(
                            port("in", "the audio signal to fold"),
                            port("drive", "how far the signal is pushed past the fold points - higher "
                                    + "values add more folds and more inharmonic complexity"),
                            port("mix", "0 is fully dry (clean), 1 is fully folded")),
                    AUDIO_SHAPED + " An inharmonic, metallic-sounding folded version of the original.",
                    """
                    LFO Sine(frequency 0.2) --> Map Range(outMin 0, outMax 20) --> Distortion Foldback.drive
                    Sawtooth --> Distortion Foldback(drive 3, mix .8) --> Audio Out.in"""),
            new Article("Effects", "Distortion Overdrive",
                    "A cubic soft-clip curve - a flatter middle and a rounder knee than Soft Clip.",
                    "A different clipping curve from Soft Clip's tanh: quiet signal passes through mostly " +
                    "unchanged, then a cubic curve rounds the knee as the signal approaches the ceiling, " +
                    "then anything beyond that is clipped flat. The flatter untouched middle plus a " +
                    "rounder knee reads as a touch warmer and less immediately compressed-sounding than " +
                    "Soft Clip at low-to-moderate drive, converging to a similarly hard ceiling at high " +
                    "drive.",
                    List.of(
                            port("in", "the audio signal to distort"),
                            port("drive", "how hard the signal is pushed into the curve"),
                            port("mix", "0 is fully dry (clean), 1 is fully distorted")),
                    AUDIO_SHAPED + " A warmly overdriven version of the original.",
                    """
                    Sine --> Distortion Overdrive(drive 3, mix .7) --> Audio Out.in
                    Sawtooth --> Distortion Overdrive(drive 1.5, mix .6) --> Audio Out.in"""),
            new Article("Effects", "Distortion Rectify",
                    "Folds the signal's negative half onto its positive half - an octave-up fuzz effect.",
                    "Takes the absolute value of the (driven) signal, so the negative half of every cycle " +
                    "is flipped on top of the positive half - the waveform's fundamental period is " +
                    "effectively halved, which is heard as a buzzy, octave-up fuzz rather than a " +
                    "conventional clipped-overdrive tone. The most drastic-sounding of the five " +
                    "Distortion types, since it changes the pitch content rather than just adding " +
                    "harmonics on top of the original.",
                    List.of(
                            port("in", "the audio signal to rectify"),
                            port("drive", "how much the signal is boosted before rectifying"),
                            port("mix", "0 is fully dry (clean), 1 is fully rectified")),
                    AUDIO_SHAPED + " An octave-up, buzzy fuzz version of the original.",
                    """
                    Sine --> Distortion Rectify(drive 2, mix 1) --> Audio Out.in
                    Sawtooth --> Distortion Rectify(drive 4, mix .5) --> Audio Out.in"""),
            new Article("Effects", "Distortion Asymmetric",
                    "Clips the positive and negative halves of the signal differently - a Tube "
                            + "Screamer-style uneven overdrive.",
                    "Same drive/mix shape as Distortion Soft Clip, but the negative half of the signal "
                    + "reaches tanh's curve sooner than the positive half does, so the two halves of "
                    + "every cycle clip at different points. Every other Distortion type is symmetric "
                    + "around zero and adds only odd-numbered harmonics; this one adds even-numbered "
                    + "harmonics too, which is what gives an uneven-diode-pair overdrive circuit its "
                    + "particular warmth and complexity compared to a symmetric fuzz.",
                    List.of(
                            port("in", "the audio signal to distort"),
                            port("drive", "how hard the signal is pushed into the clipping curve"),
                            port("mix", "0 is fully dry (clean), 1 is fully distorted")),
                    AUDIO_SHAPED + " An asymmetrically overdriven version of the original.",
                    """
                    Sine --> Distortion Asymmetric(drive 4, mix 1) --> Audio Out.in
                    Sawtooth --> Distortion Asymmetric(drive 1.5, mix .4) --> Audio Out.in"""),
            new Article("Effects", "Bitcrusher",
                    "A lo-fi effect that reduces bit depth and sample rate to sound intentionally cheap.",
                    "Quantizes the signal to a reduced number of bits and holds samples to simulate a " +
                    "lower sample rate, producing the crunchy, aliased, \"lo-fi\"/8-bit sound. Lower " +
                    "bits and rate values push the effect further; both are patchable if you want the " +
                    "grittiness itself to be modulated.",
                    List.of(
                            port("bits", "how many bits of precision are kept - lower values sound "
                                    + "grittier and more \"stepped\""),
                            port("rate", "the simulated sample rate in Hz - lower values sound more "
                                    + "crunchy/aliased, like old 8-bit hardware"),
                            port("in", "the audio signal to crush")),
                    AUDIO_SHAPED + " A crunchy, lo-fi, \"8-bit\" version of the original.",
                    """
                    Sine --> Bitcrusher(bits 4, rate 8) --> Audio Out.in
                    Sawtooth --> Bitcrusher(bits 2, rate 16000) --> Audio Out.in"""),
            new Article("Effects", "Ring Modulator",
                    "Multiplies the signal by an internal sine carrier for metallic, robotic tones.",
                    "Multiplies the input by an internal sine wave running at frequency Hz, producing " +
                    "inharmonic, metallic, bell-like or robotic-voice tones depending on the carrier " +
                    "frequency chosen. See Amplitude Modulator for the same idea with both signals " +
                    "exposed as patchable ports instead of one being a fixed internal oscillator.",
                    List.of(
                            port("in", "the audio signal to modulate"),
                            port("frequency", "the internal carrier sine's frequency, in Hz - changes "
                                    + "how metallic/robotic the result sounds"),
                            port("mix", "0 is fully dry, 1 is fully ring-modulated")),
                    AUDIO_SHAPED + " A metallic, bell-like or robotic-sounding version of the original.",
                    """
                    Sine --> Ring Modulator(frequency 137) --> Audio Out.in
                    Sawtooth --> Ring Modulator(frequency 47) --> Audio Out.in"""),
            new Article("Effects", "Frequency Shifter",
                    "Shifts every partial by a constant number of Hz, breaking harmonic ratios for "
                            + "metallic, alien tones.",
                    """
                    Unlike a pitch shifter (which multiplies every partial by the same ratio) or Ring \
                    Modulator (which multiplies by a fixed carrier and produces two symmetric \
                    sidebands), Frequency Shifter adds a constant number of Hz to every partial in the \
                    input. A harmonic sound - partials at 100/200/300/400Hz - shifted by +50Hz comes \
                    out at 150/250/350/450Hz, no longer in a whole-number ratio to each other, which is \
                    exactly what produces the technique's characteristic inharmonic, metallic, \
                    bell-like or "alien" character.

                    It's single-sideband: only one shifted copy of the spectrum survives, so a pure \
                    sine in comes out as a single pure sine at input+shift (or input-shift) Hz, not a \
                    symmetric pair the way Ring Modulator would produce. Built from a Hilbert transform \
                    (a pair of allpass filter chains that split the input into an in-phase and a \
                    90-degree-shifted quadrature signal), combined with an internal shift-frequency \
                    oscillator using the classic single-sideband modulation formula.""",
                    List.of(
                            port("in", "the audio signal to shift"),
                            port("shiftHz", "how many Hz to shift every partial by - positive raises "
                                    + "pitch content, negative lowers it; this is an additive Hz "
                                    + "amount, not a ratio")),
                    AUDIO_SHAPED + " every partial moved up or down by shiftHz, with harmonic "
                            + "relationships broken.",
                    """
                    Sawtooth(110) --> Frequency Shifter(shiftHz 137) --> Audio Out.in
                    Sine --> Frequency Shifter(shiftHz 50) --> Audio Out.in"""),
            new Article("Effects", "Compressor Peak",
                    "Automatically turns down loud peaks to even out the overall volume.",
                    "A dynamic range compressor: once the signal rises above threshold (dB), gain is " +
                    "reduced according to ratio, with an attack/release envelope smoothing how quickly " +
                    "that reduction kicks in and lets go, plus optional makeup gain to bring the overall " +
                    "level back up afterward. Useful for evening out a performance's dynamics or gluing " +
                    "a mix together. Reacts to the input's instantaneous peak level, so it responds fast " +
                    "and precisely to sharp transients - see Compressor RMS for a smoother-reacting " +
                    "alternative, or Compressor Feedback for a different topology entirely. Turning " +
                    "externalKey on makes it detect from sidechain instead of in, so one signal (e.g. a " +
                    "kick drum) can duck another (e.g. a bassline) without the two being mixed together.",
                    List.of(
                            port("in", "the audio signal to compress"),
                            port("sidechain", "an external key signal to detect from instead of in, "
                                    + "when externalKey is on - unused otherwise"),
                            port("externalKey", "0 detects from in as usual; above 0 detects from "
                                    + "sidechain instead, so a different signal (e.g. a kick drum) can "
                                    + "trigger gain reduction on this one (ducking)"),
                            port("threshold", "the level (in dB) above which gain reduction kicks in"),
                            port("ratio", "how strongly the signal is turned down above threshold - "
                                    + "4 means 4dB over becomes 1dB over"),
                            port("attack", "how many milliseconds it takes for the gain reduction to "
                                    + "kick in once the signal crosses threshold"),
                            port("release", "how many milliseconds it takes for the gain reduction to "
                                    + "let go once the signal drops back below threshold"),
                            port("makeup", "extra gain (in dB) added back afterward, to compensate for "
                                    + "the overall level the compression removed")),
                    AUDIO_SHAPED + " The same signal with its loud peaks evened out and, if makeup is "
                            + "used, brought back up to a comparable overall level.",
                    """
                    Amplifier.out --> Compressor Peak(threshold -18, ratio 4, makeup 6) --> Audio Out.in
                    Sawtooth --> Compressor Peak(threshold -24, ratio 8, attack 50, release 500).in --> Biquad Lowpass.cutoff (catch transients with fast attack)
                    Bassline.out --> Compressor Peak(externalKey 1, threshold -20, ratio 6, attack 5, release 150).in --> Audio Out.in
                    Kick.out --> Compressor Peak.sidechain (bass ducks under the kick)"""),
            new Article("Effects", "Compressor RMS",
                    "The same compressor, but reacting to average loudness instead of instantaneous peaks.",
                    "Identical threshold/ratio/attack/release/makeup controls to Compressor Peak, but the " +
                    "level it reacts to is a smoothed average of the signal's energy rather than its raw " +
                    "instantaneous peak - closer to how loud a sound actually feels to the ear. Reacts " +
                    "less to brief transients and more to sustained loudness, which reads as gentler and " +
                    "more \"musical\" than Compressor Peak, at the cost of letting sharp, brief peaks " +
                    "through a little more before gain reduction catches up.",
                    List.of(
                            port("in", "the audio signal to compress"),
                            port("sidechain", "an external key signal to detect from instead of in, "
                                    + "when externalKey is on - unused otherwise"),
                            port("externalKey", "0 detects from in as usual; above 0 detects from "
                                    + "sidechain instead, so a different signal can trigger gain "
                                    + "reduction on this one (ducking)"),
                            port("threshold", "the level (in dB) above which gain reduction kicks in"),
                            port("ratio", "how strongly the signal is turned down above threshold - "
                                    + "4 means 4dB over becomes 1dB over"),
                            port("attack", "how many milliseconds it takes for the gain reduction to "
                                    + "kick in once the sensed level crosses threshold"),
                            port("release", "how many milliseconds it takes for the gain reduction to "
                                    + "let go once the sensed level drops back below threshold"),
                            port("makeup", "extra gain (in dB) added back afterward, to compensate for "
                                    + "the overall level the compression removed")),
                    AUDIO_SHAPED + " The same signal with its overall loudness evened out more gently "
                            + "than Compressor Peak.",
                    """
                    Amplifier.out --> Compressor RMS(threshold -18, ratio 4, makeup 6) --> Audio Out.in
                    Pink Noise --> Compressor RMS(threshold -20, ratio 2, attack 10, release 100) --> Audio Out.in (gentle leveling for ambient texture)"""),
            new Article("Effects", "Compressor Feedback",
                    "A vintage-style compressor that senses gain reduction from its own output.",
                    "Same threshold/ratio/attack/release/makeup controls again, but where Compressor " +
                    "Peak and Compressor RMS both look at the incoming signal to decide how much gain " +
                    "reduction to apply, Feedback looks at what it just *output* instead - the classic " +
                    "topology behind vintage optical/feedback-style hardware compressors. Because the " +
                    "detector sees the already-compressed signal, gain reduction self-moderates and tends " +
                    "to sound smoother and more forgiving at higher ratios than the feedforward Peak/RMS " +
                    "types.",
                    List.of(
                            port("in", "the audio signal to compress"),
                            port("sidechain", "an external key signal to detect from instead of the "
                                    + "node's own output, when externalKey is on - unused otherwise"),
                            port("externalKey", "0 detects from this node's own output as usual; above "
                                    + "0 detects from sidechain instead, so a different signal can "
                                    + "trigger gain reduction on this one (ducking)"),
                            port("threshold", "the level (in dB) above which gain reduction kicks in"),
                            port("ratio", "how strongly the signal is turned down above threshold - "
                                    + "4 means 4dB over becomes 1dB over"),
                            port("attack", "how many milliseconds it takes for the gain reduction to "
                                    + "kick in once the output level crosses threshold"),
                            port("release", "how many milliseconds it takes for the gain reduction to "
                                    + "let go once the output level drops back below threshold"),
                            port("makeup", "extra gain (in dB) added back afterward, to compensate for "
                                    + "the overall level the compression removed")),
                    AUDIO_SHAPED + " The same signal with its loud peaks evened out in a smoother, more "
                            + "self-moderating way than the feedforward types.",
                    """
                    Amplifier.out --> Compressor Feedback(threshold -18, ratio 4, makeup 6) --> Audio Out.in
                    Sawtooth --> Compressor Feedback(threshold -10, ratio 10, makeup 12) --> Audio Out.in (high ratio remains smooth due to feedback topology)"""),
            new Article("Effects", "Reverb",
                    "Simulates the natural echo and ambience of a physical room or space.",
                    "A classic Schroeder/Freeverb-style reverb built from several parallel damped " +
                    "delays feeding into a pair of diffusing filters. roomSize controls how large and " +
                    "long-tailed the space feels, damping rolls off high frequencies in the reflections " +
                    "for a warmer decay, and mix blends the wet reverb signal with the dry input.",
                    List.of(
                            port("in", "the audio signal to place in a space"),
                            port("roomSize", "how large and long-tailed the simulated space feels - "
                                    + "higher is a bigger, longer-decaying room"),
                            port("damping", "how much high-frequency content is absorbed in the "
                                    + "reflections - higher sounds warmer and less bright"),
                            port("mix", "0 is fully dry, 1 is only the reverb tail")),
                    AUDIO_SHAPED + " The original signal blended with a simulated room's echo and decay.",
                    """
                    Amplifier.out --> Reverb(roomSize .7, damping .4, mix .35) --> Audio Out.in
                    Trigger --> Envelope(attack .01, decay .8).out --> Amplifier.gain --> Sine.phase (via Phasor) --> Reverb(roomSize .95, damping .2, mix .8) --> Audio Out.in (lush, long-tail cathedral reverb)"""),
            new Article("Effects", "Spring Reverb",
                    "A second, differently-flavored reverb - the boingy, chirpy character of a "
                            + "guitar-amp spring tank rather than Reverb's smooth room ambience.",
                    """
                    Runs the signal through a chain of dispersive allpass stages whose delay times wobble \
                    slowly against each other, then feeds the result back through a damping filter and \
                    into the chain again - the wobble is what gives a spring tank its distinctive \
                    "boing", and the dispersive chain (each stage delayed by a different, deliberately \
                    unrelated amount) is what makes it sound metallic and springy rather than like a \
                    smooth room.

                    This is a deliberately simplified approximation of spring-tank behavior, not a \
                    modeled circuit - it borrows the wobbled-allpass-chain-plus-damped-feedback shape \
                    that gives spring reverbs their character rather than simulating the physical \
                    spring's transfer function.""",
                    List.of(
                            port("in", "the audio signal to place in a spring tank"),
                            port("decay", "how much of the tank's sound feeds back into itself - higher "
                                    + "means a longer-ringing tail"),
                            port("tone", "brightness of the tail - higher lets more high end survive "
                                    + "each pass through the tank"),
                            port("boing", "how much the allpass chain's delay times wobble - higher is a "
                                    + "more pronounced, chirpier springiness"),
                            port("mix", "0 is fully dry, 1 is only the spring tail")),
                    AUDIO_SHAPED + " The original signal blended with a boingy, spring-tank-style decay.",
                    """
                    Amplifier.out --> Spring Reverb(decay .7, tone .6, boing .5, mix .3) --> Audio Out.in
                    White Noise --> Spring Reverb(decay .85, tone .2, boing .9, mix .6) --> Audio Out.in (high boing for extreme spring tank character)"""),
            new Article("Effects", "Phaser",
                    "A sweeping, swooshing effect made of moving phase-cancellation notches.",
                    "Passes the signal through a cascade of allpass filters whose frequency is swept by " +
                    "an internal LFO, then mixes the result with the dry signal - the shifting phase " +
                    "relationship creates the sweeping notches that give a phaser its characteristic " +
                    "swoosh. feedback deepens the notches for a more pronounced effect.",
                    List.of(
                            port("in", "the audio signal to sweep"),
                            port("rate", "how fast (in Hz) the sweep cycles"),
                            port("depth", "how wide the sweep ranges"),
                            port("feedback", "how much of the swept signal feeds back in, deepening "
                                    + "the notches"),
                            port("mix", "0 is fully dry, 1 is fully the swept copy")),
                    AUDIO_SHAPED + " A sweeping, \"swooshing\" version of the original.",
                    """
                    Sawtooth --> Phaser(rate .3, depth .7, feedback .4, mix .5) --> Audio Out.in
                    Square --> Phaser(rate 2, depth .9, feedback .8, mix .3) --> Audio Out.in (fast, intense notch movement)"""),
            new Article("Effects", "Limiter",
                    "A hard ceiling that keeps peaks from ever exceeding a set level.",
                    "A brick-wall peak limiter: gain is pulled down instantly whenever the signal would " +
                    "exceed ceiling, and eased back up over release seconds once it drops back below. " +
                    "Useful as a final safety net before Audio Out to guarantee nothing clips, " +
                    "especially after a node like Envelope whose punch can intentionally overshoot 1.",
                    List.of(
                            port("in", "the audio signal to protect"),
                            port("ceiling", "the maximum level the output is ever allowed to reach"),
                            port("release", "how many milliseconds it takes to ease gain back up once "
                                    + "the signal drops back below ceiling")),
                    AUDIO_SHAPED + " Identical to in except any peak that would exceed ceiling is "
                            + "pulled down to it - a hard, guaranteed maximum level.",
                    """
                    Amplifier.out --> Limiter(ceiling .9, release .1) --> Audio Out.in
                    White Noise --> Limiter(ceiling .3, release 50) --> Audio Out.in (extreme limiting for ducking effect)"""),
            new Article("Effects", "Noise Gate",
                    "Silences a signal automatically whenever it falls below a volume threshold.",
                    "Opens up and passes the input through once its own level rises above threshold, " +
                    "and closes to silence once it falls back below, with attack/release smoothing the " +
                    "transition. Useful for cleaning up hiss or bleed during quiet passages. Distinct " +
                    "from Gate in Conditionals, which gates based on a separate control signal rather " +
                    "than the input's own loudness.",
                    List.of(
                            port("in", "the audio signal to gate"),
                            port("threshold", "the level (in dB) in must rise above to open the gate"),
                            port("attack", "how many milliseconds it takes to open once above threshold"),
                            port("release", "how many milliseconds it takes to close once below "
                                    + "threshold")),
                    AUDIO_SHAPED + " Identical to in while loud enough; silence while it's too quiet.",
                    """
                    White Noise --> Noise Gate(threshold -30, attack .002, release .1) --> Audio Out.in
                    Sawtooth --> Noise Gate(threshold -40, attack 20, release 200) --> Audio Out.in (smooth gate fade for legato effect)"""),
            new Article("Effects", "Vibrato",
                    "Smooth, wobbling pitch modulation with no dry signal mixed in.",
                    "A fully-wet LFO-modulated delay line - unlike Chorus or Flanger there is no dry " +
                    "signal blended back in, so the constantly-shifting delay reads as pure pitch " +
                    "vibrato rather than a comb-filtered doubling effect. rate and depth control the " +
                    "wobble's speed and width.",
                    List.of(
                            port("in", "the audio signal to add vibrato to"),
                            port("rate", "how fast (in Hz) the pitch wobbles"),
                            port("depth", "how far (in milliseconds) the delay - and so the pitch - "
                                    + "wobbles")),
                    AUDIO_SHAPED + " The same tone as in, with a smooth wobble in pitch - there is no "
                            + "dry/wet knob, this output is always fully affected.",
                    """
                    Sine --> Vibrato(rate 5, depth .3) --> Audio Out.in
                    Square --> Vibrato(rate 15, depth 2) --> Audio Out.in (extreme deep vibrato for chorus-like shimmer)"""),
            new Article("Effects", "Waveshaper",
                    "Define your own distortion curve with a typed-in math formula.",
                    """
                    Waveshaper passes each incoming sample through a formula you type directly on the \
                    node, with x bound to the sample's current value (not phase, unlike an oscillator's \
                    formula). The default formula is x (identity - passes through unchanged until \
                    edited). Try tanh(3*x) for a soft distortion, clamp(x,-0.3,0.3) for a hard clip, or \
                    x-x^3/3 for a subtle odd-harmonic softening - any memoryless nonlinear curve you can \
                    write down, rather than picking from a fixed menu of effects.""",
                    List.of(port("in", "the audio signal to reshape - inside the formula box its "
                            + "current sample value is available as x")),
                    AUDIO_SHAPED + " Whatever your formula computes for each incoming sample.",
                    """
                    Sine --> Waveshaper(\"tanh(3*x)\") --> Audio Out.in
                    Triangle --> Waveshaper(\"clamp(x,-0.5,0.5)\") --> Audio Out.in (hard clipping produces squarish texture)"""),
            new Article("Effects", "Level Follower",
                    "Turns a signal's loudness over time into its own control signal.",
                    """
                    Level Follower tracks how loud its in signal is right now, smoothed by attack and \
                    release times, and outputs that loudness directly as its own signal - rather than \
                    using that tracked level internally the way a Compressor or Noise Gate does, this node \
                    exposes it so you can patch it anywhere. Wire a drum loop's Level Follower output \
                    into a filter's cutoff for an auto-wah, or into another sound's amplitude for a \
                    sidechain-style ducking effect.""",
                    List.of(
                            port("in", "the audio signal whose loudness is tracked"),
                            port("attack", "how many milliseconds it takes to follow a sudden increase "
                                    + "in loudness"),
                            port("release", "how many milliseconds it takes to follow a sudden decrease "
                                    + "in loudness")),
                    "Not audio - a slowly-changing control value that tracks how loud in is right now. "
                            + "Patch it into another node's parameter (a cutoff, a gain, a depth), not "
                            + "directly into Audio Out.",
                    """
                    Drum loop --> Level Follower(attack 5, release 80).in
                    Level Follower.out --> Map Range(outMin 300, outMax 3000) --> Biquad Lowpass.cutoff
                    Trigger --> Sawtooth.phase (via Phasor) --> Level Follower(attack 1, release 50).in --> Map Range(outMin 220, outMax 880) --> Phasor.frequency (sidechain pitch control)"""),
            new Article("Effects", "Auto Wah",
                    "A filter that sweeps itself, driven by how loud the input is - the funk-wah/"
                            + "envelope-filter pedal in one box.",
                    """
                    Auto Wah tracks in's loudness the same way Level Follower does (attack/release-\
                    smoothed), then uses that tracked level to sweep a resonant bandpass filter's cutoff \
                    between minFreq and maxFreq - louder input sweeps the filter higher, quieter input \
                    lets it fall back down. sensitivity scales how far the envelope pushes the sweep, and \
                    q controls how narrow and resonant (how "quacky") the filter sounds at each point in \
                    the sweep.

                    This is exactly what Level Follower's own worked example builds by hand (its "out" \
                    patched into a filter's "cutoff" through a Map Range) - Auto Wah is that same idea as \
                    a single dedicated node, for when you just want the classic pedal effect without \
                    wiring the pieces yourself.""",
                    List.of(
                            port("in", "the audio signal to filter"),
                            port("sensitivity", "how strongly the tracked loudness pushes the sweep - "
                                    + "higher reacts more dramatically to volume changes"),
                            port("attack", "how many milliseconds it takes to follow a sudden increase "
                                    + "in loudness"),
                            port("release", "how many milliseconds it takes to follow a sudden decrease "
                                    + "in loudness"),
                            port("minFreq", "the cutoff frequency (Hz) at silence"),
                            port("maxFreq", "the cutoff frequency (Hz) the sweep reaches at full "
                                    + "sensitivity-scaled loudness"),
                            port("q", "how narrow and resonant the sweeping filter sounds"),
                            port("mix", "0 is fully dry, 1 is fully the swept filter")),
                    AUDIO_SHAPED + " in, run through a filter that sweeps with its own loudness.",
                    """
                    Sequencer.out --> Phasor.frequency --> Sawtooth --> Auto Wah(sensitivity 2, q 6).in --> Audio Out.in
                    White Noise --> Auto Wah(sensitivity 1, minFreq 50, maxFreq 6000, q 12, mix 1) --> Audio Out.in (extreme sweep, fully wet)"""),
            new Article("Effects", "Amplitude Modulator",
                    "Multiplies two signals together - like Ring Modulator, but with both inputs exposed.",
                    """
                    Amplitude Modulator multiplies carrier by (1 + depth * modulator), where both \
                    carrier and modulator are ordinary patchable inputs rather than one of them being a \
                    fixed internal oscillator. At depth 0 the carrier passes through unchanged; at depth \
                    1 with a -1..1 modulator you get classic 100%-modulated AM, where the carrier \
                    briefly reaches zero at the modulator's troughs. Wire in an LFO Sine for the same \
                    effect Ring Modulator gives you built-in, or patch in a second voice, an envelope, \
                    or noise for sounds a fixed-carrier ring modulator can't reach.""",
                    List.of(
                            port("carrier", "the audio signal being modulated - what you'd hear "
                                    + "unmodified if depth were 0"),
                            port("modulator", "the signal doing the modulating - an LFO, another "
                                    + "oscillator, noise, or an envelope"),
                            port("depth", "how strongly modulator affects carrier - 0 is no effect, 1 "
                                    + "is full 100% modulation")),
                    AUDIO_SHAPED + " carrier with its volume shaped by modulator.",
                    """
                    Sine(440) --> Amplitude Modulator.carrier
                    LFO Sine(frequency 6) --> Amplitude Modulator.modulator (depth 1)
                    Sawtooth --> Amplitude Modulator.carrier (depth 0.8)
                    White Noise --> Amplitude Modulator.modulator (tremolo effect with noise modulation)"""),
            new Article("Effects", "Frequency Modulator",
                    "Wobbles a frequency value by an external signal - like the FM oscillator, but with "
                            + "both carrier and modulator exposed.",
                    """
                    Frequency Modulator outputs carrier + depth * modulator - a plain Hz value, not \
                    audio. carrier is the base pitch, modulator is any audio-rate signal (an oscillator, \
                    noise, an envelope), and depth sets how many Hz the modulator swings the pitch away \
                    from carrier. Wire the output into a Phasor's frequency input, and that Phasor into \
                    an oscillator, to actually hear the modulated pitch.

                    This is the same idea as the FM oscillator, generalized the same way Amplitude \
                    Modulator generalizes Ring Modulator: FM's carrier and modulator are both fixed \
                    internal sines, only reachable through mod ratio/mod index. Here, both carrier and \
                    modulator are ordinary patchable ports, so the modulator can be any signal at all - \
                    a second voice, noise, an envelope, or an LFO for plain vibrato - and the carrier \
                    itself can be modulated by something else first (an Arpeggio or Ramp output), which \
                    the FM oscillator's fixed internal carrier can't do.""",
                    List.of(
                            port("carrier", "the base frequency in Hz, before modulation"),
                            port("modulator", "the signal doing the modulating - typically an "
                                    + "oscillator's -1..1 output, but any signal works"),
                            port("depth", "how many Hz the modulator swings the frequency away from "
                                    + "carrier - 0 is no effect, larger values sweep further and sound "
                                    + "more extreme")),
                    "A frequency value in Hz, not audio - wire it into a Phasor's frequency input (which "
                            + "then feeds an oscillator) to actually hear the modulated pitch.",
                    """
                    Constant(440) --> Frequency Modulator.carrier
                    Sine(frequency 6) --> Frequency Modulator.modulator (depth 50, for vibrato)
                    Frequency Modulator.out --> Phasor.frequency --> Sine.phase --> Audio Out.in
                    Constant(220) --> Frequency Modulator.carrier (depth 200)
                    White Noise --> Frequency Modulator.modulator --> Phasor.frequency --> Square.phase --> Audio Out.in (chaotic noise FM)"""),
            new Article("Effects", "Tone Stack",
                    "Bass/Mid/Treble tone shaping - the same three-knob EQ every guitar amp and "
                            + "drive pedal has.",
                    """
                    Three fixed-frequency shelf/peak filters in series - a low shelf around 100 Hz for \
                    bass, a wide peak around 650 Hz for mid, and a high shelf around 2.5 kHz for treble - \
                    each controlled by its own gain knob in dB. Turning mid down while bass and treble \
                    stay up is the classic "scooped mids" high-gain tone; turning it up instead gives a \
                    more nasal, cutting character.

                    This models the frequencies and shape of a guitar amp's tone stack, not the literal \
                    passive circuit - a real Fender/Marshall tone stack has some interaction between the \
                    three knobs that this does not reproduce, since each band here is an independent \
                    filter. For full independent control over frequency and bandwidth as well as gain, \
                    see Parametric EQ; for a fixed set of narrower bands across the whole spectrum, see \
                    Graphic EQ.""",
                    List.of(
                            port("in", "the audio signal to shape"),
                            port("bass", "boost or cut (dB) around 100 Hz"),
                            port("mid", "boost or cut (dB) around 650 Hz, wide enough to read as \"mid "
                                    + "presence\" rather than one narrow frequency"),
                            port("treble", "boost or cut (dB) around 2.5 kHz")),
                    AUDIO_SHAPED + " in, with its bass/mid/treble balance reshaped.",
                    """
                    Distortion Overdrive.out --> Tone Stack(bass 2, mid -4, treble 3) --> Audio Out.in
                    Sawtooth --> Tone Stack(bass -6, mid 6, treble -6) --> Audio Out.in (boosted mids, scooped bass/treble)"""),
            new Article("Effects", "Parametric EQ",
                    "Several fully independent EQ bands, each with its own frequency, gain, and "
                            + "bandwidth.",
                    """
                    Parametric EQ has one freq/gain/q trio of ports per band (freq0/gain0/q0, freq1/\
                    gain1/q1, ...) - three bands by default when placed from the menu. Each band is an \
                    independent peaking filter: freq sets where it's centered, gain sets how much it \
                    boosts or cuts (dB), and q sets how narrow or wide that boost/cut is. Unlike Tone \
                    Stack's fixed guitar-amp-shaped frequencies, every band here can be placed and shaped \
                    anywhere in the spectrum - the general-purpose mixing/mastering tool rather than a \
                    pedal-style fixed voicing.""",
                    List.of(
                            port("in", "the audio signal to shape"),
                            port("freq0, freq1, ... (one per band)", "the center frequency (Hz) that "
                                    + "band boosts or cuts around"),
                            port("gain0, gain1, ... (one per band)", "how much that band boosts (positive) "
                                    + "or cuts (negative), in dB"),
                            port("q0, q1, ... (one per band)", "how narrow (high q) or wide (low q) that "
                                    + "band's boost/cut is")),
                    AUDIO_SHAPED + " in, reshaped by every band in series.",
                    """
                    Reverb.out --> Parametric EQ(freq0 120, gain0 -3, freq1 3000, gain1 4).in --> Audio Out.in
                    Sawtooth --> Parametric EQ(freq0 80, gain0 3, q0 2, freq1 1000, gain1 -6, q1 8, freq2 5000, gain2 5, q2 2).in --> Audio Out.in (multi-band shaping)"""),
            new Article("Effects", "Graphic EQ",
                    "Seven fixed-frequency sliders spanning the spectrum, gain only - the classic "
                            + "pedalboard graphic EQ.",
                    "Seven peaking filters at fixed, octave-spaced frequencies (100, 200, 400, 800, 1600, "
                    + "3200, 6400 Hz), each with its own gain-only slider - no frequency or bandwidth to "
                    + "set, which is what makes a graphic EQ quicker to reach for than Parametric EQ when "
                    + "a fixed, familiar layout of bands is all a patch needs. Sweep several bands' gains "
                    + "together (a rising Map Range feeding several gainN ports) for a tilt-EQ effect, or "
                    + "shape a single band to carve out one problem frequency.",
                    List.of(
                            port("in", "the audio signal to shape"),
                            port("gain0 (100 Hz) .. gain6 (6400 Hz)", "boost (positive) or cut (negative) "
                                    + "at that band's fixed frequency, in dB")),
                    AUDIO_SHAPED + " in, reshaped by all seven fixed bands in series.",
                    """
                    Reverb.out --> Graphic EQ(gain0 -4, gain6 3) --> Audio Out.in
                    Sawtooth --> Graphic EQ(gain0 3, gain1 2, gain2 1, gain3 0, gain4 1, gain5 2, gain6 3) --> Audio Out.in (presence boost across all frequencies)"""),
            new Article("Granular", "Granular Freeze",
                    "Freezes a moment of live audio and grains it into an evolving, shimmering texture.",
                    """
                    Granular Freeze continuously records its in signal, and while freeze is held above \
                    0 it stops recording so grains keep reading from whatever moment was just captured, \
                    over and over, rather than the live signal - a shimmering, frozen-in-time texture. \
                    position controls how far back (in seconds) into the recorded buffer grains read \
                    from, and grainSize/density/pitch (plus their jitter counterparts) shape the \
                    grain texture the same way they do on Granular Player.

                    Release freeze and it goes back to granulating the live input in real time.

                    Turn density up and grainSizeJitter/pitchJitter up for a smooth, ambient wash; \
                    turn grainSize and density both down for glitchy, percussive granulation where \
                    individual grains are audible as a stutter rather than blending into a texture.""",
                    List.of(
                            port("in", "the live audio signal being continuously recorded"),
                            port("freeze", "held above 0 to stop recording and freeze the texture in "
                                    + "place; back to 0 to resume live recording"),
                            port("position", "how many seconds behind the live write head grains read "
                                    + "from"),
                            port("positionJitter", "0 to 1 - how much each grain's read position is "
                                    + "randomly varied"),
                            port("grainSize", "how many seconds long each individual grain is"),
                            port("grainSizeJitter", "0 to 1 - how much each grain's size is randomly "
                                    + "varied"),
                            port("density", "how many grains spawn per second"),
                            port("pitch", "a playback-speed multiplier for each grain - 1 is unchanged, "
                                    + "2 is an octave up, 0.5 is an octave down"),
                            port("pitchJitter", "0 to 1 - how much each grain's pitch is randomly "
                                    + "varied"),
                            port("amp", "the loudness of each individual grain"),
                            port("trigger", "spawns one extra grain immediately on a rising pulse, on "
                                    + "top of the steady density rate")),
                    AUDIO_WAVE + " A cloud of many small overlapping snippets of the recorded (or "
                            + "frozen) buffer, mixed together into a granular texture.",
                    """
                    Sawtooth --> Granular Freeze(position .3, grainSize .1, density 25).in
                    Constant(1) --> Granular Freeze.freeze (hold at 1 to freeze the texture)
                    Sawtooth --> Granular Freeze(position .3, grainSize .1, density 25).in
                    Trance Gate --> Granular Freeze.freeze (freeze rhythmically on downbeats instead of holding)"""),
            new Article("Granular", "Granular Player",
                    "Imports a WAV file and plays it back as a cloud of small overlapping grains.",
                    """
                    Granular Player opens a file picker, imports a WAV file, and grains it: many short, \
                    overlapping, windowed snippets are read from the buffer and mixed together, each \
                    with its own randomizable position, size, and pitch. position picks where in the \
                    file grains start reading from, density controls how many grains spawn per second, \
                    and the jitter ports (positionJitter, grainSizeJitter, pitchJitter) add random \
                    variation to each new grain rather than reading the exact same snippet every time.

                    Unlike Sample, which loops the whole file once per phase cycle, this reads many \
                    small pieces at once - good for smearing a recording into a pad, stretching it in \
                    time, or breaking it into a granular texture without changing its overall pitch.

                    Turn density up and grainSizeJitter/pitchJitter up for a smooth, ambient wash; \
                    turn grainSize and density both down for glitchy, percussive granulation where \
                    individual grains are audible as a stutter rather than blending into a texture.""",
                    List.of(
                            port("position", "how many seconds into the imported file grains start "
                                    + "reading from"),
                            port("positionJitter", "0 to 1 - how much each grain's read position is "
                                    + "randomly varied"),
                            port("grainSize", "how many seconds long each individual grain is"),
                            port("grainSizeJitter", "0 to 1 - how much each grain's size is randomly "
                                    + "varied"),
                            port("density", "how many grains spawn per second"),
                            port("pitch", "a playback-speed multiplier for each grain - 1 is unchanged"),
                            port("pitchJitter", "0 to 1 - how much each grain's pitch is randomly "
                                    + "varied"),
                            port("amp", "the loudness of each individual grain"),
                            port("trigger", "spawns one extra grain immediately on a rising pulse")),
                    AUDIO_WAVE + " A cloud of many small overlapping snippets of the imported file, "
                            + "mixed together into a granular texture.",
                    """
                    Granular Player(position 0.2, density 15, grainSize .1, pitchJitter .1) --> Audio Out.in
                    Trigger.out --> Ramp(to 4.0, time 4.0).trigger (play full 4-second clip in real time)
                    Ramp.out --> Granular Player.position
                    Granular Player(grainSize .01, density 60) --> Audio Out.in
                    Unit Period Phasor(period 6) --> Map Range(outMin 12, outMax 18) --> Granular Player.position (loop seconds 12-18 repeatedly)
                    Granular Player(grainSize .01, density 60) --> Audio Out.in"""),
            new Article("Granular", "Granular Delay",
                    "A granular delay with feedback - grains re-granulate their own past output, "
                            + "capable of rising or falling shimmer.",
                    """
                    Granular Delay continuously records its in signal and grains it, the same way \
                    Granular Freeze does - but always live (no freeze gate) and with the granulated \
                    output mixed back into what gets recorded, controlled by feedback. Set pitch away \
                    from 1 and every feedback pass re-granulates an already pitch-shifted copy of the \
                    previous pass, compounding into a shimmer that rises (pitch above 1) or falls \
                    (pitch below 1) the longer feedback sustains it.

                    Unlike Granular Freeze, this is a proper delay effect with its own dry/wet mix - \
                    the input keeps passing through even with feedback at 0. Because feedback \
                    repeatedly re-processes its own output, denser grain settings (density and \
                    grainSize closer to a 2:1 ratio - see the Granular guide) sustain noticeably \
                    longer than sparse ones, which lose more energy on every pass.""",
                    List.of(
                            port("in", "the live audio signal being continuously recorded"),
                            port("time", "this delay's time, in seconds behind the live write head"),
                            port("feedback", "0 to 0.95 - how much of the granulated output feeds back "
                                    + "into what gets recorded"),
                            port("positionJitter", "0 to 1 - how much each grain's read position is "
                                    + "randomly varied"),
                            port("grainSize", "how many seconds long each individual grain is"),
                            port("grainSizeJitter", "0 to 1 - how much each grain's size is randomly "
                                    + "varied"),
                            port("density", "how many grains spawn per second"),
                            port("pitch", "a playback-speed multiplier for each grain - set away from 1 "
                                    + "for a rising or falling shimmer as feedback compounds it"),
                            port("pitchJitter", "0 to 1 - how much each grain's pitch is randomly "
                                    + "varied"),
                            port("amp", "the loudness of each individual grain"),
                            port("mix", "0 is dry only, 1 is the granulated signal only"),
                            port("trigger", "spawns one extra grain immediately on a rising pulse")),
                    AUDIO_SHAPED + " the dry signal blended with a granulated, optionally "
                            + "feedback-shimmering delay of itself.",
                    """
                    Sawtooth --> Granular Delay(time .2, feedback .8, pitch 1.5, mix .6) --> Audio Out.in
                    LFO Sine(frequency 0.2) --> Map Range(outMin 0.1, outMax 0.5) --> Granular Delay.time (slow wobble through delay time for chorus effect)
                    Sawtooth --> Granular Delay(feedback .6, mix .7) --> Audio Out.in"""),
            new Article("Granular", "Granular Cloud",
                    "Continuous live granulation where every grain gets its own randomized filter "
                            + "cutoff - a cloud of differently-toned fragments, not one uniform texture.",
                    """
                    Granular Cloud grains its live in signal the same way Granular Freeze does (no \
                    freeze gate here - always live), but each grain is also given its own low-pass \
                    filter cutoff at the moment it spawns, randomized by filterCutoffJitter. Where \
                    every other granular node in this package produces grains that all share one tone \
                    color, this produces a cloud where some grains are bright and others are muffled, \
                    all overlapping at once - a texture no filter patched after a granular node's \
                    output can produce, since that shapes the mixed sum, not each grain individually.

                    filterCutoffJitter defaults to 0.5, unlike every other jitter port in this package \
                    which defaults to 0 - a Granular Cloud with no cutoff spread is just an ordinary \
                    filtered granular texture and misses the entire point of the node.""",
                    List.of(
                            port("in", "the live audio signal being continuously recorded"),
                            port("position", "how many seconds behind the live write head grains read "
                                    + "from"),
                            port("positionJitter", "0 to 1 - how much each grain's read position is "
                                    + "randomly varied"),
                            port("grainSize", "how many seconds long each individual grain is"),
                            port("grainSizeJitter", "0 to 1 - how much each grain's size is randomly "
                                    + "varied"),
                            port("density", "how many grains spawn per second"),
                            port("pitch", "a playback-speed multiplier for each grain - 1 is unchanged"),
                            port("pitchJitter", "0 to 1 - how much each grain's pitch is randomly "
                                    + "varied"),
                            port("filterCutoff", "the low-pass cutoff, in Hz, a grain is given at the "
                                    + "moment it spawns"),
                            port("filterCutoffJitter", "0 to 1 - how much each grain's cutoff is "
                                    + "randomly varied around filterCutoff - this is what gives the "
                                    + "cloud its tonal variety"),
                            port("amp", "the loudness of each individual grain"),
                            port("trigger", "spawns one extra grain immediately on a rising pulse")),
                    AUDIO_WAVE + " a cloud of overlapping grains, each independently filtered to its "
                            + "own randomized tone color.",
                    """
                    Sawtooth --> Granular Cloud(filterCutoff 1500, filterCutoffJitter .7) --> Audio Out.in
                    Sawtooth --> Granular Cloud(filterCutoff 2000, filterCutoffJitter .95, density 200, grainSize .02) --> Audio Out.in (dense tonal wash with extreme filter variation)
                    LFO Sine(frequency 0.3) --> Map Range(outMin 500, outMax 4000) --> Granular Cloud.filterCutoff (sweeping cloud effect)
                    Sawtooth --> Granular Cloud(filterCutoffJitter .9, density 80) --> Audio Out.in"""),
            new Article("Instruments", "Instrument",
                    "A multisample instrument, edited OpenMPT-style, in 2/4/8/16-channel sizes: several "
                            + "samples spread across a keyboard range, played back at any frequency - not "
                            + "just the nearest semitone.",
                    """
                    Instrument comes in four sizes - place "Instrument 2", "Instrument 4", "Instrument 8", \
                    or "Instrument 16" from the node menu depending on how many simultaneous notes it \
                    needs to play. Every channel shares the same instrument definition: a list of sample \
                    zones, each imported from its own WAV file with a root pitch and a low/high key range \
                    (typed as note names, the same "A4", "C#3+37c" notation Note and the tracker piano \
                    roll already use). On a rising edge of a channel's gate it picks the zone whose range \
                    contains that channel's current pitch (or the nearest zone by root pitch if none \
                    matches) and plays it back at pitch / that zone's root frequency - a plain ratio, so \
                    nothing anywhere in the signal path snaps to the nearest semitone. Feed it a genuinely \
                    microtonal pitch - a 19-EDO step, a just-intonation ratio, anything an Expression node \
                    can compute - and it comes out exactly that pitch, the same way Note's fractional-cents \
                    field already does.

                    Its "Edit" button opens a dedicated screen: the zone list (import/remove samples, \
                    edit name/root/range/loop), and a click-to-add, drag-to-move, \
                    double-click-to-mark-sustain, right-click-to-delete breakpoint editor for its volume \
                    envelope - the same idea as OpenMPT's instrument envelope, just breakpoints in \
                    seconds instead of ticks. A zone's Loop checkbox sustains the whole sample while that \
                    channel's gate is held, then finishes the tail once it releases. Editing the \
                    instrument this way changes every channel at once, since they all share it - only \
                    each channel's playback position, envelope time, and active zone are independent.

                    Right-click an Instrument node and choose "Convert channels" to swap it for a \
                    different size in place - the shared instrument and every wire connected to a \
                    surviving pitch/gate/out set are carried over untouched; nothing is lost on the \
                    channels being dropped, since there is no per-channel data beyond in-flight playback \
                    state.

                    Wire a Tracker's pitchN/gateN pair into the matching channel of an Instrument the \
                    same size (or larger) for one voice per Tracker channel - one Instrument node now \
                    covers a whole song's worth of channels, rather than one Instrument per channel.""",
                    List.of(
                            port("pitchN, gateN", "one pitch/gate pair per channel - pitch is the "
                                    + "frequency to play that channel's current note at, in Hz, a plain "
                                    + "continuous frequency, not a quantized note number; gate is 0 or 1, "
                                    + "a rising edge picks a zone and starts the envelope, a falling edge "
                                    + "releases it")),
                    "One output per channel (out0, out1, ...), each an audio signal roughly between -1 "
                            + "and 1: that channel's selected zone sample, pitch-shifted by pitch divided "
                            + "by that zone's root frequency and shaped by the volume envelope.",
                    """
                    Tracker 4.pitch0 --> Instrument 4.pitch0
                    Tracker 4.gate0 --> Instrument 4.gate0
                    Tracker 4.pitch1 --> Instrument 4.pitch1
                    Tracker 4.gate1 --> Instrument 4.gate1
                    Instrument 4.out0 --> Mix.in0
                    Instrument 4.out1 --> Mix.in1
                    Mix --> Audio Out.in

                    Note("A4+37c") --> Instrument 2.pitch0 (a microtonal test pitch, no snapping to the nearest semitone)
                    Trigger.out --> Instrument 2.gate0"""),
            new Article("Physical", "Resonator",
                    "An excitable string or bell that rings on its own once struck.",
                    """
                    Resonator is a Mutable Rings-inspired physical-modeling resonator: it doesn't shape \
                    a continuously-playing tone the way a filter does, it rings in response to a short \
                    excitation on in - a noise burst, an impulse, or any transient - the way plucking a \
                    string or striking a bar does. pitch tunes the fundamental (feed it a Tracker \
                    channel's pitchN output for a pitched percussive voice).

                    Two resonance algorithms run in parallel and structure crossfades between them: at \
                    0, pure string mode - a plucked-string delay line with a damping filter in its \
                    feedback loop, brightness controlling how long the upper harmonics survive and decay \
                    controlling overall sustain. At 1, pure modal mode - a bank of resonant bandpass \
                    stages tuned to pitch's harmonic (structure below 0.5) or inharmonic, bell-like \
                    (structure 0.5 and above) partials, decay controlling how narrow (and long-ringing) \
                    each stage is. Sweeping structure across its full range morphs string into \
                    harmonic-modal into inharmonic-modal.""",
                    List.of(
                            port("in", "the excitation signal - a noise burst, an impulse, or any audio "
                                    + "signal that starts the resonator ringing"),
                            port("pitch", "the fundamental frequency in Hz - pairs naturally with a "
                                    + "Tracker channel's pitchN output"),
                            port("structure", "0 to 1 - crossfades string output (0) to modal output "
                                    + "(1), and picks the modal algorithm's harmonic (below 0.5) or "
                                    + "inharmonic, bell-like (0.5 and above) partial ratios"),
                            port("brightness", "0 to 1, string mode only - how long the upper harmonics "
                                    + "survive in the feedback loop before the damping filter smooths "
                                    + "them away"),
                            port("decay", "0 to 1 - overall resonance duration: feedback gain in string "
                                    + "mode, resonance sharpness (Q) in modal mode")),
                    AUDIO_WAVE + " A ringing, pitched resonance that decays after the excitation stops - "
                            + "sustained, not a one-shot click.",
                    """
                    Noise --> Envelope(short attack, short decay) --> Resonator.in
                    Tracker.pitch1 --> Resonator.pitch (structure .2, brightness .7, decay .8)
                    Resonator.out --> Audio Out.in
                    Trigger --> Envelope.trigger
                    Envelope.out --> Amplifier.in --> Resonator.in
                    Constant(220) --> Resonator.pitch (structure .85, decay .9)
                    Resonator.out --> Audio Out.in (bell-like inharmonic resonance)"""),
            new Article("Spectral", "Spectral Freeze",
                    "Freezes a sound's frequency content into a smooth, endlessly sustained pad.",
                    """
                    Spectral Freeze analyzes the input in the frequency domain (via a short-time \
                    Fourier transform) and, while freeze is held above 0, stops updating what it \
                    captured and holds it - reconstructing the frozen spectrum every analysis frame \
                    with each frequency bin's phase nudged by a small random amount, so the sustain \
                    doesn't lock into an audible repeating loop. Releasing freeze resumes tracking the \
                    live input.

                    Unlike Granular Freeze, which freezes in the time domain (looping small windowed \
                    snippets, giving a textured, grainy sustain), this freezes in the frequency domain \
                    - the result is a smooth, chord-like pad with none of granular synthesis's \
                    graininess, closer to what a spectral freeze plugin or a bowed-string EBow does.""",
                    List.of(
                            port("in", "the audio signal to freeze"),
                            port("freeze", "above 0 holds whatever frequency content was present the "
                                    + "moment it crossed above 0; at or below 0, tracks the live input")),
                    AUDIO_SHAPED + " a smooth sustained pad while frozen, or the live input passed "
                            + "through (at the engine's inherent short analysis delay) otherwise.",
                    """
                    Sawtooth --> Spectral Freeze.in
                    Constant(1) --> Spectral Freeze.freeze (hold at 1 to sustain the current chord)
                    Triangle --> Spectral Freeze.in
                    CV Gate.out --> Map Range(outMin 0, outMax 1) --> Spectral Freeze.freeze (freeze toggles on gate edge)"""),
            new Article("Spectral", "Spectral Gate",
                    "A frequency-domain noise gate - quiets whichever frequencies are weak this "
                            + "instant, independently of each other.",
                    """
                    Where an ordinary Noise Gate makes one loud/quiet decision for the whole signal at \
                    once, Spectral Gate makes that decision separately for every frequency bin: bins \
                    whose strength (relative to the loudest bin in that instant) falls below threshold \
                    are quieted, while stronger bins pass through - so a quiet hiss sitting underneath a \
                    loud tone gets suppressed even while the tone itself keeps playing normally, which a \
                    single whole-signal gate can't do.

                    blur optionally smooths each bin's strength across time before the threshold is \
                    applied, turning a sharp gate into a slower, more forgiving one that reacts to a \
                    frequency's average recent strength rather than every instantaneous fluctuation.""",
                    List.of(
                            port("in", "the audio signal to gate"),
                            port("threshold", "0 to 1 - frequencies weaker than this, relative to the "
                                    + "loudest frequency present right now, are quieted"),
                            port("blur", "0 to 1 - smooths each frequency's strength across time before "
                                    + "thresholding; 0 reacts instantly, higher values react more slowly")),
                    AUDIO_SHAPED + " with weak frequencies quieted and strong ones passed through.",
                    """
                    Sawtooth --> Spectral Gate(threshold .2, blur .3) --> Audio Out.in
                    White Noise --> Spectral Gate(threshold .6) --> Audio Out.in (extract spectral peaks from noise)"""),
            new Article("Spectral", "Phase Vocoder Pitch Shift",
                    "True pitch transposition that keeps every partial in the same ratio to each "
                            + "other, unlike Frequency Shifter.",
                    """
                    Phase Vocoder Pitch Shift multiplies every partial's frequency by pitchRatio - a \
                    ratio, not an added amount - so a harmonic sound stays harmonic after shifting \
                    (100/200/300Hz at pitchRatio 2 becomes 200/400/600Hz, still a clean harmonic \
                    series), unlike Frequency Shifter's additive Hz shift which breaks those ratios on \
                    purpose. This is the same operation a "pitch" knob does, just implemented in the \
                    frequency domain: it tracks each frequency bin's true instantaneous frequency from \
                    how its phase evolves between analysis frames, then reconstructs the spectrum with \
                    every bin's frequency (and matching phase trajectory) scaled by pitchRatio.""",
                    List.of(
                            port("in", "the audio signal to pitch-shift"),
                            port("pitchRatio", "a multiplier on frequency - 2 is an octave up, 0.5 is "
                                    + "an octave down, 1 is unchanged")),
                    AUDIO_SHAPED + " transposed by pitchRatio, with harmonic relationships preserved.",
                    """
                    Sawtooth(220) --> Phase Vocoder Pitch Shift(pitchRatio 1.5) --> Audio Out.in
                    Sawtooth --> Phase Vocoder Pitch Shift.in
                    LFO Sine(frequency 0.5) --> Map Range(outMin 0.5, outMax 2) --> Phase Vocoder Pitch Shift.pitchRatio
                    Phase Vocoder Pitch Shift.out --> Audio Out.in (smooth pitch sweep preserving harmonics)"""),
            new Article("Spectral", "Pitch Correction Chromatic",
                    "Classic autotune - detects the input's pitch and snaps it to the nearest semitone.",
                    """
                    Pitch Correction listens to a single monophonic voice or instrument, detects its \
                    fundamental pitch, and re-pitches it (via the same phase-vocoder shifting Phase \
                    Vocoder Pitch Shift uses) so it lands exactly on the nearest note - the real \
                    Auto-Tune effect: self-contained pitch detection plus correction, not a harmonizer \
                    driven by an external target-pitch track. Fed a chord or other polyphonic material, \
                    it tracks whichever pitch is dominant, same as real hardware/software autotune. \
                    Chromatic allows every semitone, so root has no audible effect here; it only exists \
                    so all five Pitch Correction variants share the same three ports - see Pitch \
                    Correction Major/Minor for scale-restricted versions. Silence or unpitched/noisy \
                    input passes through uncorrected rather than being forced onto a wrong pitch.""",
                    List.of(
                            port("in", "the monophonic audio signal to detect and correct"),
                            port("root", "a frequency in Hz whose pitch class sets the tonic - only "
                                    + "which of the 12 note names it is matters, not the octave"),
                            port("retuneSpeed", "how fast the corrected pitch glides to the detected "
                                    + "note, in milliseconds - near 0 is the hard, instant \"robotic\" "
                                    + "snap, higher values are a more natural-sounding gradual "
                                    + "correction")),
                    AUDIO_SHAPED + " with its pitch snapped to the nearest semitone.",
                    """
                    Mic Input --> Pitch Correction Chromatic --> Audio Out.in
                    Mic Input --> Pitch Correction Chromatic(retuneSpeed 0) --> Audio Out.in (hard robotic snap)"""),
            new Article("Spectral", "Pitch Correction Major",
                    "Autotune restricted to the notes of a major scale.",
                    "Same pitch detection and correction as Pitch Correction Chromatic, restricted to "
                    + "the 7 notes of a major scale built on root (root, whole, whole, half, whole, "
                    + "whole, whole, half - the same pattern as playing only the white keys starting "
                    + "from C, transposed to whichever note root names). Patch a Note or Constant into "
                    + "root to change key without touching the rest of the patch.",
                    List.of(
                            port("in", "the monophonic audio signal to detect and correct"),
                            port("root", "a frequency in Hz whose pitch class sets the major scale's "
                                    + "tonic - only which of the 12 note names it is matters, not the "
                                    + "octave"),
                            port("retuneSpeed", "how fast the corrected pitch glides to the detected "
                                    + "note, in milliseconds - near 0 is the hard, instant \"robotic\" "
                                    + "snap, higher values are a more natural-sounding gradual "
                                    + "correction")),
                    AUDIO_SHAPED + " with its pitch snapped to the nearest major-scale note.",
                    """
                    Mic Input --> Pitch Correction Major --> Audio Out.in
                    Note("D4").out --> Pitch Correction Major.root"""),
            new Article("Spectral", "Pitch Correction Minor",
                    "Autotune restricted to the notes of a natural minor scale.",
                    "Same pitch detection and correction as Pitch Correction Chromatic, restricted to "
                    + "the 7 notes of a natural minor scale built on root (root, whole, half, whole, "
                    + "whole, half, whole - the same pattern as playing only the white keys starting "
                    + "from A, transposed to whichever note root names). The moodier, darker-sounding "
                    + "sibling of Pitch Correction Major.",
                    List.of(
                            port("in", "the monophonic audio signal to detect and correct"),
                            port("root", "a frequency in Hz whose pitch class sets the minor scale's "
                                    + "tonic - only which of the 12 note names it is matters, not the "
                                    + "octave"),
                            port("retuneSpeed", "how fast the corrected pitch glides to the detected "
                                    + "note, in milliseconds - near 0 is the hard, instant \"robotic\" "
                                    + "snap, higher values are a more natural-sounding gradual "
                                    + "correction")),
                    AUDIO_SHAPED + " with its pitch snapped to the nearest minor-scale note.",
                    """
                    Mic Input --> Pitch Correction Minor --> Audio Out.in"""),
            new Article("Spectral", "Pitch Correction Major Pentatonic",
                    "Autotune restricted to a 5-note major pentatonic scale.",
                    "Same pitch detection and correction as Pitch Correction Chromatic, restricted to "
                    + "the 5 notes of a major pentatonic scale built on root - Pitch Correction Major "
                    + "with the two half-step-adjacent notes removed, so nothing it outputs can land a "
                    + "dissonant half-step next to another note. A dependable choice for correcting a "
                    + "loose, exploratory vocal or lead take and always getting a pleasant-sounding "
                    + "melody back out.",
                    List.of(
                            port("in", "the monophonic audio signal to detect and correct"),
                            port("root", "a frequency in Hz whose pitch class sets the pentatonic "
                                    + "scale's tonic - only which of the 12 note names it is matters, "
                                    + "not the octave"),
                            port("retuneSpeed", "how fast the corrected pitch glides to the detected "
                                    + "note, in milliseconds - near 0 is the hard, instant \"robotic\" "
                                    + "snap, higher values are a more natural-sounding gradual "
                                    + "correction")),
                    AUDIO_SHAPED + " with its pitch snapped to the nearest major-pentatonic note.",
                    """
                    Mic Input --> Pitch Correction Major Pentatonic --> Audio Out.in"""),
            new Article("Spectral", "Pitch Correction Minor Pentatonic",
                    "Autotune restricted to a 5-note minor pentatonic scale.",
                    "Same pitch detection and correction as Pitch Correction Chromatic, restricted to "
                    + "the 5 notes of a minor pentatonic scale built on root - the blues/rock lead-"
                    + "guitar scale, and the minor equivalent of Pitch Correction Major Pentatonic's "
                    + "forgiving, always-consonant behavior.",
                    List.of(
                            port("in", "the monophonic audio signal to detect and correct"),
                            port("root", "a frequency in Hz whose pitch class sets the pentatonic "
                                    + "scale's tonic - only which of the 12 note names it is matters, "
                                    + "not the octave"),
                            port("retuneSpeed", "how fast the corrected pitch glides to the detected "
                                    + "note, in milliseconds - near 0 is the hard, instant \"robotic\" "
                                    + "snap, higher values are a more natural-sounding gradual "
                                    + "correction")),
                    AUDIO_SHAPED + " with its pitch snapped to the nearest minor-pentatonic note.",
                    """
                    Mic Input --> Pitch Correction Minor Pentatonic --> Audio Out.in"""),
            new Article("Spectral", "Additive Resynth",
                    "Imports a WAV file, analyzes it into a handful of sine partials, and replays it "
                            + "as their sum - at a fidelity you choose.",
                    """
                    Additive Resynth opens a file picker, imports a WAV file, and - unlike Granular \
                    Player, which grains and replays the actual recorded waveform - analyzes it once \
                    into a set of sinusoidal partials: for every short analysis window across the \
                    file, it finds the strongest peaks in the frequency spectrum and tracks how each \
                    one's frequency and loudness drift from window to window, building a "trajectory" \
                    per partial across the file's duration. Playback then sums real sine oscillators \
                    following those trajectories - not the original waveform at all.

                    That gives it two things granular and straight sample playback don't: pitch \
                    shifting is an exact multiplication of every partial's frequency, with no \
                    artifacts, since there's no filter or window involved at playback time; and speed \
                    is fully decoupled from pitch, since partials are just read at a different rate \
                    along their own timeline - genuine time-stretching with none of a phase vocoder's \
                    smearing. Fidelity (set when the file is imported) controls how many simultaneous \
                    partials are tracked - low fidelity keeps only the strongest few, giving a \
                    simplified, more synthetic/organ-like tone; high fidelity keeps many, closer to a \
                    faithful reproduction of the original recording.""",
                    List.of(
                            port("position", "0 to 1 through the analyzed file - where a fresh trigger "
                                    + "starts reading from"),
                            port("speed", "playback rate through the analyzed timeline, independent of "
                                    + "pitch - 1 is the original duration, 0.5 is twice as slow"),
                            port("pitch", "a frequency multiplier applied to every partial - 2 is an "
                                    + "octave up, 0.5 is an octave down, 1 is unchanged"),
                            port("trigger", "a rising pulse restarts playback from position"),
                            port("amp", "overall output level")),
                    AUDIO_WAVE + " the sum of every tracked partial's sine wave, following the "
                            + "imported file's analyzed frequency/amplitude trajectories.",
                    """
                    Additive Resynth(pitch 1.5, speed 0.5) --> Audio Out.in
                    Trigger.out --> Additive Resynth.trigger
                    Unit Phasor(frequency 1) --> Additive Resynth.position
                    Additive Resynth.out --> Audio Out.in (scan through file position on each trigger)"""),
            new Article("Conditionals", "Select",
                    "A patchable if/else between two full signals.",
                    "Passes ifTrue through while condition is above threshold, and ifFalse otherwise - " +
                    "an audio-rate if/else between two complete signals, rather than switching between " +
                    "two fixed numbers.",
                    List.of(
                            port("condition", "compared against threshold each sample to decide which "
                                    + "input passes through"),
                            port("ifTrue", "passed through whenever condition is above threshold"),
                            port("ifFalse", "passed through whenever condition is at or below threshold"),
                            port("threshold", "the cutoff condition is compared against")),
                    "Whichever of ifTrue/ifFalse is currently selected, passed straight through - could "
                            + "be audio or a control signal, depending on what you wire into those ports.",
                    """
                    LFO Square(frequency 2) --> Select.condition
                    Sine --> Select.ifTrue
                    Triangle --> Select.ifFalse

                    Sawtooth --> Biquad Lowpass(cutoff 1000) --> Select.ifTrue
                    Sawtooth --> Select.ifFalse
                    ADSR.out --> Select.condition (route unfiltered audio when envelope is low)"""),
            new Article("Conditionals", "CV Gate",
                    "Passes a signal through only while a separate control signal is open.",
                    "Passes in through unchanged whenever control is above threshold, and outputs " +
                    "silence otherwise. Distinct from Noise Gate in Effects, which gates a signal based " +
                    "on its own loudness rather than a separate control input.",
                    List.of(
                            port("in", "the signal to let through or silence"),
                            port("control", "compared against threshold to decide whether in passes "
                                    + "through"),
                            port("threshold", "the level control must be above to open the gate")),
                    "Either in, unchanged, or silence (0) - the same kind of signal as whatever is "
                            + "wired into in.",
                    """
                    Sine --> CV Gate.in
                    LFO Square(frequency 2) --> CV Gate.control

                    Biquad Lowpass(cutoff 1000).in --> CV Gate.in
                    Sawtooth --> Biquad Lowpass.in
                    ADSR.out --> CV Gate.control (let filter sound only while envelope is above threshold)"""),
            new Article("Conditionals", "Toggle",
                    "Flips between 0 and 1 each time it receives a trigger.",
                    "An edge-triggered flip-flop: each time trigger crosses above threshold it flips " +
                    "its output between 0 and 1 and holds there until the next trigger - useful for a " +
                    "hit that should alternate between two behaviors each time it fires.",
                    List.of(
                            port("trigger", "each rising crossing above threshold flips the output"),
                            port("threshold", "the level trigger must cross to count as a new trigger")),
                    "0 or 1, flipping each time it's triggered and holding steady in between - a "
                            + "control value, not audio.",
                    """
                    Trigger.out --> Toggle.trigger --> Multiplexer.select
                    Trigger(interval 1).out --> Toggle.trigger --> Multiplexer.select
                    Constant(0.1) --> Multiplexer.in0
                    Constant(10) --> Multiplexer.in1
                    Multiplexer.out --> Amplifier.gain (alternate between two gain levels)"""),
            new Article("Conditionals", "Multiplexer",
                    "Picks one of several inputs to pass through, based on a numeric index.",
                    "An N-way selector: outputs whichever of in0..in3 the (rounded) select input " +
                    "currently points at. Feed it a stepping value from a Counter or Sequencer to cycle " +
                    "through several signals over time.",
                    List.of(
                            port("select", "rounded to the nearest whole number to choose which input "
                                    + "(0, 1, 2...) is passed through"),
                            port("in0, in1, ... (one per input)", "the signals to choose between - "
                                    + "four by default when placed from the menu")),
                    "Whichever input select currently points at, passed straight through unchanged - "
                            + "could be audio or a control signal.",
                    """
                    Counter.out --> Multiplexer.select
                    Sine --> Multiplexer.in0, Triangle --> Multiplexer.in1, Sawtooth --> Multiplexer.in2

                    Sequencer(step0 0, step1 1, step2 2, step3 3).out --> Multiplexer.select
                    Constant(200) --> Multiplexer.in0
                    Constant(500) --> Multiplexer.in1
                    Constant(1000) --> Multiplexer.in2
                    Constant(2000) --> Multiplexer.in3
                    Multiplexer.out --> Biquad Lowpass.cutoff (sequence through filter cutoff values)"""),
            new Article("Conditionals", "Latch",
                    "Samples a signal at the instant of a trigger and holds that value.",
                    "Captures whatever in reads at the moment trigger fires (a rising edge) and holds " +
                    "that value steady until the next trigger - a sample-and-hold driven by an explicit " +
                    "trigger, rather than its own internal clock. Use it to freeze a Random draw, a " +
                    "pitch, or a filter cutoff at exactly the same instant something else fires.",
                    List.of(
                            port("in", "the signal that gets sampled the instant trigger fires"),
                            port("trigger", "a pulse that captures the current value of in")),
                    "Whatever in was at the moment of the last trigger - a held value, not a "
                            + "continuously-changing signal.",
                    """
                    Random(min 200, max 2000).out --> Latch.in
                    Trigger.out --> Latch.trigger --> Biquad Lowpass.cutoff

                    Random(min .1, max 1).out --> Latch.in
                    Clock Divider(divide 2).out --> Latch.trigger --> Ladder Filter.resonance (sample random resonance every other beat)"""),
            new Article("Conditionals", "Queue Quantizer",
                    "Snaps live playing to a clock: notes wait in a queue and come out one per trigger.",
                    """
                    A realtime quantizer for live playing. Every time a new note arrives it joins a \
                    queue - a note counts as new when in changes value, or when gate goes high (so \
                    playing the same key twice still queues it twice). Each time trigger fires (a \
                    rising edge), the oldest waiting note leaves the queue: out jumps to its value and \
                    holds it, and pulse fires for exactly one sample.

                    Notes are never lost just because they landed between clocks - three quick notes \
                    before one beat come out on that beat and the next two. If nothing is waiting when \
                    trigger fires, out keeps its last value and pulse stays at 0. The queue holds up to \
                    64 notes; past that, the oldest waiting note is dropped. Notes held in the queue are \
                    cleared whenever playback or a render starts over.

                    Use pulse, not gate, to start the sound - it fires even when the same pitch repeats, \
                    where out alone wouldn't change. It is a one-sample trigger, so it suits a one-shot \
                    Envelope; an ADSR driven by it releases right away. Pitch bend on a MIDI Input \
                    changes its pitch output, so bending while playing queues extra notes.""",
                    List.of(
                            port("in", "the value to quantize, e.g. MIDI Input's pitch - a change queues it"),
                            port("gate", "a rising edge queues in's current value even if it didn't change, "
                                    + "e.g. MIDI Input's gate"),
                            port("trigger", "the clock - each rising edge releases the oldest queued value")),
                    "out holds the most recently released value; pulse is 1 for one sample each time a "
                            + "value is released.",
                    """
                    MIDI Input.pitch --> Queue Quantizer.in
                    MIDI Input.gate --> Queue Quantizer.gate
                    Trigger(interval .125).out --> Queue Quantizer.trigger (sixteenth notes at 120 bpm)
                    Queue Quantizer.out --> Phasor.frequency --> Sine.phase
                    Queue Quantizer.pulse --> Envelope.trigger --> Amplifier.gain
                    Sine --> Amplifier.in --> Audio Out.in"""),
            new Article("Conditionals", "Counter",
                    "Counts trigger pulses and outputs the running total.",
                    "Increments its output by one on each rising edge of trigger. Setting wrap above " +
                    "zero cycles the count back to 0 once it reaches wrap (so wrap = 8 counts 0..7); a " +
                    "rising edge on reset returns it to 0 immediately. Feed the output into Multiplexer's " +
                    "select input to turn a stream of pulses into a stepped choice between signals.",
                    List.of(
                            port("trigger", "each rising pulse adds one to the running count"),
                            port("wrap", "if above 0, the count cycles back to 0 once it reaches this "
                                    + "number; 0 means count without limit"),
                            port("reset", "a rising pulse that returns the count to 0 immediately")),
                    "The running count as a whole number - a control value, not audio.",
                    """
                    Trigger.out --> Counter(wrap 8).trigger --> Multiplexer.select
                    Trigger(interval .2).out --> Counter(wrap 4).trigger --> Multiplexer.select
                    Constant(0.05) --> Multiplexer.in0
                    Constant(0.1) --> Multiplexer.in1
                    Constant(0.2) --> Multiplexer.in2
                    Constant(0.5) --> Multiplexer.in3
                    Multiplexer.out --> Envelope.time (cycle through envelope durations)"""),
            new Article("Conditionals", "Rungler",
                    "An 8-bit shift-register chaos generator - Rob Hordijk's Benjolin core, feeding "
                            + "back on itself to produce an ever-shifting stepped CV.",
                    """
                    Shifts a new bit into an internal 8-bit register on every rising edge of clock, \
                    taking the bit from whether data is currently positive or negative. Reads the \
                    register's top 3 bits back out as an 8-step bipolar voltage (stepped) and the \
                    freshly-shifted bit as a raw 0/1 gate (bit).

                    Left unpatched, both clock and data fall back to their own internal free-running \
                    square-wave oscillators (clock at rate Hz, data at a fixed non-integer multiple of \
                    it) so the node runs and produces motion on its own; patching either input hands \
                    control of that input to whatever's wired in.

                    The classic way to use it is the Benjolin self-patch: clock it from one oscillator, \
                    feed a second oscillator into data, then patch stepped back into both oscillators' \
                    pitch - the loop's own memory of its recent history is what makes the output chaotic \
                    rather than random.""",
                    List.of(
                            port("clock", "each rising edge shifts a new bit into the register - "
                                    + "unpatched, an internal oscillator at rate Hz drives it instead"),
                            port("data", "positive means shift in a 1, negative or zero means shift in "
                                    + "a 0 - unpatched, an internal oscillator drives it instead"),
                            port("rate", "frequency in Hz of the internal fallback oscillators used "
                                    + "when clock/data are left unpatched")),
                    "stepped is one of 8 evenly-spaced values between -1 and 1, chaotic control "
                            + "voltage; bit is the most recently shifted-in 0/1 value, a chaotic "
                            + "gate/trigger.",
                    """
                    Sine(freq A).out --> Rungler.clock
                    Triangle(freq B).out --> Rungler.data
                    Rungler.stepped --> Map Range(-1..1 to 100..800) --> Sine(freq A).frequency
                    Rungler.stepped --> Map Range(-1..1 to 100..800) --> Triangle(freq B).frequency

                    Rungler(rate 8).bit --> CV Gate.control
                    Sawtooth(frequency 220) --> CV Gate.in (chaotic bit stream gates audio on and off)"""),
            new Article("Conditionals", "Clock Divider",
                    "Divides and/or multiplies an incoming clock's rate.",
                    """
                    Watches clock for rising edges and re-times them: divide keeps only every Nth edge \
                    (divide 2 halves the rate, divide 4 quarters it), and multiply then fills the gap \
                    between two kept edges with that many evenly-spaced pulses (multiply 3 turns one \
                    kept edge into three). Both apply together, so divide 2 with multiply 3 outputs \
                    three pulses for every two edges clock receives.

                    multiply estimates each gap's length from the clock rate most recently measured, so \
                    it needs at least one full clock cycle to learn the tempo before its sub-pulses \
                    land evenly - the very first kept edge after the patch starts (or after clock's \
                    rate changes abruptly) fires once with no subdivision, exactly like any hardware \
                    clock multiplier facing the same problem of not being able to see into the future. \
                    Left at divide 1, multiply 1, it simply reproduces clock's own edges as single-\
                    sample pulses - the identity case.""",
                    List.of(
                            port("clock", "the incoming clock/trigger signal to divide or multiply"),
                            port("divide", "keep only every Nth rising edge of clock (1 keeps all of "
                                    + "them)"),
                            port("multiply", "how many evenly-spaced pulses to output for each kept "
                                    + "edge (1 outputs just the edge itself)")),
                    "A re-timed pulse train derived from clock - a control signal, not audio.",
                    """
                    LFO Square(frequency 2).out --> Clock Divider(divide 1, multiply 3).clock
                    Clock Divider.out --> Envelope.trigger

                    Trigger(interval .1).out --> Clock Divider(divide 3, multiply 1).clock
                    Clock Divider.out --> Sequencer.trigger (create a polyrhythm by dividing trigger down)"""),
            new Article("Conditionals", "Sequencer",
                    "Steps through a fixed list of its own inputs, one per trigger.",
                    """
                    Sequencer has one input port per step (step0, step1, ...) and outputs whichever \
                    step is currently active. Each rising trigger edge advances one step and wraps back \
                    to the start at the end; reset jumps back to step0 immediately.

                    Since every step is an ordinary input, each one can be a constant, a macro, or any \
                    other node's output. Driving trigger from a Trigger node with a nonzero interval, and \
                    feeding the Sequencer's output into a Phasor's frequency, builds a simple melodic \
                    pattern.""",
                    List.of(
                            port("trigger", "each rising pulse advances to the next step, wrapping "
                                    + "back to the start at the end"),
                            port("reset", "a rising pulse that jumps back to step0 immediately"),
                            port("step0, step1, ... (one per step)", "the values stepped through - "
                                    + "eight by default when placed from the menu, each an ordinary "
                                    + "input you can wire anything into")),
                    "Whichever step is currently active, passed straight through - could be audio or a "
                            + "control signal depending on what's wired into the steps.",
                    """
                    Trigger(interval .2).out --> Sequencer.trigger
                    Constant(220)->step0, Constant(330)->step1, Constant(440)->step2, ...
                    Sequencer.out --> Phasor.frequency --> Sine.phase --> Audio Out.in

                    Trigger(interval .15).out --> Sequencer.trigger
                    Constant(100)->step0, Constant(500)->step1, Constant(2000)->step2, Constant(800)->step3
                    Sequencer.out --> Biquad Lowpass.cutoff (sequence through filter cutoff values over time)"""),
            new Article("Tracker", "Tracker",
                    "A step sequencer with a pattern grid editor and MIDI import, in 2/4/8/16-channel "
                            + "sizes - for driving several instruments as one song.",
                    """
                    Tracker comes in four sizes - place "Tracker 2", "Tracker 4", "Tracker 8", or \
                    "Tracker 16" from the node menu depending on how many instruments the song needs. \
                    Each holds its own note pattern: one list per channel of notes with a step, a \
                    length, a pitch, and an optional per-note effect (see below). Click its "Edit" \
                    button to open the pattern editor, a full-screen step grid. Notes are typed or placed \
                    and resized with the mouse; selecting a note also opens an effect panel for that \
                    note. For free timing, chords and velocity, use a Piano Roll node instead.

                    Right-click a Tracker node and choose "Convert channels" to swap it for a \
                    different size in place - notes on channels that still exist afterward, and every \
                    wire connected to a surviving pitch/gate pair, are carried over untouched; shrinking \
                    asks for confirmation first, since notes on the channels being dropped are lost.

                    Selecting a note opens an effect panel on the right with every \
                    per-note effect field for that one note; edit them and click Apply to commit - \
                    leaving a field at 0 (or Slide at None) keeps that effect off. Every field measured \
                    in ticks is finer-grained than the step grid: one step is as many ticks as the \
                    pattern's Speed setting (6 by default), so at that setting a rate or duration of 6 \
                    ticks lines up with one full step and 1 tick is six times faster.

                    Slide (None, Glide, or Glissando) makes a note's pitch arrive gradually instead of \
                    snapping there instantly - but only when it immediately follows another note on the \
                    same channel with no gap between them, since gliding needs a pitch to glide *from*. \
                    Glide sweeps continuously between the two pitches; Glissando sweeps the same \
                    distance but snapped to the nearest whole semitone at every instant, so it sounds \
                    like a fast chromatic run rather than a smooth portamento. Slide steps sets how many \
                    pattern steps the glide takes to complete - tempo-relative, so the same value takes \
                    the same musical time regardless of bpm. A note with a gap before it, or the first \
                    note on a channel, ignores its own slide setting entirely and just plays at pitch.

                    Arp +semi 1 and Arp +semi 2 are two semitone offsets added on top of the note's own \
                    pitch; Arp rate (ticks) is how long the pitch holds at each step of the cycle before \
                    advancing to the next. With a nonzero rate, the note's pitch cycles root, \
                    root+semi 1, root+semi 2, back to root, and so on for as long as the note is held - \
                    the classic fast chord-arpeggiation effect. Arpeggio and slide are mutually \
                    exclusive on the same note: if slide is actually engaged (a legato pair with a slide \
                    mode set), arpeggio is skipped for that note even when its rate is also set, \
                    matching how a real tracker's effect column treats these as alternatives rather than \
                    something to combine.

                    Delay fraction (0 to 1) starts the note late within its own step instead of exactly \
                    on the step boundary - 0.5 starts it halfway between this step and the next, for \
                    swing or a slightly loose, humanized feel. It only shifts when the note starts \
                    sounding; its length and every other effect are unaffected.

                    Retrigger (ticks) drops the gate for a single sample and immediately brings it back \
                    high every N ticks for as long as the note is held, without changing its pitch - a \
                    real rising edge a gate-driven envelope will fire from again, for rolls and \
                    stutters. Cut (ticks) forces the gate off for good once N ticks have elapsed since \
                    the note started, ending it early regardless of how long the note cell looks on the \
                    grid - useful for a short, punchy hit inside a longer note. Both are independent of \
                    slide and arpeggio and can be combined with either.

                    Effects that don't need note-boundary awareness - vibrato, tremolo - are \
                    deliberately left to Vibrato/Tremolo, patched onto Tracker's own pitch/gate outputs, \
                    rather than duplicated here.

                    The editor can also import a Standard MIDI File. Import reads the file's tempo and \
                    maps its first N channels that hold notes (N being this Tracker's size) onto \
                    Tracker's channels, in channel-number order; anything beyond N channels, or a \
                    second overlapping note on an already-playing channel, is dropped - Tracker's \
                    channels are strictly one note at a time, like a monophonic voice. Two notes placed \
                    back-to-back with no gap between them do not retrigger a gate-driven instrument, \
                    since the gate never drops between them - this matches how a held gate behaves on \
                    real hardware, and is worth knowing if an imported file plays legato (or wire a \
                    slide/arpeggio effect for something more expressive).

                    Each channel's pitch and gate pair is meant to drive one instrument - typically a \
                    saved patch loaded as a node (see Patches), wired to that instrument's pitch and gate \
                    macros. Wire up as many instruments this way as the Tracker has channels, and it \
                    plays them together as a song.""",
                    List.of(
                            port("bpm", "the song's tempo in beats per minute - the pattern editor's "
                                    + "steps-per-beat setting turns this into real time"),
                            port("play", "1 plays, 0 pauses in place - gates drop while paused, but the "
                                    + "song position is remembered and resumes exactly where it left off"),
                            port("reset", "a rising pulse that jumps every channel back to the first step "
                                    + "immediately"),
                            port("loop", "1 repeats the pattern forever; 0 plays through once and then "
                                    + "holds silent at the end")),
                    "One pitch and gate pair per channel (pitch0/gate0, pitch1/gate1, ...): pitch is "
                            + "the held note's frequency in Hz, gate is 1 for as long as a note is "
                            + "sounding and 0 between notes - wire each pair into one instrument's "
                            + "pitch and gate inputs.",
                    """
                    Tracker.pitch0 --> Instrument 1(patch).pitch
                    Tracker.gate0 --> Instrument 1(patch).gate
                    Tracker.pitch1 --> Instrument 2(patch).pitch
                    Tracker.gate1 --> Instrument 2(patch).gate

                    Tracker 4(bpm 120).pitch0 --> Bass.pitch
                    Tracker 4.gate0 --> Bass.gate
                    Tracker 4.pitch1 --> Lead.pitch
                    Tracker 4.gate1 --> Lead.gate
                    Tracker 4.pitch2 --> Pad.pitch
                    Tracker 4.gate2 --> Pad.gate (four-channel song with bass, lead, and two drums)"""),
            new Article("Tracker", "Piano Roll",
                    "A polyphonic piano-roll clip with per-note velocity, in 4/8/16-voice sizes - chords, "
                            + "free timing and MIDI import, drawn with the mouse.",
                    """
                    Piano Roll comes in three sizes - "Piano Roll 4", "Piano Roll 8", or "Piano Roll 16" - \
                    plus "Piano Roll (Custom Size)..." for any other voice count. Click its "Edit" button \
                    to open the piano roll screen: time runs left to right, pitch bottom to top, with a \
                    keyboard down the left edge, a bar ruler along the top and a velocity lane along the \
                    bottom. Unlike Tracker, notes may overlap freely - stack them into chords and each \
                    note is put on its own voice (see below).

                    Click an empty spot to place a note, and drag right to stretch it as you place it; \
                    new notes take the length and velocity of the last note you touched. Drag a note to \
                    move it, or drag its right edge to resize it. Right-click a note to delete it. \
                    Double-click a note (or select it and press Enter) to type an exact pitch - a note \
                    name like "C#4+25c" or a bare MIDI number like "61.25". Hold Alt while placing, moving \
                    or resizing to ignore the grid: pitches then land between the semitone rows and \
                    times between grid lines, for microtonal and loose, unquantized parts.

                    Shift-click adds or removes a note from the selection; Shift- or Ctrl-drag on an \
                    empty spot draws a selection box. Moving or resizing any selected note moves or \
                    resizes the whole selection together. With a selection: Delete removes it, Up/Down \
                    transposes it a semitone (Shift for an octave), Left/Right nudges it one grid step \
                    (Shift for a bar), Q snaps its start times to the grid, Ctrl+D duplicates it just \
                    after itself, and Ctrl+C / Ctrl+X / Ctrl+V copy, cut and paste at the mouse \
                    position. Ctrl+A selects everything, Escape clears the selection, Ctrl+Z / \
                    Ctrl+Shift+Z undo and redo.

                    Drag up and down in the velocity lane to set how hard notes are played - every \
                    note whose start the mouse passes over is changed, or only the selected notes when \
                    there is a selection. Velocity comes out of the node on its own port per voice, \
                    ready to scale an amplifier or open a filter.

                    Every note belongs to a voice - one of the node's pitch/gate/velocity output sets - \
                    shown by its colour and a "v1", "v2" ... label. A new, moved or pasted note goes \
                    to the lowest voice that's free for its whole length, so chords spread out on their \
                    own; [ and ] move the selected notes to the previous or next voice by hand. A voice \
                    plays one note at a time, so two overlapping notes on the same voice cut each other \
                    short, and notes on a voice the node doesn't have (after converting to fewer voices) \
                    are greyed out and silent - the toolbar counts both.

                    Each voice also has its own pitch curve, OpenUTAU style. Pick the "Pitch pen" tool \
                    and draw over a note: the line through each note is the pitch it really plays, and \
                    the pen moves it to wherever you draw - above the note's centre bends up, below \
                    bends down, by up to an octave. A stroke writes to the curve of the voice of the \
                    note you started on, so bending one note of a chord leaves the others alone. \
                    Right-drag with the pen erases that voice's curve. Set the bottom lane to "Pitch \
                    bend" to see and draw one voice's curve directly (-12 to +12 semitones), choosing \
                    the voice with "Curve voice"; "Clear voice curve" removes it. A curve belongs to \
                    the voice's timeline rather than to a note, so it stays put when notes move - and \
                    a note that has to change voice to fit somewhere new picks up that voice's curve. \
                    Each stroke eases back to no bend right after its ends.

                    The Play button (or Space) previews the clip on its own, without the rest of the \
                    patch, looping at the "Preview BPM" tempo; edits are heard as you make them. With \
                    "Audition notes" on, placing, clicking or dragging a note plays a short blip of it. \
                    Both use a preview voice patch, one copy per voice. The built-in one is a plain sine \
                    with an ADSR; to use your own sound, save a one-voice patch as preview.ptr in \
                    Petroleum's config folder (~/.config/petroleum on Linux, ~/Library/Application \
                    Support/Petroleum on macOS, %APPDATA%\\Petroleum on Windows - the Play button's \
                    tooltip shows the exact path). It needs macros named "pitch" (Hz) and "gate", and \
                    optionally "velocity" (0-1); the piano roll's outputs are wired into them, just like \
                    loading a patch as a node. The file is re-read every time you press Play, so you \
                    can tweak it and try again without restarting.

                    The Snap box sets the grid (from a whole bar down to 1/32, including triplets, or \
                    Off). The clip's length is set in bars in the toolbar, or by dragging the marker in \
                    the ruler; notes past the end are shown dimmed and do not play. Scroll to move \
                    around, Shift+scroll to scroll sideways, Ctrl+scroll to zoom time and Alt+scroll to \
                    zoom pitch; Fit zooms to the whole clip. Import MIDI replaces the clip with a \
                    Standard MIDI File's notes, keeping their exact timing, chords and velocities.

                    When more notes overlap than the node has voices, some have to share a voice and \
                    cut each other short - the toolbar reports how many, and a bigger Piano Roll \
                    (right-click the node, "Convert channels") fixes it. When a voice starts a new note right as its previous note ends, its gate \
                    drops to 0 for a single sample, so an envelope hears a fresh note.""",
                    List.of(
                            port("bpm", "the tempo in beats per minute, four beats to a bar"),
                            port("play", "1 plays, 0 pauses in place - gates drop while paused and the "
                                    + "clip resumes where it left off"),
                            port("reset", "a rising pulse that jumps back to the start of the clip"),
                            port("loop", "1 repeats the clip forever; 0 plays it once and then stays "
                                    + "silent")),
                    "Three outputs per voice (pitch0/gate0/velocity0, pitch1/gate1/velocity1, ...): pitch "
                            + "is the note's frequency in Hz, gate is 1 while the note sounds, and "
                            + "velocity is the note's velocity, 0-1. Wire each voice into one instrument "
                            + "voice.",
                    """
                    Piano Roll 4.pitch0 --> Instrument 4.pitch0
                    Piano Roll 4.gate0 --> Instrument 4.gate0
                    (same for voices 1 to 3)
                    Instrument 4.out --> Audio Out.in

                    Piano Roll 4(bpm 96).pitch0 --> Phasor.frequency --> Sawtooth.phase
                    Piano Roll 4.gate0 --> ADSR.gate
                    Piano Roll 4.velocity0 --> Amplifier.gain
                    ADSR.out --> Amplifier.in"""),
            new Article("Tracker", "MIDI Player",
                    "Plays a MIDI file exactly as written - original timing, tempo changes and chords "
                            + "- across 2/4/8/16 voices.",
                    """
                    MIDI Player comes in four sizes - "MIDI Player 2", "MIDI Player 4", "MIDI Player 8", \
                    or "MIDI Player 16" - plus "MIDI Player (Custom Size)..." for any other voice count. \
                    Click its Load button to pick a .mid file; a summary shows how many notes it holds \
                    and how long it runs. The file is stored inside the patch, so a saved patch keeps \
                    playing it even if the original file is moved or deleted.

                    Unlike importing into a Tracker, nothing is snapped to a grid: every note starts and \
                    stops at the exact time written in the file, and tempo changes partway through the \
                    song are followed. Notes from every MIDI channel are dealt out across the voices, so \
                    chords play in full - each new note takes the first free voice. When more notes \
                    sound at once than there are voices, the note that would have ended soonest is cut \
                    short to make room; the load summary says how many notes that happened to, and a \
                    bigger MIDI Player fixes it.

                    When a voice starts a new note right as its previous note ends, its gate drops to 0 \
                    for a single sample, so an ADSR or instrument hears a fresh note and retriggers \
                    instead of treating the two as one long note. Drum tracks (MIDI channel 10) are \
                    played as ordinary pitched notes like any other channel.""",
                    List.of(
                            port("play", "1 plays, 0 pauses in place - gates drop while paused and the "
                                    + "song resumes exactly where it left off"),
                            port("reset", "a rising pulse that jumps back to the start of the song"),
                            port("loop", "1 starts the song again when it ends; 0 plays it once and then "
                                    + "stays silent")),
                    "Three outputs per voice (pitch0/gate0/velocity0, pitch1/gate1/velocity1, ...): pitch "
                            + "is the note's frequency in Hz, gate is 1 while the note sounds, and "
                            + "velocity is how hard it was played, 0-1. Wire each voice into one "
                            + "instrument.",
                    """
                    MIDI Player 4.pitch0 --> Instrument 4.pitch0
                    MIDI Player 4.gate0 --> Instrument 4.gate0
                    MIDI Player 4.pitch1 --> Instrument 4.pitch1
                    MIDI Player 4.gate1 --> Instrument 4.gate1
                    (same for voices 2 and 3)
                    Instrument 4.out --> Audio Out.in

                    MIDI Player 2.pitch0 --> Phasor.frequency --> Sine.phase
                    MIDI Player 2.gate0 --> ADSR.gate
                    MIDI Player 2.velocity0 --> Amplifier.gain
                    ADSR.out --> Amplifier.in"""),
            new Article("Mixers", "Mix",
                    "Sums several inputs together, each at its own weight.",
                    "Adds four named inputs (in0..in3) together, each scaled by its own fixed weight - " +
                    "a plain mixer when all weights are equal, or a fixed weighted blend otherwise. " +
                    "Unused inputs can simply be left unwired.",
                    List.of(port("in0, in1, in2, in3", "the signals being summed together, each "
                            + "multiplied by its own fixed weight before adding - unused ones can be "
                            + "left unwired")),
                    AUDIO_SHAPED + " The sum of every connected input, each scaled by its weight.",
                    """
                    Sine --> Mix.in0
                    Triangle --> Mix.in1
                    Mix.out --> Audio Out.in
                    Sawtooth(frequency 220) --> Mix.in0
                    Square(frequency 440) --> Mix.in1
                    Sine(frequency 330) --> Mix.in2
                    Mix.out --> Audio Out.in"""),
            new Article("Mixers", "Crossfade",
                    "Smoothly blends between two signals with a single control.",
                    "Blends a and b together according to mix: 0 is all a, 1 is all b, and values in " +
                    "between are a linear blend of the two. Wire an envelope, LFO, or macro into mix to " +
                    "morph between two related sounds live.",
                    List.of(
                            port("a", "the signal heard when mix is 0"),
                            port("b", "the signal heard when mix is 1"),
                            port("mix", "0 is fully a, 1 is fully b, in between is a blend of both")),
                    AUDIO_SHAPED + " A blend of a and b according to mix.",
                    """
                    Sine --> Crossfade.a
                    Sawtooth --> Crossfade.b
                    LFO Triangle(frequency .1) --> Map Range(outMin 0, outMax 1) --> Crossfade.mix
                    Distortion Soft Clip --> Crossfade.a
                    Distortion Foldback --> Crossfade.b
                    Envelope(attack .05, decay .2).out --> Crossfade.mix"""),
            new Article("Mixers", "Panner Equal Power",
                    "Places a mono signal somewhere across the stereo field, without dipping in the middle.",
                    "Splits in into a left and right output according to pan, using an equal-power law " +
                    "so the sound doesn't seem to get quieter as it crosses the center - -1 is hard left, " +
                    "0 is centered (both channels at equal, slightly-attenuated strength), 1 is hard " +
                    "right. This is the standard choice for placing one independent sound source in the " +
                    "stereo field - use Panner Linear instead when panning two correlated/duplicated " +
                    "signals, where the center dip this law avoids would be the wrong behavior. Feed " +
                    "outL/outR into a Stereo Audio Out (see Sources & Sinks), directly or through a " +
                    "Stereo Mix.",
                    List.of(
                            port("in", "the mono signal to place in the stereo field"),
                            port("pan", "-1 is hard left, 0 is centered, 1 is hard right")),
                    AUDIO_SHAPED + " outL and outR - the same signal, split across the stereo field "
                            + "according to pan.",
                    """
                    Sine --> Panner Equal Power.in
                    LFO Triangle(frequency 0.2) --> Panner Equal Power.pan
                    Panner Equal Power.outL --> Stereo Audio Out.inL
                    Panner Equal Power.outR --> Stereo Audio Out.inR"""),
            new Article("Mixers", "Panner Linear",
                    "Places a mono signal somewhere across the stereo field using a plain crossfade.",
                    "Splits in into a left and right output according to pan using a straight linear " +
                    "crossfade - -1 is hard left, 0 is centered (both channels at exactly half strength), " +
                    "1 is hard right. Unlike Panner Equal Power, the combined loudness dips as pan crosses " +
                    "the center (about -6dB versus -3dB), the same tradeoff Crossfade makes over Mix for " +
                    "blending two related signals - reach for this law specifically when that predictable, " +
                    "purely-additive behavior matters more than constant perceived loudness, such as " +
                    "panning two already-correlated copies of the same signal rather than placing one " +
                    "independent source.",
                    List.of(
                            port("in", "the mono signal to place in the stereo field"),
                            port("pan", "-1 is hard left, 0 is centered, 1 is hard right")),
                    AUDIO_SHAPED + " outL and outR - the same signal, split across the stereo field "
                            + "according to pan.",
                    """
                    Sine --> Panner Linear.in
                    Constant(-0.5) --> Panner Linear.pan
                    Panner Linear.outL --> Stereo Audio Out.inL
                    Panner Linear.outR --> Stereo Audio Out.inR"""),
            new Article("Mixers", "Stereo Mix",
                    "Sums up to four left/right signal pairs into one stereo bus.",
                    "Adds four pairs of named inputs (inL0/inR0 through inL3/inR3) together at unity " +
                    "gain, keeping left and right separate - the stereo counterpart to Mix. Typically " +
                    "fed by several Panner Equal Power/Panner Linear nodes (or any other node with " +
                    "outL/outR outputs), combined here before the final Stereo Audio Out. Unused pairs " +
                    "can simply be left unwired.",
                    List.of(port("inL0/inR0 .. inL3/inR3", "up to four stereo signal pairs being summed "
                            + "together - unused pairs can be left unwired")),
                    AUDIO_SHAPED + " outL and outR - the sum of every connected pair's left and right "
                            + "channels respectively.",
                    """
                    Sine --> Panner Equal Power.in
                    Constant(-0.7) --> Panner Equal Power.pan
                    Panner Equal Power.outL --> Stereo Mix.inL0
                    Panner Equal Power.outR --> Stereo Mix.inR0
                    Sawtooth(frequency 220) --> Panner Equal Power 2.in
                    Constant(0.7) --> Panner Equal Power 2.pan
                    Panner Equal Power 2.outL --> Stereo Mix.inL1
                    Panner Equal Power 2.outR --> Stereo Mix.inR1
                    Stereo Mix.outL --> Stereo Audio Out.inL
                    Stereo Mix.outR --> Stereo Audio Out.inR"""),
            new Article("Utility", "Splitter",
                    "Fans one signal out to several output ports, for a tidier-looking patch.",
                    "Copies its single in input to several outputs (out0..out3). A single output can " +
                    "already feed any number of destinations just by wiring more connections from it, so " +
                    "this node exists purely to make that fan-out visible and explicit in the graph.",
                    List.of(port("in", "the single signal to copy out to every output")),
                    "Several identical copies of in (out0, out1, ... - four by default) - each one is "
                            + "exactly the same signal, just wired to a different destination.",
                    """
                    Sine --> Splitter.in
                    Splitter.out0 --> Amplifier.in
                    Splitter.out1 --> Delay.in
                    Sawtooth --> Splitter.in
                    Splitter.out0 --> Biquad Lowpass.in
                    Splitter.out1 --> Distortion Soft Clip.in
                    Splitter.out2 --> Audio Out.in"""),
            new Article("Utility", "Amplifier",
                    "Multiplies a signal by a gain value - boost, attenuate, or invert.",
                    "Multiplies in by gain. Values above 1 boost the signal, values between 0 and 1 " +
                    "attenuate it, and negative values invert its polarity. The simplest way to control " +
                    "loudness anywhere in a patch.",
                    List.of(
                            port("in", "the signal to scale"),
                            port("gain", "the multiplier - above 1 boosts, between 0 and 1 quiets it "
                                    + "down, negative flips its polarity")),
                    AUDIO_SHAPED + " in, scaled by gain - louder, quieter, or inverted.",
                    """
                    Sine --> Amplifier(gain 0.5) --> Audio Out.in
                    Sawtooth --> Amplifier(gain 2) --> Audio Out.in"""),
            new Article("Utility", "Attenuverter",
                    "Multiplies a signal by a gain value - identical to Amplifier, named the way a "
                            + "modular rack would label this exact job.",
                    "Multiplies in by gain, exactly like Amplifier - a gain above 1 boosts, between 0 "
                    + "and 1 attenuates, and negative inverts the signal's polarity. It exists as its "
                    + "own node purely so it turns up under its own name in the node menu: a physical "
                    + "modular rack always has a utility explicitly labeled \"attenuverter\", and this "
                    + "gives that same instantly-recognizable name to the identical operation Amplifier "
                    + "already performs.",
                    List.of(
                            port("in", "the signal to scale"),
                            port("gain", "the multiplier - above 1 boosts, between 0 and 1 quiets it "
                                    + "down, negative flips its polarity")),
                    AUDIO_SHAPED + " in, scaled by gain - louder, quieter, or inverted.",
                    """
                    Random(min -1, max 1).out --> Attenuverter(gain 0.3) --> Biquad Lowpass.cutoff
                    LFO Triangle(frequency 2).out --> Attenuverter(gain 0.5) --> Delay.time"""),
            new Article("Utility", "Invert",
                    "Flips a signal's polarity - the mirror image around zero.",
                    "Outputs -in, unconditionally. The same result as an Amplifier or Attenuverter set "
                    + "to gain -1, but as a single-purpose node with nothing to dial: a dedicated"
                    + " inverter is common in a physical rack precisely because it needs no settings, "
                    + "just a signal in and its mirror image out.",
                    List.of(port("in", "the signal to flip")),
                    AUDIO_SHAPED + " The exact mirror image of in around zero.",
                    """
                    LFO Triangle(frequency 2).out --> Invert --> Amplifier.gain
                    Sine --> Invert --> Distortion Soft Clip.in"""),
            new Article("Utility", "Map Range",
                    "Rescales a signal from one range of values to another.",
                    "Linearly remaps in from [inMin, inMax] to [outMin, outMax]. Useful for taking a " +
                    "0..1 control signal (a unit phasor, an LFO after rescaling) and stretching it to " +
                    "whatever range a destination port actually expects, like a filter's cutoff in Hz.",
                    List.of(
                            port("in", "the signal to rescale"),
                            port("inMin", "the value of in that should map to outMin"),
                            port("inMax", "the value of in that should map to outMax"),
                            port("outMin", "what inMin becomes after rescaling"),
                            port("outMax", "what inMax becomes after rescaling")),
                    "The same shape as in, stretched into the new [outMin, outMax] range - not audio "
                            + "by itself, typically feeds a Hz/seconds/other real-world parameter.",
                    """
                    Unit Phasor(frequency 0.2) --> Map Range(outMin 200, outMax 2000) --> Biquad Lowpass.cutoff
                    LFO Sine(frequency 0.5) --> Map Range(inMin -1, inMax 1, outMin 0, outMax 1) --> Crossfade.mix"""),
            new Article("Utility", "Clamp",
                    "Hard-limits a signal to a fixed minimum and maximum.",
                    "Clamps in to the [min, max] range (default -1..1) - anything outside that range is " +
                    "flattened to the nearest edge instead of passing through unchanged. A simple safety " +
                    "net anywhere a signal might otherwise overshoot.",
                    List.of(
                            port("in", "the signal to limit"),
                            port("min", "the lowest value the output is ever allowed to reach"),
                            port("max", "the highest value the output is ever allowed to reach")),
                    "The same as in, except anything outside [min, max] is flattened to that edge "
                            + "instead of passing through.",
                    """
                    Envelope(punch 1.5).out --> Clamp(min -1, max 1) --> Audio Out.in
                    LFO Triangle(frequency 2) --> Map Range(outMin -3, outMax 3) --> Clamp(min -2, max 2)"""),
            new Article("Utility", "Round",
                    "Rounds a signal to the nearest whole number.",
                    "Outputs in rounded to the nearest integer (halfway values round up, matching " +
                    "Math.round). Useful for turning a continuous control signal into discrete steps - " +
                    "quantizing a sweeping value into semitone-like jumps, or forcing a select input to " +
                    "land exactly on a whole index.",
                    List.of(port("in", "the signal to round")),
                    "The same shape as in, snapped to the nearest whole number - a control value, not "
                            + "audio.",
                    """
                    LFO Triangle(frequency 0.5) --> Map Range(outMin 0, outMax 7) --> Round --> Multiplexer.select
                    Random(min 0, max 5).out --> Round --> Sequencer.step"""),
            new Article("Utility", "Ceil",
                    "Rounds a signal up to the next whole number.",
                    "Outputs in rounded up to the nearest integer that is greater than or equal to it " +
                    "(matching Math.ceil) - unlike Round, a value like 2.1 becomes 3, not 2. Useful " +
                    "wherever a value needs to clear a whole-number boundary rather than land on the "
                    + "closer one.",
                    List.of(port("in", "the signal to round up")),
                    "The same shape as in, rounded up to the next whole number - a control value, not "
                            + "audio.",
                    """
                    Random(min 0, max 8).out --> Ceil --> Counter.wrap
                    Unit Phasor(frequency 0.5) --> Map Range(outMin 0, outMax 4) --> Ceil --> Multiplexer.select"""),
            new Article("Utility", "Floor",
                    "Rounds a signal down to the next whole number.",
                    "Outputs in rounded down to the nearest integer that is less than or equal to it " +
                    "(matching Math.floor) - unlike Round, a value like 2.9 becomes 2, not 3. The " +
                    "standard way to turn a fractional phase or index into a whole step count.",
                    List.of(port("in", "the signal to round down")),
                    "The same shape as in, rounded down to the next whole number - a control value, not "
                            + "audio.",
                    """
                    Unit Phasor(frequency 1).out --> Map Range(outMin 0, outMax 4) --> Floor --> Multiplexer.select
                    LFO Triangle(frequency 0.1) --> Map Range(outMin 0, outMax 8) --> Floor --> Counter.wrap"""),
            new Article("Utility", "Slew",
                    "Smooths sudden jumps into a glide, at a settable speed.",
                    """
                    Slew limits how fast its input can change: rise and fall (each in seconds, how long \
                    it takes to travel one unit) control the speed of increases and decreases \
                    separately, so a fast attack with a slow release is one node instead of two.

                    This is portamento/glide for control signals - a stepped Sequencer output through a \
                    Slew becomes a smooth glide between notes, and a jumpy macro through one stops a \
                    knob drag from clicking audibly. Unlike a filter's smoothing, Slew moves at a \
                    constant rate and arrives exactly at its target rather than approaching it forever.""",
                    List.of(
                            port("in", "the sudden or stepped signal to smooth"),
                            port("rise", "how many seconds it takes to travel one unit upward"),
                            port("fall", "how many seconds it takes to travel one unit downward")),
                    "A smoothed, gliding version of in that can never change faster than rise/fall "
                            + "allow.",
                    """
                    Sequencer.out --> Slew(rise .05, fall .2) --> Phasor.frequency
                    Random.out --> Slew(rise .1, fall .1) --> Biquad Lowpass.cutoff"""),
            new Article("Utility", "Multiplier",
                    "Multiplies two independent signals together.",
                    "Outputs a times b - the one node in Utility that combines two signals instead of " +
                    "transforming one. Wiring two different signals into a and b is amplitude " +
                    "modulation with the modulator fully exposed, similar in spirit to Amplitude " +
                    "Modulator but without the extra depth/carrier scaling.",
                    List.of(
                            port("a", "the first signal to multiply"),
                            port("b", "the second signal to multiply")),
                    AUDIO_SHAPED + " a multiplied by b, sample for sample.",
                    """
                    Sine --> Multiplier.a
                    LFO Sine(frequency 6) --> Multiplier.b
                    Sawtooth --> Multiplier.a
                    Envelope(attack .1, decay .3).out --> Multiplier.b"""),
            new Article("Utility", "Difference",
                    "Subtracts one signal from another.",
                    "Outputs a - b - the subtractive counterpart to Multiplier, for combining two " +
                    "signals instead of transforming one. Feeding a slowly-varying signal into b " +
                    "subtracts out a drifting DC offset or slow modulation from a; feeding two copies " +
                    "of nearly the same signal, one delayed, into a and b is a simple comb-style " +
                    "cancellation.",
                    List.of(
                            port("a", "the signal to subtract from"),
                            port("b", "the signal to subtract")),
                    AUDIO_SHAPED + " a minus b, sample for sample.",
                    """
                    Sine --> Difference.a
                    Slew(rate 0.5).out --> Difference.b
                    LFO Triangle(frequency 2) --> Difference.a
                    Constant(0.5) --> Difference.b"""),
            new Article("Utility", "Pitch to Time",
                    "Converts a frequency in Hz to the matching period in seconds.",
                    "Outputs 1 / pitch - the length of one cycle at that frequency. Tuning a delay to a " +
                    "musical pitch (so its repeats ring at a specific note rather than an arbitrary echo) " +
                    "means setting the delay's time to the period of that note, not its frequency - this " +
                    "node does that conversion so a Phasor's frequency and a Delay's time can share the " +
                    "same pitch input. A pitch at or below 0 is treated as vanishingly small rather than " +
                    "dividing by zero, so the output is always a large but finite time instead of " +
                    "infinity or NaN.",
                    List.of(port("pitch", "a frequency in Hz - the same kind of value a Phasor or "
                            + "oscillator's frequency port expects")),
                    CONTROL_NOT_AUDIO + " The period of pitch, in seconds (1 / pitch).",
                    """
                    Constant(220) --> Phasor.frequency
                    Constant(220) --> Pitch to Time --> Delay.time
                    Note("A2") --> Pitch to Time --> Comb Filter.time"""),
            new Article("Utility", "Time to Pitch",
                    "Converts a period in seconds to the matching frequency in Hz.",
                    "Outputs 1 / time - the exact inverse of Pitch to Time, for going the other " +
                    "direction: reading a clock or LFO's period as a pitch, so an oscillator can track " +
                    "another node's timing instead of the other way around. A time at or below 0 is " +
                    "treated as vanishingly small rather than dividing by zero, so the output is always a " +
                    "large but finite pitch instead of infinity or NaN.",
                    List.of(port("time", "a duration in seconds - the same kind of value a Delay's time "
                            + "or a Ramp's time port expects")),
                    CONTROL_NOT_AUDIO + " The reciprocal of time, in Hz (1 / time).",
                    """
                    Constant(0.25) --> Time to Pitch --> Phasor.frequency
                    Ramp(time 0.5) --> Time to Pitch --> Square.phase"""),
            new Article("Utility", "Quantizer Chromatic",
                    "Snaps a pitch to the nearest semitone - all twelve notes allowed.",
                    """
                    Reads in as a frequency in Hz (the same unit every pitch port in the graph already \
                    speaks - a Phasor's frequency, a Note's output, a Tracker's pitch ports) and snaps \
                    it to the nearest whole semitone, then converts back to Hz. root sets which \
                    frequency counts as the tonic (only its pitch class - C, C#, D, ... - matters, not \
                    which octave); Chromatic allows every semitone, so root has no audible effect here, \
                    it only exists so all five Quantizer variants share the same two ports.

                    This is the one that turns a smoothly sweeping or randomly wandering pitch into \
                    exact, in-tune notes without picking a musical key - see Quantizer Major/Minor for \
                    scale-restricted versions, and Round for the same idea applied to a plain control \
                    number instead of a musical pitch.""",
                    List.of(
                            port("in", "the frequency in Hz to snap to the nearest semitone"),
                            port("root", "a frequency in Hz whose pitch class sets the scale's tonic - "
                                    + "only which of the 12 note names it is matters, not the octave")),
                    CONTROL_NOT_AUDIO + " The nearest in-tune frequency to in, in Hz.",
                    """
                    Random(min 200, max 800).out --> Quantizer Chromatic.in --> Phasor.frequency
                    LFO Triangle(frequency 0.5) --> Map Range(outMin 100, outMax 2000) --> Quantizer Chromatic.in"""),
            new Article("Utility", "Quantizer Major",
                    "Snaps a pitch to the nearest note of a major scale.",
                    "Same Hz-in, Hz-out snapping as Quantizer Chromatic, restricted to the 7 notes of a "
                    + "major scale built on root (root, whole, whole, half, whole, whole, whole, half - "
                    + "the same pattern as playing only the white keys starting from C, transposed to "
                    + "whichever note root names). Patch a Note or Constant into root to change key "
                    + "without touching the rest of the patch.",
                    List.of(
                            port("in", "the frequency in Hz to snap to the nearest major-scale note"),
                            port("root", "a frequency in Hz whose pitch class sets the major scale's "
                                    + "tonic - only which of the 12 note names it is matters, not the "
                                    + "octave")),
                    CONTROL_NOT_AUDIO + " The nearest major-scale frequency to in, in Hz.",
                    """
                    Sequencer(step0 300, step1 500, step2 650).out --> Quantizer Major.in
                    Note("D4").out --> Quantizer Major.root
                    Quantizer Major.out --> Phasor.frequency
                    Random(min 150, max 1200).out --> Quantizer Major.in
                    Constant(220) --> Quantizer Major.root"""),
            new Article("Utility", "Quantizer Minor",
                    "Snaps a pitch to the nearest note of a natural minor scale.",
                    "Same Hz-in, Hz-out snapping as Quantizer Chromatic, restricted to the 7 notes of a "
                    + "natural minor scale built on root (root, whole, half, whole, whole, half, whole - "
                    + "the same pattern as playing only the white keys starting from A, transposed to "
                    + "whichever note root names). The moodier, darker-sounding sibling of Quantizer "
                    + "Major.",
                    List.of(
                            port("in", "the frequency in Hz to snap to the nearest minor-scale note"),
                            port("root", "a frequency in Hz whose pitch class sets the minor scale's "
                                    + "tonic - only which of the 12 note names it is matters, not the "
                                    + "octave")),
                    CONTROL_NOT_AUDIO + " The nearest minor-scale frequency to in, in Hz.",
                    """
                    Random(min 200, max 800).out --> Quantizer Minor.in --> Phasor.frequency
                    LFO Sine(frequency 0.3) --> Map Range(outMin 100, outMax 1600) --> Quantizer Minor.in"""),
            new Article("Utility", "Quantizer Major Pentatonic",
                    "Snaps a pitch to the nearest note of a 5-note major pentatonic scale.",
                    "Same Hz-in, Hz-out snapping as Quantizer Chromatic, restricted to the 5 notes of a "
                    + "major pentatonic scale built on root - Quantizer Major with the two "
                    + "half-step-adjacent notes removed, so nothing it outputs can land a dissonant "
                    + "half-step next to another note. A dependable choice for feeding a chaotic or "
                    + "random source (Rungler, Random, Chaotic) and always getting a pleasant-sounding "
                    + "melody back out.",
                    List.of(
                            port("in", "the frequency in Hz to snap to the nearest pentatonic note"),
                            port("root", "a frequency in Hz whose pitch class sets the pentatonic "
                                    + "scale's tonic - only which of the 12 note names it is matters, "
                                    + "not the octave")),
                    CONTROL_NOT_AUDIO + " The nearest major-pentatonic frequency to in, in Hz.",
                    """
                    Rungler.stepped --> Map Range(-1..1 to 200..800) --> Quantizer Major Pentatonic.in
                    Random(min 100, max 2000).out --> Quantizer Major Pentatonic.in --> Phasor.frequency"""),
            new Article("Utility", "Quantizer Minor Pentatonic",
                    "Snaps a pitch to the nearest note of a 5-note minor pentatonic scale.",
                    "Same Hz-in, Hz-out snapping as Quantizer Chromatic, restricted to the 5 notes of a "
                    + "minor pentatonic scale built on root - the blues/rock lead-guitar scale, and the "
                    + "minor equivalent of Quantizer Major Pentatonic's forgiving, always-consonant "
                    + "behavior.",
                    List.of(
                            port("in", "the frequency in Hz to snap to the nearest pentatonic note"),
                            port("root", "a frequency in Hz whose pitch class sets the pentatonic "
                                    + "scale's tonic - only which of the 12 note names it is matters, "
                                    + "not the octave")),
                    CONTROL_NOT_AUDIO + " The nearest minor-pentatonic frequency to in, in Hz.",
                    """
                    Rungler.stepped --> Map Range(-1..1 to 200..800) --> Quantizer Minor Pentatonic.in
                    LFO Triangle(frequency 0.2) --> Map Range(outMin 50, outMax 400) --> Quantizer Minor Pentatonic.in"""),
            new Article("Patches", "Load patch as node...",
                    "Turns an entire saved patch into a single reusable node - a subpatch.",
                    """
                    Opens a file picker and loads a previously-saved .ptr patch as one node in the \
                    current graph. Any macro the saved patch had becomes an ordinary input port on this \
                    node, and its Audio Out becomes the node's single output - so a whole instrument or \
                    effect chain you built once can be dropped into a bigger patch and wired up exactly \
                    like any single built-in node.

                    "New Subpatch" in this same menu places an empty one instead of loading a file - just \
                    an Audio Out, ready to be filled in.

                    However it was created, a subpatch's "Edit" header button opens its contents in this \
                    same graph editor, in place of the parent graph, with a "Done Editing Subpatch" button \
                    above the canvas to return. Anything you do while inside - adding nodes, wiring, \
                    exposing macros - is folded back into the parent patch's copy of the node the moment \
                    you click Done; Save Patch... and Open Patch... work as usual while nested, so a \
                    subpatch can also be saved out to its own file or have a different saved patch swapped \
                    into it, independently of the patch it lives inside.

                    The loaded patch is embedded by value into whatever you save afterward, so the \
                    resulting file still opens on its own even without the original patch file present.

                    Any MIDI Input, Virtual Line Out, or Stereo Virtual Line Out node inside the loaded \
                    patch is muted rather than embedded live - it keeps its place in the graph (so wires \
                    to and from it still resolve) but never opens a device or produces anything but \
                    silence, since a live device connection made in the original standalone patch has no \
                    business running once that patch is just one node inside a bigger one.

                    An Annotation node placed inside a subpatch has nothing to wire up, so it never shows \
                    up as a port here - instead, its text is collected into a "Notes" section on this same \
                    documentation page whenever you open the "?" for that particular subpatch node.""",
                    List.of(port("(one per macro in the loaded patch)", "each macro the saved patch had "
                            + "becomes its own input port here, named after that macro - the exact "
                            + "ports depend entirely on which patch you load")),
                    "Whatever the loaded patch's own Audio Out produced - typically audio, but exactly "
                            + "as complex as the patch you loaded.",
                    """
                    Load patch as node... ("Pluck.ptr") --> Delay(time .2, feedback .3) --> Audio Out.in
                    Sequencer.out --> Load patch as node... ("Lead Synth.ptr").pitch
                    Envelope.out --> Load patch as node... ("Lead Synth.ptr").gate"""),
            new Article("Utility", "Annotation",
                    "A free-form text box for leaving notes in a patch - no inputs, no outputs, no effect on sound.",
                    """
                    Annotation has no ports and does nothing when the patch plays - it exists purely so a \
                    patch can document itself. Its body is a plain text box, resized like any other node \
                    by dragging its corner handle.

                    Dropped directly in a patch you open in the graph editor, it just sits there as a \
                    visible note. The same is true one level in: if that patch is itself opened for \
                    editing as a subpatch (see Load patch as node...), the Annotation node is still an \
                    ordinary node on the canvas.

                    It earns its keep at the third level - once the patch is used as a node rather than \
                    edited directly, its individual nodes (Annotation included) disappear from view behind \
                    the single subpatch box. Every Annotation node's text found at that patch's own top \
                    level is collected into a "Notes" section on the subpatch node's own documentation \
                    page, so whatever you wrote is still one "?" click away - a note left inside a nested \
                    subpatch-of-a-subpatch stays on that inner node's own documentation instead, one level \
                    down.""",
                    List.of(),
                    "Nothing - Annotation has no output port.",
                    """
                    Annotation("Sidechain input expects a gate signal, not raw audio.")
                    (placed anywhere on the canvas, wired to nothing)"""));

    private static final Map<String, Article> NODE_ARTICLES = buildNodeArticles();

    private static Map<String, Article> buildNodeArticles() {
        Map<String, Article> map = new LinkedHashMap<>();
        for (Article article : NODE_ARTICLE_LIST) {
            map.put(article.title(), article);
        }
        return map;
    }

    private static final List<Article> GUIDE_ARTICLES = List.of(
            new Article(GUIDE_CATEGORY, "Adding & Connecting Nodes",
                    "Right-click to add a node, then drag between ports to wire them together.",
                    """
                    Right-click anywhere on the empty canvas to open a categorized menu of every node \
                    type. Selecting one places it at the click point.

                    To wire two nodes together, press and drag from an output port (on a node's right \
                    edge) to an input port (on the left edge) of another node, then release. Dropping a \
                    wire on empty canvas instead of a port opens the add-node menu at the drop point and \
                    automatically wires the new node's first input to what you dragged from - a quick \
                    way to build a chain without aiming at small port circles each time.

                    Right-click an input port to delete whatever wire is plugged into it.""",
                    List.of(), "",
                    """
                    Right-click empty canvas -> Oscillators -> Sine
                    Drag from Sine's output circle -> drop on Audio Out's input circle"""),
            new Article(GUIDE_CATEGORY, "Moving, Resizing & Selecting Nodes",
                    "Drag a node's header to move it; drag its corner to resize it.",
                    """
                    Drag a node by its title header to move it around the canvas. A small handle in the \
                    node's bottom-right corner resizes it - useful for nodes with a formula editor or \
                    many ports, which can grow to show more at once.

                    Click a node or a wire to select it (shown with a highlighted border), and press \
                    Delete or Backspace to remove whichever is selected. Right-clicking a node's header \
                    offers the same delete action from a menu.""",
                    List.of(), "",
                    "Drag an Expression node's bottom-right handle downward to see more of its formula box at once."),
            new Article(GUIDE_CATEGORY, "Navigating the Canvas",
                    "Zoom with Ctrl+scroll or the header buttons, jump back with Center View, and tidy a messy patch with Organize.",
                    """
                    Hold Ctrl and scroll over the canvas to zoom in or out, centered on wherever the \
                    cursor is - useful for zooming into a tight cluster of ports without losing your \
                    place. The header's "−" and "+" buttons zoom by the same steps, centered on the \
                    middle of the visible area instead, and the percentage between them shows the \
                    current zoom level.

                    Click "Center View" to scroll the canvas so the whole patch is centered in the \
                    window, at the current zoom level - handy after zooming or panning away from the \
                    graph. The canvas itself has no fixed size: it grows in every direction whenever a \
                    node goes past its edge, and dragging a node, wire or resize handle to the edge of \
                    the window scrolls the view and keeps expanding the work area for as long as you \
                    hold it there, so a big patch never runs out of room.

                    Click "Organize" (or View > Organize Layout) to tidy a messy patch: nodes are \
                    arranged into left-to-right columns following the signal flow, with sources on \
                    the left and the output on the right, and the view is centered on the result. \
                    It is a single undo step, so Ctrl+Z puts every node back where it was.""",
                    List.of(), "",
                    "Ctrl+scroll in on a cluster of filter nodes to wire their ports precisely, "
                            + "then click Center View to zoom back out to the whole patch."),
            new Article(GUIDE_CATEGORY, "Knobs & Live Values",
                    "Every unwired input port gets its own slider so you can set a value without a Constant node.",
                    """
                    Every input port on a node shows its own slider-and-number-field row beneath the \
                    node, seeded to that port's default value. Drag the slider or type a number to set \
                    it directly - no separate Constant node is needed just to give a port a fixed value.

                    Dragging a real wire into that same port automatically takes over from the slider, \
                    which greys out to show it's no longer in control. Deleting that wire hands control \
                    back to the slider, restoring whatever value it last showed.""",
                    List.of(), "",
                    "Place a Biquad Lowpass with nothing wired to cutoff - drag its cutoff slider and listen to the tone change live."),
            new Article(GUIDE_CATEGORY, "Macros",
                    "Promote any knob to a named slider in the side panel, for the controls you use most.",
                    """
                    Every knob row (and every Constant node) ends in a small button - a "+" if it isn't \
                    a macro yet. Click it and give it a name to promote that knob into the macro panel \
                    on the right side of the editor, where it shows up as its own labeled slider \
                    alongside every other promoted control.

                    Once promoted, the button shows the macro's name and opens a menu to rename it, \
                    change its slider's range, or remove it again. A macro whose target port has since \
                    been disconnected or deleted is labeled "not driving anything" and its slider is \
                    disabled, rather than silently doing nothing.""",
                    List.of(), "",
                    "Click the + next to Biquad Lowpass.cutoff -> name it \"Tone\" -> it now shows up as its own slider in the macro panel."),
            new Article(GUIDE_CATEGORY, "Undo & Redo",
                    "Every structural edit can be undone - adding, deleting, wiring, moving, and macro changes.",
                    """
                    The Undo and Redo buttons above the canvas step backward and forward through every \
                    add, delete, connect, disconnect, move, and macro change you've made, labeled with \
                    what each step will undo (for example "Undo delete Envelope"). The usual platform \
                    shortcut (Ctrl+Z / Cmd+Z, plus Shift for redo) works too, as long as focus isn't \
                    inside a text field.

                    Undoing a delete restores the exact same node you deleted, with its state intact - \
                    it is not rebuilt from scratch, so a delayed effect or a running oscillator keeps \
                    its place rather than resetting.""",
                    List.of(), "",
                    "Delete a node by accident -> press Ctrl+Z (Cmd+Z on Mac) -> it comes back exactly as it was."),
            new Article(GUIDE_CATEGORY, "Playback & Rendering to WAV",
                    "Play/Stop audition your patch live; Render to WAV... exports it to a file.",
                    """
                    Play builds a patch from the current graph and starts it playing from the \
                    beginning; Stop halts it. While playing, any further edit to the graph is picked up \
                    immediately - the running patch is rebuilt and swapped in on the fly, so you can \
                    tweak knobs and wiring while listening.

                    Render to WAV... asks for a duration and export settings (sample rate, bit depth, \
                    channels, normalize, trim silence), then writes the current patch to a file offline, \
                    without needing to listen to it play out in real time first.""",
                    List.of(), "",
                    "Press Play -> drag a filter's cutoff slider while it's still playing -> the sound updates immediately."),
            new Article(GUIDE_CATEGORY, "Saving & Loading Patches",
                    "Save Patch... and Load Patch... store and restore an entire graph as a .ptr file.",
                    """
                    Save Patch... writes the whole current graph - every node, its position on the \
                    canvas, every connection, every knob value, and every macro - to a single .ptr \
                    file. Load Patch... reads one back, replacing whatever is currently on the canvas.

                    A .ptr file is fully self-contained: an imported sample or another patch used as \
                    a node is embedded directly in the file rather than referenced by path, so the file \
                    still opens correctly even without the original WAV or nested patch file present. \
                    Samples are compressed losslessly, so saving never changes how a sound plays.""",
                    List.of(), "",
                    "Build an instrument -> Save Patch... as Lead.ptr -> later, Load Patch... to pick up exactly where you left off."),
            new Article(GUIDE_CATEGORY, "Diesel Scripts",
                    "Write or read a patch as Diesel, a C-like text format, and render it from the command line.",
                    """
                    Diesel is Petroleum's text format for patches. Pick the "Diesel scripts" filter (or \
                    just type a name ending in .dsl) in Save Patch... to write the current graph as text, \
                    and open a .dsl file with Load Patch... or Load patch as node... exactly like a .ptr. \
                    Nothing is lost either way: samples and other imported data are embedded as base64 \
                    blocks at the end of the file, so a .dsl opens on its own just like a .ptr does.

                    A script is one patch block holding node declarations (a node type, then a name, \
                    then an optional body in braces), connections written source.port -> destination.port, \
                    and macros. Inside a node body, name = value; sets a construction field such as a \
                    position or an oscillator, and .port = number; sets an unconnected input's knob. \
                    Comments use // and /* */. Nodes must be declared before a connection or macro names \
                    them, and every error message gives the line and column.

                    From a terminal, petroleum render song.dsl -o song.wav renders a script straight to \
                    WAV without opening the editor; add --duration for a patch that plays forever, and \
                    --rate, --bits, --channels, --normalize, --trim for the same settings as Render to WAV...""",
                    List.of(), "",
                    """
                    patch "Drone" {
                        "LFO Sine" lfo { .frequency = 220; }
                        "Audio Out" out;
                        lfo.out -> out.in;
                    }

                    petroleum render Drone.dsl -o Drone.wav --duration 3"""),
            new Article(GUIDE_CATEGORY, "Quick Sounds Screen",
                    "Generate a ready-made sound effect from a category and a seed, without building a graph.",
                    """
                    The Quick sounds... screen generates classic sound-effect categories (pickups, \
                    lasers, explosions, and more) from a seed number, with one slider per adjustable \
                    parameter. Mutate nudges the current sound into a related variation; Play auditions \
                    it; Export WAV... saves the current sound, and Export all rolls... batch-exports \
                    everything in the roll history at once.

                    Open in graph editor -> takes the currently generated sound into the full node \
                    graph as an ordinary editable patch - the only way back out of this screen, since \
                    switching to it clears whatever was previously on the canvas.""",
                    List.of(), "",
                    "Quick sounds... -> pick \"Explosion\" -> Mutate a few times -> Open in graph editor -> to keep tweaking it by hand."),
            new Article(GUIDE_CATEGORY, "Patches as Nodes",
                    "Build an instrument or effect once, save it, and reuse it as a single node elsewhere.",
                    """
                    Any saved .ptr patch can be placed as a single node in another patch through the \
                    Load patch as node... entry in the add-node menu. Every macro the original patch had \
                    becomes an ordinary input port on the new node, and its Audio Out becomes the node's \
                    one output - so a complex instrument or effect chain you've already built and named \
                    can be wired up exactly like any built-in node, without rebuilding it by hand.""",
                    List.of(), "",
                    "Save a lead-synth patch as Lead.ptr -> in a new patch, Load patch as node... -> drop the whole instrument in as one box."));

    public static List<Article> nodeArticlesByCategory() {
        List<Article> ordered = new ArrayList<>();
        for (String category : NodeCatalog.CATEGORY_ORDER) {
            for (Article article : NODE_ARTICLE_LIST) {
                if (article.category().equals(category)) {
                    ordered.add(article);
                }
            }
        }
        return ordered;
    }

    public static List<Article> guideArticles() {
        return GUIDE_ARTICLES;
    }

    public static Optional<Article> nodeArticle(String key) {
        return Optional.ofNullable(key == null ? null : NODE_ARTICLES.get(key));
    }

    public static String shortDescription(String key) {
        Article article = key == null ? null : NODE_ARTICLES.get(key);
        return article == null ? "No documentation is available for this node yet." : article.summary();
    }

    public static String keyFor(NodeFactory nodeFactory, Node node) {
        String factoryKey = nodeFactory.nameOf(node);
        if (factoryKey != null) {
            return factoryKey;
        }
        if (node instanceof PatchNode) {
            return "Load patch as node...";
        }
        if (node instanceof GranularPlayerNode) {
            return "Granular Player";
        }
        if (node instanceof NoteNode) {
            return "Note";
        }
        if (node instanceof TrackerNode) {
            return "Tracker";
        }
        if (node instanceof MidiPlayerNode) {
            return "MIDI Player";
        }
        if (node instanceof PianoRollNode) {
            return "Piano Roll";
        }
        if (node instanceof InstrumentNode) {
            return "Instrument";
        }
        if (node instanceof AdditiveResynthNode) {
            return "Additive Resynth";
        }
        if (node instanceof OscillatorNode oscillatorNode) {
            IOscillator oscillator = oscillatorNode.getOscillator();
            if (oscillator instanceof DualOscillator) {
                return "Dual Oscillator";
            }
            if (oscillator instanceof Wavefolder) {
                return "Wavefolder";
            }
            if (oscillator instanceof ExpressionOscillator) {
                return "Expression";
            }
            if (oscillator instanceof Wavetable) {
                return "Sound Clip";
            }
            if (oscillator instanceof DrawnWaveform) {
                return "Draw Waveform";
            }
            return oscillator.getName();
        }
        return null;
    }
}
