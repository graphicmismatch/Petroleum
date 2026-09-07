package JSynth.Tracker;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class TrackerStateCodec {
    private static final int CHUNK = 8192;
    private static final int NOTE_RECORD_BYTES = 44;
    private static final String LENGTH_KEY = "patternLength";
    private static final String DATA_KEY = "pattern";

    private static final TrackerPattern.SlideMode[] SLIDE_MODES = TrackerPattern.SlideMode.values();

    private TrackerStateCodec() {
    }

    static void save(TrackerPattern pattern, Map<String, String> state) {
        byte[] raw = encode(pattern);
        state.put(LENGTH_KEY, Integer.toString(raw.length));
        state.put(DATA_KEY, Base64.getEncoder().encodeToString(deflate(raw)));
    }

    static TrackerPattern load(Map<String, String> state) {
        String data = state.get(DATA_KEY);
        if (data == null) {
            return null;
        }
        int expectedLength = Integer.parseInt(state.get(LENGTH_KEY));
        byte[] raw = inflate(Base64.getDecoder().decode(data), expectedLength);
        return decode(raw);
    }

    private static byte[] encode(TrackerPattern pattern) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
        ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(pattern.channelCount());
        header.putInt(pattern.totalSteps());
        header.putInt(pattern.stepsPerBeat());
        bytes.writeBytes(header.array());

        for (int c = 0; c < pattern.channelCount(); c++) {
            byte[] name = pattern.channelName(c).getBytes(StandardCharsets.UTF_8);
            List<TrackerPattern.NoteEvent> notes = pattern.notes(c);
            ByteBuffer channelHeader = ByteBuffer.allocate(8 + name.length).order(ByteOrder.LITTLE_ENDIAN);
            channelHeader.putInt(name.length);
            channelHeader.put(name);
            channelHeader.putInt(notes.size());
            bytes.writeBytes(channelHeader.array());

            ByteBuffer noteBuffer = ByteBuffer.allocate(notes.size() * NOTE_RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (TrackerPattern.NoteEvent note : notes) {
                TrackerPattern.NoteEffect effect = note.effect();
                noteBuffer.putInt(note.startStep());
                noteBuffer.putInt(note.lengthSteps());
                noteBuffer.putFloat(note.pitch());
                noteBuffer.putInt(effect.slideMode().ordinal());
                noteBuffer.putInt(effect.slideSteps());
                noteBuffer.putInt(effect.arpSemitone1());
                noteBuffer.putInt(effect.arpSemitone2());
                noteBuffer.putInt(effect.arpRateTicks());
                noteBuffer.putFloat(effect.delayFraction());
                noteBuffer.putInt(effect.retriggerTicks());
                noteBuffer.putInt(effect.cutTicks());
            }
            bytes.writeBytes(noteBuffer.array());
        }

        return bytes.toByteArray();
    }

    private static TrackerPattern decode(byte[] raw) {
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int channelCount = buffer.getInt();
        int totalSteps = buffer.getInt();
        int stepsPerBeat = buffer.getInt();
        TrackerPattern pattern = new TrackerPattern(channelCount, totalSteps, stepsPerBeat);

        for (int c = 0; c < channelCount; c++) {
            int nameLength = buffer.getInt();
            byte[] nameBytes = new byte[nameLength];
            buffer.get(nameBytes);
            pattern.setChannelName(c, new String(nameBytes, StandardCharsets.UTF_8));

            int noteCount = buffer.getInt();
            for (int i = 0; i < noteCount; i++) {
                int startStep = buffer.getInt();
                int lengthSteps = buffer.getInt();
                float pitch = buffer.getFloat();
                TrackerPattern.SlideMode slideMode = SLIDE_MODES[buffer.getInt()];
                int slideSteps = buffer.getInt();
                int arpSemitone1 = buffer.getInt();
                int arpSemitone2 = buffer.getInt();
                int arpRateTicks = buffer.getInt();
                float delayFraction = buffer.getFloat();
                int retriggerTicks = buffer.getInt();
                int cutTicks = buffer.getInt();
                TrackerPattern.NoteEffect effect = new TrackerPattern.NoteEffect(
                        slideMode, slideSteps, arpSemitone1, arpSemitone2, arpRateTicks,
                        delayFraction, retriggerTicks, cutTicks);
                pattern.addNote(c, new TrackerPattern.NoteEvent(startStep, lengthSteps, pitch, effect));
            }
        }

        return pattern;
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 2 + 64);
        byte[] chunk = new byte[CHUNK];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] compressed, int expectedLength) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] raw = new byte[expectedLength];
        try {
            int written = 0;
            while (written < expectedLength && !inflater.finished()) {
                int produced = inflater.inflate(raw, written, expectedLength - written);
                if (produced == 0 && inflater.needsInput()) {
                    throw new IllegalStateException("Tracker pattern data is truncated");
                }
                written += produced;
            }
            if (written < expectedLength) {
                throw new IllegalStateException("Tracker pattern data is truncated");
            }
        } catch (DataFormatException ex) {
            throw new IllegalStateException("Tracker pattern data is corrupt", ex);
        } finally {
            inflater.end();
        }
        return raw;
    }
}
