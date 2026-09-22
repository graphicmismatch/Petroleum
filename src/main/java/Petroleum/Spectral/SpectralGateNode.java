package Petroleum.Spectral;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class SpectralGateNode extends AbstractNode {
    public static final int IN = 0;
    public static final int THRESHOLD = 1;
    public static final int BLUR = 2;

    private static final int WINDOW_SIZE = 1024;
    private static final int HOP_SIZE = 256;
    private static final float BELOW_THRESHOLD_GAIN = 0.05f;
    private static final float MAX_BLUR_COEFF = 0.98f;

    private final float[] smoothedMagnitude = new float[WINDOW_SIZE];
    private float currentThreshold;
    private float currentBlur;

    private final StftEngine engine = new StftEngine(WINDOW_SIZE, HOP_SIZE) {
        @Override
        protected void processFrame(float[] magnitude, float[] phase) {
            float peak = 0f;
            for (float value : magnitude) {
                peak = Math.max(peak, value);
            }

            float blurCoeff = Math.max(0f, Math.min(1f, currentBlur)) * MAX_BLUR_COEFF;
            float threshold = Math.max(0f, Math.min(1f, currentThreshold));

            for (int i = 0; i < magnitude.length; i++) {
                smoothedMagnitude[i] = smoothedMagnitude[i] * blurCoeff + magnitude[i] * (1f - blurCoeff);
                float normalized = peak > 0f ? smoothedMagnitude[i] / peak : 0f;
                magnitude[i] = normalized >= threshold
                        ? smoothedMagnitude[i]
                        : smoothedMagnitude[i] * BELOW_THRESHOLD_GAIN;
            }
        }
    };

    public SpectralGateNode() {
        super(List.of(new Port("in", 0f), new Port("threshold", 0.1f), new Port("blur", 0f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            currentThreshold = inputs[THRESHOLD][s];
            currentBlur = inputs[BLUR][s];
            engine.pushSample(inputs[IN][s]);
            outputs[OUT][s] = engine.popSample();
        }
    }

    @Override
    public String getName() {
        return "Spectral Gate";
    }
}
