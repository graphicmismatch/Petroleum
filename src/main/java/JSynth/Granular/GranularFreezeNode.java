package JSynth.Granular;

import JSynth.Dsp.GrainPool;
import JSynth.Dsp.LiveGrainRecorder;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class GranularFreezeNode extends AbstractNode {
    public static final int IN = 0;
    public static final int FREEZE = 1;
    public static final int POSITION = 2;
    public static final int POSITION_JITTER = 3;
    public static final int GRAIN_SIZE = 4;
    public static final int GRAIN_SIZE_JITTER = 5;
    public static final int DENSITY = 6;
    public static final int PITCH = 7;
    public static final int PITCH_JITTER = 8;
    public static final int AMP = 9;
    public static final int TRIGGER = 10;

    private static final double MAX_RECORD_SECONDS = 3.0;

    private final GrainPool pool = new GrainPool(GrainPool.DEFAULT_MAX_GRAINS);
    private final LiveGrainRecorder recorder = new LiveGrainRecorder(MAX_RECORD_SECONDS);

    public GranularFreezeNode() {
        super(List.of(
                new Port("in", 0f),
                new Port("freeze", 0f),
                new Port("position", 0.5f),
                new Port("positionJitter", 0f),
                new Port("grainSize", 0.08f),
                new Port("grainSizeJitter", 0f),
                new Port("density", 20f),
                new Port("pitch", 1f),
                new Port("pitchJitter", 0f),
                new Port("amp", 0.6f),
                new Port("trigger", 0f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        recorder.ensureAllocated(context.sampleRate);

        for (int s = 0; s < frameCount; s++) {
            boolean frozen = inputs[FREEZE][s] > 0f;
            if (!frozen) {
                recorder.write(inputs[IN][s]);
            }

            double positionSamples = recorder.positionBehind(inputs[POSITION][s], context.sampleRate);

            outputs[OUT][s] = pool.process(recorder, context.sampleRate,
                    positionSamples, inputs[POSITION_JITTER][s],
                    inputs[GRAIN_SIZE][s], inputs[GRAIN_SIZE_JITTER][s],
                    inputs[DENSITY][s], inputs[PITCH][s], inputs[PITCH_JITTER][s],
                    inputs[AMP][s], inputs[TRIGGER][s]);
        }
    }

    @Override
    public String getName() {
        return "Granular Freeze";
    }
}
