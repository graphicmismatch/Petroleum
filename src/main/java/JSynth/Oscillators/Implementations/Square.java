package JSynth.Oscillators.Implementations;

import JSynth.Graph.Port;
import JSynth.Oscillators.ModulatableOscillator;

import java.util.List;

public class Square implements ModulatableOscillator {
    private volatile double width;

    public Square() {
        this(0.5);
    }

    public Square(double width) {
        this.width = width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    @Override
    public List<Port> getParameterPorts() {
        return List.of(new Port("width", 0.5f));
    }

    @Override
    public void setParameter(int index, float value) {
        if (index == 0) {
            setWidth(value);
        }
    }

    @Override
    public float getSample(double phase) {
        double t = phase / (2 * Math.PI);
        return t < Math.max(0.0, Math.min(1.0, width)) ? 1f : -1f;
    }

    @Override
    public String getName() {
        return "Square";
    }
}
