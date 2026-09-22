package Petroleum.Oscillators.Implementations;

import Petroleum.Dsp.DspMath;
import Petroleum.Oscillators.IOscillator;

public class Sawtooth implements IOscillator {
    @Override
    public float getSample(double phase, double dt) {
        double t = phase / (2 * Math.PI);
        double value = 2.0 * t - 1.0;
        if (dt > 0) {
            value -= DspMath.polyBlep(t, dt);
        }
        return (float) value;
    }

    @Override
    public String getName() {
        return "Sawtooth";
    }
}
