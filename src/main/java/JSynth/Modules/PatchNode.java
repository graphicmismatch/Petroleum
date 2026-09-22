package JSynth.Modules;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.Node;
import JSynth.Graph.NodeContext;
import JSynth.Graph.OneShot;
import JSynth.Graph.Port;
import JSynth.Patch.Patch;
import JSynth.Serialization.Macro;
import JSynth.Serialization.PatchDocument;
import JSynth.Sinks.VirtualLineOut;
import JSynth.Sources.MicInputNode;
import JSynth.Sources.MidiInputNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatchNode extends AbstractNode {
    private final String name;
    private final PatchDocument document;
    private final Patch inner;
    private final PortBridgeNode[] bridges;

    public static PatchNode create(String name, PatchDocument document) {
        return holdsOneShot(document)
                ? new OneShotPatchNode(name, document)
                : new PatchNode(name, document);
    }

    private static boolean holdsOneShot(PatchDocument document) {
        for (Node node : document.getNodes()) {
            if (node instanceof OneShot) {
                return true;
            }
        }
        return false;
    }

    PatchNode(String name, PatchDocument document) {
        super(inputPortsOf(name, document), List.of("out"));
        this.name = name == null || name.isBlank() ? "Patch" : name;
        this.document = document;

        List<Macro> macros = document.getMacros();
        Map<Node, Node> substitutions = new IdentityHashMap<>();
        this.bridges = new PortBridgeNode[macros.size()];
        for (int i = 0; i < macros.size(); i++) {
            Macro macro = macros.get(i);
            PortBridgeNode bridge = new PortBridgeNode(macro.name());
            bridges[i] = bridge;
            if (substitutions.put(macro.target(), bridge) != null) {
                throw new IllegalArgumentException("Patch '" + this.name + "' has two macros bound to the same knob,"
                        + " so they cannot both become input ports");
            }
        }
        for (Node node : document.getNodes()) {
            if (node instanceof MidiInputNode || node instanceof MicInputNode || node instanceof VirtualLineOut) {
                substitutions.put(node, new MutedNode(node.getInputPorts(), node.getOutputPorts()));
            }
        }

        try {
            this.inner = document.buildPatch(substitutions);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Patch '" + this.name + "' cannot be used as a node: " + ex.getMessage(), ex);
        }
    }

    private static List<Port> inputPortsOf(String name, PatchDocument document) {
        List<Port> ports = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Macro macro : document.getMacros()) {
            if (!seen.add(macro.name())) {
                throw new IllegalArgumentException("Patch '" + name + "' has two macros called '"
                        + macro.name() + "', so they cannot both become input ports");
            }
            ports.add(new Port(macro.name(), macro.target().getValue()));
        }
        return ports;
    }

    public PatchDocument getDocument() {
        return document;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        for (int i = 0; i < bridges.length; i++) {
            bridges[i].bind(inputs[i], 0);
        }
        inner.renderNested(outputs[OUT], 0, frameCount, context);
    }

    boolean innerFinished() {
        return !inner.hasOneShots() || inner.isFinished();
    }

    @Override
    public String getName() {
        return name;
    }
}
