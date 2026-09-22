package JSynth.Oscillators.Implementations;

import JSynth.Graph.Port;
import JSynth.Oscillators.IOscillator;
import JSynth.Oscillators.WrapperOscillator;

import java.util.List;

public class DualOscillator implements WrapperOscillator {
    public static final double DEFAULT_OFFSET = Math.PI;

    private volatile IOscillator base;
    private volatile double offset;

    public DualOscillator(IOscillator base, double offset) {
        this.base = base;
        this.offset = offset;
    }

    public IOscillator getBase() {
        return base;
    }

    public void setBase(IOscillator base) {
        this.base = base;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }

    @Override
    public List<Port> getParameterPorts() {
        return List.of(new Port("offset", (float) DEFAULT_OFFSET));
    }

    @Override
    public void setParameter(int index, float value) {
        if (index == 0) {
            setOffset(value);
        }
    }

    @Override
    public float getSample(double phase, double dt) {
        IOscillator currentBase = base;
        float a = currentBase.getSample(phase, dt);
        float b = currentBase.getSample((phase + offset) % (2 * Math.PI), dt);
        return (a + b) * 0.5f;
    }

    @Override
    public String getName() {
        return "Dual " + base.getName();
    }
}
