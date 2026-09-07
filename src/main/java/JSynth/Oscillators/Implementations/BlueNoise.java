package JSynth.Oscillators.Implementations;

public class BlueNoise extends NoiseOscillator {
    private float lastWhite = 0f;

    @Override
    public float getSample(double gate) {
        float white = white();
        float blue = (white - lastWhite) * 0.5f;
        lastWhite = white;
        return gate > 0 ? blue : 0f;
    }

    @Override
    public String getName() {
        return "Blue Noise";
    }
}
