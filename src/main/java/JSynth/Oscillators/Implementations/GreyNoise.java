package JSynth.Oscillators.Implementations;

public class GreyNoise extends NoiseOscillator {
    private float lowState = 0f;

    @Override
    public float getSample(double gate) {
        float white = white();
        lowState += 0.05f * (white - lowState);
        float low = lowState;
        float high = white - low;
        float grey = 0.6f * low + 0.9f * high;
        float sample = Math.max(-1f, Math.min(1f, grey));
        return gate > 0 ? sample : 0f;
    }

    @Override
    public String getName() {
        return "Grey Noise";
    }
}
