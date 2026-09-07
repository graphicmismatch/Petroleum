package JSynth.Sinks;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

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
