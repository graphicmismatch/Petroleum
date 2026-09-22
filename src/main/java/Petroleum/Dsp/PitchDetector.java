package Petroleum.Dsp;

public class PitchDetector {
    private static final int HOP_SIZE = 512;
    private static final float ABSOLUTE_THRESHOLD = 0.15f;
    private static final float ENERGY_THRESHOLD = 1e-6f;

    private final double sampleRate;
    private final int tauMax;
    private final int bufferSize;

    private final float[] ring;
    private final float[] linearized;
    private final float[] diff;
    private final float[] cmnd;
    private int writeIndex;
    private int samplesSinceHop;

    private float frequencyHz;
    private float confidence;
    private boolean voiced;

    public PitchDetector(double sampleRate, float minFrequencyHz) {
        this.sampleRate = sampleRate;
        this.tauMax = Math.max(2, (int) Math.ceil(sampleRate / minFrequencyHz));
        this.bufferSize = tauMax * 2;
        this.ring = new float[bufferSize];
        this.linearized = new float[bufferSize];
        this.diff = new float[tauMax];
        this.cmnd = new float[tauMax];
        this.frequencyHz = (float) minFrequencyHz;
    }

    public void pushSample(float sample) {
        ring[writeIndex] = sample;
        writeIndex++;
        if (writeIndex >= bufferSize) {
            writeIndex = 0;
        }

        samplesSinceHop++;
        if (samplesSinceHop >= HOP_SIZE) {
            samplesSinceHop = 0;
            analyze();
        }
    }

    public float getFrequencyHz() {
        return frequencyHz;
    }

    public float getConfidence() {
        return confidence;
    }

    public boolean isVoiced() {
        return voiced;
    }

    private void analyze() {
        int idx = writeIndex;
        for (int i = 0; i < bufferSize; i++) {
            linearized[i] = ring[idx];
            idx++;
            if (idx >= bufferSize) {
                idx = 0;
            }
        }

        int window = tauMax;
        double energy = 0;
        for (int j = 0; j < window; j++) {
            energy += linearized[j] * (double) linearized[j];
        }
        if (energy / window < ENERGY_THRESHOLD) {
            voiced = false;
            confidence = 0f;
            return;
        }

        diff[0] = 0f;
        for (int tau = 1; tau < tauMax; tau++) {
            double sum = 0;
            for (int j = 0; j < window; j++) {
                float delta = linearized[j] - linearized[j + tau];
                sum += delta * delta;
            }
            diff[tau] = (float) sum;
        }

        cmnd[0] = 1f;
        double runningSum = 0;
        for (int tau = 1; tau < tauMax; tau++) {
            runningSum += diff[tau];
            cmnd[tau] = runningSum == 0 ? 1f : (float) (diff[tau] * tau / runningSum);
        }

        int bestTau = -1;
        for (int tau = 2; tau < tauMax - 1; tau++) {
            if (cmnd[tau] < ABSOLUTE_THRESHOLD) {
                while (tau + 1 < tauMax - 1 && cmnd[tau + 1] < cmnd[tau]) {
                    tau++;
                }
                bestTau = tau;
                break;
            }
        }

        if (bestTau < 0) {
            voiced = false;
            confidence = 0f;
            return;
        }

        float tauRefined = bestTau;
        float s0 = cmnd[bestTau - 1];
        float s1 = cmnd[bestTau];
        float s2 = cmnd[bestTau + 1];
        float denominator = 2 * (2 * s1 - s2 - s0);
        if (denominator != 0f) {
            tauRefined = bestTau + (s2 - s0) / denominator;
        }

        frequencyHz = (float) (sampleRate / tauRefined);
        confidence = 1f - cmnd[bestTau];
        voiced = true;
    }
}
