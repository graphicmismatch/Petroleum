package JSynth.Graph;

import java.util.List;

public abstract class AbstractNode implements Node {
    private final List<Port> inputPorts;
    private final List<String> outputPorts;

    protected AbstractNode(List<Port> inputPorts, List<String> outputPorts) {
        this.inputPorts = List.copyOf(inputPorts);
        this.outputPorts = List.copyOf(outputPorts);
    }

    @Override
    public List<Port> getInputPorts() {
        return inputPorts;
    }

    @Override
    public List<String> getOutputPorts() {
        return outputPorts;
    }

    protected static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
