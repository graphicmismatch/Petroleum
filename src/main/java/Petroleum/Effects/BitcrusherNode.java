package Petroleum.Effects;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class BitcrusherNode extends AbstractNode {
    public static final int IN = 0;
    public static final int BITS = 1;
    public static final int RATE = 2;

    private float held = 0f;
    private double sampleCounter = 0;

    private float cachedBits = Float.NaN;
    private double cachedLevels;

    public BitcrusherNode() {
        super(List.of(new Port("in", 0f), new Port("bits", 8f), new Port("rate", 8000f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float bits = clamp(inputs[BITS][s], 1f, 16f);
            float targetRate = Math.max(1f, inputs[RATE][s]);
            double samplesPerHold = context.sampleRate / targetRate;

            sampleCounter += 1.0;
            if (sampleCounter >= samplesPerHold) {
                sampleCounter -= samplesPerHold;
                held = inputs[IN][s];
            }

            if (bits != cachedBits) {
                cachedLevels = Math.pow(2, bits);
                cachedBits = bits;
            }
            outputs[OUT][s] = (float) (Math.round(held * cachedLevels) / cachedLevels);
        }
    }

    @Override
    public String getName() {
        return "Bitcrusher";
    }
}
