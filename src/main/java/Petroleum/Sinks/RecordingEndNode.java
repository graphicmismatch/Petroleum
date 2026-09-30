package Petroleum.Sinks;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class RecordingEndNode extends AbstractNode implements RecordingEnd {
    public static final int TRIGGER = 0;

    private boolean started;
    private long expectedBlockStart;
    private float previousTrigger;
    private volatile int endedOffset = -1;

    public RecordingEndNode() {
        super(List.of(new Port("trigger", 0f)), List.of());
    }

    @Override
    public int endedOffset() {
        return endedOffset;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        if (!started || blockStart != expectedBlockStart) {
            previousTrigger = 0f;
            endedOffset = -1;
            started = true;
        }
        expectedBlockStart = blockStart + frameCount;

        if (endedOffset >= 0) {
            return;
        }
        for (int s = 0; s < frameCount; s++) {
            float trigger = inputs[TRIGGER][s];
            if (previousTrigger <= 0f && trigger > 0f) {
                previousTrigger = trigger;
                endedOffset = s;
                return;
            }
            previousTrigger = trigger;
        }
    }

    @Override
    public String getName() {
        return "Recording End";
    }
}
