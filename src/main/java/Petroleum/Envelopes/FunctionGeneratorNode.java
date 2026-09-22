package Petroleum.Envelopes;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.OneShot;
import Petroleum.Graph.Port;

import java.util.List;

public class FunctionGeneratorNode extends AbstractNode implements OneShot {
    public static final int TRIGGER = 0;
    public static final int CYCLE = 1;
    public static final int RISE = 2;
    public static final int FALL = 3;

    public static final int EOC = 1;

    private static final int STAGE_IDLE = 0;
    private static final int STAGE_RISE = 1;
    private static final int STAGE_FALL = 2;

    private volatile boolean idle = true;

    private int stage = STAGE_IDLE;
    private int stagePos;

    private final EdgeDetector triggerEdge = new EdgeDetector();

    private float cachedRise = Float.NaN;
    private float cachedFall = Float.NaN;
    private double cachedSampleRate = -1;
    private float riseSamples;
    private float fallSamples;

    public FunctionGeneratorNode() {
        super(List.of(
                new Port("trigger", 0f),
                new Port("cycle", 0f),
                new Port("rise", 0.1f),
                new Port("fall", 0.3f)), List.of("out", "eoc"));
    }

    @Override
    public boolean isIdle() {
        return idle;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float rise = inputs[RISE][s];
            float fall = inputs[FALL][s];
            if (rise != cachedRise || fall != cachedFall || context.sampleRate != cachedSampleRate) {
                float rate = (float) context.sampleRate;
                riseSamples = Math.max(0f, rise) * rate;
                fallSamples = Math.max(0f, fall) * rate;
                cachedRise = rise;
                cachedFall = fall;
                cachedSampleRate = context.sampleRate;
            }

            boolean cycleOn = inputs[CYCLE][s] > 0f;
            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0 || (stage == STAGE_IDLE && cycleOn)) {
                stage = STAGE_RISE;
                stagePos = 0;
                idle = false;
            }

            if (stage == STAGE_RISE && stagePos >= riseSamples) {
                stage = STAGE_FALL;
                stagePos = 0;
            }

            float eoc = 0f;
            if (stage == STAGE_FALL && stagePos >= fallSamples) {
                eoc = 1f;
                stage = cycleOn ? STAGE_RISE : STAGE_IDLE;
                stagePos = 0;
                idle = stage == STAGE_IDLE;
            }

            float value;
            switch (stage) {
                case STAGE_RISE -> value = stagePos / riseSamples;
                case STAGE_FALL -> value = 1f - stagePos / fallSamples;
                default -> value = 0f;
            }
            outputs[OUT][s] = value;
            outputs[EOC][s] = eoc;

            if (stage != STAGE_IDLE) {
                stagePos++;
            }
        }
    }

    @Override
    public String getName() {
        return "Function Generator";
    }
}
