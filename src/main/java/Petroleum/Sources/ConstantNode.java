package Petroleum.Sources;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Stateful;

import java.util.List;
import java.util.Map;

public class ConstantNode extends AbstractNode implements Stateful {
    private volatile float value;

    public ConstantNode() {
        this(0f);
    }

    public ConstantNode(float value) {
        super(List.of(), List.of("out"));
        this.value = value;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    @Override
    public void saveState(Map<String, String> state) {
        state.put("value", Float.toString(value));
    }

    @Override
    public void loadState(Map<String, String> state) {
        String saved = state.get("value");
        if (saved != null) {
            value = Float.parseFloat(saved);
        }
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        float v = value;
        java.util.Arrays.fill(outputs[OUT], 0, frameCount, v);
    }

    @Override
    public String getName() {
        return "Constant";
    }
}
