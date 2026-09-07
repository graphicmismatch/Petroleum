package JSynth.Graph;

import java.util.List;

public interface Node {
    int OUT = 0;

    List<Port> getInputPorts();

    List<String> getOutputPorts();

    void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context);

    String getName();

    default int inputIndexOf(String name) {
        List<Port> ports = getInputPorts();
        for (int i = 0; i < ports.size(); i++) {
            if (ports.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown input port '" + name + "' on " + getName());
    }

    default int outputIndexOf(String name) {
        List<String> ports = getOutputPorts();
        for (int i = 0; i < ports.size(); i++) {
            if (ports.get(i).equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown output port '" + name + "' on " + getName());
    }
}
