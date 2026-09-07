package JSynth.Envelopes;

import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class ArpeggioNode extends AbstractNode {
    public static final int FREQUENCY = 0;
    public static final int TRIGGER = 1;
    public static final int DELAY = 2;
    public static final int RATIO = 3;
    public static final int STEPS = 4;

    private float multiplier = 1f;
    private int stepsTaken;
    private int position;
    private final EdgeDetector triggerEdge = new EdgeDetector();

    private float cachedDelay = Float.NaN;
    private double cachedSampleRate = -1;
    private long delaySamples;

    public ArpeggioNode() {
        super(List.of(
                new Port("frequency", 440f),
                new Port("trigger", 0f),
                new Port("delay", 0.08f),
                new Port("ratio", 1.5f),
                new Port("steps", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float delay = inputs[DELAY][s];
            if (delay != cachedDelay || context.sampleRate != cachedSampleRate) {
                delaySamples = Math.max(1L, Math.round(Math.max(0f, delay) * context.sampleRate));
                cachedDelay = delay;
                cachedSampleRate = context.sampleRate;
            }

            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                multiplier = 1f;
                stepsTaken = 0;
                position = 0;
            }

            int steps = (int) inputs[STEPS][s];
            if (steps <= 0 || stepsTaken < steps) {
                position++;
                if (position >= delaySamples) {
                    position = 0;
                    multiplier *= inputs[RATIO][s];
                    stepsTaken++;
                }
            }

            outputs[OUT][s] = inputs[FREQUENCY][s] * multiplier;
        }
    }

    @Override
    public String getName() {
        return "Arpeggio";
    }
}
