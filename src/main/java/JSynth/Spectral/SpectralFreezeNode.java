package JSynth.Spectral;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;
import java.util.Random;

public class SpectralFreezeNode extends AbstractNode {
    public static final int IN = 0;
    public static final int FREEZE = 1;

    private static final int WINDOW_SIZE = 1024;
    private static final int HOP_SIZE = 256;
    private static final float PHASE_DRIFT = 0.05f;

    private final Random random = new Random();
    private final float[] frozenMagnitude = new float[WINDOW_SIZE];
    private final float[] driftPhase = new float[WINDOW_SIZE];
    private boolean frozen;
    private boolean freezeGateOn;

    private final StftEngine engine = new StftEngine(WINDOW_SIZE, HOP_SIZE) {
        @Override
        protected void processFrame(float[] magnitude, float[] phase) {
            if (freezeGateOn) {
                if (!frozen) {
                    System.arraycopy(magnitude, 0, frozenMagnitude, 0, magnitude.length);
                    System.arraycopy(phase, 0, driftPhase, 0, phase.length);
                    frozen = true;
                }
                for (int i = 0; i < magnitude.length; i++) {
                    driftPhase[i] += (random.nextFloat() - 0.5f) * PHASE_DRIFT;
                    magnitude[i] = frozenMagnitude[i];
                    phase[i] = driftPhase[i];
                }
            } else {
                frozen = false;
            }
        }
    };

    public SpectralFreezeNode() {
        super(List.of(new Port("in", 0f), new Port("freeze", 0f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            freezeGateOn = inputs[FREEZE][s] > 0f;
            engine.pushSample(inputs[IN][s]);
            outputs[OUT][s] = engine.popSample();
        }
    }

    @Override
    public String getName() {
        return "Spectral Freeze";
    }
}
