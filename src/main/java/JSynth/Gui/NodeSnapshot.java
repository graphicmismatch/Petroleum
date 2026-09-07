package JSynth.Gui;

import JSynth.Graph.Node;
import JSynth.Patch.Connection;
import JSynth.Serialization.Macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NodeSnapshot {
    private final Node node;
    private final double x;
    private final double y;
    private final Map<String, Float> portValues;
    private final List<Connection> connections;
    private final List<Macro> macros;

    private NodeSnapshot(Node node, double x, double y, Map<String, Float> portValues,
                         List<Connection> connections, List<Macro> macros) {
        this.node = node;
        this.x = x;
        this.y = y;
        this.portValues = portValues;
        this.connections = connections;
        this.macros = macros;
    }

    static NodeSnapshot capture(GraphEditor editor, NodeView view) {
        Node node = view.getNode();
        List<Connection> attached = new ArrayList<>();
        for (Connection connection : editor.getModel().getConnections()) {
            if (connection.source() == node || connection.destination() == node) {
                attached.add(connection);
            }
        }
        List<Macro> owned = new ArrayList<>();
        for (var knob : view.ownedKnobs()) {
            Macro macro = editor.findMacro(knob);
            if (macro != null) {
                owned.add(macro);
            }
        }
        return new NodeSnapshot(node, view.getLayoutX(), view.getLayoutY(),
                new LinkedHashMap<>(view.getShadowValues()), attached, owned);
    }

    Node node() {
        return node;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    Map<String, Float> portValues() {
        return portValues;
    }

    List<Connection> connections() {
        return connections;
    }

    List<Macro> macros() {
        return macros;
    }
}
