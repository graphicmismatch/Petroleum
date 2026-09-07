package JSynth.Effects;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class ParametricEqNode extends AbstractNode {
    public static final int IN = 0;
    public static final int FIRST_BAND = 1;
    private static final int PORTS_PER_BAND = 3;

    private final int bands;
    private final PeakingStage[] stages;

    public ParametricEqNode() {
        this(3);
    }

    public ParametricEqNode(int bands) {
        super(buildPorts(bands), List.of("out"));
        this.bands = bands;
        this.stages = new PeakingStage[bands];
        for (int i = 0; i < bands; i++) {
            stages[i] = new PeakingStage();
        }
    }

    private static List<Port> buildPorts(int bands) {
        List<Port> ports = new ArrayList<>();
        ports.add(new Port("in", 0f));
        for (int i = 0; i < bands; i++) {
            ports.add(new Port("freq" + i, 1000f));
            ports.add(new Port("gain" + i, 0f));
            ports.add(new Port("q" + i, 1f));
        }
        return ports;
    }

    public int getBands() {
        return bands;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float signal = inputs[IN][s];
            for (int i = 0; i < bands; i++) {
                int base = FIRST_BAND + i * PORTS_PER_BAND;
                float freq = clamp(inputs[base][s], 1f, (float) (context.sampleRate * 0.499));
                float gain = inputs[base + 1][s];
                float q = Math.max(0.0001f, inputs[base + 2][s]);
                signal = stages[i].process(signal, freq, q, gain, context.sampleRate);
            }
            outputs[OUT][s] = signal;
        }
    }

    @Override
    public String getName() {
        return "Parametric EQ";
    }

    private static final class PeakingStage {
        private float x1, x2, y1, y2;

        private float cachedFreq = Float.NaN;
        private float cachedQ = Float.NaN;
        private float cachedGain = Float.NaN;
        private double cachedSampleRate = -1;
        private double b0, b1, b2, a1, a2;

        float process(float x0, double freq, double q, double gainDb, double sampleRate) {
            if (freq != cachedFreq || q != cachedQ || gainDb != cachedGain || sampleRate != cachedSampleRate) {
                recompute(freq, q, gainDb, sampleRate);
                cachedFreq = (float) freq;
                cachedQ = (float) q;
                cachedGain = (float) gainDb;
                cachedSampleRate = sampleRate;
            }

            double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;

            x2 = x1;
            x1 = x0;
            y2 = y1;
            y1 = (float) y0;

            return (float) y0;
        }

        private void recompute(double freq, double q, double gainDb, double sampleRate) {
            double w0 = 2 * Math.PI * freq / sampleRate;
            double cosw0 = Math.cos(w0);
            double sinw0 = Math.sin(w0);
            double alpha = sinw0 / (2 * q);
            double a = Math.pow(10, gainDb / 40);

            double a0 = 1 + alpha / a;
            double rawB0 = 1 + alpha * a;
            double rawB1 = -2 * cosw0;
            double rawB2 = 1 - alpha * a;
            double rawA1 = -2 * cosw0;
            double rawA2 = 1 - alpha / a;

            b0 = rawB0 / a0;
            b1 = rawB1 / a0;
            b2 = rawB2 / a0;
            a1 = rawA1 / a0;
            a2 = rawA2 / a0;
        }
    }
}
