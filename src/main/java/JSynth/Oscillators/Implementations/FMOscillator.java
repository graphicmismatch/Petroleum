package JSynth.Oscillators.Implementations;

import JSynth.Graph.Port;
import JSynth.Oscillators.ModulatableOscillator;

import java.util.List;

public class FMOscillator implements ModulatableOscillator {
    private volatile double modRatio;
    private volatile double modIndex;

    public FMOscillator() {
        this(2.0, 3.0);
    }

    public FMOscillator(double modRatio, double modIndex) {
        this.modRatio = modRatio;
        this.modIndex = modIndex;
    }

    public void setModRatio(double modRatio) {
        this.modRatio = modRatio;
    }

    public void setModIndex(double modIndex) {
        this.modIndex = modIndex;
    }

    @Override
    public List<Port> getParameterPorts() {
        return List.of(new Port("mod ratio", 2f), new Port("mod index", 3f));
    }

    @Override
    public void setParameter(int index, float value) {
        switch (index) {
            case 0 -> setModRatio(value);
            case 1 -> setModIndex(value);
            default -> { }
        }
    }

    @Override
    public float getSample(double phase, double dt) {
        double modulator = Math.sin(phase * modRatio);
        return (float) Math.sin(phase + modIndex * modulator);
    }

    @Override
    public String getName() {
        return "FM";
    }
}
