package JSynth.Sinks;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class VirtualLineOutNode extends AbstractNode implements VirtualLineOut {
    public static final int IN = 0;

    private volatile String mixerName;

    public VirtualLineOutNode() {
        super(List.of(new Port("in", 0f)), List.of());
    }

    @Override
    public String getMixerName() {
        return mixerName;
    }

    @Override
    public void setMixerName(String mixerName) {
        this.mixerName = mixerName;
    }

    @Override
    public boolean isStereo() {
        return false;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
    }

    @Override
    public String getName() {
        return "Virtual Line Out";
    }
}
