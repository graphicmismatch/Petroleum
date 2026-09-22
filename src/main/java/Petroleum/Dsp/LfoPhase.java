package Petroleum.Dsp;

public class LfoPhase {
    private double phase = 0;

    public double next(double freqHz, double sampleRate) {
        phase += 2 * Math.PI * freqHz / sampleRate;
        if (phase > 2 * Math.PI) {
            phase -= 2 * Math.PI;
        }
        return phase;
    }
}
