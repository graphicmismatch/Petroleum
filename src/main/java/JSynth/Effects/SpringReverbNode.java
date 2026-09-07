package JSynth.Effects;

import JSynth.Dsp.DelayLine;
import JSynth.Dsp.DspMath;
import JSynth.Dsp.LfoPhase;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class SpringReverbNode extends AbstractNode {
    public static final int IN = 0;
    public static final int DECAY = 1;
    public static final int TONE = 2;
    public static final int BOING = 3;
    public static final int MIX = 4;

    private static final int STAGES = 6;
    private static final int[] BASE_DELAY_SAMPLES_AT_44100 = {397, 601, 773, 887, 1013, 1129};
    private static final float WOBBLE_RATE_HZ = 0.6f;
    private static final float WOBBLE_AMPLITUDE_SAMPLES_AT_44100 = 12f;
    private static final float MIN_TONE_HZ = 800f;
    private static final float MAX_TONE_HZ = 6000f;

    private ModulatedAllpass[] stages;
    private double[] baseDelaySamples;
    private float wobbleAmplitudeSamples;
    private final LfoPhase wobble = new LfoPhase();
    private float feedbackState;
    private float dampState;

    private float cachedTone = Float.NaN;
    private double cachedSampleRate = -1;
    private float dampAlpha;

    public SpringReverbNode() {
        super(List.of(new Port("in", 0f), new Port("decay", 0.6f), new Port("tone", 0.5f),
                new Port("boing", 0.3f), new Port("mix", 0.3f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (stages == null) {
            double scale = context.sampleRate / 44100.0;
            wobbleAmplitudeSamples = (float) (WOBBLE_AMPLITUDE_SAMPLES_AT_44100 * scale);
            stages = new ModulatedAllpass[STAGES];
            baseDelaySamples = new double[STAGES];
            for (int i = 0; i < STAGES; i++) {
                baseDelaySamples[i] = BASE_DELAY_SAMPLES_AT_44100[i] * scale;
                int capacity = (int) Math.ceil(baseDelaySamples[i] + wobbleAmplitudeSamples) + 4;
                stages[i] = new ModulatedAllpass(capacity);
            }
        }

        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float decay = clamp(inputs[DECAY][s], 0f, 0.98f);
            float tone = clamp(inputs[TONE][s], 0f, 1f);
            float boing = clamp(inputs[BOING][s], 0f, 1f);
            float mix = clamp(inputs[MIX][s], 0f, 1f);

            if (tone != cachedTone || context.sampleRate != cachedSampleRate) {
                float cutoffHz = MIN_TONE_HZ + tone * (MAX_TONE_HZ - MIN_TONE_HZ);
                dampAlpha = DspMath.onePoleLowpassAlpha(cutoffHz, context.sampleRate);
                cachedTone = tone;
                cachedSampleRate = context.sampleRate;
            }

            double wobblePhase = wobble.next(WOBBLE_RATE_HZ, context.sampleRate);

            float signal = x0 + feedbackState * decay;
            for (int i = 0; i < STAGES; i++) {
                double modOffset = boing * wobbleAmplitudeSamples
                        * Math.sin(wobblePhase + i * (2 * Math.PI / STAGES));
                double delaySamples = baseDelaySamples[i] + modOffset;
                signal = stages[i].process(signal, delaySamples);
            }
            signal = (float) Math.tanh(signal);

            dampState += dampAlpha * (signal - dampState);
            feedbackState = dampState;

            outputs[OUT][s] = x0 * (1 - mix) + signal * mix;
        }
    }

    @Override
    public String getName() {
        return "Spring Reverb";
    }

    private static final class ModulatedAllpass {
        private static final float FEEDBACK = 0.6f;
        private final DelayLine delay;

        ModulatedAllpass(int capacity) {
            delay = new DelayLine(capacity);
        }

        float process(float input, double delaySamples) {
            float buffered = delay.readFractional(delaySamples);
            float output = -FEEDBACK * input + buffered;
            delay.write(input + buffered * FEEDBACK);
            return output;
        }
    }
}
