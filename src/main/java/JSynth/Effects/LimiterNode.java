package JSynth.Effects;

import JSynth.Dsp.EnvelopeFollower;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class LimiterNode extends AbstractNode {
    public static final int IN = 0;
    public static final int CEILING = 1;
    public static final int RELEASE = 2;

    private float envelope = 0f;

    private final EnvelopeFollower envelopeFollower = new EnvelopeFollower();

    public LimiterNode() {
        super(List.of(new Port("in", 0f), new Port("ceiling", 0.9f), new Port("release", 50f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float ceiling = clamp(inputs[CEILING][s], 0.01f, 1f);
            float releaseMs = Math.max(0.01f, inputs[RELEASE][s]);

            envelopeFollower.updateReleaseCoefficient(releaseMs, context.sampleRate);

            float inputLevel = Math.abs(x0);
            envelope = envelopeFollower.followInstantAttack(envelope, inputLevel);

            float gain = envelope > ceiling ? ceiling / envelope : 1f;
            outputs[OUT][s] = x0 * gain;
        }
    }

    @Override
    public String getName() {
        return "Limiter";
    }
}
