package Petroleum.Oscillators.Implementations;

import Petroleum.Dsp.DspMath;
import Petroleum.Graph.Port;
import Petroleum.Oscillators.ModulatableOscillator;

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
    public float getSample(double phase, double dt) {
        double t = phase / (2 * Math.PI);
        double w = Math.max(0.0, Math.min(1.0, width));
        double value = t < w ? 1.0 : -1.0;
        if (dt > 0) {
            value += DspMath.polyBlep(t, dt);
            double fallEdge = t - w;
            if (fallEdge < 0) {
                fallEdge += 1.0;
            }
            value -= DspMath.polyBlep(fallEdge, dt);
        }
        return (float) value;
    }

    @Override
    public String getName() {
        return "Square";
    }
}
