package JSynth.Effects;

import JSynth.Dsp.LfoPhase;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class TremoloNode extends AbstractNode {
    public static final int IN = 0;
    public static final int RATE = 1;
    public static final int DEPTH = 2;

    private final LfoPhase lfo = new LfoPhase();

    public TremoloNode() {
        super(List.of(new Port("in", 0f), new Port("rate", 5f), new Port("depth", 0.5f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float rate = Math.max(0f, inputs[RATE][s]);
            float depth = clamp(inputs[DEPTH][s], 0f, 1f);

            double lfoPhase = lfo.next(rate, context.sampleRate);

            float lfoValue = (float) ((Math.sin(lfoPhase) + 1) / 2);
            float gain = 1 - depth * (1 - lfoValue);
            outputs[OUT][s] = x0 * gain;
        }
    }

    @Override
    public String getName() {
        return "Tremolo";
    }
}
