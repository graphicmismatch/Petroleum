package Petroleum.Spectral;

import Petroleum.Dsp.EnvelopeFollower;
import Petroleum.Dsp.PitchDetector;
import Petroleum.Dsp.PitchQuantizer;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class PitchCorrectionNode extends AbstractNode {
    public static final int IN = 0;
    public static final int ROOT = 1;
    public static final int RETUNE_SPEED = 2;

    private static final int WINDOW_SIZE = 2048;
    private static final int HOP_SIZE = 512;
    private static final float MIN_FREQUENCY_HZ = 60f;
    private static final double TWO_PI = 2 * Math.PI;

    private static final int HALF = WINDOW_SIZE / 2;

    private final PitchQuantizer.Scale scale;

    private final double[] previousInputPhase = new double[WINDOW_SIZE];
    private final double[] outputPhase = new double[WINDOW_SIZE];
    private final float[] shiftedMagnitude = new float[WINDOW_SIZE];
    private final float[] shiftedPhase = new float[WINDOW_SIZE];
    private boolean firstFrame = true;
    private float currentPitchRatio = 1f;

    private PitchDetector detector;
    private final EnvelopeFollower glide = new EnvelopeFollower();
    private float smoothedRatio = 1f;

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

                int targetBin = Math.round(k * pitchRatio);
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

    public PitchCorrectionNode(PitchQuantizer.Scale scale) {
        super(List.of(new Port("in", 0f), new Port("root", 261.6256f), new Port("retuneSpeed", 20f)),
                List.of("out"));
        this.scale = scale;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (detector == null) {
            detector = new PitchDetector(context.sampleRate, MIN_FREQUENCY_HZ);
        }

        for (int s = 0; s < frameCount; s++) {
            float in = inputs[IN][s];
            float root = inputs[ROOT][s];
            float retuneSpeedMs = Math.max(0f, inputs[RETUNE_SPEED][s]);

            detector.pushSample(in);

            float targetRatio = 1f;
            if (detector.isVoiced()) {
                float detectedHz = detector.getFrequencyHz();
                float quantizedHz = PitchQuantizer.quantize(scale, detectedHz, root);
                targetRatio = quantizedHz / detectedHz;
            }

            glide.updateCoefficients(retuneSpeedMs, retuneSpeedMs, context.sampleRate);
            smoothedRatio = (float) glide.followAttackRelease(smoothedRatio, targetRatio);
            currentPitchRatio = smoothedRatio;

            engine.pushSample(in);
            outputs[OUT][s] = engine.popSample();
        }
    }

    private static double wrap(double phase) {
        return phase - TWO_PI * Math.round(phase / TWO_PI);
    }

    @Override
    public String getName() {
        return "Pitch Correction " + scale.label();
    }
}
