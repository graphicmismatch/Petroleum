package Petroleum.Oscillators.Implementations;

public class VioletNoise extends NoiseOscillator {
    private float lastWhite = 0f;
    private float lastDiff = 0f;

    @Override
    public float getSample(double gate, double dt) {
        float white = white();
        float diff = white - lastWhite;
        float violet = (diff - lastDiff) * 0.25f;
        lastWhite = white;
        lastDiff = diff;
        float sample = Math.max(-1f, Math.min(1f, violet));
        return gate > 0 ? sample : 0f;
    }

    @Override
    public String getName() {
        return "Violet Noise";
    }
}
