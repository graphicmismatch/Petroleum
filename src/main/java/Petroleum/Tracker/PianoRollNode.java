package Petroleum.Tracker;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PianoRollNode extends AbstractNode {
    public static final int BPM = 0;
    public static final int PLAY = 1;
    public static final int RESET = 2;
    public static final int LOOP = 3;

    public static final int DEFAULT_VOICES = 8;

    public static int pitchPort(int voice) {
        return voice * 3;
    }

    public static int gatePort(int voice) {
        return voice * 3 + 1;
    }

    public static int velocityPort(int voice) {
        return voice * 3 + 2;
    }

    record Voice(int[] on, int[] off, float[] hz, float[] velocity) {
    }

    record Schedule(Voice[] voices, int lengthTicks, int stolenNotes, float[][] bendRatio) {
    }

    private final int voiceCount;
    private volatile PianoRollClip clip;
    private volatile Schedule schedule;
    private volatile double publishedTick = -1;

    private Schedule playingSchedule;
    private boolean started;
    private long expectedBlockStart;
    private double tick;
    private boolean finished;
    private final int[] cursor;
    private final boolean[] sounding;
    private final boolean[] gateWasHigh;
    private final float[] pitch;
    private final float[] velocity;
    private final EdgeDetector resetEdge = new EdgeDetector();

    public PianoRollNode() {
        this(DEFAULT_VOICES);
    }

    public PianoRollNode(int voiceCount) {
        super(List.of(
                new Port("bpm", 120f),
                new Port("play", 1f),
                new Port("reset", 0f),
                new Port("loop", 1f)), buildOutputNames(voiceCount));
        if (voiceCount < 1) {
            throw new IllegalArgumentException("A Piano Roll needs at least one voice");
        }
        this.voiceCount = voiceCount;
        cursor = new int[voiceCount];
        sounding = new boolean[voiceCount];
        gateWasHigh = new boolean[voiceCount];
        pitch = new float[voiceCount];
        velocity = new float[voiceCount];
        setClip(new PianoRollClip());
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

    public PianoRollClip getClip() {
        return clip.copy();
    }

    public void setClip(PianoRollClip newClip) {
        PianoRollClip copy = newClip.copy();
        clip = copy;
        schedule = buildSchedule(copy, voiceCount);
    }

    public int getStolenNoteCount() {
        return schedule.stolenNotes();
    }

    public double getPlayheadTick() {
        return publishedTick;
    }

    public byte[] saveClipData() {
        return clip.encode();
    }

    public void loadClipData(byte[] data) {
        setClip(PianoRollClip.decode(data));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        Schedule current = this.schedule;
        if (!started || blockStart != expectedBlockStart) {
            playingSchedule = current;
            restart();
            Arrays.fill(gateWasHigh, false);
            started = true;
        } else if (current != playingSchedule) {
            playingSchedule = current;
            resync();
        }
        float[][] bendRatio = current.bendRatio();
        double ticksPerSecondPerBpm = PianoRollClip.TICKS_PER_BEAT / 60.0 / context.sampleRate;

        for (int s = 0; s < frameCount; s++) {
            if (resetEdge.edge(inputs[RESET][s]) > 0) {
                restart();
            }
            boolean playing = inputs[PLAY][s] > 0f;
            double curvePosition = tick / PianoRollClip.CURVE_STEP;
            int curveIndex = (int) curvePosition;
            float curveFraction = (float) (curvePosition - curveIndex);

            for (int v = 0; v < voiceCount; v++) {
                Voice voice = current.voices()[v];
                if (sounding[v] && tick >= voice.off()[cursor[v] - 1]) {
                    sounding[v] = false;
                }
                boolean noteStarted = false;
                while (cursor[v] < voice.on().length && tick >= voice.on()[cursor[v]]) {
                    int i = cursor[v]++;
                    pitch[v] = voice.hz()[i];
                    velocity[v] = voice.velocity()[i];
                    sounding[v] = tick < voice.off()[i];
                    noteStarted = true;
                }
                boolean gate = playing && !finished && sounding[v] && !(noteStarted && gateWasHigh[v]);
                gateWasHigh[v] = gate;
                float[] ratios = bendRatio[v];
                float ratio = 1f;
                if (ratios != null) {
                    int index = Math.min(curveIndex, ratios.length - 2);
                    ratio = ratios[index] + (ratios[index + 1] - ratios[index]) * curveFraction;
                }
                outputs[pitchPort(v)][s] = pitch[v] * ratio;
                outputs[gatePort(v)][s] = gate ? 1f : 0f;
                outputs[velocityPort(v)][s] = velocity[v];
            }

            if (playing && !finished) {
                tick += Math.max(1f, inputs[BPM][s]) * ticksPerSecondPerBpm;
                if (tick >= current.lengthTicks()) {
                    if (inputs[LOOP][s] > 0f) {
                        tick %= current.lengthTicks();
                        rewindVoices();
                    } else {
                        finished = true;
                    }
                }
            }
        }
        expectedBlockStart = blockStart + frameCount;
        publishedTick = finished ? -1 : tick;
    }

    private void restart() {
        tick = 0;
        finished = false;
        rewindVoices();
    }

    private void rewindVoices() {
        for (int v = 0; v < voiceCount; v++) {
            cursor[v] = 0;
            sounding[v] = false;
        }
    }

    private void resync() {
        for (int v = 0; v < voiceCount; v++) {
            Voice voice = playingSchedule.voices()[v];
            int c = 0;
            while (c < voice.on().length && voice.on()[c] < tick) {
                c++;
            }
            cursor[v] = c;
            sounding[v] = c > 0 && tick < voice.off()[c - 1];
            if (sounding[v]) {
                pitch[v] = voice.hz()[c - 1];
                velocity[v] = voice.velocity()[c - 1];
            }
        }
    }

    static Schedule buildSchedule(PianoRollClip clip, int voiceCount) {
        int length = clip.lengthTicks();
        List<List<PianoRollClip.Note>> perVoice = new ArrayList<>(voiceCount);
        for (int v = 0; v < voiceCount; v++) {
            perVoice.add(new ArrayList<>());
        }
        int stolen = 0;
        for (PianoRollClip.Note note : clip.notes()) {
            if (note.start() >= length) {
                continue;
            }
            if (note.voice() >= voiceCount) {
                stolen++;
            } else {
                perVoice.get(note.voice()).add(note);
            }
        }

        Voice[] voices = new Voice[voiceCount];
        float[][] bendRatio = new float[voiceCount][];
        for (int v = 0; v < voiceCount; v++) {
            List<PianoRollClip.Note> list = perVoice.get(v);
            int n = list.size();
            int[] on = new int[n];
            int[] off = new int[n];
            float[] hz = new float[n];
            float[] vel = new float[n];
            for (int i = 0; i < n; i++) {
                PianoRollClip.Note note = list.get(i);
                int end = Math.min(note.end(), length);
                if (i + 1 < n && list.get(i + 1).start() < end) {
                    end = list.get(i + 1).start();
                    stolen++;
                }
                on[i] = note.start();
                off[i] = end;
                hz[i] = (float) (440.0 * Math.pow(2.0, (note.pitch() - 69) / 12.0));
                vel[i] = note.velocity();
            }
            voices[v] = new Voice(on, off, hz, vel);
            if (!clip.bend(v).isEmpty()) {
                float[] ratios = new float[length / PianoRollClip.CURVE_STEP + 2];
                for (int i = 0; i < ratios.length; i++) {
                    ratios[i] = (float) Math.pow(2.0, clip.bendAt(v, i * PianoRollClip.CURVE_STEP) / 1200.0);
                }
                bendRatio[v] = ratios;
            }
        }
        return new Schedule(voices, length, stolen, bendRatio);
    }

    @Override
    public String getName() {
        return "Piano Roll";
    }
}
