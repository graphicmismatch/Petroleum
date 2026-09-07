package JSynth.Instruments;

import java.util.ArrayList;
import java.util.List;

public final class Instrument {
    public static final class SampleZone {
        public String name;
        public float[] samples;
        public float rootHz;
        public float minHz;
        public float maxHz;
        public boolean loopEnabled;
        public int loopStartFrame;
        public int loopEndFrame;

        public SampleZone(String name, float[] samples, float rootHz, float minHz, float maxHz) {
            this.name = name;
            this.samples = samples;
            this.rootHz = rootHz;
            this.minHz = minHz;
            this.maxHz = maxHz;
            this.loopStartFrame = 0;
            this.loopEndFrame = samples.length;
        }

        SampleZone copy() {
            SampleZone copy = new SampleZone(name, samples, rootHz, minHz, maxHz);
            copy.loopEnabled = loopEnabled;
            copy.loopStartFrame = loopStartFrame;
            copy.loopEndFrame = loopEndFrame;
            return copy;
        }
    }

    public record EnvelopePoint(float timeSeconds, float level) {
    }

    private final List<SampleZone> zones = new ArrayList<>();
    private final List<EnvelopePoint> envelope = new ArrayList<>();
    private int sustainPointIndex = -1;

    public Instrument() {
        envelope.add(new EnvelopePoint(0f, 0f));
        envelope.add(new EnvelopePoint(0.01f, 1f));
        envelope.add(new EnvelopePoint(0.3f, 0f));
    }

    public List<SampleZone> zones() {
        return zones;
    }

    public List<EnvelopePoint> envelope() {
        return envelope;
    }

    public void setEnvelope(List<EnvelopePoint> points) {
        List<EnvelopePoint> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> Float.compare(a.timeSeconds(), b.timeSeconds()));
        envelope.clear();
        envelope.addAll(sorted);
    }

    public int sustainPointIndex() {
        return sustainPointIndex;
    }

    public void setSustainPointIndex(int index) {
        sustainPointIndex = index;
    }

    public Instrument copy() {
        Instrument copy = new Instrument();
        copy.zones.clear();
        for (SampleZone zone : zones) {
            copy.zones.add(zone.copy());
        }
        copy.envelope.clear();
        copy.envelope.addAll(envelope);
        copy.sustainPointIndex = sustainPointIndex;
        return copy;
    }
}
