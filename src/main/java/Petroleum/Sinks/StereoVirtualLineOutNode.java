package Petroleum.Sinks;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class StereoVirtualLineOutNode extends AbstractNode implements VirtualLineOut {
    public static final int IN_L = 0;
    public static final int IN_R = 1;

    private volatile String mixerName;

    public StereoVirtualLineOutNode() {
        super(List.of(new Port("inL", 0f), new Port("inR", 0f)), List.of());
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
        return true;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
    }

    @Override
    public String getName() {
        return "Stereo Virtual Line Out";
    }
}
