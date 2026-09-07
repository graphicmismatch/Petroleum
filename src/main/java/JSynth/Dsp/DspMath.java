package JSynth.Dsp;

public class DspMath {
    public static float lerp(float a, float b, float t) {
        return a * (1 - t) + b * t;
    }

    public static float onePoleLowpassAlpha(float cutoffHz, double sampleRate) {
        double rc = 1.0 / (2 * Math.PI * cutoffHz);
        double dt = 1.0 / sampleRate;
        return (float) (dt / (rc + dt));
    }

    public static float onePoleHighpassAlpha(float cutoffHz, double sampleRate) {
        double rc = 1.0 / (2 * Math.PI * cutoffHz);
        double dt = 1.0 / sampleRate;
        return (float) (rc / (rc + dt));
    }
}
