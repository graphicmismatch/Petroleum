package JSynth.Effects;

import JSynth.Dsp.EnvelopeFollower;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class EnvelopeFollowerNode extends AbstractNode {
    public static final int IN = 0;
    public static final int ATTACK = 1;
    public static final int RELEASE = 2;

    private float envelope = 0f;

    private final EnvelopeFollower envelopeFollower = new EnvelopeFollower();

    public EnvelopeFollowerNode() {
        super(List.of(new Port("in", 0f), new Port("attack", 10f), new Port("release", 100f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float attackMs = Math.max(0.01f, inputs[ATTACK][s]);
            float releaseMs = Math.max(0.01f, inputs[RELEASE][s]);

            envelopeFollower.updateCoefficients(attackMs, releaseMs, context.sampleRate);

            float inputLevel = Math.abs(inputs[IN][s]);
            envelope = envelopeFollower.followAttackRelease(envelope, inputLevel);

            outputs[OUT][s] = envelope;
        }
    }

    @Override
    public String getName() {
        return "Level Follower";
    }
}
