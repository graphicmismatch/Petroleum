package Petroleum.Special;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class TurretNode extends AbstractNode {
    public static final int TRIGGER = 0;
    public static final int COUNT = 1;
    public static final int INTERVAL = 2;

    private static final long NEVER = Long.MAX_VALUE;

    private final EdgeDetector triggerEdge = new EdgeDetector();

    private boolean started;
    private long expectedBlockStart;
    private long nextShot = NEVER;
    private long shotsRemaining;

    public TurretNode() {
        super(List.of(new Port("trigger", 0f), new Port("count", 4f), new Port("interval", 0.05f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        if (!started || blockStart != expectedBlockStart) {
            started = true;
            shotsRemaining = 0;
            nextShot = NEVER;
        }

        for (int s = 0; s < frameCount; s++) {
            long absolute = blockStart + s;

            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                shotsRemaining = Math.max(0, Math.round(inputs[COUNT][s]));
                nextShot = absolute;
            }

            if (shotsRemaining > 0 && absolute >= nextShot) {
                outputs[OUT][s] = 1f;
                shotsRemaining--;
                long spacing = Math.max(2L, Math.round(inputs[INTERVAL][s] * context.sampleRate));
                nextShot = shotsRemaining > 0 ? absolute + spacing : NEVER;
            } else {
                outputs[OUT][s] = 0f;
            }
        }

        expectedBlockStart = blockStart + frameCount;
    }

    @Override
    public String getName() {
        return "Turret";
    }
}
