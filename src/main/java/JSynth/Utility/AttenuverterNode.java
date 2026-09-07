package JSynth.Utility;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class AttenuverterNode extends AbstractNode {
    public static final int IN = 0;
    public static final int GAIN = 1;

    public AttenuverterNode() {
        super(List.of(new Port("in", 0f), new Port("gain", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = inputs[IN][s] * inputs[GAIN][s];
        }
    }

    @Override
    public String getName() {
        return "Attenuverter";
    }
}
