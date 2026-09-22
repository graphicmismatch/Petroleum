package JSynth.Utility;

import JSynth.Dsp.PitchQuantizer;
import JSynth.Dsp.PitchQuantizer.Scale;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class QuantizerNode extends AbstractNode {
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
                cachedOutHz = PitchQuantizer.quantize(scale, in, root);
                cachedIn = in;
                cachedRoot = root;
            }

            outputs[OUT][s] = cachedOutHz;
        }
    }

    @Override
    public String getName() {
        return "Quantizer " + scale.label();
    }
}
