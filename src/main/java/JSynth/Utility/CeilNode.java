package JSynth.Utility;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class CeilNode extends AbstractNode {
    public static final int IN = 0;

    public CeilNode() {
        super(List.of(new Port("in", 0f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = (float) Math.ceil(inputs[IN][s]);
        }
    }

    @Override
    public String getName() {
        return "Ceil";
    }
}
