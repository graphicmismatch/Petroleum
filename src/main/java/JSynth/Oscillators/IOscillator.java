package JSynth.Oscillators;

public interface IOscillator {
    public float getSample(double phase);

    public String getName();
}
