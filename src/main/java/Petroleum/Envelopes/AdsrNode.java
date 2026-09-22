package Petroleum.Envelopes;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;

public class AdsrNode extends AbstractNode {
    public static final int GATE = 0;
    public static final int ATTACK = 1;
    public static final int DECAY = 2;
    public static final int SUSTAIN = 3;
    public static final int RELEASE = 4;

    private static final int STAGE_IDLE = 0;
    private static final int STAGE_ATTACK = 1;
    private static final int STAGE_DECAY = 2;
    private static final int STAGE_SUSTAIN = 3;
    private static final int STAGE_RELEASE = 4;

    private int stage = STAGE_IDLE;
    private int stagePos;
    private final EdgeDetector gateEdge = new EdgeDetector();
    private float releaseStart;
    private float value;

    private float cachedAttack = Float.NaN;
    private float cachedDecay = Float.NaN;
    private float cachedRelease = Float.NaN;
    private double cachedSampleRate = -1;
    private float attackSamples;
    private float decaySamples;
    private float releaseSamples;

    public AdsrNode() {
        super(List.of(
                new Port("gate", 0f),
                new Port("attack", 0.01f),
                new Port("decay", 0.1f),
                new Port("sustain", 0.7f),
                new Port("release", 0.2f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float attack = inputs[ATTACK][s];
            float decay = inputs[DECAY][s];
            float release = inputs[RELEASE][s];
            if (attack != cachedAttack || decay != cachedDecay
                    || release != cachedRelease || context.sampleRate != cachedSampleRate) {
                float rate = (float) context.sampleRate;
                attackSamples = Math.max(0f, attack) * rate;
                decaySamples = Math.max(0f, decay) * rate;
                releaseSamples = Math.max(0f, release) * rate;
                cachedAttack = attack;
                cachedDecay = decay;
                cachedRelease = release;
                cachedSampleRate = context.sampleRate;
            }

            float sustainLevel = inputs[SUSTAIN][s];
            int edge = gateEdge.edge(inputs[GATE][s]);
            if (edge > 0) {
                stage = STAGE_ATTACK;
                stagePos = 0;
            } else if (edge < 0 && stage != STAGE_IDLE) {
                stage = STAGE_RELEASE;
                stagePos = 0;
                releaseStart = value;
            }

            if (stage == STAGE_ATTACK && stagePos >= attackSamples) {
                stage = STAGE_DECAY;
                stagePos = 0;
            }
            if (stage == STAGE_DECAY && stagePos >= decaySamples) {
                stage = STAGE_SUSTAIN;
                stagePos = 0;
            }
            if (stage == STAGE_RELEASE && stagePos >= releaseSamples) {
                stage = STAGE_IDLE;
                stagePos = 0;
            }

            switch (stage) {
                case STAGE_ATTACK -> value = stagePos / attackSamples;
                case STAGE_DECAY -> value = 1f - (1f - sustainLevel) * (stagePos / decaySamples);
                case STAGE_SUSTAIN -> value = sustainLevel;
                case STAGE_RELEASE -> value = releaseStart * (1f - stagePos / releaseSamples);
                default -> value = 0f;
            }
            outputs[OUT][s] = value;

            if (stage != STAGE_IDLE && stage != STAGE_SUSTAIN) {
                stagePos++;
            }
        }
    }

    @Override
    public String getName() {
        return "ADSR";
    }
}
