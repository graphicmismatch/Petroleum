package JSynth.Oscillators.Implementations;

import JSynth.Dsp.DspMath;
import JSynth.Oscillators.IOscillator;

public class Wavetable implements IOscillator {
    private final float[] table;
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

    @Override
    public float getSample(double phase) {
        double t = phase / (2 * Math.PI);
        t = t - Math.floor(t);
        double index = t * table.length;
        int i0 = (int) index;
        if (i0 >= table.length) {
            i0 = 0;
        }
        int i1 = i0 + 1;
        if (i1 >= table.length) {
            i1 = 0;
        }
        float frac = (float) (index - Math.floor(index));
        return DspMath.lerp(table[i0], table[i1], frac);
    }

    @Override
    public String getName() {
        return name;
    }
}
