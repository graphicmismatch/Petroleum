package JSynth.Effects;

import JSynth.Dsp.DelayLine;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class DelayNode extends AbstractNode {
    public static final int IN = 0;
    public static final int TIME = 1;
    public static final int FEEDBACK = 2;
    public static final int MIX = 3;

    private final double maxDelaySeconds;
    private DelayLine delayLine;

    public DelayNode() {
        this(2.0);
    }

    public DelayNode(double maxDelaySeconds) {
        super(List.of(new Port("in", 0f), new Port("time", 0.3f), new Port("feedback", 0.3f), new Port("mix", 0.5f)),
                List.of("out"));
        this.maxDelaySeconds = maxDelaySeconds;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (delayLine == null) {
            delayLine = new DelayLine(Math.max(1, (int) Math.ceil(context.sampleRate * maxDelaySeconds)));
        }

        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float feedback = clamp(inputs[FEEDBACK][s], 0f, 0.98f);
            float mix = clamp(inputs[MIX][s], 0f, 1f);
            double delaySeconds = clamp(inputs[TIME][s], 0f, (float) maxDelaySeconds);
            double delaySamples = delaySeconds * context.sampleRate;

            float delayed = delayLine.readFractional(delaySamples);
            delayLine.write(x0 + delayed * feedback);

            outputs[OUT][s] = x0 * (1 - mix) + delayed * mix;
        }
    }

    @Override
    public String getName() {
        return "Delay";
    }
}
