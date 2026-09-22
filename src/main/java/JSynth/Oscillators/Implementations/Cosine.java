package JSynth.Oscillators.Implementations;

import JSynth.Oscillators.IOscillator;

public class Cosine implements IOscillator {
    @Override
    public float getSample(double phase, double dt) {
        return (float) Math.cos(phase);
    }

    @Override
    public String getName() {
        return "Cosine";
    }
}
