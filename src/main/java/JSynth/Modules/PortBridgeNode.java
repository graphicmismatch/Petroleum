package JSynth.Modules;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;

import java.util.Arrays;
import java.util.List;

final class PortBridgeNode extends AbstractNode {
    private final String name;
    private float[] source;
    private int offset;

    PortBridgeNode(String name) {
        super(List.of(), List.of("out"));
        this.name = name;
    }

    void bind(float[] source, int offset) {
        this.source = source;
        this.offset = offset;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        if (source == null) {
            Arrays.fill(outputs[OUT], 0, frameCount, 0f);
            return;
        }
        System.arraycopy(source, offset, outputs[OUT], 0, frameCount);
    }

    @Override
    public String getName() {
        return name;
    }
}
