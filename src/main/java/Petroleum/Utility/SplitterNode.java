package Petroleum.Utility;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class SplitterNode extends AbstractNode {
    public static final int IN = 0;

    public SplitterNode() {
        this(4);
    }

    public SplitterNode(int numOutputs) {
        super(List.of(new Port("in", 0f)), buildOutputNames(numOutputs));
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
        for (int i = 0; i < outputs.length; i++) {
            System.arraycopy(inputs[IN], 0, outputs[i], 0, frameCount);
        }
    }

    @Override
    public String getName() {
        return "Splitter";
    }
}
