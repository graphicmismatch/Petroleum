package Petroleum.Oscillators.Implementations;

import Petroleum.Oscillators.IOscillator;

import java.util.Random;

public abstract class NoiseOscillator implements IOscillator {
    protected final Random random = new Random();

    protected final float white() {
        return (float) (random.nextDouble() * 2.0 - 1.0);
    }
}
