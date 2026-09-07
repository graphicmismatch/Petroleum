package JSynth.Effects;

import JSynth.Dsp.EnvelopeFollower;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class CompressorNode extends AbstractNode {
    public enum CompressorType {
        PEAK("Peak"), RMS("RMS"), FEEDBACK("Feedback");

        private final String label;

        CompressorType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final int IN = 0;
    public static final int THRESHOLD = 1;
    public static final int RATIO = 2;
    public static final int ATTACK = 3;
    public static final int RELEASE = 4;
    public static final int MAKEUP = 5;

    private final CompressorType type;

    private float envelope = 0f;
    private float previousOutput = 0f;

    private final EnvelopeFollower envelopeFollower = new EnvelopeFollower();

    public CompressorNode(CompressorType type) {
        super(List.of(new Port("in", 0f), new Port("threshold", -18f), new Port("ratio", 4f),
                new Port("attack", 10f), new Port("release", 100f), new Port("makeup", 0f)), List.of("out"));
        this.type = type;
    }

    public CompressorType getType() {
        return type;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float thresholdDb = inputs[THRESHOLD][s];
            float ratio = Math.max(1f, inputs[RATIO][s]);
            float attackMs = Math.max(0.01f, inputs[ATTACK][s]);
            float releaseMs = Math.max(0.01f, inputs[RELEASE][s]);
            float makeupDb = inputs[MAKEUP][s];

            envelopeFollower.updateCoefficients(attackMs, releaseMs, context.sampleRate);

            float detectorLevel = switch (type) {
                case PEAK -> Math.abs(x0);
                case RMS -> x0 * x0;
                case FEEDBACK -> Math.abs(previousOutput);
            };
            envelope = envelopeFollower.followAttackRelease(envelope, detectorLevel);
            float sensedLevel = type == CompressorType.RMS ? (float) Math.sqrt(envelope) : envelope;

            double envelopeDb = 20 * Math.log10(Math.max(sensedLevel, 1e-6));
            double gainReductionDb = envelopeDb > thresholdDb
                    ? (thresholdDb - envelopeDb) * (1 - 1.0 / ratio)
                    : 0;

            float gain = (float) Math.pow(10, (gainReductionDb + makeupDb) / 20.0);
            float y0 = x0 * gain;
            outputs[OUT][s] = y0;
            previousOutput = y0;
        }
    }

    @Override
    public String getName() {
        return "Compressor " + type.label();
    }
}
