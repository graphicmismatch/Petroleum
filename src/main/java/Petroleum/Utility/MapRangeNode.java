package Petroleum.Utility;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class MapRangeNode extends AbstractNode {
    public static final int IN = 0;
    public static final int IN_MIN = 1;
    public static final int IN_MAX = 2;
    public static final int OUT_MIN = 3;
    public static final int OUT_MAX = 4;

    public MapRangeNode() {
        super(List.of(new Port("in", 0f), new Port("inMin", 0f), new Port("inMax", 1f),
                new Port("outMin", 0f), new Port("outMax", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float inMin = inputs[IN_MIN][s];
            float inMax = inputs[IN_MAX][s];
            float outMin = inputs[OUT_MIN][s];
            if (inMax == inMin) {
                outputs[OUT][s] = outMin;
                continue;
            }
            float t = (inputs[IN][s] - inMin) / (inMax - inMin);
            outputs[OUT][s] = outMin + t * (inputs[OUT_MAX][s] - outMin);
        }
    }

    @Override
    public String getName() {
        return "Map Range";
    }
}
