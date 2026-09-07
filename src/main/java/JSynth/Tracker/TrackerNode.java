package JSynth.Tracker;

import JSynth.Dsp.DspMath;
import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;
import JSynth.Graph.Stateful;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrackerNode extends AbstractNode implements Stateful {
    public static final int BPM = 0;
    public static final int PLAY = 1;
    public static final int RESET = 2;
    public static final int LOOP = 3;

    private static final int TICKS_PER_STEP = 4;

    public static int pitchPort(int channel) {
        return channel * 2;
    }

    public static int gatePort(int channel) {
        return channel * 2 + 1;
    }

    private final int channelCount;

    private volatile TrackTable snapshot;
    private volatile int publishedStep = -1;

    private boolean started;
    private long expectedBlockStart;
    private long samplePosition;
    private int stepPosition;
    private boolean finished;

    private final int[] channelCursor;
    private final boolean[] gateOn;
    private final int[] activeEnd;
    private final float[] activeFreq;
    private final float[] basePitch;
    private final float[] lastOutputSemitone;
    private final TrackerPattern.NoteEffect[] activeEffect;

    private final boolean[] sliding;
    private final float[] slideFromSemitone;
    private final float[] slideToSemitone;
    private final long[] slideSamplesTotal;
    private final long[] slideSamplesElapsed;

    private final int[] noteTick;
    private final int[] noteTickSampleCounter;
    private final int[] arpIndex;
    private final int[] retriggerPulseSamplesLeft;

    private final boolean[] pendingActive;
    private final int[] pendingNoteIndex;
    private final float[] pendingDelayFraction;

    private final EdgeDetector resetEdge = new EdgeDetector();

    private float cachedBpm = Float.NaN;
    private int cachedStepsPerBeat = -1;
    private double cachedSampleRate = -1;
    private long samplesPerStep = 1;
    private long tickLengthSamples = 1;

    public TrackerNode() {
        this(TrackerPattern.DEFAULT_CHANNELS);
    }

    public TrackerNode(int channelCount) {
        super(List.of(
                new Port("bpm", 120f),
                new Port("play", 1f),
                new Port("reset", 0f),
                new Port("loop", 1f)), buildOutputNames(channelCount));
        this.channelCount = channelCount;

        this.snapshot = TrackTable.from(new TrackerPattern(channelCount,
                TrackerPattern.DEFAULT_TOTAL_STEPS, TrackerPattern.DEFAULT_STEPS_PER_BEAT));

        channelCursor = new int[channelCount];
        gateOn = new boolean[channelCount];
        activeEnd = new int[channelCount];
        activeFreq = new float[channelCount];
        basePitch = new float[channelCount];
        lastOutputSemitone = new float[channelCount];
        activeEffect = new TrackerPattern.NoteEffect[channelCount];

        sliding = new boolean[channelCount];
        slideFromSemitone = new float[channelCount];
        slideToSemitone = new float[channelCount];
        slideSamplesTotal = new long[channelCount];
        slideSamplesElapsed = new long[channelCount];

        noteTick = new int[channelCount];
        noteTickSampleCounter = new int[channelCount];
        arpIndex = new int[channelCount];
        retriggerPulseSamplesLeft = new int[channelCount];

        pendingActive = new boolean[channelCount];
        pendingNoteIndex = new int[channelCount];
        pendingDelayFraction = new float[channelCount];

        for (int c = 0; c < channelCount; c++) {
            activeEffect[c] = TrackerPattern.NoteEffect.NONE;
        }
    }

    public int getChannelCount() {
        return channelCount;
    }

    private static List<String> buildOutputNames(int channelCount) {
        List<String> names = new ArrayList<>(channelCount * 2);
        for (int c = 0; c < channelCount; c++) {
            names.add("pitch" + c);
            names.add("gate" + c);
        }
        return names;
    }

    public TrackerPattern getPattern() {
        return snapshot.toPattern();
    }

    public void setPattern(TrackerPattern pattern) {
        snapshot = TrackTable.from(pattern);
    }

    public int getCurrentStep() {
        return publishedStep;
    }

    @Override
    public void saveState(Map<String, String> state) {
        TrackerStateCodec.save(getPattern(), state);
    }

    @Override
    public void loadState(Map<String, String> state) {
        TrackerPattern pattern = TrackerStateCodec.load(state);
        if (pattern != null) {
            setPattern(pattern);
        }
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        TrackTable table = this.snapshot;

        if (!started || blockStart != expectedBlockStart) {
            resetToStart();
            activateStep(table, stepPosition);
            started = true;
        }

        for (int s = 0; s < frameCount; s++) {
            float bpm = Math.max(1f, inputs[BPM][s]);
            if (bpm != cachedBpm || table.stepsPerBeat != cachedStepsPerBeat || context.sampleRate != cachedSampleRate) {
                samplesPerStep = Math.max(1L, Math.round(60.0 / bpm / table.stepsPerBeat * context.sampleRate));
                tickLengthSamples = Math.max(1L, samplesPerStep / TICKS_PER_STEP);
                cachedBpm = bpm;
                cachedStepsPerBeat = table.stepsPerBeat;
                cachedSampleRate = context.sampleRate;
            }

            if (resetEdge.edge(inputs[RESET][s]) > 0) {
                resetToStart();
                activateStep(table, stepPosition);
            }

            boolean playing = inputs[PLAY][s] > 0f;

            for (int c = 0; c < channelCount; c++) {
                if (pendingActive[c]
                        && samplePosition >= Math.round(pendingDelayFraction[c] * samplesPerStep)) {
                    activateNote(c, table.channels[c], pendingNoteIndex[c], stepPosition, gateOn[c]);
                    pendingActive[c] = false;
                }

                if (gateOn[c]) {
                    noteTickSampleCounter[c]++;
                    if (noteTickSampleCounter[c] >= tickLengthSamples) {
                        noteTickSampleCounter[c] = 0;
                        noteTick[c]++;
                        TrackerPattern.NoteEffect effect = activeEffect[c];
                        if (!sliding[c] && effect.arpRateTicks() > 0 && noteTick[c] % effect.arpRateTicks() == 0) {
                            arpIndex[c]++;
                        }
                        if (effect.retriggerTicks() > 0 && noteTick[c] % effect.retriggerTicks() == 0) {
                            retriggerPulseSamplesLeft[c] = 1;
                        }
                    }
                    if (activeEffect[c].cutTicks() > 0 && noteTick[c] >= activeEffect[c].cutTicks()) {
                        gateOn[c] = false;
                    }
                }

                float semitone = basePitch[c];
                if (sliding[c]) {
                    double t = Math.min(1.0, slideSamplesElapsed[c] / (double) slideSamplesTotal[c]);
                    semitone = DspMath.lerp(slideFromSemitone[c], slideToSemitone[c], (float) t);
                    if (activeEffect[c].slideMode() == TrackerPattern.SlideMode.GLISSANDO) {
                        semitone = Math.round(semitone);
                    }
                    slideSamplesElapsed[c]++;
                    if (slideSamplesElapsed[c] >= slideSamplesTotal[c]) {
                        sliding[c] = false;
                    }
                    activeFreq[c] = pitchToHz(semitone);
                } else if (gateOn[c] && activeEffect[c].arpRateTicks() > 0) {
                    int idx = arpIndex[c] % 3;
                    float offset = idx == 0 ? 0f : idx == 1 ? activeEffect[c].arpSemitone1() : activeEffect[c].arpSemitone2();
                    semitone = basePitch[c] + offset;
                    activeFreq[c] = pitchToHz(semitone);
                }
                lastOutputSemitone[c] = semitone;

                boolean retriggerLow = retriggerPulseSamplesLeft[c] > 0;
                if (retriggerLow) {
                    retriggerPulseSamplesLeft[c]--;
                }

                outputs[pitchPort(c)][s] = activeFreq[c];
                outputs[gatePort(c)][s] = (playing && gateOn[c] && !retriggerLow) ? 1f : 0f;
            }

            if (playing && !finished) {
                samplePosition++;
                if (samplePosition >= samplesPerStep) {
                    samplePosition = 0;
                    stepPosition++;
                    if (stepPosition >= table.totalSteps) {
                        if (inputs[LOOP][s] > 0f) {
                            stepPosition = 0;
                            for (int c = 0; c < channelCount; c++) {
                                channelCursor[c] = 0;
                            }
                        } else {
                            finished = true;
                            for (int c = 0; c < channelCount; c++) {
                                gateOn[c] = false;
                                pendingActive[c] = false;
                            }
                        }
                    }
                    if (!finished) {
                        activateStep(table, stepPosition);
                    }
                }
            }
        }

        expectedBlockStart = blockStart + frameCount;
        publishedStep = stepPosition;
    }

    private void resetToStart() {
        stepPosition = 0;
        samplePosition = 0;
        finished = false;
        for (int c = 0; c < channelCount; c++) {
            channelCursor[c] = 0;
            gateOn[c] = false;
            activeEnd[c] = 0;
            sliding[c] = false;
            pendingActive[c] = false;
            noteTick[c] = 0;
            noteTickSampleCounter[c] = 0;
            arpIndex[c] = 0;
            retriggerPulseSamplesLeft[c] = 0;
            activeEffect[c] = TrackerPattern.NoteEffect.NONE;
        }
    }

    private void activateStep(TrackTable table, int step) {
        for (int c = 0; c < channelCount; c++) {
            TrackTable.ChannelTrack track = table.channels[c];
            while (channelCursor[c] < track.startStep.length && track.startStep[channelCursor[c]] < step) {
                channelCursor[c]++;
            }
            boolean wasSounding = gateOn[c];
            if (gateOn[c] && step >= activeEnd[c]) {
                gateOn[c] = false;
            }
            if (channelCursor[c] < track.startStep.length && track.startStep[channelCursor[c]] == step) {
                int noteIndex = channelCursor[c];
                channelCursor[c]++;
                TrackerPattern.NoteEffect effect = track.effect[noteIndex];
                if (effect.delayFraction() > 0f) {
                    pendingActive[c] = true;
                    pendingNoteIndex[c] = noteIndex;
                    pendingDelayFraction[c] = clamp(effect.delayFraction(), 0f, 1f);
                } else {
                    activateNote(c, track, noteIndex, step, wasSounding);
                }
            }
        }
    }

    private void activateNote(int c, TrackTable.ChannelTrack track, int noteIndex, int step, boolean wasSounding) {
        TrackerPattern.NoteEffect effect = track.effect[noteIndex];
        float fromSemitone = lastOutputSemitone[c];
        float toSemitone = track.pitch[noteIndex];

        gateOn[c] = true;
        activeEnd[c] = step + track.lengthSteps[noteIndex];
        basePitch[c] = toSemitone;
        activeEffect[c] = effect;
        noteTick[c] = 0;
        noteTickSampleCounter[c] = 0;
        arpIndex[c] = 0;
        retriggerPulseSamplesLeft[c] = 0;

        if (effect.slideMode() != TrackerPattern.SlideMode.NONE && wasSounding) {
            sliding[c] = true;
            slideFromSemitone[c] = fromSemitone;
            slideToSemitone[c] = toSemitone;
            slideSamplesTotal[c] = Math.max(1L, (long) effect.slideSteps() * samplesPerStep);
            slideSamplesElapsed[c] = 0;
        } else {
            sliding[c] = false;
            activeFreq[c] = pitchToHz(toSemitone);
            lastOutputSemitone[c] = toSemitone;
        }
    }

    private static float pitchToHz(float semitone) {
        return (float) (440.0 * Math.pow(2.0, (semitone - 69) / 12.0));
    }

    @Override
    public String getName() {
        return "Tracker";
    }
}
