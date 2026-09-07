package JSynth.Effects;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class AmplitudeModulatorNode extends AbstractNode {
    public static final int CARRIER = 0;
    public static final int MODULATOR = 1;
    public static final int DEPTH = 2;

    public AmplitudeModulatorNode() {
        super(List.of(new Port("carrier", 0f), new Port("modulator", 0f), new Port("depth", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float depth = clamp(inputs[DEPTH][s], 0f, 1f);
            outputs[OUT][s] = inputs[CARRIER][s] * (1f + depth * inputs[MODULATOR][s]);
        }
    }

    @Override
    public String getName() {
        return "Amplitude Modulator";
    }
}
