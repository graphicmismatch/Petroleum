package JSynth.Utility;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class TimeToPitchNode extends AbstractNode {
    public static final int TIME = 0;
    private static final float MIN_TIME = 1e-6f;

    public TimeToPitchNode() {
        super(List.of(new Port("time", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = 1f / Math.max(MIN_TIME, inputs[TIME][s]);
        }
    }

    @Override
    public String getName() {
        return "Time to Pitch";
    }
}
