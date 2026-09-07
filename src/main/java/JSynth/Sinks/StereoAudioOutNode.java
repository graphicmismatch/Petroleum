package JSynth.Sinks;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class StereoAudioOutNode extends AbstractNode implements AudioSink {
    public static final int IN_L = 0;
    public static final int IN_R = 1;

    public StereoAudioOutNode() {
        super(List.of(new Port("inL", 0f), new Port("inR", 0f)), List.of());
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
    }

    @Override
    public String getName() {
        return "Stereo Audio Out";
    }
}
