package JSynth.Filters;

import JSynth.Dsp.DspMath;
import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class LowPassGateNode extends AbstractNode {
    public static final int IN = 0;
    public static final int STRIKE = 1;
    public static final int DECAY = 2;
    public static final int VACTROL_AMOUNT = 3;

    private static final float MIN_DECAY = 0.001f;
    private static final float TAU_FAST_RATIO = 0.15f;
    private static final float LOW_CUTOFF_HZ = 80f;
    private static final float HIGH_CUTOFF_HZ = 8000f;
    private static final float CUTOFF_EPSILON = 0.5f;

    private final EdgeDetector strikeEdge = new EdgeDetector();

    private boolean struck;
    private long samplesSinceStrike;

    private float cachedDecay = Float.NaN;
    private float cachedVactrolAmount = Float.NaN;
    private float tauFast;
    private float tauSlow;
    private float blend;

    private float cachedCutoff = Float.NaN;
    private double cachedSampleRate = -1;
    private float alpha;

    private float filterState;

    public LowPassGateNode() {
        super(List.of(
                new Port("in", 0f),
                new Port("strike", 0f),
                new Port("decay", 0.3f),
                new Port("vactrolAmount", 0.5f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float decay = Math.max(MIN_DECAY, inputs[DECAY][s]);
            float vactrolAmount = clamp(inputs[VACTROL_AMOUNT][s], 0f, 1f);
            if (decay != cachedDecay || vactrolAmount != cachedVactrolAmount) {
                tauFast = decay * TAU_FAST_RATIO;
                tauSlow = decay;
                blend = vactrolAmount;
                cachedDecay = decay;
                cachedVactrolAmount = vactrolAmount;
            }

            if (strikeEdge.edge(inputs[STRIKE][s]) > 0) {
                struck = true;
                samplesSinceStrike = 0;
            }

            float env;
            if (!struck) {
                env = 0f;
            } else {
                float t = (float) (samplesSinceStrike / context.sampleRate);
                env = blend * (float) Math.exp(-t / tauFast) + (1f - blend) * (float) Math.exp(-t / tauSlow);
                samplesSinceStrike++;
            }

            float cutoff = LOW_CUTOFF_HZ * (float) Math.pow(HIGH_CUTOFF_HZ / LOW_CUTOFF_HZ, env);
            if (Math.abs(cutoff - cachedCutoff) > CUTOFF_EPSILON || context.sampleRate != cachedSampleRate) {
                alpha = DspMath.onePoleLowpassAlpha(cutoff, context.sampleRate);
                cachedCutoff = cutoff;
                cachedSampleRate = context.sampleRate;
            }

            filterState += alpha * (inputs[IN][s] - filterState);
            outputs[OUT][s] = filterState * env;
        }
    }

    @Override
    public String getName() {
        return "Low-Pass Gate";
    }
}
