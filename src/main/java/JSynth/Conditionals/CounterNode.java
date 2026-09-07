package JSynth.Conditionals;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;
import java.util.List;
import JSynth.Dsp.EdgeDetector;

public class CounterNode extends AbstractNode {
    public static final int TRIGGER = 0;
    public static final int WRAP = 1;
    public static final int RESET = 2;
    public static final int OVERFLOW = 1;
    private int count;
    private final EdgeDetector triggerEdge = new EdgeDetector();
    private final EdgeDetector resetEdge = new EdgeDetector();
    private int overflow = 0;

    public CounterNode() {
        super(List.of(new Port("trigger", 0f), new Port("wrap", 0f), new Port("reset", 0f)),
                List.of("out", "ovfl"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            if (resetEdge.edge(inputs[RESET][s]) > 0) {
                count = 0;
            }
            overflow = 0;
            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                count++;
                int wrap = (int) inputs[WRAP][s];
                if (wrap > 0 && count >= wrap) {
                    count = 0;
                    overflow = 1;
                }
            }

            outputs[OUT][s] = count;
            outputs[OVERFLOW][s] = overflow;
        }
    }

    @Override
    public String getName() {
        return "Counter";
    }
}
