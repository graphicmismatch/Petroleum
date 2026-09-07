package JSynth.Dsp;

import java.util.Random;

public final class GrainPool {
    public static final int DEFAULT_MAX_GRAINS = 32;

    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final float MIN_GRAIN_SECONDS = 0.001f;
    private static final float MIN_PITCH_MAGNITUDE = 0.01f;

    public interface Source {
        float read(double positionInSamples);

        default int length() {
            return Integer.MAX_VALUE;
        }
    }

    private final int maxGrains;
    private final double[] position;
    private final float[] increment;
    private final int[] age;
    private final int[] duration;
    private final float[] amp;
    private final boolean[] active;
    private final float[] filterState;
    private final float[] filterAlpha;

    private final EdgeDetector triggerEdge = new EdgeDetector();
    private final Random random = new Random();
    private double spawnPhase;

    public GrainPool(int maxGrains) {
        this.maxGrains = Math.max(1, maxGrains);
        this.position = new double[this.maxGrains];
        this.increment = new float[this.maxGrains];
        this.age = new int[this.maxGrains];
        this.duration = new int[this.maxGrains];
        this.amp = new float[this.maxGrains];
        this.active = new boolean[this.maxGrains];
        this.filterState = new float[this.maxGrains];
        this.filterAlpha = new float[this.maxGrains];
    }

    public float process(Source source,
                          double sampleRate,
                          double positionSamples,
                          float positionJitter,
                          float grainSizeSeconds,
                          float grainSizeJitter,
                          float density,
                          float pitch,
                          float pitchJitter,
                          float amplitude,
                          float trigger) {
        return process(source, sampleRate, positionSamples, positionJitter, grainSizeSeconds,
                grainSizeJitter, density, pitch, pitchJitter, amplitude, trigger, 0f, 0f);
    }

    public float process(Source source,
                          double sampleRate,
                          double positionSamples,
                          float positionJitter,
                          float grainSizeSeconds,
                          float grainSizeJitter,
                          float density,
                          float pitch,
                          float pitchJitter,
                          float amplitude,
                          float trigger,
                          float filterCutoffHz,
                          float filterCutoffJitter) {
        spawnPhase += Math.max(0f, density) / sampleRate;
        if (spawnPhase >= 1.0) {
            spawnPhase -= 1.0;
            spawn(positionSamples, grainSizeSeconds, positionJitter, grainSizeJitter,
                    pitch, pitchJitter, amplitude, sampleRate, filterCutoffHz, filterCutoffJitter);
        }
        if (triggerEdge.edge(trigger) > 0) {
            spawn(positionSamples, grainSizeSeconds, positionJitter, grainSizeJitter,
                    pitch, pitchJitter, amplitude, sampleRate, filterCutoffHz, filterCutoffJitter);
        }

        float sum = 0f;
        for (int i = 0; i < maxGrains; i++) {
            if (!active[i]) {
                continue;
            }
            float t = age[i] / (float) duration[i];
            float window = 0.5f - 0.5f * (float) Math.cos(TWO_PI * t);
            float raw = source.read(position[i]);
            filterState[i] += filterAlpha[i] * (raw - filterState[i]);
            sum += filterState[i] * window * amp[i];
            position[i] += increment[i];
            age[i]++;
            if (age[i] >= duration[i]) {
                active[i] = false;
            }
        }
        return sum;
    }

    private void spawn(double positionSamples, float grainSizeSeconds, float positionJitter,
                        float grainSizeJitter, float pitch, float pitchJitter,
                        float amplitude, double sampleRate, float filterCutoffHz, float filterCutoffJitter) {
        int slot = findFreeSlot();
        if (slot < 0) {
            slot = findOldestSlot();
        }

        float jitteredSizeSeconds = grainSizeSeconds
                * (1f + (float) (random.nextDouble() * 2 - 1) * grainSizeJitter);
        jitteredSizeSeconds = Math.max(MIN_GRAIN_SECONDS, jitteredSizeSeconds);
        int grainSamples = Math.max(1, (int) Math.round(jitteredSizeSeconds * sampleRate));

        double jitteredPosition = positionSamples
                + (random.nextDouble() * 2 - 1) * positionJitter * grainSamples;

        float jitteredPitch = pitch * (1f + (float) (random.nextDouble() * 2 - 1) * pitchJitter);
        jitteredPitch = Math.max(MIN_PITCH_MAGNITUDE, jitteredPitch);

        position[slot] = jitteredPosition;
        increment[slot] = jitteredPitch;
        age[slot] = 0;
        duration[slot] = grainSamples;
        amp[slot] = amplitude;
        active[slot] = true;
        filterState[slot] = 0f;
        if (filterCutoffHz <= 0f) {
            filterAlpha[slot] = 1f;
        } else {
            float jitteredCutoff = filterCutoffHz
                    * (1f + (float) (random.nextDouble() * 2 - 1) * filterCutoffJitter);
            jitteredCutoff = Math.max(20f, jitteredCutoff);
            filterAlpha[slot] = DspMath.onePoleLowpassAlpha(jitteredCutoff, sampleRate);
        }
    }

    private int findFreeSlot() {
        for (int i = 0; i < maxGrains; i++) {
            if (!active[i]) {
                return i;
            }
        }
        return -1;
    }

    private int findOldestSlot() {
        int oldest = 0;
        int oldestAge = age[0];
        for (int i = 1; i < maxGrains; i++) {
            if (age[i] > oldestAge) {
                oldest = i;
                oldestAge = age[i];
            }
        }
        return oldest;
    }
}
