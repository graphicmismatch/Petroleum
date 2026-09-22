package Petroleum.Mixers;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MixNode extends AbstractNode {
    private final float[] weights;

    public MixNode(Map<String, Float> weights) {
        super(buildPorts(weights), List.of("out"));
        this.weights = new float[weights.size()];
        int i = 0;
        for (Map.Entry<String, Float> entry : weights.entrySet()) {
            this.weights[i++] = entry.getValue();
        }
    }

    private static List<Port> buildPorts(Map<String, Float> weights) {
        List<Port> ports = new ArrayList<>();
        for (String name : weights.keySet()) {
            ports.add(new Port(name, 0f));
        }
        return ports;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float sum = 0f;
            for (int i = 0; i < weights.length; i++) {
                sum += inputs[i][s] * weights[i];
            }
            outputs[OUT][s] = sum;
        }
    }

    @Override
    public String getName() {
        return "Mix";
    }
}
