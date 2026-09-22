package Petroleum.Conditionals;

import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;
import java.util.List;
import Petroleum.Graph.AbstractNode;

public class ToggleNode extends AbstractNode {
    public static final int TRIGGER = 0;
    public static final int THRESHOLD = 1;

    private boolean state = false;
    private float lastTrigger = 0f;

    public ToggleNode() {
        super(List.of(new Port("trigger", 0f), new Port("threshold", 0.5f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float trigger = inputs[TRIGGER][s];
            float threshold = inputs[THRESHOLD][s];
            if (trigger > threshold && lastTrigger <= threshold) {
                state = !state;
            }
            lastTrigger = trigger;
            outputs[OUT][s] = state ? 1f : 0f;
        }
    }

    @Override
    public String getName() {
        return "Toggle";
    }
}
