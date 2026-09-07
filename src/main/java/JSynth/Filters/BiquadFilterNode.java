package JSynth.Filters;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class BiquadFilterNode extends AbstractNode {
    public enum FilterType {
        LOWPASS("Lowpass"), HIGHPASS("Highpass"), BANDPASS("Bandpass"), NOTCH("Notch"),
        ALLPASS("Allpass"), PEAKING("Peaking"), LOWSHELF("Low Shelf"), HIGHSHELF("High Shelf");

        private final String label;

        FilterType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final int IN = 0;
    public static final int CUTOFF = 1;
    public static final int Q = 2;
    public static final int GAIN = 3;

    private final FilterType type;

    private float x1, x2, y1, y2;

    private float cachedCutoff = Float.NaN;
    private float cachedQ = Float.NaN;
    private float cachedGain = Float.NaN;
    private double cachedSampleRate = -1;
    private double b0, b1, b2, a1, a2;

    public BiquadFilterNode(FilterType type) {
        super(List.of(new Port("in", 0f), new Port("cutoff", 1000f), new Port("q", 0.7071f), new Port("gain", 0f)),
                List.of("out"));
        this.type = type;
    }

    public FilterType getType() {
        return type;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float cutoff = clamp(inputs[CUTOFF][s], 1f, (float) (context.sampleRate * 0.499));
            float q = Math.max(0.0001f, inputs[Q][s]);
            float gainDb = inputs[GAIN][s];

            if (cutoff != cachedCutoff || q != cachedQ || gainDb != cachedGain || context.sampleRate != cachedSampleRate) {
                recomputeCoefficients(cutoff, q, gainDb, context.sampleRate);
                cachedCutoff = cutoff;
                cachedQ = q;
                cachedGain = gainDb;
                cachedSampleRate = context.sampleRate;
            }

            double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;

            x2 = x1;
            x1 = x0;
            y2 = y1;
            y1 = (float) y0;

            outputs[OUT][s] = (float) y0;
        }
    }

    private void recomputeCoefficients(double cutoff, double q, double gainDb, double sampleRate) {
        double w0 = 2 * Math.PI * cutoff / sampleRate;
        double cosw0 = Math.cos(w0);
        double sinw0 = Math.sin(w0);
        double alpha = sinw0 / (2 * q);

        double a0, rawA1, rawA2, rawB0, rawB1, rawB2;
        switch (type) {
            case HIGHPASS:
                rawB0 = (1 + cosw0) / 2;
                rawB1 = -(1 + cosw0);
                rawB2 = (1 + cosw0) / 2;
                a0 = 1 + alpha;
                rawA1 = -2 * cosw0;
                rawA2 = 1 - alpha;
                break;
            case BANDPASS:
                rawB0 = alpha;
                rawB1 = 0;
                rawB2 = -alpha;
                a0 = 1 + alpha;
                rawA1 = -2 * cosw0;
                rawA2 = 1 - alpha;
                break;
            case NOTCH:
                rawB0 = 1;
                rawB1 = -2 * cosw0;
                rawB2 = 1;
                a0 = 1 + alpha;
                rawA1 = -2 * cosw0;
                rawA2 = 1 - alpha;
                break;
            case ALLPASS:
                rawB0 = 1 - alpha;
                rawB1 = -2 * cosw0;
                rawB2 = 1 + alpha;
                a0 = 1 + alpha;
                rawA1 = -2 * cosw0;
                rawA2 = 1 - alpha;
                break;
            case PEAKING: {
                double a = Math.pow(10, gainDb / 40);
                rawB0 = 1 + alpha * a;
                rawB1 = -2 * cosw0;
                rawB2 = 1 - alpha * a;
                a0 = 1 + alpha / a;
                rawA1 = -2 * cosw0;
                rawA2 = 1 - alpha / a;
                break;
            }
            case LOWSHELF: {
                double a = Math.pow(10, gainDb / 40);
                double twoSqrtAAlpha = 2 * Math.sqrt(a) * alpha;
                rawB0 = a * ((a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha);
                rawB1 = 2 * a * ((a - 1) - (a + 1) * cosw0);
                rawB2 = a * ((a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha);
                a0 = (a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha;
                rawA1 = -2 * ((a - 1) + (a + 1) * cosw0);
                rawA2 = (a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha;
                break;
            }
            case HIGHSHELF: {
                double a = Math.pow(10, gainDb / 40);
                double twoSqrtAAlpha = 2 * Math.sqrt(a) * alpha;
                rawB0 = a * ((a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha);
                rawB1 = -2 * a * ((a - 1) + (a + 1) * cosw0);
                rawB2 = a * ((a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha);
                a0 = (a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha;
                rawA1 = 2 * ((a - 1) - (a + 1) * cosw0);
                rawA2 = (a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha;
                break;
            }
            case LOWPASS:
            default:
                rawB0 = (1 - cosw0) / 2;
                rawB1 = 1 - cosw0;
                rawB2 = (1 - cosw0) / 2;
                a0 = 1 + alpha;
                rawA1 = -2 * cosw0;
                rawA2 = 1 - alpha;
                break;
        }

        b0 = rawB0 / a0;
        b1 = rawB1 / a0;
        b2 = rawB2 / a0;
        a1 = rawA1 / a0;
        a2 = rawA2 / a0;
    }

    @Override
    public String getName() {
        return "Biquad " + type.label();
    }
}
