package Petroleum.Tracker;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class PianoRollClip {
    public static final int TICKS_PER_BEAT = 96;
    public static final int BEATS_PER_BAR = 4;
    public static final int TICKS_PER_BAR = TICKS_PER_BEAT * BEATS_PER_BAR;
    public static final int DEFAULT_BARS = 4;
    public static final float DEFAULT_VELOCITY = 0.8f;
    public static final float MIN_PITCH = 0f;
    public static final float MAX_PITCH = 127f;

    public static final int CURVE_STEP = 4;
    public static final float MAX_BEND_CENTS = 1200f;

    private static final int FORMAT = 3;
    private static final Comparator<Note> ORDER =
            Comparator.comparingInt(Note::start).thenComparingDouble(Note::pitch);

    public record Note(int start, int length, float pitch, float velocity, int voice) {
        public Note {
            if (length < 1) {
                throw new IllegalArgumentException("length must be >= 1");
            }
            start = Math.max(0, start);
            pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
            velocity = Math.max(0f, Math.min(1f, velocity));
            voice = Math.max(0, voice);
        }

        public Note(int start, int length, float pitch, float velocity) {
            this(start, length, pitch, velocity, 0);
        }

        public boolean overlaps(int otherStart, int otherEnd) {
            return start < otherEnd && otherStart < end();
        }

        public int end() {
            return start + length;
        }

        public Note withStart(int newStart) {
            return new Note(newStart, length, pitch, velocity, voice);
        }

        public Note withLength(int newLength) {
            return new Note(start, newLength, pitch, velocity, voice);
        }

        public Note withPitch(float newPitch) {
            return new Note(start, length, newPitch, velocity, voice);
        }

        public Note withVelocity(float newVelocity) {
            return new Note(start, length, pitch, newVelocity, voice);
        }

        public Note withVoice(int newVoice) {
            return new Note(start, length, pitch, velocity, newVoice);
        }
    }

    public record ImportResult(PianoRollClip clip, float detectedBpm, int noteCount) {
    }

    private static final int LEGACY_VOICES = 16;

    private int lengthTicks;
    private final List<Note> notes = new ArrayList<>();
    private final TreeMap<Integer, TreeMap<Integer, Float>> bends = new TreeMap<>();

    public PianoRollClip() {
        this(DEFAULT_BARS * TICKS_PER_BAR);
    }

    public PianoRollClip(int lengthTicks) {
        this.lengthTicks = Math.max(1, lengthTicks);
    }

    public int lengthTicks() {
        return lengthTicks;
    }

    public void setLengthTicks(int lengthTicks) {
        this.lengthTicks = Math.max(1, lengthTicks);
    }

    public List<Note> notes() {
        return Collections.unmodifiableList(notes);
    }

    public void add(Note note) {
        notes.removeIf(n -> n.start() == note.start() && n.pitch() == note.pitch());
        int index = Collections.binarySearch(notes, note, ORDER);
        notes.add(index < 0 ? -index - 1 : index, note);
    }

    public boolean remove(Note note) {
        return notes.remove(note);
    }

    public int lastNoteEnd() {
        int end = 0;
        for (Note note : notes) {
            end = Math.max(end, note.end());
        }
        return end;
    }

    public int freeVoice(int start, int end, int voiceCount, List<Note> ignoring) {
        int[] overlaps = new int[Math.max(1, voiceCount)];
        for (Note note : notes) {
            if (note.voice() < overlaps.length && note.overlaps(start, end) && !ignoring.contains(note)) {
                overlaps[note.voice()]++;
            }
        }
        int best = 0;
        for (int v = 1; v < overlaps.length; v++) {
            if (overlaps[v] < overlaps[best]) {
                best = v;
            }
        }
        return best;
    }

    public boolean voiceIsFree(Note candidate, List<Note> ignoring) {
        for (Note note : notes) {
            if (note.voice() == candidate.voice() && note.overlaps(candidate.start(), candidate.end())
                    && !note.equals(candidate) && !ignoring.contains(note)) {
                return false;
            }
        }
        return true;
    }

    public int highestVoice() {
        int highest = -1;
        for (Note note : notes) {
            highest = Math.max(highest, note.voice());
        }
        return highest;
    }

    public NavigableMap<Integer, Float> bend(int voice) {
        TreeMap<Integer, Float> curve = bends.get(voice);
        return curve == null ? Collections.emptyNavigableMap() : Collections.unmodifiableNavigableMap(curve);
    }

    public boolean hasBend() {
        return !bends.isEmpty();
    }

    public float bendAt(int voice, double tick) {
        TreeMap<Integer, Float> curve = bends.get(voice);
        if (curve == null) {
            return 0f;
        }
        Map.Entry<Integer, Float> before = curve.floorEntry((int) Math.floor(tick));
        Map.Entry<Integer, Float> after = curve.ceilingEntry((int) Math.ceil(tick));
        if (before == null || after == null) {
            return 0f;
        }
        if (before.getKey().equals(after.getKey())) {
            return before.getValue();
        }
        double t = (tick - before.getKey()) / (after.getKey() - before.getKey());
        return (float) (before.getValue() + (after.getValue() - before.getValue()) * t);
    }

    public void drawBend(int voice, double fromTick, float fromCents, double toTick, float toCents) {
        if (toTick < fromTick) {
            drawBend(voice, toTick, toCents, fromTick, fromCents);
            return;
        }
        TreeMap<Integer, Float> curve = bends.computeIfAbsent(voice, v -> new TreeMap<>());
        int first = (int) Math.round(fromTick / CURVE_STEP) * CURVE_STEP;
        int last = (int) Math.round(toTick / CURVE_STEP) * CURVE_STEP;
        for (int tick = Math.max(0, first); tick <= last; tick += CURVE_STEP) {
            double t = last == first ? 1 : (tick - first) / (double) (last - first);
            float cents = (float) (fromCents + (toCents - fromCents) * t);
            curve.put(tick, Math.max(-MAX_BEND_CENTS, Math.min(MAX_BEND_CENTS, cents)));
        }
    }

    public void anchorBend(int voice, double fromTick, double toTick) {
        TreeMap<Integer, Float> curve = bends.get(voice);
        if (curve == null) {
            return;
        }
        int first = (int) Math.round(Math.min(fromTick, toTick) / CURVE_STEP) * CURVE_STEP - CURVE_STEP;
        int last = (int) Math.round(Math.max(fromTick, toTick) / CURVE_STEP) * CURVE_STEP + CURVE_STEP;
        if (first >= 0) {
            curve.putIfAbsent(first, 0f);
        }
        curve.putIfAbsent(last, 0f);
        pruneBend(voice);
    }

    public void eraseBend(int voice, double fromTick, double toTick) {
        if (bends.containsKey(voice)) {
            drawBend(voice, fromTick, 0f, toTick, 0f);
            pruneBend(voice);
        }
    }

    public void clearBend(int voice) {
        bends.remove(voice);
    }

    private void pruneBend(int voice) {
        TreeMap<Integer, Float> curve = bends.get(voice);
        List<Integer> redundant = new ArrayList<>();
        for (Map.Entry<Integer, Float> entry : curve.entrySet()) {
            Float previous = curve.get(entry.getKey() - CURVE_STEP);
            Float next = curve.get(entry.getKey() + CURVE_STEP);
            if (entry.getValue() == 0f && (previous == null || previous == 0f) && (next == null || next == 0f)) {
                redundant.add(entry.getKey());
            }
        }
        redundant.forEach(curve::remove);
        if (curve.isEmpty()) {
            bends.remove(voice);
        }
    }

    private void assignVoices(int voiceCount) {
        List<Note> placed = new ArrayList<>(notes);
        notes.clear();
        for (Note note : placed) {
            notes.add(note.withVoice(freeVoice(note.start(), note.end(), voiceCount, List.of())));
        }
    }

    public PianoRollClip copy() {
        PianoRollClip copy = new PianoRollClip(lengthTicks);
        copy.notes.addAll(notes);
        bends.forEach((voice, curve) -> copy.bends.put(voice, new TreeMap<>(curve)));
        return copy;
    }

    public byte[] encode() {
        int curveBytes = 0;
        for (TreeMap<Integer, Float> curve : bends.values()) {
            curveBytes += 8 + curve.size() * 8;
        }
        ByteBuffer buffer = ByteBuffer.allocate(16 + notes.size() * 20 + curveBytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(FORMAT);
        buffer.putInt(lengthTicks);
        buffer.putInt(notes.size());
        for (Note note : notes) {
            buffer.putInt(note.start());
            buffer.putInt(note.length());
            buffer.putFloat(note.pitch());
            buffer.putFloat(note.velocity());
            buffer.putInt(note.voice());
        }
        buffer.putInt(bends.size());
        for (Map.Entry<Integer, TreeMap<Integer, Float>> curve : bends.entrySet()) {
            buffer.putInt(curve.getKey());
            buffer.putInt(curve.getValue().size());
            for (Map.Entry<Integer, Float> point : curve.getValue().entrySet()) {
                buffer.putInt(point.getKey());
                buffer.putFloat(point.getValue());
            }
        }
        return buffer.array();
    }

    public static PianoRollClip decode(byte[] raw) {
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int format = buffer.getInt();
        if (format < 1 || format > FORMAT) {
            throw new IllegalArgumentException("Unknown piano roll data format " + format);
        }
        PianoRollClip clip = new PianoRollClip(buffer.getInt());
        int count = buffer.getInt();
        for (int i = 0; i < count; i++) {
            clip.add(new Note(buffer.getInt(), buffer.getInt(), buffer.getFloat(), buffer.getFloat(),
                    format >= 3 ? buffer.getInt() : 0));
        }
        if (format < 3) {
            clip.assignVoices(LEGACY_VOICES);
        }
        if (format == 2) {
            TreeMap<Integer, Float> shared = new TreeMap<>();
            int points = buffer.getInt();
            for (int i = 0; i < points; i++) {
                shared.put(buffer.getInt(), buffer.getFloat());
            }
            if (!shared.isEmpty()) {
                for (int v = 0; v <= clip.highestVoice(); v++) {
                    clip.bends.put(v, new TreeMap<>(shared));
                }
            }
        } else if (format >= 3) {
            int curves = buffer.getInt();
            for (int c = 0; c < curves; c++) {
                int voice = buffer.getInt();
                int points = buffer.getInt();
                TreeMap<Integer, Float> curve = new TreeMap<>();
                for (int i = 0; i < points; i++) {
                    curve.put(buffer.getInt(), buffer.getFloat());
                }
                clip.bends.put(voice, curve);
            }
        }
        return clip;
    }

    public static ImportResult importMidi(File file, int voiceCount) throws IOException {
        Sequence sequence;
        try {
            sequence = MidiSystem.getSequence(file);
        } catch (InvalidMidiDataException ex) {
            throw new IllegalArgumentException("Not a valid MIDI file: " + ex.getMessage(), ex);
        }
        if (sequence.getDivisionType() != Sequence.PPQ) {
            throw new IllegalArgumentException(
                    "Only tick-based (PPQ) MIDI files are supported, not SMPTE-timed files");
        }
        double scale = TICKS_PER_BEAT / (double) sequence.getResolution();

        List<MidiEvent> events = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof ShortMessage) {
                    events.add(track.get(i));
                }
            }
        }
        events.sort(Comparator.comparingLong(MidiEvent::getTick));

        PianoRollClip clip = new PianoRollClip();
        Map<Integer, ArrayDeque<long[]>> open = new HashMap<>();
        for (MidiEvent event : events) {
            ShortMessage sm = (ShortMessage) event.getMessage();
            int key = sm.getChannel() * 128 + sm.getData1();
            if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                open.computeIfAbsent(key, k -> new ArrayDeque<>())
                        .add(new long[] {event.getTick(), sm.getData1(), sm.getData2()});
            } else if (sm.getCommand() == ShortMessage.NOTE_OFF || sm.getCommand() == ShortMessage.NOTE_ON) {
                ArrayDeque<long[]> held = open.get(key);
                if (held != null && !held.isEmpty()) {
                    long[] on = held.poll();
                    int start = (int) Math.round(on[0] * scale);
                    int end = (int) Math.round(event.getTick() * scale);
                    clip.add(new Note(start, Math.max(1, end - start), on[1], on[2] / 127f));
                }
            }
        }
        clip.assignVoices(voiceCount);
        int bars = Math.max(1, (clip.lastNoteEnd() + TICKS_PER_BAR - 1) / TICKS_PER_BAR);
        clip.setLengthTicks(bars * TICKS_PER_BAR);
        return new ImportResult(clip, MidiImporter.detectBpm(sequence), clip.notes().size());
    }
}
