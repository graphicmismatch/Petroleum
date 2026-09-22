package JSynth.Sources;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class PhasorNode extends AbstractNode {
    public static final float TWO_PI = (float) (2 * Math.PI);
    public static final float UNIT_RANGE = 1f;

    private static final int DRIVER = 0;

    private final String name;
    private final boolean periodDriven;
    private final float range;
    private final float halfRange;
    private final boolean pingPong;

    private double phase = 0;

    public PhasorNode(String name, boolean periodDriven, float range, boolean pingPong) {
        super(List.of(periodDriven ? new Port("period", 1f) : new Port("frequency", 440f)), List.of("out"));
        this.name = name;
        this.periodDriven = periodDriven;
        this.range = range;
        this.halfRange = range / 2f;
        this.pingPong = pingPong;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = (float) (pingPong
                    ? (phase < halfRange ? phase * 2f : (range - phase) * 2f)
                    : phase);

            if (periodDriven) {
                float period = inputs[DRIVER][s];
                if (period != 0f) {
                    phase += range / (period * context.sampleRate);
                }
            } else {
                phase += range * inputs[DRIVER][s] / context.sampleRate;
            }
            if (phase >= range) {
                phase -= range;
            } else if (phase < 0f) {
                phase += range;
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }
}
