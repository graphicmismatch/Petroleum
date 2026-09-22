package Petroleum.Sinks;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class AudioOutNode extends AbstractNode implements AudioSink {
    public static final int IN = 0;

    public AudioOutNode() {
        super(List.of(new Port("in", 0f)), List.of());
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
    }

    @Override
    public String getName() {
        return "Audio Out";
    }
}
