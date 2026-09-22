package JSynth.Oscillators.Implementations;

import JSynth.Oscillators.IOscillator;

public class Sine implements IOscillator {
    @Override
    public float getSample(double phase, double dt) {
        return (float) Math.sin(phase);
    }

    @Override
    public String getName() {
        return "Sine";
    }
}
