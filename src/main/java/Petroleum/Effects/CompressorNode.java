package Petroleum.Effects;

import Petroleum.Dsp.EnvelopeFollower;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

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
    public static final int SIDECHAIN = 1;
    public static final int EXTERNAL_KEY = 2;
    public static final int THRESHOLD = 3;
    public static final int RATIO = 4;
    public static final int ATTACK = 5;
    public static final int RELEASE = 6;
    public static final int MAKEUP = 7;

    private final CompressorType type;

    private double envelope = 0;
    private float previousOutput = 0f;

    private final EnvelopeFollower envelopeFollower = new EnvelopeFollower();

    public CompressorNode(CompressorType type) {
        super(List.of(new Port("in", 0f), new Port("sidechain", 0f), new Port("externalKey", 0f),
                new Port("threshold", -18f), new Port("ratio", 4f),
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
            boolean externalKey = inputs[EXTERNAL_KEY][s] > 0f;
            float keySignal = externalKey ? inputs[SIDECHAIN][s] : x0;
            float thresholdDb = inputs[THRESHOLD][s];
            float ratio = Math.max(1f, inputs[RATIO][s]);
            float attackMs = Math.max(0.01f, inputs[ATTACK][s]);
            float releaseMs = Math.max(0.01f, inputs[RELEASE][s]);
            float makeupDb = inputs[MAKEUP][s];

            envelopeFollower.updateCoefficients(attackMs, releaseMs, context.sampleRate);

            float detectorLevel = switch (type) {
                case PEAK -> Math.abs(keySignal);
                case RMS -> keySignal * keySignal;
                case FEEDBACK -> externalKey ? Math.abs(keySignal) : Math.abs(previousOutput);
            };
            envelope = envelopeFollower.followAttackRelease(envelope, detectorLevel);
            double sensedLevel = type == CompressorType.RMS ? Math.sqrt(envelope) : envelope;

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
