package Petroleum.Effects;

import Petroleum.Dsp.EnvelopeFollower;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class AutoWahNode extends AbstractNode {
    public static final int IN = 0;
    public static final int SENSITIVITY = 1;
    public static final int ATTACK = 2;
    public static final int RELEASE = 3;
    public static final int MIN_FREQ = 4;
    public static final int MAX_FREQ = 5;
    public static final int Q = 6;
    public static final int MIX = 7;

    private final EnvelopeFollower envelopeFollower = new EnvelopeFollower();
    private double envelope = 0;

    private double x1, x2, y1, y2;

    private float cachedCutoff = Float.NaN;
    private float cachedQ = Float.NaN;
    private double cachedSampleRate = -1;
    private double b0, b1, b2, a1, a2;

    public AutoWahNode() {
        super(List.of(
                new Port("in", 0f),
                new Port("sensitivity", 1f),
                new Port("attack", 5f),
                new Port("release", 80f),
                new Port("minFreq", 300f),
                new Port("maxFreq", 3000f),
                new Port("q", 5f),
                new Port("mix", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float attackMs = Math.max(0.01f, inputs[ATTACK][s]);
            float releaseMs = Math.max(0.01f, inputs[RELEASE][s]);
            envelopeFollower.updateCoefficients(attackMs, releaseMs, context.sampleRate);
            envelope = envelopeFollower.followAttackRelease(envelope, Math.abs(x0));

            float sensitivity = Math.max(0f, inputs[SENSITIVITY][s]);
            float minFreq = inputs[MIN_FREQ][s];
            float maxFreq = inputs[MAX_FREQ][s];
            float cutoff = clamp((float) (minFreq + envelope * sensitivity * (maxFreq - minFreq)),
                    1f, (float) (context.sampleRate * 0.499));
            float q = Math.max(0.0001f, inputs[Q][s]);
            float mix = clamp(inputs[MIX][s], 0f, 1f);

            if (cutoff != cachedCutoff || q != cachedQ || context.sampleRate != cachedSampleRate) {
                recomputeCoefficients(cutoff, q, context.sampleRate);
                cachedCutoff = cutoff;
                cachedQ = q;
                cachedSampleRate = context.sampleRate;
            }

            double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;

            x2 = x1;
            x1 = x0;
            y2 = y1;
            y1 = y0;

            outputs[OUT][s] = (float) (x0 * (1 - mix) + y0 * mix);
        }
    }

    private void recomputeCoefficients(double cutoff, double q, double sampleRate) {
        double w0 = 2 * Math.PI * cutoff / sampleRate;
        double cosw0 = Math.cos(w0);
        double sinw0 = Math.sin(w0);
        double alpha = sinw0 / (2 * q);

        double a0 = 1 + alpha;
        double rawB0 = alpha;
        double rawB1 = 0;
        double rawB2 = -alpha;
        double rawA1 = -2 * cosw0;
        double rawA2 = 1 - alpha;

        b0 = rawB0 / a0;
        b1 = rawB1 / a0;
        b2 = rawB2 / a0;
        a1 = rawA1 / a0;
        a2 = rawA2 / a0;
    }

    @Override
    public String getName() {
        return "Auto Wah";
    }
}
