package JSynth.Dsp;

public class DspMath {
    public static float lerp(float a, float b, float t) {
        return a * (1 - t) + b * t;
    }

    public static double onePoleLowpassAlpha(double cutoffHz, double sampleRate) {
        double rc = 1.0 / (2 * Math.PI * cutoffHz);
        double dt = 1.0 / sampleRate;
        return dt / (rc + dt);
    }

    public static double onePoleHighpassAlpha(double cutoffHz, double sampleRate) {
        double rc = 1.0 / (2 * Math.PI * cutoffHz);
        double dt = 1.0 / sampleRate;
        return rc / (rc + dt);
    }

    public static double polyBlep(double t, double dt) {
        if (t < dt) {
            t /= dt;
            return t + t - t * t - 1.0;
        } else if (t > 1.0 - dt) {
            t = (t - 1.0) / dt;
            return t * t + t + t + 1.0;
        }
        return 0.0;
    }
}
