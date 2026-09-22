package Petroleum.Physical;

import Petroleum.Dsp.DelayLine;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.Arrays;
import java.util.List;

public class ResonatorNode extends AbstractNode {
    public static final int IN = 0;
    public static final int PITCH = 1;
    public static final int STRUCTURE = 2;
    public static final int BRIGHTNESS = 3;
    public static final int DECAY = 4;

    private static final float MIN_PITCH = 20f;

    private static final float MIN_STRING_DAMPING_R = 0.2f;
    private static final float MAX_STRING_DAMPING_R = 0.9995f;
    private static final float MIN_STRING_FEEDBACK = 0.9f;
    private static final float MAX_STRING_FEEDBACK = 0.9995f;

    private static final int MODAL_STAGES = 6;
    private static final float[] HARMONIC_RATIOS = {1f, 2f, 3f, 4f, 5f, 6f};
    private static final float[] INHARMONIC_RATIOS = {1f, 1.59f, 2.14f, 2.65f, 3.02f, 3.99f};
    private static final float MIN_MODAL_Q = 1.5f;
    private static final float MAX_MODAL_Q = 80f;

    private DelayLine stringDelay;
    private float stringDampedState;

    private final float[] modalX1 = new float[MODAL_STAGES];
    private final float[] modalX2 = new float[MODAL_STAGES];
    private final float[] modalY1 = new float[MODAL_STAGES];
    private final float[] modalY2 = new float[MODAL_STAGES];

    private final float[] cachedModalCutoff = new float[MODAL_STAGES];
    private float cachedModalQ = Float.NaN;
    private double cachedModalSampleRate = -1;
    private final double[] modalB0 = new double[MODAL_STAGES];
    private final double[] modalB2 = new double[MODAL_STAGES];
    private final double[] modalA1 = new double[MODAL_STAGES];
    private final double[] modalA2 = new double[MODAL_STAGES];

    public ResonatorNode() {
        super(List.of(
                new Port("in", 0f),
                new Port("pitch", 220f),
                new Port("structure", 0.5f),
                new Port("brightness", 0.5f),
                new Port("decay", 0.5f)),
                List.of("out"));
        Arrays.fill(cachedModalCutoff, Float.NaN);
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (stringDelay == null) {
            int capacity = (int) Math.ceil(context.sampleRate / MIN_PITCH) + 4;
            stringDelay = new DelayLine(capacity);
        }

        for (int s = 0; s < frameCount; s++) {
            float excitation = inputs[IN][s];
            float pitch = clamp(inputs[PITCH][s], MIN_PITCH, (float) (context.sampleRate * 0.499));
            float structure = clamp(inputs[STRUCTURE][s], 0f, 1f);
            float brightness = clamp(inputs[BRIGHTNESS][s], 0f, 1f);
            float decay = clamp(inputs[DECAY][s], 0f, 1f);

            float stringOut = processString(excitation, pitch, brightness, decay, context.sampleRate);
            float modalOut = processModal(excitation, pitch, structure, decay, context.sampleRate);

            outputs[OUT][s] = stringOut * (1f - structure) + modalOut * structure;
        }
    }

    private float processString(float excitation, float pitch, float brightness, float decay, double sampleRate) {
        double delaySamples = sampleRate * (1.0 / pitch);
        float delayOut = stringDelay.readFractional(delaySamples);

        float r = MIN_STRING_DAMPING_R + (MAX_STRING_DAMPING_R - MIN_STRING_DAMPING_R) * brightness;
        stringDampedState = (1f - r) * delayOut + r * stringDampedState;

        float feedbackGain = MIN_STRING_FEEDBACK + (MAX_STRING_FEEDBACK - MIN_STRING_FEEDBACK) * decay;
        stringDelay.write(excitation + feedbackGain * stringDampedState);

        return delayOut;
    }

    private float processModal(float excitation, float pitch, float structure, float decay, double sampleRate) {
        float[] ratios = structure < 0.5f ? HARMONIC_RATIOS : INHARMONIC_RATIOS;
        float q = MIN_MODAL_Q + (MAX_MODAL_Q - MIN_MODAL_Q) * decay;

        float sum = 0f;
        for (int i = 0; i < MODAL_STAGES; i++) {
            float cutoff = clamp(pitch * ratios[i], 1f, (float) (sampleRate * 0.499));

            if (cutoff != cachedModalCutoff[i] || q != cachedModalQ || sampleRate != cachedModalSampleRate) {
                recomputeModalCoefficients(i, cutoff, q, sampleRate);
                cachedModalCutoff[i] = cutoff;
            }

            double x0 = excitation;
            double y0 = modalB0[i] * x0 + modalB2[i] * modalX2[i]
                    - modalA1[i] * modalY1[i] - modalA2[i] * modalY2[i];

            modalX2[i] = modalX1[i];
            modalX1[i] = (float) x0;
            modalY2[i] = modalY1[i];
            modalY1[i] = (float) y0;

            sum += (float) y0;
        }
        cachedModalQ = q;
        cachedModalSampleRate = sampleRate;

        return sum / MODAL_STAGES;
    }

    private void recomputeModalCoefficients(int stage, double cutoff, double q, double sampleRate) {
        double w0 = 2 * Math.PI * cutoff / sampleRate;
        double cosw0 = Math.cos(w0);
        double sinw0 = Math.sin(w0);
        double alpha = sinw0 / (2 * q);

        double a0 = 1 + alpha;
        modalB0[stage] = alpha / a0;
        modalB2[stage] = -alpha / a0;
        modalA1[stage] = (-2 * cosw0) / a0;
        modalA2[stage] = (1 - alpha) / a0;
    }

    @Override
    public String getName() {
        return "Resonator";
    }
}
