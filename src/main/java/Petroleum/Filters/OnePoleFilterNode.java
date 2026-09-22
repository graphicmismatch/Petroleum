package Petroleum.Filters;

import Petroleum.Dsp.DspMath;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class OnePoleFilterNode extends AbstractNode {
    public static final int IN = 0;
    public static final int CUTOFF = 1;

    private double state = 0;

    private float cachedCutoff = Float.NaN;
    private double cachedSampleRate = -1;
    private double alpha;

    public OnePoleFilterNode() {
        super(List.of(new Port("in", 0f), new Port("cutoff", 1000f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float cutoff = Math.max(1f, inputs[CUTOFF][s]);
            if (cutoff != cachedCutoff || context.sampleRate != cachedSampleRate) {
                alpha = DspMath.onePoleLowpassAlpha(cutoff, context.sampleRate);
                cachedCutoff = cutoff;
                cachedSampleRate = context.sampleRate;
            }
            state += alpha * (inputs[IN][s] - state);
            outputs[OUT][s] = (float) state;
        }
    }

    @Override
    public String getName() {
        return "One-Pole Filter";
    }
}
