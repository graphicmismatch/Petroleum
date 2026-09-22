package Petroleum.Instruments;

import Petroleum.Dsp.DspMath;
import Petroleum.Dsp.EdgeDetector;
import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Port;

import java.util.ArrayList;
import java.util.List;

public class InstrumentNode extends AbstractNode {
    private final int channelCount;

    private volatile Instrument instrument = new Instrument();

    private final EdgeDetector[] gateEdge;
    private final Instrument.SampleZone[] activeZone;
    private final double[] position;
    private final float[] envelopeTime;
    private final boolean[] sustained;
    private final boolean[] gateHeld;

    public InstrumentNode() {
        this(1);
    }

    public InstrumentNode(int channelCount) {
        super(buildInputPorts(channelCount), buildOutputNames(channelCount));
        this.channelCount = channelCount;
        gateEdge = new EdgeDetector[channelCount];
        activeZone = new Instrument.SampleZone[channelCount];
        position = new double[channelCount];
        envelopeTime = new float[channelCount];
        sustained = new boolean[channelCount];
        gateHeld = new boolean[channelCount];
        for (int c = 0; c < channelCount; c++) {
            gateEdge[c] = new EdgeDetector();
        }
    }

    public int getChannelCount() {
        return channelCount;
    }

    public static int pitchPort(int channel) {
        return channel * 2;
    }

    public static int gatePort(int channel) {
        return channel * 2 + 1;
    }

    private static List<Port> buildInputPorts(int channelCount) {
        List<Port> ports = new ArrayList<>(channelCount * 2);
        for (int c = 0; c < channelCount; c++) {
            ports.add(new Port("pitch" + c, 440f));
            ports.add(new Port("gate" + c, 0f));
        }
        return ports;
    }

    private static List<String> buildOutputNames(int channelCount) {
        List<String> names = new ArrayList<>(channelCount);
        for (int c = 0; c < channelCount; c++) {
            names.add("out" + c);
        }
        return names;
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

        for (int c = 0; c < channelCount; c++) {
            int pitchPort = pitchPort(c);
            int gatePort = gatePort(c);
            for (int s = 0; s < frameCount; s++) {
                float pitch = inputs[pitchPort][s];
                float gate = inputs[gatePort][s];
                gateHeld[c] = gate > 0f;

                if (gateEdge[c].edge(gate) > 0) {
                    activeZone[c] = pickZone(zones, pitch);
                    position[c] = 0;
                    envelopeTime[c] = 0f;
                    sustained[c] = false;
                }

                float value = 0f;
                if (activeZone[c] != null && !envelope.isEmpty()) {
                    value = readZone(c, activeZone[c], pitch) * currentEnvelopeLevel(c, envelope, sustainIndex);
                    advanceEnvelope(c, envelope, sustainIndex, sampleRate);
                }
                outputs[c][s] = value;
            }
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

    private float readZone(int c, Instrument.SampleZone zone, float pitch) {
        float[] samples = zone.samples;
        int length = samples.length;
        if (position[c] >= length) {
            return 0f;
        }
        int i0 = (int) position[c];
        int i1 = i0 + 1 >= length ? i0 : i0 + 1;
        float frac = (float) (position[c] - i0);
        float value = DspMath.lerp(samples[i0], samples[i1], frac);

        double ratio = zone.rootHz > 0f ? pitch / zone.rootHz : 1.0;
        position[c] += ratio;
        if (zone.loopEnabled && gateHeld[c] && zone.loopEndFrame > zone.loopStartFrame
                && position[c] >= zone.loopEndFrame) {
            position[c] -= (zone.loopEndFrame - zone.loopStartFrame);
        }
        return value;
    }

    private float currentEnvelopeLevel(int c, List<Instrument.EnvelopePoint> envelope, int sustainIndex) {
        float lastTime = envelope.get(envelope.size() - 1).timeSeconds();
        if (!gateHeld[c] && envelopeTime[c] >= lastTime) {
            return 0f;
        }
        if (envelopeTime[c] <= envelope.get(0).timeSeconds()) {
            return envelope.get(0).level();
        }
        for (int i = 1; i < envelope.size(); i++) {
            Instrument.EnvelopePoint point = envelope.get(i);
            if (envelopeTime[c] < point.timeSeconds()) {
                Instrument.EnvelopePoint previous = envelope.get(i - 1);
                float span = point.timeSeconds() - previous.timeSeconds();
                float t = span <= 0f ? 1f : (envelopeTime[c] - previous.timeSeconds()) / span;
                return DspMath.lerp(previous.level(), point.level(), t);
            }
        }
        return envelope.get(envelope.size() - 1).level();
    }

    private void advanceEnvelope(int c, List<Instrument.EnvelopePoint> envelope, int sustainIndex, float sampleRate) {
        if (sustainIndex >= 0 && sustainIndex < envelope.size() && gateHeld[c]
                && envelopeTime[c] >= envelope.get(sustainIndex).timeSeconds()) {
            sustained[c] = true;
        }
        if (sustained[c] && gateHeld[c]) {
            return;
        }
        envelopeTime[c] += 1f / sampleRate;
    }

    @Override
    public String getName() {
        return "Instrument";
    }
}
