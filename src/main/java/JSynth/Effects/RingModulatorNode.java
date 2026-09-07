package JSynth.Effects;

import JSynth.Dsp.LfoPhase;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class RingModulatorNode extends AbstractNode {
    public static final int IN = 0;
    public static final int FREQUENCY = 1;
    public static final int MIX = 2;

    private final LfoPhase carrier = new LfoPhase();

    public RingModulatorNode() {
        super(List.of(new Port("in", 0f), new Port("frequency", 30f), new Port("mix", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float frequency = Math.max(0f, inputs[FREQUENCY][s]);
            float mix = clamp(inputs[MIX][s], 0f, 1f);

            double carrierPhase = carrier.next(frequency, context.sampleRate);

            float modulated = x0 * (float) Math.sin(carrierPhase);
            outputs[OUT][s] = x0 * (1 - mix) + modulated * mix;
        }
    }

    @Override
    public String getName() {
        return "Ring Modulator";
    }
}
