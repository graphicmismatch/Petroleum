package Petroleum.Serialization;

import Petroleum.Graph.Node;
import Petroleum.Graph.Port;
import Petroleum.Patch.Connection;
import Petroleum.Patch.Patch;
import Petroleum.Sources.ConstantNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PatchDocument {
    private final List<Node> nodes = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();
    private final Map<Node, double[]> positions = new IdentityHashMap<>();
    private final Map<Node, Map<String, Float>> portValues = new IdentityHashMap<>();
    private final List<Macro> macros = new ArrayList<>();

    private String originCategory;
    private long originSeed;
    private boolean hasOrigin;
    private String name = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public List<Macro> getMacros() {
        return macros;
    }

    public void addNode(Node node, double x, double y) {
        nodes.add(node);
        positions.put(node, new double[] {x, y});
    }

    public void addConnection(Connection connection) {
        connections.add(connection);
    }

    public void addMacro(Macro macro) {
        macros.add(macro);
    }

    public void setOrigin(String category, long seed) {
        this.originCategory = category;
        this.originSeed = seed;
        this.hasOrigin = true;
    }

    public boolean hasOrigin() {
        return hasOrigin;
    }

    public String getOriginCategory() {
        return originCategory;
    }

    public long getOriginSeed() {
        return originSeed;
    }

    public void setPortValues(Node node, Map<String, Float> values) {
        portValues.put(node, values);
    }

    public Map<String, Float> getPortValues(Node node) {
        return portValues.getOrDefault(node, Map.of());
    }

    public Patch buildPatch() {
        return buildPatch(Map.of());
    }

    public Patch buildPatch(Map<Node, Node> substitutions) {
        List<Node> allNodes = new ArrayList<>();
        for (Node node : nodes) {
            allNodes.add(substitute(substitutions, node));
        }

        List<Connection> allConnections = new ArrayList<>();
        for (Connection connection : connections) {
            allConnections.add(new Connection(
                    substitute(substitutions, connection.source()), connection.sourceOutput(),
                    substitute(substitutions, connection.destination()), connection.destinationInput()));
        }

        Set<String> driven = new HashSet<>();
        for (Connection connection : allConnections) {
            driven.add(System.identityHashCode(connection.destination()) + "/" + connection.destinationInput());
        }

        for (Node node : nodes) {
            Node target = substitute(substitutions, node);
            for (Map.Entry<String, Float> entry : getPortValues(node).entrySet()) {
                if (driven.contains(System.identityHashCode(target) + "/" + entry.getKey())) {
                    continue;
                }
                ConstantNode source = new ConstantNode(entry.getValue());
                allNodes.add(source);
                allConnections.add(new Connection(source, "out", target, entry.getKey()));
            }
        }
        return new Patch(allNodes, allConnections);
    }

    private static Node substitute(Map<Node, Node> substitutions, Node node) {
        Node replacement = substitutions.get(node);
        return replacement == null ? node : replacement;
    }

    public void replaceNode(Node oldNode, Node newNode) {
        int index = nodes.indexOf(oldNode);
        if (index < 0) {
            throw new IllegalArgumentException("Node is not part of this document");
        }
        nodes.set(index, newNode);
        double[] position = positions.remove(oldNode);
        if (position != null) {
            positions.put(newNode, position);
        }
        Map<String, Float> values = portValues.remove(oldNode);
        if (values != null) {
            Map<String, Float> kept = new LinkedHashMap<>();
            for (Port port : newNode.getInputPorts()) {
                Float value = values.get(port.name());
                if (value != null) {
                    kept.put(port.name(), value);
                }
            }
            portValues.put(newNode, kept);
        }
        for (int i = 0; i < connections.size(); i++) {
            Connection connection = connections.get(i);
            if (connection.source() == oldNode || connection.destination() == oldNode) {
                Node source = connection.source() == oldNode ? newNode : connection.source();
                Node destination = connection.destination() == oldNode ? newNode : connection.destination();
                connections.set(i, new Connection(source, connection.sourceOutput(),
                        destination, connection.destinationInput()));
            }
        }
    }

    public double getX(Node node) {
        double[] position = positions.get(node);
        return position == null ? 0 : position[0];
    }

    public double getY(Node node) {
        double[] position = positions.get(node);
        return position == null ? 0 : position[1];
    }
}
