package Petroleum.Oscillators.Implementations;

import Petroleum.Dsp.DspMath;
import Petroleum.Oscillators.IOscillator;

public class Wavetable implements IOscillator {
    private volatile float[] table;
    private final String name;

    public Wavetable(float[] table) {
        this(table, "Wavetable");
    }

    public Wavetable(float[] table, String name) {
        this.table = table;
        this.name = name;
    }

    public float[] getTable() {
        return table;
    }

    public void setTable(float[] table) {
        this.table = table;
    }

    @Override
    public float getSample(double phase, double dt) {
        float[] t = table;
        double phaseFraction = phase / (2 * Math.PI);
        phaseFraction = phaseFraction - Math.floor(phaseFraction);
        double index = phaseFraction * t.length;
        int i0 = (int) index;
        if (i0 >= t.length) {
            i0 = 0;
        }
        int i1 = i0 + 1;
        if (i1 >= t.length) {
            i1 = 0;
        }
        float frac = (float) (index - Math.floor(index));
        return DspMath.lerp(t[i0], t[i1], frac);
    }

    @Override
    public String getName() {
        return name;
    }
}
