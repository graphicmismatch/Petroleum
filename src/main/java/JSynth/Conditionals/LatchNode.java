package JSynth.Conditionals;

import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class LatchNode extends AbstractNode {
    public static final int IN = 0;
    public static final int TRIGGER = 1;

    private float held;
    private final EdgeDetector triggerEdge = new EdgeDetector();

    public LatchNode() {
        super(List.of(new Port("in", 0f), new Port("trigger", 0f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                held = inputs[IN][s];
            }
            outputs[OUT][s] = held;
        }
    }

    @Override
    public String getName() {
        return "Latch";
    }
}
