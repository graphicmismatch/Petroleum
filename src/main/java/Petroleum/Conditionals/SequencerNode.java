package Petroleum.Conditionals;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class SequencerNode extends AbstractNode {
    public static final int TRIGGER = 0;
    public static final int RESET = 1;
    public static final int FIRST_STEP = 2;

    private final int steps;

    private int position;
    private final EdgeDetector triggerEdge = new EdgeDetector();
    private final EdgeDetector resetEdge = new EdgeDetector();

    public SequencerNode(int steps) {
        super(buildPorts(steps), List.of("out"));
        this.steps = steps;
    }

    private static List<Port> buildPorts(int steps) {
        List<Port> ports = new ArrayList<>();
        ports.add(new Port("trigger", 0f));
        ports.add(new Port("reset", 0f));
        for (int i = 0; i < steps; i++) {
            ports.add(new Port("step" + i, 0f));
        }
        return ports;
    }

    public int getSteps() {
        return steps;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            if (resetEdge.edge(inputs[RESET][s]) > 0) {
                position = 0;
            }

            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                position++;
                if (position >= steps) {
                    position = 0;
                }
            }

            outputs[OUT][s] = inputs[FIRST_STEP + position][s];
        }
    }

    @Override
    public String getName() {
        return "Sequencer";
    }
}
