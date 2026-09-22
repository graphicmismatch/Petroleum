package JSynth.Oscillators;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;
import JSynth.Oscillators.Implementations.NoiseOscillator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OscillatorNode extends AbstractNode {
    public static final int PHASE = 0;

    private static final double TWO_PI = 2 * Math.PI;

    private final IOscillator oscillator;
    private final ModulatableOscillator modulatable;
    private final float[] lastParams;
    private double lastPhase = Double.NaN;

    public OscillatorNode(IOscillator oscillator) {
        super(buildPorts(oscillator), List.of("out"));
        this.oscillator = oscillator;
        this.modulatable = oscillator instanceof ModulatableOscillator m ? m : null;
        this.lastParams = new float[modulatable == null ? 0 : modulatable.getParameterPorts().size()];
        Arrays.fill(lastParams, Float.NaN);
    }

    public IOscillator getOscillator() {
        return oscillator;
    }

    private static List<Port> buildPorts(IOscillator oscillator) {
        List<Port> ports = new ArrayList<>();
        ports.add(oscillator instanceof NoiseOscillator ? new Port("gate", 1f) : new Port("phase", 0f));
        if (oscillator instanceof ModulatableOscillator m) {
            ports.addAll(m.getParameterPorts());
        }
        return ports;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            if (modulatable != null) {
                for (int i = 1; i < inputs.length; i++) {
                    int paramIndex = i - 1;
                    float value = inputs[i][s];
                    if (value != lastParams[paramIndex]) {
                        modulatable.setParameter(paramIndex, value);
                        lastParams[paramIndex] = value;
                    }
                }
            }
            double phase = inputs[PHASE][s];
            outputs[OUT][s] = oscillator.getSample(phase, nextDt(phase));
        }
    }

    private double nextDt(double phase) {
        double dt = 0;
        if (!Double.isNaN(lastPhase)) {
            double delta = (phase - lastPhase) / TWO_PI;
            if (delta < 0) {
                delta += 1.0;
            }
            if (delta > 0 && delta < 0.5) {
                dt = delta;
            }
        }
        lastPhase = phase;
        return dt;
    }

    @Override
    public String getName() {
        return oscillator.getName();
    }
}
