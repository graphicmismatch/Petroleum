package JSynth.Effects;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class ReverbNode extends AbstractNode {
    public static final int IN = 0;
    public static final int ROOM_SIZE = 1;
    public static final int DAMPING = 2;
    public static final int MIX = 3;

    private static final int[] COMB_LENGTHS_AT_44100 = {1557, 1617, 1491, 1422};
    private static final int[] ALLPASS_LENGTHS_AT_44100 = {225, 556};

    private Comb[] combs;
    private Allpass[] allpasses;

    public ReverbNode() {
        super(List.of(new Port("in", 0f), new Port("roomSize", 0.5f), new Port("damping", 0.5f),
                new Port("mix", 0.3f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (combs == null) {
            double scale = context.sampleRate / 44100.0;
            combs = new Comb[COMB_LENGTHS_AT_44100.length];
            for (int i = 0; i < combs.length; i++) {
                combs[i] = new Comb(Math.max(1, (int) Math.round(COMB_LENGTHS_AT_44100[i] * scale)));
            }
            allpasses = new Allpass[ALLPASS_LENGTHS_AT_44100.length];
            for (int i = 0; i < allpasses.length; i++) {
                allpasses[i] = new Allpass(Math.max(1, (int) Math.round(ALLPASS_LENGTHS_AT_44100[i] * scale)));
            }
        }

        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float roomSize = clamp(inputs[ROOM_SIZE][s], 0f, 1f);
            float damping = clamp(inputs[DAMPING][s], 0f, 1f);
            float mix = clamp(inputs[MIX][s], 0f, 1f);

            float combSum = 0f;
            for (Comb comb : combs) {
                comb.feedback = 0.28f + roomSize * 0.7f;
                comb.damping = damping;
                combSum += comb.process(x0);
            }
            combSum /= combs.length;

            float wet = combSum;
            for (Allpass allpass : allpasses) {
                wet = allpass.process(wet);
            }

            outputs[OUT][s] = x0 * (1 - mix) + wet * mix;
        }
    }

    @Override
    public String getName() {
        return "Reverb";
    }

    private static final class Comb {
        private final float[] buffer;
        private final int length;
        private int index = 0;
        private float feedback;
        private float damping;
        private float filterState = 0f;

        Comb(int length) {
            this.length = length;
            buffer = new float[length];
        }

        float process(float input) {
            float output = buffer[index];
            filterState = output * (1 - damping) + filterState * damping;
            buffer[index] = input + filterState * feedback;
            index++;
            if (index >= length) {
                index = 0;
            }
            return output;
        }
    }

    private static final class Allpass {
        private static final float FEEDBACK = 0.5f;
        private final float[] buffer;
        private final int length;
        private int index = 0;

        Allpass(int length) {
            this.length = length;
            buffer = new float[length];
        }

        float process(float input) {
            float bufferedValue = buffer[index];
            float output = -input + bufferedValue;
            buffer[index] = input + bufferedValue * FEEDBACK;
            index++;
            if (index >= length) {
                index = 0;
            }
            return output;
        }
    }
}
