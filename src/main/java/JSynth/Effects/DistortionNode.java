package JSynth.Effects;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class DistortionNode extends AbstractNode {
    public enum DistortionType {
        SOFT_CLIP("Soft Clip"), HARD_CLIP("Hard Clip"), FOLDBACK("Foldback"),
        OVERDRIVE("Overdrive"), RECTIFY("Rectify"), ASYMMETRIC("Asymmetric");

        private final String label;

        DistortionType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final int IN = 0;
    public static final int DRIVE = 1;
    public static final int MIX = 2;

    private static final float ASYMMETRY_RATIO = 1.6f;

    private final DistortionType type;

    public DistortionNode(DistortionType type) {
        super(List.of(new Port("in", 0f), new Port("drive", 5f), new Port("mix", 1f)), List.of("out"));
        this.type = type;
    }

    public DistortionType getType() {
        return type;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float drive = Math.max(0f, inputs[DRIVE][s]);
            float mix = clamp(inputs[MIX][s], 0f, 1f);
            float driven = x0 * (1 + drive);

            float shaped = shape(driven);
            outputs[OUT][s] = x0 * (1 - mix) + shaped * mix;
        }
    }

    private float shape(float driven) {
        return switch (type) {
            case SOFT_CLIP -> (float) Math.tanh(driven);
            case HARD_CLIP -> clamp(driven, -1f, 1f);
            case FOLDBACK -> Math.abs(wrap(driven - 1f, 4f) - 2f) - 1f;
            case OVERDRIVE -> overdrive(driven);
            case RECTIFY -> Math.min(1f, Math.abs(driven));
            case ASYMMETRIC -> driven >= 0
                    ? (float) Math.tanh(driven)
                    : (float) Math.tanh(driven * ASYMMETRY_RATIO);
        };
    }

    private static float overdrive(float driven) {
        float magnitude = Math.abs(driven);
        if (magnitude < 1f / 3f) {
            return 2f * driven;
        }
        if (magnitude < 2f / 3f) {
            float t = 2f - 3f * magnitude;
            return Math.signum(driven) * (3f - t * t) / 3f;
        }
        return Math.signum(driven);
    }

    private static float wrap(float value, float period) {
        float wrapped = value % period;
        return wrapped < 0 ? wrapped + period : wrapped;
    }

    @Override
    public String getName() {
        return "Distortion " + type.label();
    }
}
