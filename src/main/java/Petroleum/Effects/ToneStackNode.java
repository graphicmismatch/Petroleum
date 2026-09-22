package Petroleum.Effects;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class ToneStackNode extends AbstractNode {
    public static final int IN = 0;
    public static final int BASS = 1;
    public static final int MID = 2;
    public static final int TREBLE = 3;

    private static final float BASS_FREQ = 100f;
    private static final float MID_FREQ = 650f;
    private static final float MID_Q = 0.5f;
    private static final float TREBLE_FREQ = 2500f;
    private static final float SHELF_Q = 0.7071f;

    private final BiquadStage bassStage = new BiquadStage();
    private final BiquadStage midStage = new BiquadStage();
    private final BiquadStage trebleStage = new BiquadStage();

    public ToneStackNode() {
        super(List.of(new Port("in", 0f), new Port("bass", 0f), new Port("mid", 0f), new Port("treble", 0f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float bassDb = inputs[BASS][s];
            float midDb = inputs[MID][s];
            float trebleDb = inputs[TREBLE][s];

            float y1 = bassStage.process(x0, BASS_FREQ, SHELF_Q, bassDb, context.sampleRate, Shape.LOW_SHELF);
            float y2 = midStage.process(y1, MID_FREQ, MID_Q, midDb, context.sampleRate, Shape.PEAKING);
            float y3 = trebleStage.process(y2, TREBLE_FREQ, SHELF_Q, trebleDb, context.sampleRate, Shape.HIGH_SHELF);

            outputs[OUT][s] = y3;
        }
    }

    @Override
    public String getName() {
        return "Tone Stack";
    }

    private enum Shape {
        LOW_SHELF, PEAKING, HIGH_SHELF
    }

    private static final class BiquadStage {
        private float x1, x2, y1, y2;

        private float cachedGain = Float.NaN;
        private double cachedSampleRate = -1;
        private double b0, b1, b2, a1, a2;

        float process(float x0, double freq, double q, double gainDb, double sampleRate, Shape shape) {
            if (gainDb != cachedGain || sampleRate != cachedSampleRate) {
                recompute(freq, q, gainDb, sampleRate, shape);
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

        private void recompute(double freq, double q, double gainDb, double sampleRate, Shape shape) {
            double w0 = 2 * Math.PI * freq / sampleRate;
            double cosw0 = Math.cos(w0);
            double sinw0 = Math.sin(w0);
            double alpha = sinw0 / (2 * q);

            double a0, rawA1, rawA2, rawB0, rawB1, rawB2;
            switch (shape) {
                case PEAKING -> {
                    double a = Math.pow(10, gainDb / 40);
                    rawB0 = 1 + alpha * a;
                    rawB1 = -2 * cosw0;
                    rawB2 = 1 - alpha * a;
                    a0 = 1 + alpha / a;
                    rawA1 = -2 * cosw0;
                    rawA2 = 1 - alpha / a;
                }
                case LOW_SHELF -> {
                    double a = Math.pow(10, gainDb / 40);
                    double twoSqrtAAlpha = 2 * Math.sqrt(a) * alpha;
                    rawB0 = a * ((a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha);
                    rawB1 = 2 * a * ((a - 1) - (a + 1) * cosw0);
                    rawB2 = a * ((a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha);
                    a0 = (a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha;
                    rawA1 = -2 * ((a - 1) + (a + 1) * cosw0);
                    rawA2 = (a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha;
                }
                case HIGH_SHELF -> {
                    double a = Math.pow(10, gainDb / 40);
                    double twoSqrtAAlpha = 2 * Math.sqrt(a) * alpha;
                    rawB0 = a * ((a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha);
                    rawB1 = -2 * a * ((a - 1) + (a + 1) * cosw0);
                    rawB2 = a * ((a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha);
                    a0 = (a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha;
                    rawA1 = 2 * ((a - 1) - (a + 1) * cosw0);
                    rawA2 = (a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha;
                }
                default -> throw new IllegalStateException("Unhandled shape: " + shape);
            }

            b0 = rawB0 / a0;
            b1 = rawB1 / a0;
            b2 = rawB2 / a0;
            a1 = rawA1 / a0;
            a2 = rawA2 / a0;
        }
    }
}
