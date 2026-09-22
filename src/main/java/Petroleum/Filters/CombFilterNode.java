package Petroleum.Filters;

import Petroleum.Dsp.DelayLine;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class CombFilterNode extends AbstractNode {
    public static final int IN = 0;
    public static final int TIME = 1;
    public static final int FEEDBACK = 2;

    private static final double MAX_DELAY_MS = 50.0;

    private DelayLine delayLine;

    public CombFilterNode() {
        super(List.of(new Port("in", 0f), new Port("time", 10f), new Port("feedback", 0.5f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (delayLine == null) {
            delayLine = new DelayLine(Math.max(1, (int) Math.ceil(context.sampleRate * MAX_DELAY_MS / 1000.0)));
        }

        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float feedback = clamp(inputs[FEEDBACK][s], -0.99f, 0.99f);
            double delayMs = clamp(inputs[TIME][s], 0f, (float) MAX_DELAY_MS);
            double delaySamples = delayMs / 1000.0 * context.sampleRate;

            float delayed = delayLine.readFractional(delaySamples);

            float y0 = x0 + feedback * delayed;
            delayLine.write(y0);

            outputs[OUT][s] = y0;
        }
    }

    @Override
    public String getName() {
        return "Comb Filter";
    }
}
