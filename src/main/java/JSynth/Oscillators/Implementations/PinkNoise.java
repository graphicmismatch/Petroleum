package JSynth.Oscillators.Implementations;

public class PinkNoise extends NoiseOscillator {

    private float b0, b1, b2, b3, b4, b5, b6;

    @Override
    public float getSample(double gate) {
        float white = white();

        b0 = 0.99886f * b0 + white * 0.0555179f;
        b1 = 0.99332f * b1 + white * 0.0750759f;
        b2 = 0.96900f * b2 + white * 0.1538520f;
        b3 = 0.86650f * b3 + white * 0.3104856f;
        b4 = 0.55000f * b4 + white * 0.5329522f;
        b5 = -0.7616f * b5 - white * 0.0168980f;
        float pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f;
        b6 = white * 0.115926f;

        return gate > 0 ? pink * 0.11f : 0f;
    }

    @Override
    public String getName() {
        return "Pink Noise";
    }
}
