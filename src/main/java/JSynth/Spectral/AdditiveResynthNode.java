package JSynth.Spectral;

import JSynth.Dsp.DspMath;
import JSynth.Dsp.EdgeDetector;
import JSynth.Dsp.Fft;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class AdditiveResynthNode extends AbstractNode {
    public static final int POSITION = 0;
    public static final int SPEED = 1;
    public static final int PITCH = 2;
    public static final int TRIGGER = 3;
    public static final int AMP = 4;

    private static final int WINDOW_SIZE = 2048;
    private static final int HOP_SIZE = 512;
    private static final int HALF = WINDOW_SIZE / 2;
    private static final int MIN_PARTIALS = 4;
    private static final int MAX_PARTIALS = 64;
    private static final int MIN_TRAJECTORY_FRAMES = 2;
    private static final double TWO_PI = 2 * Math.PI;

    private final float[] samples;
    private final float sampleRate;
    private final String name;
    private final float fidelity;
    private final int partialCount;
    private final Partial[] partials;
    private final int totalFrames;

    private final double[] phase;
    private float playheadFrames;
    private final EdgeDetector triggerEdge = new EdgeDetector();

    public AdditiveResynthNode(float[] samples, float sampleRate, String name, float fidelity) {
        super(List.of(
                new Port("position", 0f),
                new Port("speed", 1f),
                new Port("pitch", 1f),
                new Port("trigger", 0f),
                new Port("amp", 0.6f)),
                List.of("out"));
        this.samples = samples;
        this.sampleRate = sampleRate;
        this.name = name;
        this.fidelity = fidelity;
        this.partialCount = Math.max(MIN_PARTIALS,
                Math.min(MAX_PARTIALS, Math.round(MIN_PARTIALS + fidelity * (MAX_PARTIALS - MIN_PARTIALS))));

        Analysis analysis = analyze(samples, sampleRate, partialCount);
        this.partials = analysis.partials;
        this.totalFrames = analysis.totalFrames;
        this.phase = new double[partials.length];
    }

    public float[] getSamples() {
        return samples;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public float getFidelity() {
        return fidelity;
    }

    private static final class Partial {
        final int startFrame;
        final float[] frequencies;
        final float[] amplitudes;

        Partial(int startFrame, float[] frequencies, float[] amplitudes) {
            this.startFrame = startFrame;
            this.frequencies = frequencies;
            this.amplitudes = amplitudes;
        }
    }

    private static final class Trajectory {
        int startFrame;
        final List<Float> frequencies = new ArrayList<>();
        final List<Float> amplitudes = new ArrayList<>();

        float lastFrequency() {
            return frequencies.get(frequencies.size() - 1);
        }

        Partial toPartial() {
            float[] freq = new float[frequencies.size()];
            float[] amp = new float[amplitudes.size()];
            for (int i = 0; i < freq.length; i++) {
                freq[i] = frequencies.get(i);
                amp[i] = amplitudes.get(i);
            }
            return new Partial(startFrame, freq, amp);
        }
    }

    private static final class Peak {
        final float frequency;
        final float amplitude;

        Peak(float frequency, float amplitude) {
            this.frequency = frequency;
            this.amplitude = amplitude;
        }
    }

    private static final class Analysis {
        final Partial[] partials;
        final int totalFrames;

        Analysis(Partial[] partials, int totalFrames) {
            this.partials = partials;
            this.totalFrames = totalFrames;
        }
    }

    private static Analysis analyze(float[] samples, float sampleRate, int partialCount) {
        float[] hann = new float[WINDOW_SIZE];
        for (int i = 0; i < WINDOW_SIZE; i++) {
            hann[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (WINDOW_SIZE - 1));
        }

        Fft fft = new Fft(WINDOW_SIZE);
        float[] re = new float[WINDOW_SIZE];
        float[] im = new float[WINDOW_SIZE];
        float[] magnitude = new float[HALF + 1];
        float binHz = sampleRate / WINDOW_SIZE;
        float frequencyTolerance = binHz * 2.5f;
        float amplitudeScale = 2f / WINDOW_SIZE;

        List<Trajectory> active = new ArrayList<>();
        List<Trajectory> completed = new ArrayList<>();

        int frame = 0;
        for (int start = 0; start + WINDOW_SIZE <= samples.length; start += HOP_SIZE, frame++) {
            for (int i = 0; i < WINDOW_SIZE; i++) {
                re[i] = samples[start + i] * hann[i];
                im[i] = 0f;
            }
            fft.forward(re, im);
            for (int k = 0; k <= HALF; k++) {
                magnitude[k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
            }

            List<Peak> peaks = pickPeaks(magnitude, partialCount, sampleRate, amplitudeScale);

            boolean[] matched = new boolean[peaks.size()];
            List<Trajectory> stillActive = new ArrayList<>();
            for (Trajectory trajectory : active) {
                int bestIndex = -1;
                float bestDistance = frequencyTolerance;
                float lastFreq = trajectory.lastFrequency();
                for (int i = 0; i < peaks.size(); i++) {
                    if (matched[i]) {
                        continue;
                    }
                    float distance = Math.abs(peaks.get(i).frequency - lastFreq);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestIndex = i;
                    }
                }
                if (bestIndex >= 0) {
                    matched[bestIndex] = true;
                    Peak peak = peaks.get(bestIndex);
                    trajectory.frequencies.add(peak.frequency);
                    trajectory.amplitudes.add(peak.amplitude);
                    stillActive.add(trajectory);
                } else {
                    completed.add(trajectory);
                }
            }
            for (int i = 0; i < peaks.size(); i++) {
                if (!matched[i]) {
                    Trajectory trajectory = new Trajectory();
                    trajectory.startFrame = frame;
                    trajectory.frequencies.add(peaks.get(i).frequency);
                    trajectory.amplitudes.add(peaks.get(i).amplitude);
                    stillActive.add(trajectory);
                }
            }
            active = stillActive;
        }
        completed.addAll(active);

        List<Partial> result = new ArrayList<>();
        for (Trajectory trajectory : completed) {
            if (trajectory.frequencies.size() >= MIN_TRAJECTORY_FRAMES) {
                result.add(trajectory.toPartial());
            }
        }
        return new Analysis(result.toArray(new Partial[0]), frame);
    }

    private static List<Peak> pickPeaks(float[] magnitude, int partialCount, float sampleRate, float amplitudeScale) {
        List<Peak> candidates = new ArrayList<>();
        for (int k = 1; k < magnitude.length - 1; k++) {
            float m = magnitude[k];
            if (m > magnitude[k - 1] && m > magnitude[k + 1] && m > 0f) {
                float alpha = (float) Math.log(Math.max(magnitude[k - 1], 1e-9f));
                float beta = (float) Math.log(Math.max(m, 1e-9f));
                float gamma = (float) Math.log(Math.max(magnitude[k + 1], 1e-9f));
                float denom = alpha - 2 * beta + gamma;
                float p = denom != 0f ? 0.5f * (alpha - gamma) / denom : 0f;
                float interpolatedBin = k + p;
                float freq = interpolatedBin * sampleRate / WINDOW_SIZE;
                float amp = m * amplitudeScale;
                candidates.add(new Peak(freq, amp));
            }
        }
        candidates.sort((a, b) -> Float.compare(b.amplitude, a.amplitude));
        if (candidates.size() > partialCount) {
            candidates = candidates.subList(0, partialCount);
        }
        return candidates;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        float framesPerHop = HOP_SIZE;
        for (int s = 0; s < frameCount; s++) {
            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                playheadFrames = inputs[POSITION][s] * Math.max(0, totalFrames - 1);
            }

            float pitchRatio = inputs[PITCH][s];
            float sum = 0f;

            int frameLo = (int) Math.floor(playheadFrames);
            int frameHi = frameLo + 1;
            float t = playheadFrames - frameLo;

            for (int p = 0; p < partials.length; p++) {
                Partial partial = partials[p];
                int idxLo = frameLo - partial.startFrame;
                int idxHi = frameHi - partial.startFrame;
                if (idxLo < 0 || idxHi >= partial.frequencies.length) {
                    continue;
                }
                float freq = DspMath.lerp(partial.frequencies[idxLo], partial.frequencies[idxHi], t);
                float amp = DspMath.lerp(partial.amplitudes[idxLo], partial.amplitudes[idxHi], t);

                phase[p] += TWO_PI * freq * pitchRatio / context.sampleRate;
                if (phase[p] > TWO_PI) {
                    phase[p] -= TWO_PI * Math.floor(phase[p] / TWO_PI);
                }
                sum += amp * (float) Math.sin(phase[p]);
            }

            outputs[OUT][s] = sum * inputs[AMP][s];
            playheadFrames += inputs[SPEED][s] / framesPerHop;
        }
    }

    @Override
    public String getName() {
        return name;
    }
}
