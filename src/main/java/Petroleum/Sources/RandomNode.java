package Petroleum.Sources;

import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.List;
import java.util.Random;

public class RandomNode extends AbstractNode {
    public static final int TRIGGER = 0;
    public static final int MIN = 1;
    public static final int MAX = 2;

    private final Random random = new Random();
    private final EdgeDetector triggerEdge = new EdgeDetector();

    private float value;
    private boolean started;

    public RandomNode() {
        super(List.of(new Port("trigger", 0f), new Port("min", 0f), new Port("max", 1f)),
                List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            boolean fired = triggerEdge.edge(inputs[TRIGGER][s]) > 0;

            if (fired || !started) {
                float min = inputs[MIN][s];
                float max = inputs[MAX][s];
                value = min + (float) random.nextDouble() * (max - min);
                started = true;
            }
            outputs[OUT][s] = value;
        }
    }

    @Override
    public String getName() {
        return "Random";
    }
}
