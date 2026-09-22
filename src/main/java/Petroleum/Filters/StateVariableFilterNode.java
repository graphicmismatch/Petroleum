package Petroleum.Filters;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class StateVariableFilterNode extends AbstractNode {
    public static final int IN = 0;
    public static final int CUTOFF = 1;
    public static final int Q = 2;

    public static final int LOWPASS = 0;
    public static final int HIGHPASS = 1;
    public static final int BANDPASS = 2;
    public static final int NOTCH = 3;

    private float low = 0f;
    private float band = 0f;

    private float cachedCutoff = Float.NaN;
    private double cachedSampleRate = -1;
    private float f;

    public StateVariableFilterNode() {
        super(List.of(new Port("in", 0f), new Port("cutoff", 1000f), new Port("q", 0.7071f)),
                List.of("lowpass", "highpass", "bandpass", "notch"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float cutoff = clamp(inputs[CUTOFF][s], 1f, (float) (context.sampleRate * 0.24));
            if (cutoff != cachedCutoff || context.sampleRate != cachedSampleRate) {
                f = (float) (2 * Math.sin(Math.PI * cutoff / context.sampleRate));
                cachedCutoff = cutoff;
                cachedSampleRate = context.sampleRate;
            }
            float q = Math.max(0.5f, inputs[Q][s]);
            float x0 = inputs[IN][s];

            float damping = 1f / q;

            low += f * band;
            float high = x0 - low - damping * band;
            band += f * high;
            float notch = high + low;

            outputs[LOWPASS][s] = low;
            outputs[HIGHPASS][s] = high;
            outputs[BANDPASS][s] = band;
            outputs[NOTCH][s] = notch;
        }
    }

    @Override
    public String getName() {
        return "State Variable Filter";
    }
}
