package JSynth.Gui;

final class SoundClipTrack {
    private String name;
    private float[] samples;
    private boolean muted;
    private boolean solo;

    SoundClipTrack(String name, float[] samples) {
        this.name = name;
        this.samples = samples;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    float[] getSamples() {
        return samples;
    }

    void setSamples(float[] samples) {
        this.samples = samples;
    }

    boolean isMuted() {
        return muted;
    }

    void setMuted(boolean muted) {
        this.muted = muted;
    }

    boolean isSolo() {
        return solo;
    }

    void setSolo(boolean solo) {
        this.solo = solo;
    }
}
