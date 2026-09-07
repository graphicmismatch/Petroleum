package JSynth.Graph;

public class NodeContext {
    public final double sampleRate;
    private long sampleIndex;

    public NodeContext(double sampleRate) {
        this.sampleRate = sampleRate;
        this.sampleIndex = 0;
    }

    public long sampleIndex() {
        return sampleIndex;
    }

    public void advance(int frames) {
        sampleIndex += frames;
    }
}
