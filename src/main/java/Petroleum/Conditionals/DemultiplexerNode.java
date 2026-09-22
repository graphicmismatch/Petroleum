package Petroleum.Conditionals;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class DemultiplexerNode extends AbstractNode {
    public static final int SELECT = 0;
    public static final int IN = 1;

    private final int numOutputs;

    public DemultiplexerNode(int numOutputs) {
        super(List.of(new Port("select", 0f), new Port("in", 0f)), buildOutputNames(numOutputs));
        this.numOutputs = numOutputs;
    }

    private static List<String> buildOutputNames(int numOutputs) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < numOutputs; i++) {
            names.add("out" + i);
        }
        return names;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            int index = (int) clamp(inputs[SELECT][s], 0f, numOutputs - 1);
            for (int o = 0; o < numOutputs; o++) {
                outputs[o][s] = o == index ? inputs[IN][s] : 0f;
            }
        }
    }

    @Override
    public String getName() {
        return "Demultiplexer";
    }
}
