package JSynth.Utility;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class QuantizerNode extends AbstractNode {
    public enum Scale {
        CHROMATIC("Chromatic", new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}),
        MAJOR("Major", new int[]{0, 2, 4, 5, 7, 9, 11}),
        MINOR("Minor", new int[]{0, 2, 3, 5, 7, 8, 10}),
        MAJOR_PENTATONIC("Major Pentatonic", new int[]{0, 2, 4, 7, 9}),
        MINOR_PENTATONIC("Minor Pentatonic", new int[]{0, 3, 5, 7, 10});

        private final String label;
        private final int[] intervals;

        Scale(String label, int[] intervals) {
            this.label = label;
            this.intervals = intervals;
        }

        public String label() {
            return label;
        }
    }

    public static final int IN = 0;
    public static final int ROOT = 1;

    private static final float MIN_HZ = 1e-6f;

    private final Scale scale;

    private float cachedIn = Float.NaN;
    private float cachedRoot = Float.NaN;
    private float cachedOutHz;

    public QuantizerNode(Scale scale) {
        super(List.of(new Port("in", 440f), new Port("root", 261.6256f)), List.of("out"));
        this.scale = scale;
    }

    public Scale getScale() {
        return scale;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float in = Math.max(MIN_HZ, inputs[IN][s]);
            float root = Math.max(MIN_HZ, inputs[ROOT][s]);

            if (in != cachedIn || root != cachedRoot) {
                cachedOutHz = quantize(in, root);
                cachedIn = in;
                cachedRoot = root;
            }

            outputs[OUT][s] = cachedOutHz;
        }
    }

    private float quantize(float hz, float rootHz) {
        float pitch = hzToSemitone(hz);
        float rootPitch = hzToSemitone(rootHz);
        int rootClass = Math.floorMod(Math.round(rootPitch), 12);

        int baseOctave = (int) Math.floor(pitch) - 12;
        float best = pitch;
        float bestDistance = Float.MAX_VALUE;
        for (int octave = baseOctave; octave <= baseOctave + 24; octave += 12) {
            for (int interval : scale.intervals) {
                float candidate = octave + rootClass + interval;
                float distance = Math.abs(candidate - pitch);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }

        return semitoneToHz(best);
    }

    private static float hzToSemitone(float hz) {
        return (float) (69.0 + 12.0 * (Math.log(hz / 440.0) / Math.log(2.0)));
    }

    private static float semitoneToHz(float semitone) {
        return (float) (440.0 * Math.pow(2.0, (semitone - 69) / 12.0));
    }

    @Override
    public String getName() {
        return "Quantizer " + scale.label();
    }
}
