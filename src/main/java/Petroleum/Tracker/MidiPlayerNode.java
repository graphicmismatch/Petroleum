package Petroleum.Tracker;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MidiPlayerNode extends AbstractNode {
    public static final int PLAY = 0;
    public static final int RESET = 1;
    public static final int LOOP = 2;

    private static final int SET_TEMPO_TYPE = 0x51;
    private static final double MIN_NOTE_SECONDS = 0.005;

    public static int pitchPort(int voice) {
        return voice * 3;
    }

    public static int gatePort(int voice) {
        return voice * 3 + 1;
    }

    public static int velocityPort(int voice) {
        return voice * 3 + 2;
    }

    public record LoadResult(int noteCount, int stolenNotes, double lengthSeconds) {
    }

    record Voice(double[] on, double[] off, float[] hz, float[] velocity) {
    }

    record Song(Voice[] voices, double lengthSeconds, int noteCount, int stolenNotes) {
    }

    private record Note(double on, double[] off, int key, float velocity) {
    }

    private final int voiceCount;
    private volatile Song song;
    private volatile byte[] midiData;
    private volatile String fileName;

    private Song playingSong;
    private boolean started;
    private long expectedBlockStart;
    private long position;
    private boolean finished;
    private final int[] cursor;
    private final boolean[] sounding;
    private final boolean[] gateWasHigh;
    private final float[] pitch;
    private final float[] velocity;
    private final EdgeDetector resetEdge = new EdgeDetector();

    public MidiPlayerNode() {
        this(8);
    }

    public MidiPlayerNode(int voiceCount) {
        super(List.of(
                new Port("play", 1f),
                new Port("reset", 0f),
                new Port("loop", 1f)), buildOutputNames(voiceCount));
        if (voiceCount < 1) {
            throw new IllegalArgumentException("A MIDI Player needs at least one voice");
        }
        this.voiceCount = voiceCount;
        this.song = emptySong(voiceCount);
        cursor = new int[voiceCount];
        sounding = new boolean[voiceCount];
        gateWasHigh = new boolean[voiceCount];
        pitch = new float[voiceCount];
        velocity = new float[voiceCount];
    }

    private static List<String> buildOutputNames(int voiceCount) {
        List<String> names = new ArrayList<>(voiceCount * 3);
        for (int v = 0; v < voiceCount; v++) {
            names.add("pitch" + v);
            names.add("gate" + v);
            names.add("velocity" + v);
        }
        return names;
    }

    public int getChannelCount() {
        return voiceCount;
    }

    public String getFileName() {
        return fileName;
    }

    public LoadResult loadMidi(byte[] data, String name) {
        Song parsed = parse(data, voiceCount);
        midiData = data.clone();
        fileName = name;
        song = parsed;
        return new LoadResult(parsed.noteCount(), parsed.stolenNotes(), parsed.lengthSeconds());
    }

    public byte[] getMidiData() {
        byte[] data = midiData;
        return data == null ? null : data.clone();
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        Song current = this.song;
        if (!started || blockStart != expectedBlockStart || current != playingSong) {
            playingSong = current;
            restart();
            started = true;
        }
        double sampleRate = context.sampleRate;

        for (int s = 0; s < frameCount; s++) {
            if (resetEdge.edge(inputs[RESET][s]) > 0) {
                restart();
            }
            boolean playing = inputs[PLAY][s] > 0f;
            double time = position / sampleRate;

            for (int v = 0; v < voiceCount; v++) {
                Voice voice = current.voices()[v];
                if (sounding[v] && time >= voice.off()[cursor[v] - 1]) {
                    sounding[v] = false;
                }
                boolean noteStarted = false;
                while (cursor[v] < voice.on().length && time >= voice.on()[cursor[v]]) {
                    int i = cursor[v]++;
                    pitch[v] = voice.hz()[i];
                    velocity[v] = voice.velocity()[i];
                    sounding[v] = time < voice.off()[i];
                    noteStarted = true;
                }
                boolean gate = playing && !finished && sounding[v] && !(noteStarted && gateWasHigh[v]);
                gateWasHigh[v] = gate;
                outputs[pitchPort(v)][s] = pitch[v];
                outputs[gatePort(v)][s] = gate ? 1f : 0f;
                outputs[velocityPort(v)][s] = velocity[v];
            }

            if (playing && !finished) {
                position++;
                if (position / sampleRate >= current.lengthSeconds()) {
                    if (inputs[LOOP][s] > 0f && current.lengthSeconds() > 0) {
                        restart();
                    } else {
                        finished = true;
                    }
                }
            }
        }
        expectedBlockStart = blockStart + frameCount;
    }

    private void restart() {
        position = 0;
        finished = false;
        for (int v = 0; v < voiceCount; v++) {
            cursor[v] = 0;
            sounding[v] = false;
        }
    }

    @Override
    public String getName() {
        return "MIDI Player";
    }

    private static Song emptySong(int voiceCount) {
        Voice[] voices = new Voice[voiceCount];
        for (int v = 0; v < voiceCount; v++) {
            voices[v] = new Voice(new double[0], new double[0], new float[0], new float[0]);
        }
        return new Song(voices, 0, 0, 0);
    }

    static Song parse(byte[] data, int voiceCount) {
        Sequence sequence;
        try {
            sequence = MidiSystem.getSequence(new ByteArrayInputStream(data));
        } catch (InvalidMidiDataException | IOException ex) {
            throw new IllegalArgumentException("Not a valid MIDI file: " + ex.getMessage(), ex);
        }

        List<MidiEvent> events = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                events.add(track.get(i));
            }
        }
        events.sort(Comparator.comparingLong(MidiEvent::getTick)
                .thenComparingInt(event -> isNoteOn(event) ? 1 : 0));

        boolean ppq = sequence.getDivisionType() == Sequence.PPQ;
        double secondsPerTick = ppq
                ? 0.5 / sequence.getResolution()
                : 1.0 / (sequence.getDivisionType() * sequence.getResolution());

        List<Note> notes = new ArrayList<>();
        Map<Integer, ArrayDeque<Note>> open = new HashMap<>();
        double seconds = 0;
        long lastTick = 0;
        for (MidiEvent event : events) {
            seconds += (event.getTick() - lastTick) * secondsPerTick;
            lastTick = event.getTick();
            if (ppq && event.getMessage() instanceof MetaMessage meta && meta.getType() == SET_TEMPO_TYPE
                    && meta.getData().length == 3) {
                byte[] d = meta.getData();
                int microsPerQuarter = ((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF);
                if (microsPerQuarter > 0) {
                    secondsPerTick = microsPerQuarter / 1e6 / sequence.getResolution();
                }
            } else if (event.getMessage() instanceof ShortMessage sm) {
                int key = sm.getChannel() * 128 + sm.getData1();
                if (isNoteOn(event)) {
                    Note note = new Note(seconds, new double[] {Double.NaN}, sm.getData1(), sm.getData2() / 127f);
                    notes.add(note);
                    open.computeIfAbsent(key, k -> new ArrayDeque<>()).add(note);
                } else if (sm.getCommand() == ShortMessage.NOTE_OFF || sm.getCommand() == ShortMessage.NOTE_ON) {
                    ArrayDeque<Note> held = open.get(key);
                    if (held != null && !held.isEmpty()) {
                        held.poll().off()[0] = seconds;
                    }
                }
            }
        }
        double length = seconds;
        for (Note note : notes) {
            if (Double.isNaN(note.off()[0])) {
                note.off()[0] = length;
            }
            note.off()[0] = Math.max(note.off()[0], note.on() + MIN_NOTE_SECONDS);
            length = Math.max(length, note.off()[0]);
        }

        List<List<Note>> assigned = new ArrayList<>();
        for (int v = 0; v < voiceCount; v++) {
            assigned.add(new ArrayList<>());
        }
        int stolen = 0;
        for (Note note : notes) {
            int chosen = -1;
            int soonest = 0;
            for (int v = 0; v < voiceCount; v++) {
                List<Note> list = assigned.get(v);
                double busyUntil = list.isEmpty() ? Double.NEGATIVE_INFINITY : list.get(list.size() - 1).off()[0];
                if (busyUntil <= note.on()) {
                    chosen = v;
                    break;
                }
                List<Note> best = assigned.get(soonest);
                if (busyUntil < best.get(best.size() - 1).off()[0]) {
                    soonest = v;
                }
            }
            if (chosen < 0) {
                chosen = soonest;
                List<Note> list = assigned.get(chosen);
                list.get(list.size() - 1).off()[0] = note.on();
                stolen++;
            }
            assigned.get(chosen).add(note);
        }

        Voice[] voices = new Voice[voiceCount];
        for (int v = 0; v < voiceCount; v++) {
            List<Note> list = assigned.get(v);
            double[] on = new double[list.size()];
            double[] off = new double[list.size()];
            float[] hz = new float[list.size()];
            float[] vel = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Note note = list.get(i);
                on[i] = note.on();
                off[i] = note.off()[0];
                hz[i] = (float) (440.0 * Math.pow(2.0, (note.key() - 69) / 12.0));
                vel[i] = note.velocity();
            }
            voices[v] = new Voice(on, off, hz, vel);
        }
        return new Song(voices, length, notes.size(), stolen);
    }

    private static boolean isNoteOn(MidiEvent event) {
        return event.getMessage() instanceof ShortMessage sm
                && sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0;
    }
}
