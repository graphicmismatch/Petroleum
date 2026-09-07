package JSynth.Oscillators.Implementations;

import JSynth.Oscillators.IOscillator;

public class Sawtooth implements IOscillator {
    @Override
    public float getSample(double phase) {
        double t = phase / (2 * Math.PI);
        return (float) (2.0 * t - 1.0);
    }

    @Override
    public String getName() {
        return "Sawtooth";
    }
}
