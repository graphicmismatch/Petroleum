package JSynth.Conditionals;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class GateNode extends AbstractNode {
    public static final int IN = 0;
    public static final int CONTROL = 1;
    public static final int THRESHOLD = 2;

    public GateNode() {
        super(List.of(new Port("in", 0f), new Port("control", 0f), new Port("threshold", 0.5f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = inputs[CONTROL][s] > inputs[THRESHOLD][s] ? inputs[IN][s] : 0f;
        }
    }

    @Override
    public String getName() {
        return "CV Gate";
    }
}
