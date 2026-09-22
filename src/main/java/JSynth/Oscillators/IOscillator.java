package JSynth.Oscillators;

public interface IOscillator {
    public float getSample(double phase, double dt);

    public String getName();
}
