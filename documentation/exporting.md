# Exporting and rendering

Turning a patch into an audio file, either from the editor or from the command
line.

## Exporting from the editor

**File > Export to WAV...**, or the button in the transport bar, runs through
three steps.

### 1. Duration

You are asked for a length in seconds, defaulting to 5.

Leaving it blank renders until a **Recording End** node in the patch fires,
which is how you let the patch decide its own length. A blank duration with no
Recording End node is refused, because nothing would stop the render.

### 2. Export options

| Option | Values | Default |
|---|---|---|
| Sample rate (Hz) | 1000 to 192000 | 44100 |
| Bit depth | 8, 16, 24, or 32 | 16 |
| Channels | Mono or Stereo | Stereo |
| Normalize to peak | on or off | off |
| Trim leading and trailing silence | on or off | off |

Bit depth 32 means 32-bit float.

The sample rate is not a conversion applied afterwards. It drives the patch
itself, so exporting at a lower rate is a genuine lower-rate render rather than
a resampled copy of a 44100 Hz one. Anything whose character depends on the
sample rate will differ accordingly.

**Normalize to peak** scales the result so its loudest point sits just below
full scale. **Trim leading and trailing silence** removes silence from both
ends. Both need the whole render in memory before they can be applied, so they
take a little longer on long files.

### 3. Destination

A normal save dialog, defaulting to `patch.wav`.

## Rendering from the command line

`render` needs no display and no audio device, which makes it the right tool
for scripting, for batch work, and for machines with no sound card:

```
java -cp target/classes Petroleum.Main render song.dsl -o song.wav
```

It accepts both patch formats, `.dsl` and `.ptr`.

| Option | Meaning |
|---|---|
| `-o`, `--output <file>` | WAV file to write. Required. |
| `-d`, `--duration <sec>` | Length in seconds. |
| `--max-duration <sec>` | Ceiling when no `--duration` is given, so a Recording End that never fires still stops. Default 600. |
| `--rate <hz>` | Sample rate. Default 44100. |
| `--bits <8\|16\|24\|32>` | Bit depth. Default 16. |
| `--channels <1\|2>` | Channel count. Default 2. |
| `--normalize` | Scale the peak to just under full scale. |
| `--trim` | Cut leading and trailing silence. |

### How length is decided

With no `--duration`:

- A patch with a **Recording End** node runs until that node fires, or until
  `--max-duration` is reached.
- A one-shot patch, one built around an envelope that finishes, runs until it
  has finished.
- A patch that is neither is rejected, since it would never stop. Pass
  `--duration`.

### Rendering several patches

There is no batch flag; the command renders one patch per invocation, which
composes with an ordinary shell loop:

```
for f in patches/*.dsl; do
    java -cp target/classes Petroleum.Main render "$f" -o "wav/$(basename "${f%.dsl}").wav" --duration 4
done
```

Because Diesel patches are text, generating a family of variants with a script
and rendering them this way is a reasonable workflow.

## Exporting generated sounds

The Quick Sounds screen exports directly. A single sound can be written with
**Export WAV**, and a batch of rolled sounds can be written to a folder in one
step, which is useful when you want a set of variations on a sound effect
rather than one take.
