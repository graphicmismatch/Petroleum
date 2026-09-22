package JSynth.Tracker;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class TrackerStateCodec {
    private static final int NOTE_RECORD_BYTES = 44;

    private static final TrackerPattern.SlideMode[] SLIDE_MODES = TrackerPattern.SlideMode.values();

    private TrackerStateCodec() {
    }

    static byte[] encode(TrackerSong song) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
        int channelCount = song.channelCount();
        int patternCount = song.patternCount();
        int orderLength = song.orderLength();

        ByteBuffer header = ByteBuffer.allocate(12 + orderLength * 4).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(channelCount);
        header.putInt(patternCount);
        header.putInt(orderLength);
        for (int i = 0; i < orderLength; i++) {
            header.putInt(song.orderAt(i));
        }
        bytes.writeBytes(header.array());

        for (int p = 0; p < patternCount; p++) {
            TrackerPattern pattern = song.pattern(p);
            ByteBuffer patternHeader = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            patternHeader.putInt(pattern.totalSteps());
            patternHeader.putInt(pattern.stepsPerBeat());
            patternHeader.putInt(pattern.speed());
            bytes.writeBytes(patternHeader.array());

            for (int c = 0; c < channelCount; c++) {
                byte[] name = pattern.channelName(c).getBytes(StandardCharsets.UTF_8);
                List<TrackerPattern.NoteEvent> notes = pattern.notes(c);
                ByteBuffer channelHeader = ByteBuffer.allocate(8 + name.length).order(ByteOrder.LITTLE_ENDIAN);
                channelHeader.putInt(name.length);
                channelHeader.put(name);
                channelHeader.putInt(notes.size());
                bytes.writeBytes(channelHeader.array());

                ByteBuffer noteBuffer =
                        ByteBuffer.allocate(notes.size() * NOTE_RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
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
        }

        return bytes.toByteArray();
    }

    static TrackerSong decode(byte[] raw) {
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int channelCount = buffer.getInt();
        int patternCount = buffer.getInt();
        int orderLength = buffer.getInt();
        List<Integer> order = new ArrayList<>(orderLength);
        for (int i = 0; i < orderLength; i++) {
            order.add(buffer.getInt());
        }

        List<TrackerPattern> patterns = new ArrayList<>(patternCount);
        for (int p = 0; p < patternCount; p++) {
            int totalSteps = buffer.getInt();
            int stepsPerBeat = buffer.getInt();
            int speed = buffer.getInt();
            TrackerPattern pattern = new TrackerPattern(channelCount, totalSteps, stepsPerBeat);
            pattern.setSpeed(speed);

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
            patterns.add(pattern);
        }

        return TrackerSong.fromParts(channelCount, patterns, order);
    }
}
