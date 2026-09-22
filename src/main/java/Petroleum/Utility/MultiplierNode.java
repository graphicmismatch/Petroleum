package Petroleum.Utility;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class MultiplierNode extends AbstractNode {
    public static final int A = 0;
    public static final int B = 1;

    public MultiplierNode() {
        super(List.of(new Port("a", 0f), new Port("b", 0f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = inputs[A][s] * inputs[B][s];
        }
    }

    @Override
    public String getName() {
        return "Multiplier";
    }
}
