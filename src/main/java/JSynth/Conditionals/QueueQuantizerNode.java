package JSynth.Conditionals;

import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class QueueQuantizerNode extends AbstractNode {
    public static final int IN = 0;
    public static final int GATE = 1;
    public static final int TRIGGER = 2;
    public static final int PULSE = 1;
    public static final int CAPACITY = 64;

    private final float[] queue = new float[CAPACITY];
    private int head;
    private int size;
    private float held;
    private float previousIn;
    private boolean started;
    private long expectedBlockStart;
    private final EdgeDetector gateEdge = new EdgeDetector();
    private final EdgeDetector triggerEdge = new EdgeDetector();

    public QueueQuantizerNode() {
        super(List.of(new Port("in", 0f), new Port("gate", 0f), new Port("trigger", 0f)),
                List.of("out", "pulse"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        long blockStart = context.sampleIndex();
        if (!started || blockStart != expectedBlockStart) {
            head = 0;
            size = 0;
            held = 0f;
            previousIn = inputs[IN][0];
            gateEdge.edge(inputs[GATE][0]);
            triggerEdge.edge(0f);
            started = true;
        }

        for (int s = 0; s < frameCount; s++) {
            float in = inputs[IN][s];
            boolean gateRose = gateEdge.edge(inputs[GATE][s]) > 0;
            if (gateRose || in != previousIn) {
                push(in);
            }
            previousIn = in;

            float pulse = 0f;
            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0 && size > 0) {
                held = queue[head];
                if (++head >= CAPACITY) {
                    head = 0;
                }
                size--;
                pulse = 1f;
            }
            outputs[OUT][s] = held;
            outputs[PULSE][s] = pulse;
        }
        expectedBlockStart = blockStart + frameCount;
    }

    private void push(float value) {
        if (size == CAPACITY) {
            if (++head >= CAPACITY) {
                head = 0;
            }
            size--;
        }
        int tail = head + size;
        if (tail >= CAPACITY) {
            tail -= CAPACITY;
        }
        queue[tail] = value;
        size++;
    }

    @Override
    public String getName() {
        return "Queue Quantizer";
    }
}
