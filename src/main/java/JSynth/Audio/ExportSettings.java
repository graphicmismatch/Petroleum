package JSynth.Audio;

public record ExportSettings(
        float sampleRate,
        int bitDepth,
        int channels,
        boolean normalize,
        boolean trimSilence) {

    public static final float TRIM_THRESHOLD = 1e-4f;
    public static final float NORMALIZE_TARGET = 0.98f;

    public ExportSettings {
        if (sampleRate < 1000f || sampleRate > 192000f) {
            throw new IllegalArgumentException("Sample rate must be between 1000 and 192000 Hz");
        }
        if (bitDepth != 8 && bitDepth != 16 && bitDepth != 24 && bitDepth != 32) {
            throw new IllegalArgumentException("Bit depth must be 8, 16, 24, or 32 (32 = float)");
        }
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Channels must be 1 or 2");
        }
    }

    public static ExportSettings defaults() {
        return new ExportSettings(44100f, 16, 2, false, false);
    }

    public boolean needsBuffering() {
        return normalize || trimSilence;
    }

    public int bytesPerFrame() {
        return channels * (bitDepth / 8);
    }
}
