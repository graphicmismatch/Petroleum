# Audio (`Petroleum.Audio`)

`AudioEngine` is the only place the project touches real audio I/O, and it's
built entirely on `javax.sound.sampled` - part of the JDK, no native
dependency - which is what makes playback and rendering work unmodified on
Windows, macOS, and Linux.

Output format throughout is fixed: 44100 Hz, 16-bit signed PCM,
little-endian, stereo. Whether the two channels carry a genuinely
independent signal depends on the patch's sink: a plain
[`AudioOutNode`](sinks.md) only ever produces one mono signal
([`Patch.renderBlock`](patch.md)), which `AudioEngine` duplicates to both
channels when converting to bytes, exactly as it always has; a
[`StereoAudioOutNode`](sinks.md) produces real, independent left/right
signals (`Patch.renderBlockStereo`), which `AudioEngine` interleaves as two
distinct channels instead of duplicating. `Patch.isStereo()` is what
`AudioEngine` checks to pick between the two - see "Stereo playback and
export" below. Either way, samples are clamped to `[-1, 1]` before scaling
to avoid wraparound artifacts if a patch overshoots that range. The
clamp-then-scale-by-32767 step is symmetric across the full `[-1, 1]` range
but deliberately sacrifices the last representable code (`-32768`) - scaling
by 32768 instead would clip asymmetrically (peak-negative samples would
round to a different magnitude than peak-positive ones); one LSB of range is
a cheap, considered trade for that symmetry, so this is kept as-is rather
than "fixed".

The realtime playback loop's PCM encoding (`writeStereoPcm`) is a thin
wrapper around the same private `encodeFrame` the offline export path uses -
the clamp-and-scale logic above exists in exactly one place rather than once
per path. Two overloads share the name: the single-`float[]` one duplicates
one mono sample across both channels, called once per sample against a
cached `ExportSettings.defaults()` (44100 Hz, 16-bit, stereo); the
two-`float[]` (`left`, `right`) one calls `encodeFrame` twice per sample
frame against a cached single-channel `ExportSettings`
(`PLAYBACK_CHANNEL_SETTINGS`), once per channel, so each channel's bytes come
from that channel's own buffer instead of a shared one. `runPlaybackLoop`
picks which overload to call each block based on `patch.isStereo()` - see
"Stereo playback and export" below.

`renderMono(Patch, double durationSeconds, float sampleRate)` - the
buffered-export accumulate-into-one-`float[]` loop - is `public` for the
same reason [`Gui.QuickView`](gui.md)'s offline preview render calls it
directly instead of carrying its own copy: build a `Patch`, render it in
`BLOCK_SIZE`-sample chunks into one buffer, return it. The two versions
differed in one detail - `QuickView`'s always rendered a full
`BLOCK_SIZE`-sample block even on the last, shorter chunk, then copied out
only the samples actually needed, where `renderMono` renders exactly the
remaining sample count - which made no observable difference (the discarded
tail samples never affected the kept ones, and each caller's `NodeContext`
is local and thrown away right after), so unifying onto `renderMono`'s
version is a pure simplification. `renderStereo(Patch, double
durationSeconds, float sampleRate)` is the same loop's stereo counterpart,
returning `float[][] {left, right}` via `Patch.renderBlockStereo` instead of
one array via `renderBlock` - used by `renderToWavStereo` below, and
available for any future stereo offline-preview caller the same way
`renderMono` already serves `QuickView`.

`AudioEngine`'s internal `BLOCK_SIZE` constant is defined as
`Patch.DEFAULT_BLOCK_CAPACITY` rather than a separately-maintained literal,
so the audio engine's block size and the capacity `Patch` allocates its
block-aliased buffers for (see [Patch](patch.md)) can never drift apart -
`AudioEngine` never calls `renderBlock` with more frames than a
default-constructed `Patch` can accept.

- **`play(Patch patch)`** - opens a `SourceDataLine` and starts a dedicated
  daemon thread that repeatedly renders `BLOCK_SIZE`-sample blocks and writes
  them to the line, keeping one `NodeContext` alive for the whole session.

  The line is opened with an **explicit** buffer size, `lineBufferBytes(bits)`
  (`BLOCK_SIZE * bufferBlocks` frames) - never the bare `line.open(format)`
  overload. `SourceDataLine.open(AudioFormat)` without a size leaves the
  buffer to the mixer's own default, which on several Java Sound backends is
  sized for underrun-proofing rather than latency and can end up hundreds of
  milliseconds deep; since `write()` blocks for backpressure and the playback
  loop always keeps the line topologically full, the steady-state gap
  between a control change (a keyboard note in the [Tracker](tracker.md)
  editor, a knob drag, a MIDI-driven pattern) and hearing it is *exactly*
  however deep that buffer is - an unspecified buffer size is an
  unspecified, mixer-dependent input-to-output delay.

  `bufferBlocks` (`setBufferBlocks`/`getBufferBlocks`, 1-8, default 2 -
  `SynthApp`'s "Latency..." button, persisted to `Preferences` under
  `bufferBlocks`) is a user-facing tradeoff rather than a fixed constant,
  because the right value is a property of the machine the sound is played
  on, not of the patch: two blocks is enough headroom on most machines that
  the playback thread (which does real DSP work and can occasionally be
  preempted) doesn't underrun, while staying close to `BLOCK_SIZE`'s own
  ~23 ms floor at 44.1 kHz rather than adding a second, invisible latency
  source on top of it - but a faster machine can safely drop to one block,
  and a machine seeing underrun crackle under load needs more than two. This
  setting cannot change the OS-level floor on Windows (`javax.sound.sampled`
  there sits on DirectSound emulated over WASAPI shared mode, which adds its
  own fixed latency no buffer request can shrink); it only controls the part
  of the latency budget this engine actually owns. Calling
  `play` again while already playing does **not** reopen the line or start a
  second thread - it atomically swaps a `volatile Patch currentPatch` field
  that the playback thread re-reads once per block, so the [Gui](gui.md)
  package can hot-swap in a freshly rebuilt patch after every graph edit
  without an audible restart. See [Patch](patch.md) for why rebuilds reuse
  the same node instances rather than reconstructing them - the swap here is
  only safe to sound seamless because of that. Between patches (`currentPatch
  == null`, e.g. right after `play()` starts the thread but before the first
  patch has landed) the loop parks for 1 ms via
  `LockSupport.parkNanos(1_000_000L)` instead of busy-spinning, so an idle
  playback thread doesn't peg a core.

  After writing each block to the main line, the loop also calls
  `writeVirtualLines` - see "Virtual line outs" below. This only happens on
  the realtime playback path; `renderToWav`/`renderMono`/`renderStereo` never
  touch a virtual line, since exporting a file at faster-than-realtime speed
  into a virtual cable meant for another application to read live would be
  meaningless.
- **`restart(Patch patch)`** - the same swap, plus a **reset of the session**:
  the playback thread drops its `NodeContext` for a fresh one and calls
  `SourceDataLine.flush()`. This is what the Play buttons use, so pressing Play
  while a sound is already running starts it over instead of doing nothing
  audible. `play` and `restart` are the two halves of one distinction: an edit
  should not retrigger the sound, and pressing Play should.

  A new `NodeContext` puts `sampleIndex` back to 0, which is the whole
  mechanism - [convention 9](conventions.md) has every "once per sound" node key
  off that clock rather than off its own lifetime, so a `TriggerNode` sees a
  discontinuity and re-arms, and the envelopes behind it fire again. Nothing
  needs a re-arm call. `flush()` discards audio already queued in the line, so
  the restart is heard immediately rather than after the buffer drains.

  The request is a `volatile boolean` the playback thread reads at the top of
  its loop, not work done on the caller's thread: `currentPatch` is written
  before the flag, so a thread that sees the flag necessarily sees the new
  patch. `restart` on a stopped engine is just `play`.
- **`stop()`** - flags the playback thread to exit, joins it, then drains,
  stops, and closes the line. A no-op if nothing is playing. `stop` then `play`
  would also restart a sound, but it tears down and reopens the line and
  `drain()` waits for the queued audio to finish playing first, so it is the
  wrong tool for a Play press - `restart` keeps the line open and skips that
  wait.
- **`renderToWav(Patch patch, double durationSeconds, File file)`** - fully
  offline: needs no output device, only `AudioSystem.write`, so it also
  works in headless environments where `play()` cannot. It streams rather
  than buffering the whole render in memory: a private `RenderingStream`
  (`InputStream`) holds the `Patch`, a `NodeContext`, and reusable
  mono-float/PCM-byte scratch buffers sized to one `BLOCK_SIZE`-frame block,
  and renders the next block on demand from `read(byte[], int, int)` - only
  when the previously-rendered block's bytes have all been consumed -
  tracking `framesRemaining` as a `long` and returning `-1` once it hits
  zero; the final block is whatever's left, which may be shorter than
  `BLOCK_SIZE` frames.
  This is wrapped in `new AudioInputStream(stream, format, totalSamples)` and
  handed to `AudioSystem.write` exactly as before. Two things this fixes
  over the old "render everything into one `byte[]` first" approach: memory
  use is now flat (one block's worth of scratch space) regardless of
  duration, and there's no `int` truncation of `totalSamples * bytesPerFrame`
  or of a running `byte[]` write offset - both of which silently corrupted
  or crashed renders past roughly 3.4 hours at 44.1 kHz stereo 16-bit.
  `AudioSystem.write` is free to request arbitrary byte counts per
  `read` call, so `RenderingStream` keeps a leftover-offset/leftover-length
  pair into its scratch buffer and only renders a fresh block once the
  previous one is fully drained, so partial reads never lose or duplicate
  bytes.

## `ExportSettings`

```java
public record ExportSettings(
        float sampleRate, int bitDepth, int channels,
        boolean normalize, boolean trimSilence) { }
```

`renderToWav(patch, duration, file, settings)` is the four-argument overload;
the three-argument call still exists and delegates to `ExportSettings.defaults()`
(44100 Hz, 16-bit, stereo, no post-processing), so nothing that already called
it changes behaviour.

The record validates in its compact constructor: 8 or 16 bits, 1 or 2 channels,
1 kHz to 192 kHz. Those are the formats a game asset pipeline actually wants,
and rejecting the rest early beats writing a file no decoder will open.

**Sample rate is a render rate, not a resample.** `settings.sampleRate()` builds
the `NodeContext`, so the graph is evaluated at the target rate rather than
rendered at 44.1 kHz and downsampled afterwards. Every node already derives its
coefficients from `context.sampleRate` (convention 3), so an 11 kHz export is a
genuine 11 kHz render with no filtering artefacts of our own. It is also why an
export at a different rate is not sample-identical to the 44.1 kHz one -
oscillator phase lands on different instants.

**Bit depth changes the encoding, not just the width.** 16-bit WAV is signed
little-endian; 8-bit WAV is *unsigned* (0-255, centred on 128), which is the
convention `AudioSystem` and every decoder expects. `formatFor` flips the
`signed` flag accordingly and `encodeFrame` maps the float differently for each.

**Channels**: `settings.channels()` only controls `bytesPerFrame()`/mono-vs-
duplicated encoding for a patch built around a plain `AudioOutNode` - mono
writes each frame once, stereo duplicates it, exactly as playback has always
done for a mono-sink patch. A patch built around a `StereoAudioOutNode`
ignores `settings.channels()` entirely: `renderToWav` always writes a
genuine two-channel file for a stereo-sink patch, since the sink itself
already settled the question of how many independent channels exist. See
"Stereo playback and export" below.

### Render length and `Sinks.RecordingEndNode`

`durationSeconds` is a *ceiling*, not necessarily the length written. If the
patch holds a `Sinks.RecordingEndNode` (see [Sinks](sinks.md)), the first
rising edge on its `trigger` ends the render on that exact sample, and all
four render entry points honour it by asking `Patch.recordingEndOffset()`
after each block and truncating that block to the returned offset:
`renderMono`, `renderStereo`, and `RenderingStream.read` (the buffered,
stereo and streaming paths respectively; `renderBuffered`/`renderToWavStereo`
inherit it from the first two). The trigger can only cut a render short, never
extend one - a trigger at 8s inside a 5s render changes nothing.

Passing `AudioEngine.UNBOUNDED` (0) as the duration means "run until a
Recording End fires". That is what lets the editor's export dialog accept a
blank duration and the `petroleum render` CLI omit `--duration` for a patch
that has no one-shot envelope - previously rejected outright as a patch that
plays forever. It is not literally unbounded: `resolveDuration` substitutes a
ceiling so a trigger that never arrives ends the render instead of filling the
disk or hanging the GUI with no cancel. Both callers check `hasRecordingEnd()`
before allowing a blank duration, so an unbounded render always has something
that can end it.

The ceiling is a setting, not a constant - `setMaxUnboundedSeconds(double)` /
`getMaxUnboundedSeconds()` on the `AudioEngine` instance, the same shape as
`setPlaybackBitDepth`/`setBufferBlocks` (non-positive is rejected outright,
since a zero ceiling would silently make every unbounded render empty).
`DEFAULT_MAX_UNBOUNDED_SECONDS` (600) is only the starting value.
[Gui](gui.md)'s Options > "Render Limit..." menu item picks from 30s to an hour and
persists the choice in `Preferences` under `maxRenderSeconds`, restored at
startup next to the playback bit depth and buffer size - clamped back to the
default there if the stored value is not positive, since `SynthApp.start` is
not wrapped in a `try`/`catch` and a rejected setter would leave the app
unstartable with no way to fix it from inside; the CLI takes
`--max-duration <sec>`. A ceiling can only *stop* a render that would
otherwise run on - it never extends one that ends on its own, since
`resolveDuration` leaves any positive `durationSeconds` untouched and the
Recording End check runs per block regardless.

`renderToWav` resolves the duration **once**, at the top, and passes the
resolved value to `renderToWavStereo`/`renderBuffered`/`frameLimit`, so
everything downstream sees a plain positive number and no other method needs
to know the ceiling exists. The static `renderMono`/`renderStereo` entry
points are the exception - they are called directly by `Gui.QuickView` with no
`AudioEngine` instance in hand, so their own unbounded case falls back to
`DEFAULT_MAX_UNBOUNDED_SECONDS`. That path is unreachable from a normal
export, which always resolves first.

`frameLimit` makes that substitution **only** when the patch actually holds a
Recording End node; a non-positive duration on any other patch still means
zero frames, exactly as it did before this existed. Without that gate, a
`renderMono(patch, 0, rate)` that used to return an empty array would quietly
become a 600-second, 106MB render - no current caller passes 0
(`Generators.SoundGenerator.suggestedDuration` floors at 0.25), but the gate
costs two lines and does not depend on that staying true.

Two details in the WAV writing that this forced, both verified empirically
rather than assumed:

- `AudioSystem.write` **patches the RIFF/`data` sizes after writing** when the
  target is a `File` (it can seek back), so an `AudioInputStream` built with
  `AudioSystem.NOT_SPECIFIED` produces a perfectly correct header - a 4410-frame
  write lands as `getFrameLength() == 4410`. That is what makes the streaming
  path genuinely early-endable with no buffering at all.
- It does **not** correct an *over-declared* length: declaring 441000 frames
  and then supplying 4410 writes the right bytes under a header still claiming
  441000, i.e. a corrupt file. So `renderToWav` passes `NOT_SPECIFIED` instead
  of `totalFrames` whenever `patch.hasRecordingEnd()` - the length is not
  knowable up front in that case, and guessing it is worse than declaring
  nothing.

The buffered paths can no longer preallocate `new float[totalFrames]` either,
since the ceiling may be 600s of frames for a render that ends after half a
second. They start at 64 blocks and grow by doubling (`initialCapacity`/`grow`,
capped at the ceiling), then `Arrays.copyOf` down to what was actually
written. A patch with no Recording End still preallocates exactly
`totalFrames` and never grows, so the common path allocates exactly as before.

### Streaming versus buffering

`normalize` and `trimSilence` both need the whole render before the first byte
can be written: a peak is not known until the end, and neither is where the
trailing silence starts. So `renderToWav` picks a path:

- Neither option set (`needsBuffering()` false) - the streaming `RenderingStream`
  path above, unchanged, with its flat memory use for long renders.
- Either option set - `renderBuffered` renders the whole thing to a `float[]`
  first, post-processes, and writes from a `ByteArrayInputStream`.

Keeping the streaming path is the reason for the split. A game sound effect is
a second or two and buffers fine, but the engine still supports hour-long
renders and those must not suddenly need gigabytes.

**Normalize** scales so the peak hits `NORMALIZE_TARGET` (0.98) rather than
exactly 1.0, leaving a little headroom so rounding into the integer PCM range
cannot clip. A fully silent render is left alone rather than being scaled by
infinity.

**Trim** walks in from both ends while samples sit below `TRIM_THRESHOLD`
(1e-4). Both ends matter: an envelope with a long decay leaves a tail, and a
`TriggerNode` `delay` leaves a head. A render that is silent throughout would
otherwise trim to nothing, so that case is clamped to one frame - a zero-length
WAV is not a valid file.

Trim runs before normalize, so the peak is measured over the audible region.

### Stereo playback and export

Every entry point that renders a `Patch` - `runPlaybackLoop`, `renderToWav` -
checks `patch.isStereo()` once and branches, rather than a stereo-sink patch
silently falling through the mono path and losing a channel:

- **`play`/`restart`**: `runPlaybackLoop` preallocates both a mono scratch
  buffer and a left/right pair up front (no per-block allocation either way,
  per [convention 2](conventions.md)) and picks `renderBlock`+the mono
  `writeStereoPcm` or `renderBlockStereo`+the stereo `writeStereoPcm` each
  time around the loop - so hot-swapping from a mono patch to a stereo one
  (or back) mid-session, the same way any other patch rebuild hot-swaps
  today, works with no special handling.
- **`renderToWav`**: a stereo-sink patch is dispatched to
  `renderToWavStereo` before the existing mono `needsBuffering()` branch is
  even reached - the mono path (streaming `RenderingStream` for the common
  case, `renderBuffered` for normalize/trim) is completely untouched.
  `renderToWavStereo` always renders the whole patch into a `left`/`right`
  pair via `renderStereo` first, then trims/normalizes across both channels
  together (a frame counts as trimmable silence only when *both* channels
  are below `TRIM_THRESHOLD`; normalize's peak is the max magnitude across
  both channels) before interleaving and writing. Unlike the mono path,
  there is currently no streaming variant for stereo - every stereo export
  holds the full render in memory first, even when neither `normalize` nor
  `trimSilence` is set. This trades away the mono path's flat-memory
  long-render guarantee for stereo exports specifically; revisit with a
  stereo `RenderingStream` if hour-long stereo renders become a real use
  case.

### Virtual line outs

A patch's real sink ([`AudioOutNode`/`StereoAudioOutNode`](sinks.md)) is not
the only thing a patch can write to while playing: it may also hold any
number of [`VirtualLineOutNode`/`StereoVirtualLineOutNode`](sinks.md)s, each
feeding a separate virtual audio cable so another application can pick up
the sound (or a submix of it) as a live line input. `Patch.virtualLineOutNodes()`
collects them once, at construction, exactly like the real sink is found -
`AudioEngine` never has to search the graph itself.

Per [convention 2](conventions.md), a `Node`'s own `process()` never blocks, and
opening or writing a `SourceDataLine` both can - so unlike `MidiInputNode`
(which owns its `MidiDevice`/`Transmitter` directly, since a MIDI receiver
callback runs on its own thread already), a virtual line out node holds only
a `mixerName` string. `AudioEngine.writeVirtualLines`, called once per block
from the playback thread right after the main line is written, owns every
actual `SourceDataLine`: a `Map<Node, VirtualLine>` local to
`runPlaybackLoop` (never shared across threads, so it needs no
synchronization of its own) tracks one open line per sink node, keyed by
node identity so it survives a patch rebuild the same way `AudioEngine`'s
main-line handling already relies on node instances being long-lived (see
[Patch](patch.md)'s rebuild note).

Each block, for every sink still present in the current patch:

- if the map has no entry for it, or the entry's remembered mixer name no
  longer matches `sink.getMixerName()` (including a fresh `null` - not yet
  chosen, or a name the user just picked), the old line (if any) is closed
  and `openVirtualLine` tries to open a fresh one on the newly-named
  `Mixer`. A failed or not-yet-chosen attempt still gets an entry - one with
  a `null` `SourceDataLine` - so a persistently unavailable device is not
  retried every block, only when the requested name changes again.
- a live entry has the sink's current input block(s) copied out via
  `Patch.copyInputBlock` (the same method the offline render paths already
  use to read a node's blocks from outside the graph) and PCM-encoded with
  the same `encodeFrame`/`PLAYBACK_CHANNEL_SETTINGS` machinery the main
  stereo path already uses - `writeMonoPcm` for a `VirtualLineOutNode`
  (true single-channel output, not the main line's mono-duplicated-to-stereo
  encoding) and the existing two-`float[]` `writeStereoPcm` for a
  `StereoVirtualLineOutNode`.
- sinks removed from the graph since the last block (patch edited, node
  deleted) have their entry closed and dropped, so a stale device is never
  left open.

`SourceDataLine.write()` blocks under backpressure, same as the main line -
exactly what is wanted when something is actually reading the virtual cable
in real time, since the write naturally paces itself to match. What it must
never do is stall the *main* line's timing because nothing is reading the
*virtual* one: before writing, `writeVirtualLines` checks
`line.available() >= byteCount` and drops that block for that sink entirely
if the buffer is already full, rather than blocking. A dropped block is an
audible gap in the virtual feed, never in the real speakers - the ceiling
this simplification accepts; a bounded ring buffer with its own drain thread
per virtual line would remove it, at the cost of one more thread and buffer
per virtual line out in the patch.

## `AudioInputDevice`

The capture-side mirror of `Sinks.VirtualLineOut.listAvailableMixerNames`:
`listAvailableDeviceNames()` scans `AudioSystem.getMixerInfo()` and keeps
mixers exposing at least one `TargetDataLine` (via
`mixer.getTargetLineInfo()`), and `open(String mixerName, AudioFormat
format)` finds that named `Mixer.Info` and opens a `TargetDataLine` from it
(or `AudioSystem.getTargetDataLine(format)` directly if `mixerName` is
`null` or unresolved). Deliberately re-implements the named-mixer lookup
`AudioEngine.findMixerInfo` already has rather than exposing that method -
`AudioEngine` is playback-shaped throughout (`SourceDataLine`, a `Patch`
render loop), and widening its API for an unrelated capture concern would
blur that.

Two call sites share this rather than each scanning mixers themselves:
[`Sources.MicInputNode`](sources.md) (an always-live graph node) and
[`Gui.SoundClipEditorView`](gui.md) (recording a static buffer for
editing). Both open the same shape of line - mono, 16-bit, 44100 Hz - but
for otherwise unrelated purposes, which is exactly why the shared surface
is this narrow device-enumeration/opening helper rather than a bigger
"live audio capture" abstraction the two would have to bend to fit.

## AudioInputDevice and the default input device

`AudioInputDevice` is the input-side mirror of
`Sinks.VirtualLineOut.listAvailableMixerNames`: it scans
`Mixer.getTargetLineInfo()` for a `TargetDataLine` rather than a
`SourceDataLine`, and `open(displayName, format)` hands back a started line.
It is a static facade rather than a node or an `AudioEngine` member because
its two consumers - `Sources.MicInputNode` and [Gui](gui.md)'s Sound Clip
record screen - have no `AudioEngine` between them, and `Sources` must not
depend on `Gui`.

It also holds the app-wide **default input device**
(`setDefaultDeviceName`/`getDefaultDeviceName`), set from Options > "Input
Device..." and persisted in `Preferences` under `inputDevice`. The resolution
is one line inside `open()`: a `null` `displayName` falls back to the
configured default, and a `null` default falls back to
`AudioSystem.getTargetDataLine(format)` as before. Putting it there rather
than at the two call sites is what makes every existing caller inherit it
without changing - `open(null, format)` used to mean "system default" and now
means "whatever the user chose, else system default", which is the same thing
when nothing is chosen.

An unset default is represented by `null`, never by a sentinel string in the
device list. The Options dialog offers the same "System Default" entry the
output-device dialog uses and maps it back to `null` plus a `PREFS.remove`,
so the stored preference is either a real device name or absent - a sentinel
would round-trip through `Preferences` and then fail `findMixerInfo` anyway,
landing on the same system line with an extra state to get wrong.

The default is a *starting point*, not a lock: a `MicInputNode` keeps its own
`deviceName` (patch state, chosen from the node's own "Mic..." button) and the
Sound Clip screen keeps its own chooser. Both simply preselect the default
when nothing has been chosen yet, which is what removes the "Choose an input
device first" error from the common case.
