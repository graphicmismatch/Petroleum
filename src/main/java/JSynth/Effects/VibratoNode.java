package JSynth.Effects;

import JSynth.Dsp.DelayLine;
import JSynth.Dsp.LfoPhase;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class VibratoNode extends AbstractNode {
    public static final int IN = 0;
    public static final int RATE = 1;
    public static final int DEPTH = 2;

    private static final double CENTER_DELAY_MS = 7.5;
    private static final double MAX_DELAY_MS = 15.0;

    private DelayLine delayLine;
    private final LfoPhase lfo = new LfoPhase();

    public VibratoNode() {
        super(List.of(new Port("in", 0f), new Port("rate", 5f), new Port("depth", 3f)), List.of("out"));
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (delayLine == null) {
            delayLine = new DelayLine(Math.max(1, (int) Math.ceil(context.sampleRate * MAX_DELAY_MS / 1000.0)));
        }

        for (int s = 0; s < frameCount; s++) {
            float x0 = inputs[IN][s];
            float rate = Math.max(0.01f, inputs[RATE][s]);
            float depthMs = clamp(inputs[DEPTH][s], 0f, (float) (MAX_DELAY_MS - CENTER_DELAY_MS));

            double lfoPhase = lfo.next(rate, context.sampleRate);

            double delayMs = CENTER_DELAY_MS + depthMs * Math.sin(lfoPhase);
            double delaySamples = delayMs / 1000.0 * context.sampleRate;

            float delayed = delayLine.readFractional(delaySamples);
            delayLine.write(x0);

            outputs[OUT][s] = delayed;
        }
    }

    @Override
    public String getName() {
        return "Vibrato";
    }
}
