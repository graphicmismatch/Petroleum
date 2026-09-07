package JSynth.Gui;

import JSynth.Graph.Node;
import JSynth.Patch.Connection;
import JSynth.Patch.Patch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphModel {
    private final List<Node> nodes = new ArrayList<>();
    private final Map<InputKey, Connection> connections = new LinkedHashMap<>();

    public List<Node> getNodes() {
        return List.copyOf(nodes);
    }

    public List<Connection> getConnections() {
        return List.copyOf(connections.values());
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public void removeNode(Node node) {
        nodes.remove(node);
        connections.entrySet().removeIf(
                e -> e.getKey().destination() == node || e.getValue().source() == node);
    }

    public void addConnection(Connection connection) {
        connections.put(new InputKey(connection.destination(), connection.destinationInput()), connection);
    }

    public void removeConnection(Connection connection) {
        InputKey key = new InputKey(connection.destination(), connection.destinationInput());
        connections.remove(key, connection);
    }

    public void clear() {
        nodes.clear();
        connections.clear();
    }

    public Patch buildPatch() {
        return new Patch(nodes, getConnections());
    }
}
