package Petroleum.Utility;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class PitchToTimeNode extends AbstractNode {
    public static final int PITCH = 0;
    private static final float MIN_PITCH = 1e-6f;

    public PitchToTimeNode() {
        super(List.of(new Port("pitch", 440f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = 1f / Math.max(MIN_PITCH, inputs[PITCH][s]);
        }
    }

    @Override
    public String getName() {
        return "Pitch to Time";
    }
}
