package Petroleum.Dsp;

public class DelayLine {
    private final float[] buffer;
    private int writeIndex = 0;

    public DelayLine(int capacity) {
        buffer = new float[Math.max(1, capacity)];
    }

    public float readFractional(double delaySamples) {
        int capacity = buffer.length;
        double clampedDelay = delaySamples;
        if (clampedDelay < 0) {
            clampedDelay = 0;
        } else if (clampedDelay > capacity - 1) {
            clampedDelay = capacity - 1;
        }

        double readPos = writeIndex - clampedDelay;
        if (readPos < 0) {
            readPos += capacity;
        }
        if (readPos >= capacity) {
            readPos -= capacity;
        }

        int i0 = (int) readPos;
        int i1 = i0 + 1;
        if (i1 >= capacity) {
            i1 = 0;
        }
        float frac = (float) (readPos - Math.floor(readPos));
        return DspMath.lerp(buffer[i0], buffer[i1], frac);
    }

    public void write(float sample) {
        buffer[writeIndex] = sample;
        writeIndex++;
        if (writeIndex >= buffer.length) {
            writeIndex = 0;
        }
    }

    public int capacity() {
        return buffer.length;
    }
}
