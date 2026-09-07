package JSynth.Utility;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class SlewNode extends AbstractNode {
    public static final int IN = 0;
    public static final int RISE = 1;
    public static final int FALL = 2;

    private float current;
    private boolean started;

    private float cachedRise = Float.NaN;
    private float cachedFall = Float.NaN;
    private double cachedSampleRate = -1;
    private float riseStep;
    private float fallStep;

    public SlewNode() {
        super(List.of(new Port("in", 0f), new Port("rise", 0.05f), new Port("fall", 0.05f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float rise = inputs[RISE][s];
            float fall = inputs[FALL][s];
            if (rise != cachedRise || fall != cachedFall || context.sampleRate != cachedSampleRate) {
                riseStep = stepFor(rise, context.sampleRate);
                fallStep = stepFor(fall, context.sampleRate);
                cachedRise = rise;
                cachedFall = fall;
                cachedSampleRate = context.sampleRate;
            }

            float target = inputs[IN][s];
            if (!started) {
                current = target;
                started = true;
            } else if (target > current) {
                current = riseStep <= 0f ? target : Math.min(target, current + riseStep);
            } else if (target < current) {
                current = fallStep <= 0f ? target : Math.max(target, current - fallStep);
            }
            outputs[OUT][s] = current;
        }
    }

    private static float stepFor(float seconds, double sampleRate) {
        if (seconds <= 0f) {
            return 0f;
        }
        return (float) (1.0 / (seconds * sampleRate));
    }

    @Override
    public String getName() {
        return "Slew";
    }
}
