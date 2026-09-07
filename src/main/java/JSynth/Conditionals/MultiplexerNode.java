package JSynth.Conditionals;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class MultiplexerNode extends AbstractNode {
    public static final int SELECT = 0;

    private final int numInputs;

    public MultiplexerNode(int numInputs) {
        super(buildPorts(numInputs), List.of("out"));
        this.numInputs = numInputs;
    }

    private static List<Port> buildPorts(int numInputs) {
        List<Port> ports = new ArrayList<>();
        ports.add(new Port("select", 0f));
        for (int i = 0; i < numInputs; i++) {
            ports.add(new Port("in" + i, 0f));
        }
        return ports;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            int index = (int) clamp(inputs[SELECT][s], 0f, numInputs - 1);
            outputs[OUT][s] = inputs[1 + index][s];
        }
    }

    @Override
    public String getName() {
        return "Multiplexer";
    }
}
