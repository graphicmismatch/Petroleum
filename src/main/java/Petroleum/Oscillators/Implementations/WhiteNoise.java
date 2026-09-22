package Petroleum.Oscillators.Implementations;

public class WhiteNoise extends NoiseOscillator {

    @Override
    public float getSample(double gate, double dt) {
        return gate > 0 ? white() : 0f;
    }

    @Override
    public String getName() {
        return "White Noise";
    }
}
