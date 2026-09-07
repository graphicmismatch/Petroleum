package JSynth.Conditionals;

import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class ClockDividerNode extends AbstractNode {
    public static final int CLOCK = 0;
    public static final int DIVIDE = 1;
    public static final int MULTIPLY = 2;

    private static final long NEVER = Long.MAX_VALUE;

    private final EdgeDetector clockEdge = new EdgeDetector();

    private boolean started;
    private long expectedBlockStart;
    private long lastEdgeSample = -1;
    private long edgeCount;
    private long nextPulse = NEVER;
    private long pulseSpacing;
    private long pulsesRemaining;

    public ClockDividerNode() {
        super(List.of(new Port("clock", 0f), new Port("divide", 1f), new Port("multiply", 1f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        if (!started || blockStart != expectedBlockStart) {
            started = true;
            lastEdgeSample = -1;
            edgeCount = 0;
            pulsesRemaining = 0;
            nextPulse = NEVER;
        }

        for (int s = 0; s < frameCount; s++) {
            long absolute = blockStart + s;

            if (clockEdge.edge(inputs[CLOCK][s]) > 0) {
                long measuredPeriod = lastEdgeSample >= 0 ? absolute - lastEdgeSample : 0;
                lastEdgeSample = absolute;
                edgeCount++;

                int divideN = Math.max(1, Math.round(inputs[DIVIDE][s]));
                if (edgeCount % divideN == 0) {
                    long beatPeriod = measuredPeriod > 0 ? measuredPeriod * divideN : 0;
                    int multiplyN = Math.max(1, Math.round(inputs[MULTIPLY][s]));
                    pulseSpacing = beatPeriod > 0 ? beatPeriod / multiplyN : 0;
                    pulsesRemaining = multiplyN;
                    nextPulse = absolute;
                }
            }

            if (pulsesRemaining > 0 && absolute >= nextPulse) {
                outputs[OUT][s] = 1f;
                pulsesRemaining--;
                nextPulse = (pulsesRemaining > 0 && pulseSpacing > 0) ? nextPulse + pulseSpacing : NEVER;
            } else {
                outputs[OUT][s] = 0f;
            }
        }

        expectedBlockStart = blockStart + frameCount;
    }

    @Override
    public String getName() {
        return "Clock Divider";
    }
}
