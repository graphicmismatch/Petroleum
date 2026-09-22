package Petroleum.Oscillators.Implementations;

public class BrownNoise extends NoiseOscillator {
    private float lastOutput = 0f;

    @Override
    public float getSample(double gate, double dt) {
        float white = white();
        float brown = (lastOutput + 0.02f * white) / 1.02f;
        lastOutput = brown;
        float sample = Math.max(-1f, Math.min(1f, brown * 3.5f));
        return gate > 0 ? sample : 0f;
    }

    @Override
    public String getName() {
        return "Brown Noise";
    }
}
