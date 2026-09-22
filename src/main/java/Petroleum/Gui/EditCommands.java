package Petroleum.Gui;

import Petroleum.Graph.Node;
import Petroleum.Patch.Connection;
import Petroleum.Serialization.Macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class EditCommands {

    private EditCommands() {
    }

    static EditCommand<GraphEditor> addNode(NodeSnapshot snapshot) {
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                editor.deleteSnapshot(snapshot);
            }

            @Override
            public void redo(GraphEditor editor) {
                editor.restoreSnapshot(snapshot);
            }

            @Override
            public String label() {
                return "add " + snapshot.node().getName();
            }
        };
    }

    static EditCommand<GraphEditor> removeNode(NodeSnapshot snapshot) {
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                editor.restoreSnapshot(snapshot);
            }

            @Override
            public void redo(GraphEditor editor) {
                editor.deleteSnapshot(snapshot);
            }

            @Override
            public String label() {
                return "delete " + snapshot.node().getName();
            }
        };
    }

    static EditCommand<GraphEditor> connect(Connection connection, Connection replaced) {
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                editor.disconnectSilently(connection);
                if (replaced != null) {
                    editor.connectSilently(replaced);
                }
            }

            @Override
            public void redo(GraphEditor editor) {
                editor.connectSilently(connection);
            }

            @Override
            public String label() {
                return "connect " + connection.destinationInput();
            }
        };
    }

    static EditCommand<GraphEditor> disconnect(Connection connection) {
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                editor.connectSilently(connection);
            }

            @Override
            public void redo(GraphEditor editor) {
                editor.disconnectSilently(connection);
            }

            @Override
            public String label() {
                return "disconnect " + connection.destinationInput();
            }
        };
    }

    static EditCommand<GraphEditor> move(Node node, double fromX, double fromY, double toX, double toY) {
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                editor.placeNode(node, fromX, fromY);
            }

            @Override
            public void redo(GraphEditor editor) {
                editor.placeNode(node, toX, toY);
            }

            @Override
            public String label() {
                return "move " + node.getName();
            }
        };
    }

    static EditCommand<GraphEditor> arrange(List<Node> nodes, double[][] from, double[][] to) {
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                apply(editor, from);
            }

            @Override
            public void redo(GraphEditor editor) {
                apply(editor, to);
            }

            private void apply(GraphEditor editor, double[][] positions) {
                for (int i = 0; i < nodes.size(); i++) {
                    editor.placeNode(nodes.get(i), positions[0][i], positions[1][i]);
                }
            }

            @Override
            public String label() {
                return "organize layout";
            }
        };
    }

    static EditCommand<GraphEditor> convertChannels(
            Node oldNode, List<Connection> oldConnections, Map<String, Float> portValues,
            double x, double y, Node newNode, List<Connection> newConnections) {
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                editor.removeNodeByReference(newNode);
                NodeView view = editor.addNodeSilently(oldNode, x, y);
                view.applyShadowValues(portValues);
                for (Connection connection : oldConnections) {
                    editor.connectSilently(connection);
                }
            }

            @Override
            public void redo(GraphEditor editor) {
                editor.removeNodeByReference(oldNode);
                NodeView view = editor.addNodeSilently(newNode, x, y);
                view.applyShadowValues(portValues);
                for (Connection connection : newConnections) {
                    editor.connectSilently(connection);
                }
            }

            @Override
            public String label() {
                return "convert " + oldNode.getName() + " channels";
            }
        };
    }

    static EditCommand<GraphEditor> macros(List<Macro> before, List<Macro> after, String label) {
        List<Macro> from = new ArrayList<>(before);
        List<Macro> to = new ArrayList<>(after);
        return new EditCommand<GraphEditor>() {
            @Override
            public void undo(GraphEditor editor) {
                editor.replaceMacros(from);
            }

            @Override
            public void redo(GraphEditor editor) {
                editor.replaceMacros(to);
            }

            @Override
            public String label() {
                return label;
            }
        };
    }
}
