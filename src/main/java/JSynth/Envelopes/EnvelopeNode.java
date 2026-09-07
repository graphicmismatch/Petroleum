package JSynth.Envelopes;

import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.OneShot;
import JSynth.Graph.Port;

import java.util.List;

public class EnvelopeNode extends AbstractNode implements OneShot {
    public static final int TRIGGER = 0;
    public static final int ATTACK = 1;
    public static final int SUSTAIN = 2;
    public static final int PUNCH = 3;
    public static final int DECAY = 4;

    private static final int STAGE_IDLE = 0;
    private static final int STAGE_ATTACK = 1;
    private static final int STAGE_SUSTAIN = 2;
    private static final int STAGE_DECAY = 3;

    private volatile boolean idle = true;

    private int stage = STAGE_IDLE;
    private int stagePos;
    private final EdgeDetector triggerEdge = new EdgeDetector();

    private float cachedAttack = Float.NaN;
    private float cachedSustain = Float.NaN;
    private float cachedDecay = Float.NaN;
    private double cachedSampleRate = -1;
    private float attackSamples;
    private float sustainSamples;
    private float decaySamples;

    public EnvelopeNode() {
        super(List.of(
                new Port("trigger", 0f),
                new Port("attack", 0.01f),
                new Port("sustain", 0.1f),
                new Port("punch", 0.3f),
                new Port("decay", 0.2f)), List.of("out"));
    }

    @Override
    public boolean isIdle() {
        return idle;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float attack = inputs[ATTACK][s];
            float sustain = inputs[SUSTAIN][s];
            float decay = inputs[DECAY][s];
            if (attack != cachedAttack || sustain != cachedSustain
                    || decay != cachedDecay || context.sampleRate != cachedSampleRate) {
                float rate = (float) context.sampleRate;
                attackSamples = Math.max(0f, attack) * rate;
                sustainSamples = Math.max(0f, sustain) * rate;
                decaySamples = Math.max(0f, decay) * rate;
                cachedAttack = attack;
                cachedSustain = sustain;
                cachedDecay = decay;
                cachedSampleRate = context.sampleRate;
            }

            if (triggerEdge.edge(inputs[TRIGGER][s]) > 0) {
                stage = STAGE_ATTACK;
                stagePos = 0;
                idle = false;
            }

            while (stage != STAGE_IDLE && stagePos >= stageLength(stage)) {
                stage++;
                stagePos = 0;
                if (stage > STAGE_DECAY) {
                    stage = STAGE_IDLE;
                    idle = true;
                }
            }

            float value;
            switch (stage) {
                case STAGE_ATTACK -> value = stagePos / attackSamples;
                case STAGE_SUSTAIN -> value = 1f + inputs[PUNCH][s] * (1f - stagePos / sustainSamples);
                case STAGE_DECAY -> value = 1f - stagePos / decaySamples;
                default -> value = 0f;
            }
            outputs[OUT][s] = value;

            if (stage != STAGE_IDLE) {
                stagePos++;
            }
        }
    }

    private float stageLength(int currentStage) {
        return switch (currentStage) {
            case STAGE_ATTACK -> attackSamples;
            case STAGE_SUSTAIN -> sustainSamples;
            case STAGE_DECAY -> decaySamples;
            default -> 0f;
        };
    }

    @Override
    public String getName() {
        return "Envelope";
    }
}
