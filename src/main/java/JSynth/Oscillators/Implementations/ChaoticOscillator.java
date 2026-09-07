package JSynth.Oscillators.Implementations;

import JSynth.Oscillators.IOscillator;

public class ChaoticOscillator implements IOscillator {
    private final double r;
    private double x;
    private double lastPhase = Double.NaN;

    public ChaoticOscillator() {
        this(3.9997, 0.6180339887);
    }

    public ChaoticOscillator(double r, double seed) {
        this.r = r;
        this.x = seed;
    }

    @Override
    public float getSample(double phase) {
        if (!Double.isNaN(lastPhase) && lastPhase - phase > Math.PI) {
            x = r * x * (1 - x);
        }
        lastPhase = phase;
        double t = phase / (2 * Math.PI);
        return (float) Math.sin(2 * Math.PI * (t + x));
    }

    @Override
    public String getName() {
        return "Chaotic";
    }
}
