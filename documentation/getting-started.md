# Getting started

This walks through launching Petroleum and building a sound from an empty
canvas, then playing and exporting it.

## Launching

From a checkout:

```
mvn compile
mvn javafx:run
```

The first build has to run online so the JavaFX libraries download. After
that, `mvn -o compile` works without a network.

To install Petroleum properly instead, build a native installer for your
platform and run it. This bundles a Java runtime, so the installed application
does not need a JDK:

```
mvn -Pnative-linux package
```

Use `-Pnative-windows` or `-Pnative-mac` on those systems. The installer is
written to `target/dist/`.

## The fastest sound

Petroleum opens on the graph editor with an empty canvas. If you want a
finished sound immediately rather than building one, press **Quick sounds...**
in the toolbar, or **File > Quick Sounds**. Pick a category such as Pickup,
Laser or Explosion and roll until you like something.

What you get back is a complete patch, not a preset. Open it in the graph
editor and every part of it is there to be pulled apart, which makes this a
good way to learn how patches are put together.

## Building a sound from scratch

A patch is a graph. Nodes make or change a signal, and wires carry that signal
between them. Every patch ends at a single **Audio Out** node, which is what
you hear.

### Add the nodes

Right-click anywhere on the canvas to open the node menu. It is organised into
categories: Sources & Sinks, Envelopes, Oscillators, Oscillator Wrappers,
Filters, Effects, Granular, Instruments, Physical, Spectral, Conditionals,
Tracker, Mixers, Utility and Patches.

Add three nodes:

1. **Sources & Sinks > Phasor**
2. **Oscillators > Waveforms > Sine**
3. **Sources & Sinks > Audio Out**

### Understand the shape of a node

Each node is a small panel. Its name is in the header, with a **?** button that
opens the built-in documentation for that node type. Input ports run down the
left side, output ports down the right. Any input that has nothing wired into
it gets its own slider, so you can set a fixed value without adding a node just
to hold a number.

Look at the Sine node and note that its only input is **phase**, not
frequency. This is the one thing that surprises people first. An oscillator in
Petroleum is a pure waveform: hand it a phase and it gives you back the shape
of the wave at that point. Nothing about it knows how fast to run.

That is the job of the **Phasor**, which takes a **frequency** and produces a
phase that sweeps continuously. Separating the two is what lets any waveform be
driven by any source of phase, and it is why you can modulate pitch by
modulating one node rather than every oscillator.

### Wire them together

Drag from an output port to an input port:

- Phasor **out** to Sine **phase**
- Sine **out** to Audio Out **in**

Set the Phasor's **frequency** slider to 220 and press **Play** at the bottom
left. You should hear a 220 Hz sine tone. **Stop** ends playback.

### Turn it down

That tone plays at full scale, which is louder than you want and leaves no
headroom once you add anything else. Add a **Utility > Amplifier** between the
Sine and the Audio Out, wire Sine **out** to Amplifier **in** and Amplifier
**out** to Audio Out **in**, and set its **gain** to something like 0.3.

## The same patch as text

Every patch also has a text form, Diesel. The patch just built is:

```c
patch "First Sound" {
    Phasor osc { .frequency = 220; }
    Oscillator tone { oscillator = { kind = "factory", name = "Sine" }; }
    "Audio Out" out;

    osc.out -> tone.phase;
    tone.out -> out.in;
}
```

Save it as `first.dsl` and render it without opening the editor at all:

```
java -cp target/classes Petroleum.Main render first.dsl -o first.wav --duration 2
```

The two forms are the same patch. You can build visually and save as text, or
write text and open it in the editor. See the
[Diesel language guide](../DIESEL.md) for the full syntax.

## Saving and exporting

**File > Save Patch...** (Ctrl+S) writes a `.ptr` file, which holds the patch
and any samples it uses, so it opens anywhere on its own. Choosing a `.dsl`
filename saves the text form instead.

**File > Export to WAV...**, or the button at the bottom of the window, renders
what you have to an audio file. See [Exporting](exporting.md) for the settings
and for rendering in bulk.

## Where to go next

- [The graph editor](editor.md) - working at speed: moving around, macros,
  undo, and the full keyboard shortcut list
- [Nodes by purpose](nodes.md) - what is in each category and when to reach
  for it
- [Diesel language guide](../DIESEL.md) - the text format and the complete
  node reference
