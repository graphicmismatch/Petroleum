package Petroleum.Effects;

import Petroleum.Dsp.EnvelopeFollower;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class NoiseGateNode extends AbstractNode {
    public static final int IN = 0;
    public static final int THRESHOLD = 1;
    public static final int ATTACK = 2;
    public static final int RELEASE = 3;

    private double envelope = 0;
    private double gain = 0;

    private final EnvelopeFollower envelopeFollower = new EnvelopeFollower();

    public NoiseGateNode() {
        super(List.of(new Port("in", 0f), new Port("threshold", -40f), new Port("attack", 2f),
                new Port("release", 100f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float thresholdDb = inputs[THRESHOLD][s];
            float attackMs = Math.max(0.01f, inputs[ATTACK][s]);
            float releaseMs = Math.max(0.01f, inputs[RELEASE][s]);

            envelopeFollower.updateCoefficients(attackMs, releaseMs, context.sampleRate);

            float inputLevel = Math.abs(x0);
            envelope = envelopeFollower.followAttackRelease(envelope, inputLevel);

            double envelopeDb = 20 * Math.log10(Math.max(envelope, 1e-6));
            float targetGain = envelopeDb > thresholdDb ? 1f : 0f;
            gain = envelopeFollower.followAttackRelease(gain, targetGain);

            outputs[OUT][s] = (float) (x0 * gain);
        }
    }

    @Override
    public String getName() {
        return "Noise Gate";
    }
}
