package JSynth.Envelopes;

import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.OneShot;
import JSynth.Graph.Port;

import java.util.List;

public class RampNode extends AbstractNode implements OneShot {
    public static final int TRIGGER = 0;
    public static final int FROM = 1;
    public static final int TO = 2;
    public static final int TIME = 3;

    private volatile boolean idle = true;

    private boolean started;
    private int position;
    private final EdgeDetector triggerEdge = new EdgeDetector();

    private float cachedTime = Float.NaN;
    private double cachedSampleRate = -1;
    private float timeSamples;

    public RampNode() {
        super(List.of(
                new Port("trigger", 0f),
                new Port("from", 0f),
                new Port("to", 1f),
                new Port("time", 0.1f)), List.of("out"));
    }

    @Override
    public boolean isIdle() {
        return idle;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float time = inputs[TIME][s];
            if (time != cachedTime || context.sampleRate != cachedSampleRate) {
                timeSamples = Math.max(0f, time) * (float) context.sampleRate;
                cachedTime = time;
                cachedSampleRate = context.sampleRate;
            }

            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                started = true;
                position = 0;
                idle = false;
            }

            float from = inputs[FROM][s];
            float to = inputs[TO][s];

            if (!started) {
                outputs[OUT][s] = from;
                continue;
            }

            if (position >= timeSamples) {
                if (!idle) {
                    idle = true;
                }
                outputs[OUT][s] = to;
                continue;
            }

            outputs[OUT][s] = from + (to - from) * (position / timeSamples);
            position++;
        }
    }

    @Override
    public String getName() {
        return "Ramp";
    }
}
