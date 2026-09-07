package JSynth.Utility;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class ClampNode extends AbstractNode {
    public static final int IN = 0;
    public static final int MIN = 1;
    public static final int MAX = 2;

    public ClampNode() {
        super(List.of(new Port("in", 0f), new Port("min", -1f), new Port("max", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = clamp(inputs[IN][s], inputs[MIN][s], inputs[MAX][s]);
        }
    }

    @Override
    public String getName() {
        return "Clamp";
    }
}
