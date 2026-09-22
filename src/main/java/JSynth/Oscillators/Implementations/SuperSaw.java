package JSynth.Oscillators.Implementations;

import JSynth.Dsp.DspMath;
import JSynth.Oscillators.IOscillator;

public class SuperSaw implements IOscillator {
    private final int voices;
    private final double[] offsets;

    public SuperSaw() {
        this(7, 0.03);
    }

    public SuperSaw(int voices, double detune) {
        this.voices = Math.max(1, voices);
        this.offsets = new double[this.voices];
        for (int i = 0; i < this.voices; i++) {
            offsets[i] = this.voices == 1 ? 0.0
                    : (i - (this.voices - 1) / 2.0) / (this.voices - 1) * detune;
        }
    }

    @Override
    public float getSample(double phase, double dt) {
        double t = phase / (2 * Math.PI);
        float sum = 0f;
        for (double offset : offsets) {
            double ti = t + offset;
            ti = ti - Math.floor(ti);
            double value = 2.0 * ti - 1.0;
            if (dt > 0) {
                value -= DspMath.polyBlep(ti, dt);
            }
            sum += (float) value;
        }
        return sum / voices;
    }

    @Override
    public String getName() {
        return "SuperSaw";
    }
}
