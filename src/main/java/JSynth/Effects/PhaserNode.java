package JSynth.Effects;

import JSynth.Dsp.LfoPhase;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class PhaserNode extends AbstractNode {
    public static final int IN = 0;
    public static final int RATE = 1;
    public static final int DEPTH = 2;
    public static final int FEEDBACK = 3;
    public static final int MIX = 4;

    private static final int STAGES = 4;
    private static final double MIN_FREQ = 200.0;
    private static final double MAX_FREQ = 2000.0;

    private final float[] stageX1 = new float[STAGES];
    private final float[] stageY1 = new float[STAGES];
    private final LfoPhase lfo = new LfoPhase();
    private float lastOutput = 0f;

    public PhaserNode() {
        super(List.of(new Port("in", 0f), new Port("rate", 0.5f), new Port("depth", 1f),
                new Port("feedback", 0.3f), new Port("mix", 0.5f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float rate = Math.max(0.01f, inputs[RATE][s]);
            float depth = clamp(inputs[DEPTH][s], 0f, 1f);
            float feedback = clamp(inputs[FEEDBACK][s], 0f, 0.95f);
            float mix = clamp(inputs[MIX][s], 0f, 1f);

            double lfoPhase = lfo.next(rate, context.sampleRate);

            double lfoValue = (Math.sin(lfoPhase) + 1) / 2;
            double sweepFreq = MIN_FREQ + depth * lfoValue * (MAX_FREQ - MIN_FREQ);
            double tanArg = Math.tan(Math.PI * sweepFreq / context.sampleRate);
            float coefficient = (float) ((1 - tanArg) / (1 + tanArg));

            float stageInput = x0 + feedback * lastOutput;
            for (int i = 0; i < STAGES; i++) {
                float y = -coefficient * stageInput + stageX1[i] + coefficient * stageY1[i];
                stageX1[i] = stageInput;
                stageY1[i] = y;
                stageInput = y;
            }

            lastOutput = stageInput;
            outputs[OUT][s] = x0 * (1 - mix) + stageInput * mix;
        }
    }

    @Override
    public String getName() {
        return "Phaser";
    }
}
