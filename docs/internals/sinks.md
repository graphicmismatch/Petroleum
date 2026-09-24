# Sinks (`Petroleum.Sinks`)

`Node` implementations for the [Graph](graph.md) package - a sibling of
`Petroleum.Oscillators`, not nested under `Graph`. Nodes here terminate a
patch rather than producing or transforming a signal for further use.

Both node types below implement the marker interface **`AudioSink`** (no
methods). [`Patch`](patch.md) requires exactly one node implementing
`AudioSink` among the nodes it's given, mono or stereo, or throws
`IllegalStateException` - the "exactly one sink" rule is enforced against the
interface, not against either concrete class, so adding a sink type never
means touching that check again.

- **AudioOutNode** - one `in` input port, no output ports. Marks the point
  in a patch where mono audio leaves the graph. `Patch.renderBlock`/
  `renderNested` read the block fed into `in` as the patch's rendered
  samples; `process()` itself still does nothing, now taking
  `float[port][sample]` block-shaped `inputs`/`outputs` and a `frameCount`
  like every other node, per the [Graph](graph.md) `Node` contract.
  Registered in `NodeFactory` as `Audio Out`.
- **StereoAudioOutNode** - two input ports, `inL`/`inR`, no output ports.
  The stereo counterpart: marks the point in a patch where a genuinely
  two-channel signal leaves the graph. `Patch.renderBlockStereo`/
  `renderNestedStereo` read `inL`/`inR` as the patch's left/right rendered
  samples - see [Patch](patch.md) for how the render method a caller must
  use is determined by which sink the patch was built with, and
  [Audio](audio.md) for how `AudioEngine` picks between them.
  [Mixers](mixers.md)'s `PannerNode`/`StereoMixNode` are the two nodes meant
  to feed one of these. Registered in `NodeFactory` as `Stereo Audio Out`.

## Virtual line outs

**VirtualLineOutNode** (one `in` port) and **StereoVirtualLineOutNode**
(`inL`/`inR`) are a second family of terminal nodes, registered as
`Virtual Line Out`/`Stereo Virtual Line Out`. Unlike `AudioOutNode`/
`StereoAudioOutNode` they do **not** implement `AudioSink` - `Patch`'s
"exactly one `AudioSink`" rule never sees them, so a patch keeps its one
real `AudioOutNode`/`StereoAudioOutNode` and may additionally hold any
number of virtual line outs feeding separate virtual audio cables (a
loopback/virtual-cable device already installed on the machine, the kind
other software - a DAW, a streaming/recording app, a conferencing app -
reads as an ordinary line input).

Both implement the small **`VirtualLineOut`** interface
(`getMixerName()`/`setMixerName(String)`/`isStereo()`, plus the static
`listAvailableMixerNames()` that enumerates every `javax.sound.sampled`
`Mixer` exposing a `SourceDataLine`) - one shared contract for [Gui](gui.md)'s
"Line..." header button, [`Patch`](patch.md)'s `virtualLineOutNodes()`
collection, and [Audio](audio.md)'s `AudioEngine`, none of which need to
know or care which of the two concrete classes they're holding. `process()`
on both does nothing, exactly like `AudioOutNode`/`StereoAudioOutNode` -
the node itself never touches a `SourceDataLine`; only `getMixerName()`'s
current value is read, from `AudioEngine`'s own playback thread, which owns
opening/writing/closing the actual line (see [Audio](audio.md)). Not
`Stateful`: which virtual device is chosen is live session state, the same
deliberate choice `Sources.MidiInputNode` makes for its device (see
[Sources](sources.md)) - reopening a saved patch needs it picked again.

`listAvailableMixerNames()` only enumerates devices the OS already knows
about - `javax.sound.sampled` has no API to register a new one, so Petroleum
itself never creates the loopback device, only writes to one that already
exists. Per-OS setup steps are written out in full in the `Docs.java`
"Virtual Line Out" article body, reachable from its "?" button in the
running app - kept there rather than duplicated here since it's end-user
setup instructions, not codebase design rationale. One correction worth
recording here because it was wrong in an earlier pass and easy to
re-assume: on Linux, `javax.sound.sampled`'s device enumeration only walks
real ALSA sound cards (confirmed empirically - `AudioSystem.getMixerInfo()`
lists `hw:0`..`hw:3` plus one generic PulseAudio-bridge "default" entry, and
picks up neither a `pactl`-created PulseAudio/PipeWire null-sink nor a
custom named PCM alias for one added to `~/.asoundrc`). The loopback device
therefore has to be the `snd-aloop` kernel module (a real, if virtual, ALSA
card), not a PulseAudio/PipeWire sink - the sink trick only helps route
between two PulseAudio-aware applications, never into Petroleum.

## RecordingEndNode

One `trigger` input, no outputs, and - like the other nodes in this package -
a `process()` that writes nothing. It is a sink in the same sense
`AudioOutNode` is: a terminal node whose whole job is to be *noticed* by
whoever is driving the patch, not to produce a signal.

What it is noticed by is [Audio](audio.md)'s offline render. The first rising
edge on `trigger` (the ordinary convention-8 check, `previous <= 0 && value >
0` - a trigger here is a plain sample like anywhere else) records the sample
offset *within the current block* at which it fired, and the render loops cut
their output there. So a Recording End node makes the requested duration a
**ceiling rather than the length**: a trigger at 0.5s in a 10s render writes
0.5s, and a trigger at 8s in a 5s render writes the full 5s, since the node
can only end a render early, never extend one. Ending on the exact sample
rather than at the end of the 1024-frame block it fell in is why the node
reports an offset instead of a bare `boolean` - a block boundary would leave
up to 23ms of audio past the trigger.

The marker interface is `RecordingEnd` (`int endedOffset()`, -1 until it
fires), a sibling to `AudioSink`/`VirtualLineOut` and deliberately **not**
`Graph.OneShot`. `Patch.isFinished()` is an *all* of the one-shots: a patch
holding both an envelope and a Recording End would then only stop once both
agreed, which is exactly what "ends the recording no matter what else is
still running" must not do. `Patch` collects `RecordingEnd[]` in the same
constructor pass that collects `oneShots`/`virtualSinks`, and exposes
`hasRecordingEnd()` plus `recordingEndOffset()` - the *earliest* offset any of
them reported, so several Recording End nodes in one patch behave as an "or"
rather than needing a single privileged one. It lives in `Sinks` rather than
`Conditionals`, where the trigger-driven nodes otherwise live, only because
`Patch` already imports `Sinks` to find its audio sink - putting it in
`Conditionals` would have added a new `Patch` -> `Conditionals` edge for one
`instanceof`.

Per-session state follows convention 9 exactly, and this is the node's one
real hazard: node instances outlive a `NodeContext`, so a bare `private
boolean ended` would spend itself on the first render and make every
*subsequent* export write an empty file - the same class of bug that once made
Render-to-WAV write silence after a Play. It detects a new session the way
`TriggerNode` does, by comparing `context.sampleIndex()` against where its own
previous block ended, and resets both `endedOffset` and its
previous-trigger memory there. `endedOffset` is `volatile` because the render
loop reads it from outside `process()`.

Two deliberate scope limits, both worth knowing before filing a bug:

- **Live playback ignores it.** `AudioEngine.runPlaybackLoop` never reads
  `recordingEndOffset()`. Play is not a recording, so there is nothing for the
  node to end; it fires, sets its offset, and nothing looks.
- **It only ends a render from the top-level patch.** A Recording End inside
  a [`Modules`](modules.md) `PatchNode` is invisible to the outer render,
  since the outer loop asks the outer `Patch` for its own `RecordingEnd[]`,
  and `renderNested` does not advance the shared context anyway.
