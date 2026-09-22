package Petroleum.Dsp;

public final class LiveGrainRecorder implements GrainPool.Source {
    private final double maxSeconds;
    private DelayLine buffer;
    private long totalWritten;

    public LiveGrainRecorder(double maxSeconds) {
        this.maxSeconds = maxSeconds;
    }

    public synchronized void ensureAllocated(double sampleRate) {
        if (buffer == null) {
            buffer = new DelayLine(Math.max(1, (int) Math.ceil(sampleRate * maxSeconds)));
        }
    }

    public void write(float sample) {
        buffer.write(sample);
        totalWritten++;
    }

    public double positionBehind(double secondsBehind, double sampleRate) {
        return totalWritten - secondsBehind * sampleRate;
    }

    public long totalWritten() {
        return totalWritten;
    }

    @Override
    public float read(double positionInSamples) {
        double delaySamples = totalWritten - positionInSamples;
        return buffer.readFractional(delaySamples);
    }

    @Override
    public int length() {
        return buffer == null ? 0 : (int) Math.min(buffer.capacity(), totalWritten);
    }
}
