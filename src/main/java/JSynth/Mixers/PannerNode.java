package JSynth.Mixers;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class PannerNode extends AbstractNode {
    public enum PanLaw {
        LINEAR("Linear"), EQUAL_POWER("Equal Power");

        private final String label;

        PanLaw(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final int IN = 0;
    public static final int PAN = 1;
    public static final int OUT_L = 0;
    public static final int OUT_R = 1;

    private final PanLaw law;

    private float lastPan = Float.NaN;
    private float gainL;
    private float gainR;

    public PannerNode(PanLaw law) {
        super(List.of(new Port("in", 0f), new Port("pan", 0f)), List.of("outL", "outR"));
        this.law = law;
    }

    public PanLaw getLaw() {
        return law;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int s = 0; s < frameCount; s++) {
            float pan = clamp(inputs[PAN][s], -1f, 1f);
            if (pan != lastPan) {
                if (law == PanLaw.EQUAL_POWER) {
                    double angle = (pan + 1.0) * Math.PI / 4.0;
                    gainL = (float) Math.cos(angle);
                    gainR = (float) Math.sin(angle);
                } else {
                    float t = (pan + 1f) / 2f;
                    gainL = 1f - t;
                    gainR = t;
                }
                lastPan = pan;
            }
            float in = inputs[IN][s];
            outputs[OUT_L][s] = in * gainL;
            outputs[OUT_R][s] = in * gainR;
        }
    }

    @Override
    public String getName() {
        return "Panner " + law.label();
    }
}
