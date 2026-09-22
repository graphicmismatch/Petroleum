package Petroleum.Conditionals;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Dsp.LfoPhase;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class RunglerNode extends AbstractNode {
    public static final int CLOCK = 0;
    public static final int DATA = 1;
    public static final int RATE = 2;

    public static final int STEPPED = 0;
    public static final int BIT = 1;

    private static final float DATA_RATE_RATIO = 1.37f;

    private int shiftRegister;
    private final EdgeDetector clockEdge = new EdgeDetector();
    private final LfoPhase clockLfo = new LfoPhase();
    private final LfoPhase dataLfo = new LfoPhase();

    public RunglerNode() {
        super(List.of(new Port("clock", 0f), new Port("data", 0f), new Port("rate", 4f)),
                List.of("stepped", "bit"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float rate = inputs[RATE][s];

            float clockSquare = Math.sin(clockLfo.next(rate, context.sampleRate)) >= 0 ? 1f : -1f;
            float dataSquare = Math.sin(dataLfo.next(rate * DATA_RATE_RATIO, context.sampleRate)) >= 0 ? 1f : -1f;

            float combinedClock = inputs[CLOCK][s] + clockSquare;
            float combinedData = inputs[DATA][s] + dataSquare;

            if (clockEdge.edge(combinedClock) > 0) {
                int newBit = combinedData > 0 ? 1 : 0;
                shiftRegister = ((shiftRegister << 1) | newBit) & 0xFF;
            }

            int top3 = (shiftRegister >> 5) & 0b111;
            outputs[STEPPED][s] = (top3 / 7f) * 2f - 1f;
            outputs[BIT][s] = shiftRegister & 1;
        }
    }

    @Override
    public String getName() {
        return "Rungler";
    }
}
