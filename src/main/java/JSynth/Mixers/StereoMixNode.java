package JSynth.Mixers;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class StereoMixNode extends AbstractNode {
    public static final int OUT_L = 0;
    public static final int OUT_R = 1;
    private static final int PAIR_COUNT = 4;

    public StereoMixNode() {
        super(buildPorts(), List.of("outL", "outR"));
    }

    private static List<Port> buildPorts() {
        List<Port> ports = new ArrayList<>();
        for (int i = 0; i < PAIR_COUNT; i++) {
            ports.add(new Port("inL" + i, 0f));
            ports.add(new Port("inR" + i, 0f));
        }
        return ports;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float left = 0f;
            float right = 0f;
            for (int i = 0; i < PAIR_COUNT; i++) {
                left += inputs[i * 2][s];
                right += inputs[i * 2 + 1][s];
            }
            outputs[OUT_L][s] = left;
            outputs[OUT_R][s] = right;
        }
    }

    @Override
    public String getName() {
        return "Stereo Mix";
    }
}
