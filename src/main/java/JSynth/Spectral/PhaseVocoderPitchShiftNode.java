package JSynth.Spectral;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class PhaseVocoderPitchShiftNode extends AbstractNode {
    public static final int IN = 0;
    public static final int PITCH_RATIO = 1;

    private static final int WINDOW_SIZE = 1024;
    private static final int HOP_SIZE = 256;
    private static final double TWO_PI = 2 * Math.PI;

    private static final int HALF = WINDOW_SIZE / 2;

    private final double[] previousInputPhase = new double[WINDOW_SIZE];
    private final double[] outputPhase = new double[WINDOW_SIZE];
    private final float[] shiftedMagnitude = new float[WINDOW_SIZE];
    private final float[] shiftedPhase = new float[WINDOW_SIZE];
    private boolean firstFrame = true;
    private float currentPitchRatio = 1f;

    private final StftEngine engine = new StftEngine(WINDOW_SIZE, HOP_SIZE) {
        @Override
        protected void processFrame(float[] magnitude, float[] phase) {
            float pitchRatio = currentPitchRatio;
            java.util.Arrays.fill(shiftedMagnitude, 0f);

            for (int k = 0; k <= HALF; k++) {
                double measuredPhase = phase[k];
                double expectedAdvance = TWO_PI * k * HOP_SIZE / (double) WINDOW_SIZE;
                double trueAdvance;
                if (firstFrame) {
                    trueAdvance = expectedAdvance;
                } else {
                    double deltaPhase = measuredPhase - previousInputPhase[k];
                    double deviation = wrap(deltaPhase - expectedAdvance);
                    trueAdvance = expectedAdvance + deviation;
                }
                previousInputPhase[k] = measuredPhase;

                int targetBin = (int) Math.round(k * pitchRatio);
                if (targetBin >= 0 && targetBin <= HALF) {
                    shiftedMagnitude[targetBin] += magnitude[k];
                    double scaledAdvance = trueAdvance * pitchRatio;
                    outputPhase[targetBin] = wrap(outputPhase[targetBin] + scaledAdvance);
                    shiftedPhase[targetBin] = (float) outputPhase[targetBin];
                }
            }
            firstFrame = false;

            for (int k = 0; k <= HALF; k++) {
                magnitude[k] = shiftedMagnitude[k];
                phase[k] = shiftedPhase[k];
            }
            for (int k = 1; k < HALF; k++) {
                magnitude[WINDOW_SIZE - k] = shiftedMagnitude[k];
                phase[WINDOW_SIZE - k] = -shiftedPhase[k];
            }
        }
    };

    public PhaseVocoderPitchShiftNode() {
        super(List.of(new Port("in", 0f), new Port("pitchRatio", 1f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            currentPitchRatio = inputs[PITCH_RATIO][s];
            engine.pushSample(inputs[IN][s]);
            outputs[OUT][s] = engine.popSample();
        }
    }

    private static double wrap(double phase) {
        return phase - TWO_PI * Math.round(phase / TWO_PI);
    }

    @Override
    public String getName() {
        return "Phase Vocoder Pitch Shift";
    }
}
