package JSynth.Sources;

import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class LfoNode extends AbstractNode {
    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final float PI = (float) Math.PI;

    public static final int FREQUENCY = 0;
    public static final int RESET = 1;

    public enum Shape {
        SINE("Sine"), TRIANGLE("Triangle"), SQUARE("Square"), SAWTOOTH("Sawtooth");

        private final String label;

        Shape(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Shape shape;
    private float phase;
    private final EdgeDetector resetEdge = new EdgeDetector();

    public LfoNode(Shape shape) {
        super(List.of(new Port("frequency", 2f), new Port("reset", 0f)), List.of("out"));
        this.shape = shape;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            if (resetEdge.edge(inputs[RESET][s]) > 0) {
                phase = 0f;
            }

            outputs[OUT][s] = sample(phase);

            phase += TWO_PI * inputs[FREQUENCY][s] / (float) context.sampleRate;
            if (phase >= TWO_PI) {
                phase -= TWO_PI;
            } else if (phase < 0f) {
                phase += TWO_PI;
            }
        }
    }

    private float sample(float phase) {
        return switch (shape) {
            case SINE -> (float) Math.sin(phase);
            case TRIANGLE -> (float) (2.0 / Math.PI * Math.asin(Math.sin(phase)));
            case SQUARE -> phase < PI ? 1f : -1f;
            case SAWTOOTH -> 2f * (phase / TWO_PI) - 1f;
        };
    }

    @Override
    public String getName() {
        return "LFO " + shape.label();
    }
}
