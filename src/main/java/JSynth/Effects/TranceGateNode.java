package JSynth.Effects;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class TranceGateNode extends AbstractNode {
    public static final int IN = 0;
    public static final int RATE = 1;
    public static final int FIRST_STEP = 2;

    private final int steps;

    private double phase;
    private int currentStep;

    public TranceGateNode() {
        this(8);
    }

    public TranceGateNode(int steps) {
        super(buildPorts(steps), List.of("out"));
        this.steps = steps;
    }

    private static List<Port> buildPorts(int steps) {
        List<Port> ports = new ArrayList<>();
        ports.add(new Port("in", 0f));
        ports.add(new Port("rate", 4f));
        for (int i = 0; i < steps; i++) {
            ports.add(new Port("step" + i, 1f));
        }
        return ports;
    }

    public int getSteps() {
        return steps;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float rate = Math.max(0.01f, inputs[RATE][s]);
            phase += rate / context.sampleRate;
            while (phase >= 1f) {
                phase -= 1f;
                currentStep++;
                if (currentStep >= steps) {
                    currentStep = 0;
                }
            }

            float stepGain = inputs[FIRST_STEP + currentStep][s];
            outputs[OUT][s] = inputs[IN][s] * stepGain;
        }
    }

    @Override
    public String getName() {
        return "Trance Gate";
    }
}
