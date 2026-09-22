package JSynth.Oscillators.Implementations;

import JSynth.Oscillators.IOscillator;

public class Triangle implements IOscillator {
    @Override
    public float getSample(double phase, double dt) {
        double t = phase / (2 * Math.PI);
        return (float) (4 * Math.abs(t - 0.5) - 1);
    }

    @Override
    public String getName() {
        return "Triangle";
    }
}
