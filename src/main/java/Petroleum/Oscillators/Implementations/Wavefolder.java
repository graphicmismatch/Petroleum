package Petroleum.Oscillators.Implementations;

import Petroleum.Graph.Port;
import Petroleum.Oscillators.IOscillator;
import Petroleum.Oscillators.WrapperOscillator;

import java.util.List;

public class Wavefolder implements WrapperOscillator {
    public static final double DEFAULT_GAIN = 2.0;

    private volatile IOscillator base;
    private volatile double gain;

    public Wavefolder(IOscillator base, double gain) {
        this.base = base;
        this.gain = gain;
    }

    public IOscillator getBase() {
        return base;
    }

    public void setBase(IOscillator base) {
        this.base = base;
    }

    public void setGain(double gain) {
        this.gain = gain;
    }

    @Override
    public List<Port> getParameterPorts() {
        return List.of(new Port("gain", (float) DEFAULT_GAIN));
    }

    @Override
    public void setParameter(int index, float value) {
        if (index == 0) {
            setGain(value);
        }
    }

    @Override
    public float getSample(double phase, double dt) {
        double x = base.getSample(phase, dt) * gain;
        while (x > 1.0 || x < -1.0) {
            if (x > 1.0) {
                x = 2.0 - x;
            } else {
                x = -2.0 - x;
            }
        }
        return (float) x;
    }

    @Override
    public String getName() {
        return "Folded " + base.getName();
    }
}
