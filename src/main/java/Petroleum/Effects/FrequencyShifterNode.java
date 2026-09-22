package Petroleum.Effects;

import Petroleum.Dsp.HilbertTransformer;
import Petroleum.Dsp.LfoPhase;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class FrequencyShifterNode extends AbstractNode {
    public static final int IN = 0;
    public static final int SHIFT_HZ = 1;

    private final HilbertTransformer hilbert = new HilbertTransformer();
    private final LfoPhase carrier = new LfoPhase();
    private final float[] inPhaseAndQuadrature = new float[2];

    public FrequencyShifterNode() {
        super(List.of(new Port("in", 0f), new Port("shiftHz", 100f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float shiftHz = inputs[SHIFT_HZ][s];
            float sign = shiftHz >= 0 ? 1f : -1f;

            hilbert.process(x0, inPhaseAndQuadrature);
            float inPhase = inPhaseAndQuadrature[0];
            float quadrature = inPhaseAndQuadrature[1];

            double carrierPhase = carrier.next(Math.abs(shiftHz), context.sampleRate);

            outputs[OUT][s] = (float) (inPhase * Math.cos(carrierPhase)
                    - sign * quadrature * Math.sin(carrierPhase));
        }
    }

    @Override
    public String getName() {
        return "Frequency Shifter";
    }
}
