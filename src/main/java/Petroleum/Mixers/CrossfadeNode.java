package Petroleum.Mixers;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class CrossfadeNode extends AbstractNode {
    public static final int A = 0;
    public static final int B = 1;
    public static final int MIX = 2;

    public CrossfadeNode() {
        super(List.of(new Port("a", 0f), new Port("b", 0f), new Port("mix", 0.5f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float mix = clamp(inputs[MIX][s], 0f, 1f);
            outputs[OUT][s] = inputs[A][s] * (1f - mix) + inputs[B][s] * mix;
        }
    }

    @Override
    public String getName() {
        return "Crossfade";
    }
}
