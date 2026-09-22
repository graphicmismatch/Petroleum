package Petroleum.Conditionals;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class SelectNode extends AbstractNode {
    public static final int CONDITION = 0;
    public static final int IF_TRUE = 1;
    public static final int IF_FALSE = 2;
    public static final int THRESHOLD = 3;

    public SelectNode() {
        super(List.of(new Port("condition", 0f), new Port("ifTrue", 0f), new Port("ifFalse", 0f),
                new Port("threshold", 0.5f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = inputs[CONDITION][s] > inputs[THRESHOLD][s] ? inputs[IF_TRUE][s] : inputs[IF_FALSE][s];
        }
    }

    @Override
    public String getName() {
        return "Select";
    }
}
