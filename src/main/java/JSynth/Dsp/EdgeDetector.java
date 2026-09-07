package JSynth.Dsp;

public final class EdgeDetector {
    private float previous;

    public int edge(float value) {
        int result = 0;
        if (previous <= 0f && value > 0f) {
            result = 1;
        } else if (previous > 0f && value <= 0f) {
            result = -1;
        }
        previous = value;
        return result;
    }
}
