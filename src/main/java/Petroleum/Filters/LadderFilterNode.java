package Petroleum.Filters;

import Petroleum.Dsp.DspMath;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class LadderFilterNode extends AbstractNode {
    public static final int IN = 0;
    public static final int CUTOFF = 1;
    public static final int RESONANCE = 2;

    private double stage1, stage2, stage3, stage4;

    private float cachedCutoff = Float.NaN;
    private double cachedSampleRate = -1;
    private double alpha;

    public LadderFilterNode() {
        super(List.of(new Port("in", 0f), new Port("cutoff", 1000f), new Port("resonance", 0.3f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float cutoff = clamp(inputs[CUTOFF][s], 1f, (float) (context.sampleRate * 0.45));
            if (cutoff != cachedCutoff || context.sampleRate != cachedSampleRate) {
                alpha = DspMath.onePoleLowpassAlpha(cutoff, context.sampleRate);
                cachedCutoff = cutoff;
                cachedSampleRate = context.sampleRate;
            }

            float resonance = clamp(inputs[RESONANCE][s], 0f, 1f) * 4f;

            double x0 = Math.tanh(inputs[IN][s] - resonance * stage4);
            stage1 += alpha * (x0 - stage1);
            stage2 += alpha * (Math.tanh(stage1) - stage2);
            stage3 += alpha * (Math.tanh(stage2) - stage3);
            stage4 += alpha * (Math.tanh(stage3) - stage4);

            outputs[OUT][s] = (float) stage4;
        }
    }

    @Override
    public String getName() {
        return "Ladder Filter";
    }
}
