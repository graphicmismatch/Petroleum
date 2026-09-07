package JSynth.Instruments;

import JSynth.Dsp.DspMath;
import JSynth.Dsp.EdgeDetector;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class InstrumentNode extends AbstractNode {
    public static final int PITCH = 0;
    public static final int GATE = 1;

    private volatile Instrument instrument = new Instrument();

    private final EdgeDetector gateEdge = new EdgeDetector();
    private Instrument.SampleZone activeZone;
    private double position;
    private float envelopeTime;
    private boolean sustained;
    private boolean gateHeld;

    public InstrumentNode() {
        super(List.of(new Port("pitch", 440f), new Port("gate", 0f)), List.of("out"));
    }

    public Instrument getInstrument() {
        return instrument.copy();
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument.copy();
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        Instrument current = instrument;
        List<Instrument.SampleZone> zones = current.zones();
        List<Instrument.EnvelopePoint> envelope = current.envelope();
        int sustainIndex = current.sustainPointIndex();
        float sampleRate = (float) context.sampleRate;

        for (int s = 0; s < frameCount; s++) {
            float pitch = inputs[PITCH][s];
            float gate = inputs[GATE][s];
            gateHeld = gate > 0f;

            if (gateEdge.edge(gate) > 0) {
                activeZone = pickZone(zones, pitch);
                position = 0;
                envelopeTime = 0f;
                sustained = false;
            }

            float value = 0f;
            if (activeZone != null && !envelope.isEmpty()) {
                value = readZone(activeZone, pitch) * currentEnvelopeLevel(envelope, sustainIndex);
                advanceEnvelope(envelope, sustainIndex, sampleRate);
            }
            outputs[OUT][s] = value;
        }
    }

    private static Instrument.SampleZone pickZone(List<Instrument.SampleZone> zones, float pitch) {
        Instrument.SampleZone nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        float safePitch = pitch > 0f ? pitch : 0.0001f;
        for (Instrument.SampleZone zone : zones) {
            if (pitch >= zone.minHz && pitch <= zone.maxHz) {
                return zone;
            }
            float rootHz = zone.rootHz > 0f ? zone.rootHz : 0.0001f;
            float distance = Math.abs((float) Math.log(safePitch / rootHz));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = zone;
            }
        }
        return nearest;
    }

    private float readZone(Instrument.SampleZone zone, float pitch) {
        float[] samples = zone.samples;
        int length = samples.length;
        if (position >= length) {
            return 0f;
        }
        int i0 = (int) position;
        int i1 = i0 + 1 >= length ? i0 : i0 + 1;
        float frac = (float) (position - i0);
        float value = DspMath.lerp(samples[i0], samples[i1], frac);

        double ratio = zone.rootHz > 0f ? pitch / zone.rootHz : 1.0;
        position += ratio;
        if (zone.loopEnabled && gateHeld && zone.loopEndFrame > zone.loopStartFrame
                && position >= zone.loopEndFrame) {
            position -= (zone.loopEndFrame - zone.loopStartFrame);
        }
        return value;
    }

    private float currentEnvelopeLevel(List<Instrument.EnvelopePoint> envelope, int sustainIndex) {
        float lastTime = envelope.get(envelope.size() - 1).timeSeconds();
        if (!gateHeld && envelopeTime >= lastTime) {
            return 0f;
        }
        if (envelopeTime <= envelope.get(0).timeSeconds()) {
            return envelope.get(0).level();
        }
        for (int i = 1; i < envelope.size(); i++) {
            Instrument.EnvelopePoint point = envelope.get(i);
            if (envelopeTime < point.timeSeconds()) {
                Instrument.EnvelopePoint previous = envelope.get(i - 1);
                float span = point.timeSeconds() - previous.timeSeconds();
                float t = span <= 0f ? 1f : (envelopeTime - previous.timeSeconds()) / span;
                return DspMath.lerp(previous.level(), point.level(), t);
            }
        }
        return envelope.get(envelope.size() - 1).level();
    }

    private void advanceEnvelope(List<Instrument.EnvelopePoint> envelope, int sustainIndex, float sampleRate) {
        if (sustainIndex >= 0 && sustainIndex < envelope.size() && gateHeld
                && envelopeTime >= envelope.get(sustainIndex).timeSeconds()) {
            sustained = true;
        }
        if (sustained && gateHeld) {
            return;
        }
        envelopeTime += 1f / sampleRate;
    }

    @Override
    public String getName() {
        return "Instrument";
    }
}
