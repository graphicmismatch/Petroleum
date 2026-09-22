package Petroleum.Effects;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class FrequencyModulatorNode extends AbstractNode {
    public static final int CARRIER = 0;
    public static final int MODULATOR = 1;
    public static final int DEPTH = 2;

    public FrequencyModulatorNode() {
        super(List.of(new Port("carrier", 440f), new Port("modulator", 0f), new Port("depth", 50f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = inputs[CARRIER][s] + inputs[DEPTH][s] * inputs[MODULATOR][s];
        }
    }

    @Override
    public String getName() {
        return "Frequency Modulator";
    }
}
