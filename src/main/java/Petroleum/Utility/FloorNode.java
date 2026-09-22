package Petroleum.Utility;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class FloorNode extends AbstractNode {
    public static final int IN = 0;

    public FloorNode() {
        super(List.of(new Port("in", 0f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = (float) Math.floor(inputs[IN][s]);
        }
    }

    @Override
    public String getName() {
        return "Floor";
    }
}
