package JSynth.Envelopes;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class TriggerNode extends AbstractNode {
    public static final int DELAY = 0;
    public static final int INTERVAL = 1;

    private static final long NEVER = Long.MAX_VALUE;

    private boolean started;
    private long expectedBlockStart;
    private long nextFire;

    public TriggerNode() {
        super(List.of(new Port("delay", 0f), new Port("interval", 0f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        if (!started || blockStart != expectedBlockStart) {
            nextFire = blockStart + Math.round(Math.max(0f, inputs[DELAY][0]) * context.sampleRate);
            started = true;
        }

        for (int s = 0; s < frameCount; s++) {
            long absolute = blockStart + s;
            if (absolute >= nextFire) {
                outputs[OUT][s] = 1f;
                float interval = inputs[INTERVAL][s];
                if (interval > 0f) {
                    nextFire = absolute + Math.max(1L, Math.round(interval * context.sampleRate));
                } else {
                    nextFire = NEVER;
                }
            } else {
                outputs[OUT][s] = 0f;
            }
        }

        expectedBlockStart = blockStart + frameCount;
    }

    @Override
    public String getName() {
        return "Trigger";
    }
}
