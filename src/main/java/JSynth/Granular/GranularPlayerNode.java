package JSynth.Granular;

import JSynth.Dsp.DspMath;
import JSynth.Dsp.GrainPool;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class GranularPlayerNode extends AbstractNode {
    public static final int POSITION = 0;
    public static final int POSITION_JITTER = 1;
    public static final int GRAIN_SIZE = 2;
    public static final int GRAIN_SIZE_JITTER = 3;
    public static final int DENSITY = 4;
    public static final int PITCH = 5;
    public static final int PITCH_JITTER = 6;
    public static final int AMP = 7;
    public static final int TRIGGER = 8;

    private final float[] samples;
    private final String name;
    private final GrainPool pool = new GrainPool(GrainPool.DEFAULT_MAX_GRAINS);
    private final FixedBufferSource source;

    public GranularPlayerNode(float[] samples, String name) {
        super(List.of(
                new Port("position", 0f),
                new Port("positionJitter", 0f),
                new Port("grainSize", 0.08f),
                new Port("grainSizeJitter", 0f),
                new Port("density", 20f),
                new Port("pitch", 1f),
                new Port("pitchJitter", 0f),
                new Port("amp", 0.6f),
                new Port("trigger", 0f)),
                List.of("out"));
        this.samples = samples;
        this.name = name;
        this.source = new FixedBufferSource(samples);
    }

    public float[] getSamples() {
        return samples;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            double positionSamples = inputs[POSITION][s] * context.sampleRate;
            outputs[OUT][s] = pool.process(source, context.sampleRate,
                    positionSamples, inputs[POSITION_JITTER][s],
                    inputs[GRAIN_SIZE][s], inputs[GRAIN_SIZE_JITTER][s],
                    inputs[DENSITY][s], inputs[PITCH][s], inputs[PITCH_JITTER][s],
                    inputs[AMP][s], inputs[TRIGGER][s]);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    private static final class FixedBufferSource implements GrainPool.Source {
        private final float[] buffer;

        FixedBufferSource(float[] buffer) {
            this.buffer = buffer;
        }

        @Override
        public float read(double positionInSamples) {
            int length = buffer.length;
            double clamped = positionInSamples;
            if (clamped < 0) {
                clamped = 0;
            } else if (clamped > length - 1) {
                clamped = length - 1;
            }
            int i0 = (int) clamped;
            int i1 = i0 + 1;
            if (i1 >= length) {
                i1 = length - 1;
            }
            float frac = (float) (clamped - Math.floor(clamped));
            return DspMath.lerp(buffer[i0], buffer[i1], frac);
        }

        @Override
        public int length() {
            return buffer.length;
        }
    }
}
