package JSynth.Modules;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

final class MutedNode extends AbstractNode {
    MutedNode(List<Port> inputs, List<String> outputs) {
        super(inputs, outputs);
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
    }

    @Override
    public String getName() {
        return "Muted";
    }
}
