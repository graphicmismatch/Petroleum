package JSynth.Gui;

import JSynth.Graph.Node;
import JSynth.Graph.NodeFactory;
import JSynth.Graph.Port;
import JSynth.Instruments.InstrumentNode;
import JSynth.Oscillators.OscillatorFactory;
import JSynth.Patch.Connection;
import JSynth.Patch.Patch;
import JSynth.Serialization.Macro;
import JSynth.Serialization.PatchCodec;
import JSynth.Serialization.PatchDocument;
import JSynth.Sources.ConstantNode;
import JSynth.Tracker.TrackerNode;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.CubicCurve;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class GraphEditor {
    private static final double INITIAL_CANVAS_WIDTH = 3000;
    private static final double INITIAL_CANVAS_HEIGHT = 2000;
    private static final double CANVAS_GROW_MARGIN = 400;
    private static final int HISTORY_LIMIT = 100;

    public static final double MIN_ZOOM = 0.01;
    public static final double MAX_ZOOM = 100.0;

    private final GraphModel model = new GraphModel();
    private final Pane canvas = new Pane();
    private final Scale zoom = new Scale(1, 1, 0, 0);
    private double canvasWidth = INITIAL_CANVAS_WIDTH;
    private double canvasHeight = INITIAL_CANVAS_HEIGHT;
    private final Pane wireLayer = new Pane();
    private final Pane nodeLayer = new Pane();
    private final Group content = new Group(wireLayer, nodeLayer);
    private double originX;
    private double originY;
    private final List<NodeView> nodeViews = new ArrayList<>();
    private final List<WireView> wireViews = new ArrayList<>();
    private final Map<NodeView, List<WireView>> wiresByNode = new IdentityHashMap<>();
    private final Map<PortHandle, WireView> wireByTargetPort = new IdentityHashMap<>();

    private final NodeFactory nodeFactory = new NodeFactory();
    private final OscillatorFactory oscillatorFactory = new OscillatorFactory();
    private final PatchCodec patchCodec = new PatchCodec(nodeFactory, oscillatorFactory);
    private final NodeMenu nodeMenu = new NodeMenu(nodeFactory, oscillatorFactory, patchCodec);
    private final Label hintLabel = new Label("Right-click to add a node\nor try Quick Sounds for instant results");

    private PortHandle wiringSource;
    private CubicCurve tempWire;

    private final List<Macro> macros = new ArrayList<>();

    private NodeView selectedNode;
    private WireView selectedWire;
    private boolean dirty;

    private Runnable onGraphChanged = () -> { };
    private Runnable onMacrosChanged = () -> { };
    private Runnable onKnobChanged = () -> { };
    private Runnable onHistoryChanged = () -> { };
    private Consumer<Bounds> onCanvasGrown = oldCanvas -> { };
    private Consumer<Node> onOpenDocumentation = node -> { };
    private Consumer<Node> onOpenTrackerEditor = node -> { };
    private Consumer<Node> onOpenWaveformEditor = node -> { };
    private Consumer<Node> onOpenSoundClipEditor = node -> { };
    private Consumer<Node> onOpenInstrumentEditor = node -> { };
    private Consumer<Node> onOpenSubpatchEditor = node -> { };

    private final EditHistory<GraphEditor> history = new EditHistory<>(HISTORY_LIMIT);

    public GraphEditor() {
        canvas.setPrefSize(canvasWidth, canvasHeight);
        canvas.setBackground(new javafx.scene.layout.Background(
                new javafx.scene.layout.BackgroundFill(Theme.CANVAS, null, null)));
        canvas.setFocusTraversable(true);
        canvas.getTransforms().add(zoom);
        canvas.getChildren().add(content);

        hintLabel.setTextFill(Theme.TEXT_FAINT);
        hintLabel.setFont(Font.font(Theme.FONT_HEADING));
        hintLabel.setLayoutX(40);
        hintLabel.setLayoutY(40);
        hintLabel.setMouseTransparent(true);
        canvas.getChildren().add(hintLabel);

        canvas.setOnContextMenuRequested(event -> {
            Window window = canvas.getScene() == null ? null : canvas.getScene().getWindow();
            showAddNodeMenu(event.getSceneX(), event.getSceneY(), event.getScreenX(), event.getScreenY(), window);
            event.consume();
        });

        canvas.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> nodeMenu.hide());

        canvas.setOnMousePressed(event -> {
            if (event.getTarget() == canvas) {
                clearSelection();
                canvas.requestFocus();
            }
        });

    }

    public void installShortcuts(javafx.scene.Node scope) {
        scope.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
    }

    public void showAddNodeMenu(double sceneX, double sceneY, double screenX, double screenY, Window window) {
        cancelWiring();
        Point2D canvasPoint = content.sceneToLocal(sceneX, sceneY);
        nodeMenu.show(this, canvasPoint.getX(), canvasPoint.getY(), screenX, screenY, window);
    }

    private void handleShortcut(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
            if (event.isShiftDown()) {
                redo();
            } else {
                undo();
            }
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.Y) {
            redo();
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            deleteSelection();
            event.consume();
        }
    }

    public Pane getCanvas() {
        return canvas;
    }

    public boolean isEmpty() {
        return nodeViews.isEmpty();
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public String undoLabel() {
        return history.undoLabel();
    }

    public String redoLabel() {
        return history.redoLabel();
    }

    public void setOnHistoryChanged(Runnable callback) {
        this.onHistoryChanged = callback == null ? () -> { } : callback;
    }

    public void setOnCanvasGrown(Consumer<Bounds> callback) {
        this.onCanvasGrown = callback == null ? oldCanvas -> { } : callback;
    }

    public void setOnOpenDocumentation(Consumer<Node> callback) {
        this.onOpenDocumentation = callback == null ? node -> { } : callback;
    }

    public void openDocumentation(NodeView view) {
        onOpenDocumentation.accept(view.getNode());
    }

    public void setOnOpenTrackerEditor(Consumer<Node> callback) {
        this.onOpenTrackerEditor = callback == null ? node -> { } : callback;
    }

    public void openTrackerEditor(NodeView view) {
        onOpenTrackerEditor.accept(view.getNode());
    }

    public void setOnOpenWaveformEditor(Consumer<Node> callback) {
        this.onOpenWaveformEditor = callback == null ? node -> { } : callback;
    }

    public void openWaveformEditor(NodeView view) {
        onOpenWaveformEditor.accept(view.getNode());
    }

    public void setOnOpenSoundClipEditor(Consumer<Node> callback) {
        this.onOpenSoundClipEditor = callback == null ? node -> { } : callback;
    }

    public void openSoundClipEditor(NodeView view) {
        onOpenSoundClipEditor.accept(view.getNode());
    }

    public void setOnOpenInstrumentEditor(Consumer<Node> callback) {
        this.onOpenInstrumentEditor = callback == null ? node -> { } : callback;
    }

    public void openInstrumentEditor(NodeView view) {
        onOpenInstrumentEditor.accept(view.getNode());
    }

    public void setOnOpenSubpatchEditor(Consumer<Node> callback) {
        this.onOpenSubpatchEditor = callback == null ? node -> { } : callback;
    }

    public void openSubpatchEditor(NodeView view) {
        onOpenSubpatchEditor.accept(view.getNode());
    }

    public void undo() {
        if (history.undo(this)) {
            notifyGraphChanged();
            onHistoryChanged.run();
        }
    }

    public void redo() {
        if (history.redo(this)) {
            notifyGraphChanged();
            onHistoryChanged.run();
        }
    }

    private void record(EditCommand<GraphEditor> command) {
        if (history.isApplying()) {
            return;
        }
        history.record(command);
        onHistoryChanged.run();
    }

    private void clearHistory() {
        history.clear();
        onHistoryChanged.run();
    }

    void recordMove(NodeView view, double fromX, double fromY, double toX, double toY) {
        record(EditCommands.move(view.getNode(), fromX, fromY, toX, toY));
    }

    public void organizeLayout() {
        if (nodeViews.isEmpty()) {
            return;
        }
        canvas.layout();
        int n = nodeViews.size();
        List<Node> nodes = new ArrayList<>(n);
        Map<Node, Integer> index = new IdentityHashMap<>();
        double[][] from = new double[2][n];
        double[] width = new double[n];
        double[] height = new double[n];
        for (int i = 0; i < n; i++) {
            NodeView view = nodeViews.get(i);
            nodes.add(view.getNode());
            index.put(view.getNode(), i);
            from[0][i] = view.getLayoutX();
            from[1][i] = view.getLayoutY();
            width[i] = nodeWidth(view);
            height[i] = nodeHeight(view);
        }
        List<int[]> edges = new ArrayList<>();
        List<Double> sourceOffsets = new ArrayList<>();
        List<Double> targetOffsets = new ArrayList<>();
        for (Connection connection : model.getConnections()) {
            Integer source = index.get(connection.source());
            Integer destination = index.get(connection.destination());
            if (source != null && destination != null && !source.equals(destination)) {
                edges.add(new int[] {source, destination});
                sourceOffsets.add(portY(nodeViews.get(source).findOutputHandle(connection.sourceOutput())));
                targetOffsets.add(portY(nodeViews.get(destination).findInputHandle(connection.destinationInput())));
            }
        }
        double[][] to = GraphLayout.arrange(edges.toArray(new int[0][]),
                sourceOffsets.stream().mapToDouble(Double::doubleValue).toArray(),
                targetOffsets.stream().mapToDouble(Double::doubleValue).toArray(),
                width, height, from[1],
                getContentMinX() - originX, getContentMinY() - originY);
        for (int i = 0; i < n; i++) {
            placeNode(nodes.get(i), to[0][i], to[1][i]);
        }
        record(EditCommands.arrange(nodes, from, to));
        markDirty();
    }

    private static double portY(PortHandle handle) {
        return handle == null ? 0 : handle.getBoundsInParent().getCenterY();
    }

    void placeNode(Node node, double x, double y) {
        NodeView view = viewOf(node);
        if (view == null) {
            return;
        }
        view.setLayoutX(x);
        view.setLayoutY(y);
        onNodeMoved(view);
    }

    void restoreSnapshot(NodeSnapshot snapshot) {
        NodeView view = addNodeSilently(snapshot.node(), snapshot.x(), snapshot.y());
        view.applyShadowValues(snapshot.portValues());
        for (Connection connection : snapshot.connections()) {
            connectSilently(connection);
        }
        for (Macro macro : snapshot.macros()) {
            if (findMacro(macro.target()) == null) {
                macros.add(macro);
            }
        }
        macrosChanged();
    }

    void deleteSnapshot(NodeSnapshot snapshot) {
        removeNodeByReference(snapshot.node());
    }

    void removeNodeByReference(Node node) {
        NodeView view = viewOf(node);
        if (view != null) {
            removeNodeSilently(view);
        }
    }

    void connectSilently(Connection connection) {
        NodeView sourceView = viewOf(connection.source());
        NodeView targetView = viewOf(connection.destination());
        if (sourceView == null || targetView == null) {
            return;
        }
        PortHandle source = sourceView.findOutputHandle(connection.sourceOutput());
        PortHandle target = targetView.findInputHandle(connection.destinationInput());
        if (source == null || target == null) {
            return;
        }
        WireView stale = wireByTargetPort.get(target);
        if (stale != null) {
            detachWire(stale, false);
        }
        model.addConnection(connection);
        attachWire(connection, source, target);
    }

    void disconnectSilently(Connection connection) {
        for (WireView wire : new ArrayList<>(wireViews)) {
            if (wire.getConnection().equals(connection)) {
                detachWire(wire, true);
                notifyGraphChanged();
                return;
            }
        }
    }

    void replaceMacros(List<Macro> replacement) {
        macros.clear();
        macros.addAll(replacement);
        macrosChanged();
    }

    private NodeView viewOf(Node node) {
        for (NodeView view : nodeViews) {
            if (view.getNode() == node) {
                return view;
            }
        }
        return null;
    }

    public double getContentMinX() {
        double min = Double.MAX_VALUE;
        for (NodeView view : nodeViews) {
            min = Math.min(min, view.getLayoutX());
        }
        return min == Double.MAX_VALUE ? 0 : min + originX;
    }

    public double getContentMinY() {
        double min = Double.MAX_VALUE;
        for (NodeView view : nodeViews) {
            min = Math.min(min, view.getLayoutY());
        }
        return min == Double.MAX_VALUE ? 0 : min + originY;
    }

    public double getContentMaxX() {
        double max = -Double.MAX_VALUE;
        for (NodeView view : nodeViews) {
            max = Math.max(max, view.getLayoutX() + nodeWidth(view));
        }
        return max == -Double.MAX_VALUE ? canvasWidth : max + originX;
    }

    public double getContentMaxY() {
        double max = -Double.MAX_VALUE;
        for (NodeView view : nodeViews) {
            max = Math.max(max, view.getLayoutY() + nodeHeight(view));
        }
        return max == -Double.MAX_VALUE ? canvasHeight : max + originY;
    }

    private static double nodeWidth(NodeView view) {
        return Math.max(view.getWidth(), view.getMinWidth());
    }

    private static double nodeHeight(NodeView view) {
        return Math.max(view.getHeight(), view.getMinHeight());
    }

    public double getCanvasWidth() {
        return canvasWidth;
    }

    public double getCanvasHeight() {
        return canvasHeight;
    }

    public double getZoom() {
        return zoom.getX();
    }

    public void setZoom(double newZoom) {
        double clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        zoom.setX(clamped);
        zoom.setY(clamped);
    }

    private void growCanvasToFit(NodeView view) {
        double left = originX + view.getLayoutX();
        double top = originY + view.getLayoutY();
        double shiftX = left < 0 ? CANVAS_GROW_MARGIN - left : 0;
        double shiftY = top < 0 ? CANVAS_GROW_MARGIN - top : 0;
        double right = left + shiftX + nodeWidth(view) + CANVAS_GROW_MARGIN;
        double bottom = top + shiftY + nodeHeight(view) + CANVAS_GROW_MARGIN;
        double newWidth = Math.max(canvasWidth + shiftX, right);
        double newHeight = Math.max(canvasHeight + shiftY, bottom);
        if (newWidth == canvasWidth && newHeight == canvasHeight) {
            return;
        }
        Bounds oldCanvas = new BoundingBox(shiftX, shiftY, canvasWidth, canvasHeight);
        originX += shiftX;
        originY += shiftY;
        content.setTranslateX(originX);
        content.setTranslateY(originY);
        canvasWidth = newWidth;
        canvasHeight = newHeight;
        canvas.setPrefSize(canvasWidth, canvasHeight);
        onCanvasGrown.accept(oldCanvas);
    }

    OscillatorFactory getOscillatorFactory() {
        return oscillatorFactory;
    }

    NodeFactory getNodeFactory() {
        return nodeFactory;
    }

    PatchCodec getPatchCodec() {
        return patchCodec;
    }

    GraphModel getModel() {
        return model;
    }

    public void setOnGraphChanged(Runnable callback) {
        this.onGraphChanged = callback == null ? () -> { } : callback;
    }

    public NodeView addNode(Node node, double x, double y) {
        NodeView view = addNodeSilently(node, x, y);
        record(EditCommands.addNode(NodeSnapshot.capture(this, view)));
        return view;
    }

    NodeView addNodeSilently(Node node, double x, double y) {
        model.addNode(node);
        NodeView view = new NodeView(node, this);
        view.setLayoutX(x);
        view.setLayoutY(y);
        nodeViews.add(view);
        nodeLayer.getChildren().add(view);
        growCanvasToFit(view);
        updateHintVisibility();
        notifyGraphChanged();
        return view;
    }

    public void removeNode(NodeView view) {
        record(EditCommands.removeNode(NodeSnapshot.capture(this, view)));
        removeNodeSilently(view);
    }

    void removeNodeSilently(NodeView view) {
        if (view == selectedNode) {
            selectedNode = null;
        }
        List<WireView> attached = wiresByNode.get(view);
        if (attached != null) {
            for (WireView wire : new ArrayList<>(attached)) {
                detachWire(wire, wire.getTargetHandle().getOwner() != view);
            }
        }
        nodeLayer.getChildren().remove(view);
        nodeViews.remove(view);
        dropMacrosOwnedBy(view);
        view.disposeShadowNodes(model);
        model.removeNode(view.getNode());
        updateHintVisibility();
        notifyGraphChanged();
    }

    public void convertTrackerChannels(NodeView view, int newChannelCount) {
        if (!(view.getNode() instanceof TrackerNode oldNode)) {
            return;
        }
        int oldChannelCount = oldNode.getChannelCount();
        if (newChannelCount == oldChannelCount) {
            return;
        }
        if (newChannelCount < oldChannelCount) {
            boolean confirmed = Dialogs.confirm("Convert Tracker channels",
                    "Shrinking from " + oldChannelCount + " to " + newChannelCount
                            + " channels will discard the notes on channels "
                            + newChannelCount + ".." + (oldChannelCount - 1)
                            + ". Continue?");
            if (!confirmed) {
                return;
            }
        }

        TrackerNode newNode = new TrackerNode(newChannelCount);
        newNode.setSong(oldNode.getSong().withChannelCount(newChannelCount));
        convertChannels(view, oldNode, newNode);
    }

    public void convertInstrumentChannels(NodeView view, int newChannelCount) {
        if (!(view.getNode() instanceof InstrumentNode oldNode)) {
            return;
        }
        if (newChannelCount == oldNode.getChannelCount()) {
            return;
        }

        InstrumentNode newNode = new InstrumentNode(newChannelCount);
        newNode.setInstrument(oldNode.getInstrument());
        convertChannels(view, oldNode, newNode);
    }

    private void convertChannels(NodeView view, Node oldNode, Node newNode) {
        List<Connection> oldConnections = new ArrayList<>();
        List<Connection> newConnections = new ArrayList<>();
        for (Connection connection : model.getConnections()) {
            if (connection.source() == oldNode) {
                oldConnections.add(connection);
                if (portExists(newNode, connection.sourceOutput())) {
                    newConnections.add(new Connection(newNode, connection.sourceOutput(),
                            connection.destination(), connection.destinationInput()));
                }
            } else if (connection.destination() == oldNode) {
                oldConnections.add(connection);
                if (inputPortExists(newNode, connection.destinationInput())) {
                    newConnections.add(new Connection(connection.source(), connection.sourceOutput(),
                            newNode, connection.destinationInput()));
                }
            }
        }

        double x = view.getLayoutX();
        double y = view.getLayoutY();
        Map<String, Float> portValues = new HashMap<>(view.getShadowValues());

        removeNodeSilently(view);
        NodeView newView = addNodeSilently(newNode, x, y);
        newView.applyShadowValues(portValues);
        for (Connection connection : newConnections) {
            connectSilently(connection);
        }

        record(EditCommands.convertChannels(oldNode, oldConnections, portValues, x, y,
                newNode, newConnections));
    }

    private static boolean portExists(Node node, String outputName) {
        return node.getOutputPorts().contains(outputName);
    }

    private static boolean inputPortExists(Node node, String inputName) {
        for (Port port : node.getInputPorts()) {
            if (port.name().equals(inputName)) {
                return true;
            }
        }
        return false;
    }

    private void dropMacrosOwnedBy(NodeView view) {
        List<ConstantNode> owned = view.ownedKnobs();
        boolean removed = false;
        for (ConstantNode knob : owned) {
            Macro macro = findMacro(knob);
            if (macro != null) {
                macros.remove(macro);
                removed = true;
            }
        }
        if (removed) {
            macrosChanged();
        }
    }

    public void removeWire(WireView wire) {
        record(EditCommands.disconnect(wire.getConnection()));
        detachWire(wire, true);
        notifyGraphChanged();
    }

    public boolean removeWireInto(PortHandle target) {
        WireView wire = wireByTargetPort.get(target);
        if (wire == null) {
            return false;
        }
        removeWire(wire);
        return true;
    }

    private void detachWire(WireView wire, boolean restoreShadow) {
        if (wire == selectedWire) {
            selectedWire = null;
        }
        wireLayer.getChildren().remove(wire);
        wireViews.remove(wire);
        model.removeConnection(wire.getConnection());
        unindexWire(wire);
        if (restoreShadow) {
            NodeView targetView = wire.getTargetHandle().getOwner();
            targetView.restoreShadowConnection(model, wire.getTargetHandle().getPortName());
        }
    }

    private void indexWire(WireView wire) {
        NodeView sourceOwner = wire.getSourceHandle().getOwner();
        NodeView targetOwner = wire.getTargetHandle().getOwner();
        wiresByNode.computeIfAbsent(sourceOwner, k -> new ArrayList<>()).add(wire);
        if (targetOwner != sourceOwner) {
            wiresByNode.computeIfAbsent(targetOwner, k -> new ArrayList<>()).add(wire);
        }
        wireByTargetPort.put(wire.getTargetHandle(), wire);
    }

    private void unindexWire(WireView wire) {
        removeFromIndex(wire.getSourceHandle().getOwner(), wire);
        removeFromIndex(wire.getTargetHandle().getOwner(), wire);
        wireByTargetPort.remove(wire.getTargetHandle(), wire);
    }

    private void removeFromIndex(NodeView owner, WireView wire) {
        List<WireView> list = wiresByNode.get(owner);
        if (list == null) {
            return;
        }
        list.remove(wire);
        if (list.isEmpty()) {
            wiresByNode.remove(owner);
        }
    }

    public void onNodeMoved(NodeView view) {
        growCanvasToFit(view);
        List<WireView> attached = wiresByNode.get(view);
        if (attached == null) {
            return;
        }
        for (WireView wire : attached) {
            wire.updateGeometry();
        }
    }

    public void selectNode(NodeView view) {
        clearSelection();
        selectedNode = view;
        view.setSelected(true);
        canvas.requestFocus();
    }

    public void selectWire(WireView wire) {
        clearSelection();
        selectedWire = wire;
        wire.setSelected(true);
        canvas.requestFocus();
    }

    public void clearSelection() {
        if (selectedNode != null) {
            selectedNode.setSelected(false);
            selectedNode = null;
        }
        if (selectedWire != null) {
            selectedWire.setSelected(false);
            selectedWire = null;
        }
    }

    public void deleteSelection() {
        if (selectedNode != null) {
            removeNode(selectedNode);
        } else if (selectedWire != null) {
            removeWire(selectedWire);
        }
    }

    public void beginWiring(PortHandle source) {
        cancelWiring();
        wiringSource = source;
        tempWire = new CubicCurve();
        tempWire.setFill(null);
        tempWire.setStroke(Theme.ACCENT);
        tempWire.setStrokeWidth(2);
        tempWire.setMouseTransparent(true);
        Point2D start = WireView.centerInCanvas(source, content);
        WireView.layoutCurve(tempWire, start.getX(), start.getY(), start.getX(), start.getY());
        nodeLayer.getChildren().add(tempWire);
    }

    private void cancelWiring() {
        if (tempWire != null) {
            nodeLayer.getChildren().remove(tempWire);
            tempWire = null;
        }
        wiringSource = null;
    }

    public void updateWiring(double sceneX, double sceneY) {
        if (tempWire == null || wiringSource == null) {
            return;
        }
        Point2D start = WireView.centerInCanvas(wiringSource, content);
        Point2D current = content.sceneToLocal(sceneX, sceneY);
        WireView.layoutCurve(tempWire, start.getX(), start.getY(), current.getX(), current.getY());
    }

    public void finishWiring(MouseEvent event) {
        if (tempWire != null) {
            nodeLayer.getChildren().remove(tempWire);
            tempWire = null;
        }
        PortHandle source = wiringSource;
        wiringSource = null;
        if (source == null) {
            return;
        }

        javafx.scene.Node picked = event.getPickResult().getIntersectedNode();
        PortHandle target = null;
        while (picked != null) {
            if (picked instanceof PortHandle handle) {
                target = handle;
                break;
            }
            picked = picked.getParent();
        }

        if (target == null) {
            if (droppedOnEmptyCanvas(event)) {
                Point2D canvasPoint = content.sceneToLocal(event.getSceneX(), event.getSceneY());
                Window window = canvas.getScene() == null ? null : canvas.getScene().getWindow();
                nodeMenu.show(this, canvasPoint.getX(), canvasPoint.getY(),
                        event.getScreenX(), event.getScreenY(), window, source);
            }
            return;
        }

        if (target.isOutput() || target.getOwner() == source.getOwner()) {
            return;
        }

        connect(source, target);
    }

    private boolean droppedOnEmptyCanvas(MouseEvent event) {
        javafx.scene.Node picked = event.getPickResult().getIntersectedNode();
        while (picked != null) {
            if (picked instanceof NodeView) {
                return false;
            }
            picked = picked.getParent();
        }
        return true;
    }

    public NodeView addNodeWiredFrom(Node node, double x, double y, PortHandle source) {
        NodeView view = addNode(node, x, y);
        if (source == null || view.getInputHandles().isEmpty()) {
            return view;
        }
        connect(source, view.getInputHandles().get(0));
        return view;
    }

    private void connect(PortHandle source, PortHandle target) {
        Connection connection = new Connection(
                source.getOwner().getNode(), source.getPortName(),
                target.getOwner().getNode(), target.getPortName());

        WireView stale = wireByTargetPort.get(target);
        Connection replaced = stale == null ? null : stale.getConnection();
        if (stale != null) {
            detachWire(stale, false);
        }

        model.addConnection(connection);
        attachWire(connection, source, target);
        record(EditCommands.connect(connection, replaced));
    }

    private void attachWire(Connection connection, PortHandle source, PortHandle target) {
        WireView wireView = new WireView(connection, source, target, content, this);
        wireViews.add(wireView);
        indexWire(wireView);
        wireLayer.getChildren().add(wireView);
        notifyGraphChanged();
    }

    public ConstantNode materializeKnob(NodeView owner, String portName, ConstantNode shadow) {
        double x = owner.getLayoutX() - 210;
        double y = owner.getLayoutY() + owner.portRowOffset(portName);
        NodeView view = addNode(shadow, x, y);

        PortHandle source = view.findOutputHandle("out");
        PortHandle target = owner.findInputHandle(portName);
        if (source != null && target != null) {
            attachWire(new Connection(shadow, "out", owner.getNode(), portName), source, target);
        }
        return shadow;
    }

    private void updateHintVisibility() {
        hintLabel.setVisible(nodeViews.isEmpty());
    }

    private void notifyGraphChanged() {
        Map<InputKey, Connection> connectionsByPort = new HashMap<>();
        for (Connection c : model.getConnections()) {
            connectionsByPort.put(new InputKey(c.destination(), c.destinationInput()), c);
        }
        for (NodeView view : nodeViews) {
            view.refreshPortControlStates(connectionsByPort);
        }
        dirty = true;
        onGraphChanged.run();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    public List<Macro> getMacros() {
        return List.copyOf(macros);
    }

    public void setOnMacrosChanged(Runnable callback) {
        this.onMacrosChanged = callback == null ? () -> { } : callback;
    }

    public boolean isMacroInactive(Macro macro) {
        for (Connection connection : model.getConnections()) {
            if (connection.source() == macro.target()) {
                return false;
            }
        }
        return true;
    }

    public Macro findMacro(ConstantNode target) {
        for (Macro macro : macros) {
            if (macro.target() == target) {
                return macro;
            }
        }
        return null;
    }

    public void renameMacro(Macro macro, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A macro needs a name");
        }
        for (Macro other : macros) {
            if (other != macro && other.name().equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException("There is already a macro called '" + other.name() + "'");
            }
        }
        replace(macro, new Macro(trimmed, macro.target(), macro.min(), macro.max()));
    }

    public void setMacroRange(Macro macro, float min, float max) {
        float low = Math.min(min, max);
        float high = Math.max(min, max);
        if (low == high) {
            high = low + 1f;
        }
        replace(macro, new Macro(macro.name(), macro.target(), low, high));
    }

    private void replace(Macro macro, Macro replacement) {
        int index = macros.indexOf(macro);
        if (index < 0) {
            return;
        }
        List<Macro> before = new ArrayList<>(macros);
        macros.set(index, replacement);
        macrosChanged();
        record(EditCommands.macros(before, macros, "edit macro " + replacement.name()));
    }

    private void macrosChanged() {
        for (NodeView view : nodeViews) {
            view.refreshMacroButtons(this);
        }
        onMacrosChanged.run();
    }

    public void refreshKnobDisplays() {
        for (NodeView view : nodeViews) {
            view.syncControlsFromNodes();
        }
    }

    public void setOnKnobChanged(Runnable callback) {
        this.onKnobChanged = callback == null ? () -> { } : callback;
    }

    void notifyKnobChanged() {
        onKnobChanged.run();
    }

    public void promoteToMacro(ConstantNode target, String name, float min, float max) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A macro needs a name");
        }
        for (Macro macro : macros) {
            if (macro.name().equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException("There is already a macro called '" + macro.name() + "'");
            }
            if (macro.target() == target) {
                throw new IllegalArgumentException(
                        "That knob is already exposed as the macro '" + macro.name() + "'");
            }
        }
        float low = Math.min(min, max);
        float high = Math.max(min, max);
        if (low == high) {
            high = low + 1f;
        }
        List<Macro> before = new ArrayList<>(macros);
        macros.add(new Macro(trimmed, target, low, high));
        macrosChanged();
        record(EditCommands.macros(before, macros, "add macro " + trimmed));
    }

    public void removeMacro(Macro macro) {
        List<Macro> before = new ArrayList<>(macros);
        if (macros.remove(macro)) {
            macrosChanged();
            record(EditCommands.macros(before, macros, "remove macro " + macro.name()));
        }
    }

    public PatchDocument toDocument() {
        PatchDocument document = new PatchDocument();
        Map<Node, NodeView> visible = new IdentityHashMap<>();
        for (NodeView view : nodeViews) {
            visible.put(view.getNode(), view);
            document.addNode(view.getNode(), view.getLayoutX(), view.getLayoutY());
            document.setPortValues(view.getNode(), view.getShadowValues());
        }
        for (Connection connection : model.getConnections()) {
            if (visible.containsKey(connection.source()) && visible.containsKey(connection.destination())) {
                document.addConnection(connection);
            }
        }
        for (Macro macro : macros) {
            document.addMacro(macro);
        }
        return document;
    }

    public void loadDocument(PatchDocument document) {
        history.runSuppressed(() -> loadDocumentInternal(document));
    }

    private void loadDocumentInternal(PatchDocument document) {
        clear();
        Map<Node, NodeView> views = new IdentityHashMap<>();
        for (Node node : document.getNodes()) {
            NodeView view = addNode(node, document.getX(node), document.getY(node));
            view.applyShadowValues(document.getPortValues(node));
            views.put(node, view);
        }
        for (Connection connection : document.getConnections()) {
            NodeView sourceView = views.get(connection.source());
            NodeView targetView = views.get(connection.destination());
            if (sourceView == null || targetView == null) {
                continue;
            }
            PortHandle source = sourceView.findOutputHandle(connection.sourceOutput());
            PortHandle target = targetView.findInputHandle(connection.destinationInput());
            if (source != null && target != null) {
                connect(source, target);
            }
        }
        macros.clear();
        macros.addAll(document.getMacros());
        notifyGraphChanged();
        macrosChanged();
        clearHistory();
        dirty = false;
    }

    public void clear() {
        cancelWiring();
        clearSelection();
        nodeLayer.getChildren().clear();
        wireLayer.getChildren().clear();
        nodeViews.clear();
        wireViews.clear();
        wiresByNode.clear();
        wireByTargetPort.clear();
        model.clear();
        macros.clear();
        canvasWidth = INITIAL_CANVAS_WIDTH;
        canvasHeight = INITIAL_CANVAS_HEIGHT;
        canvas.setPrefSize(canvasWidth, canvasHeight);
        originX = 0;
        originY = 0;
        content.setTranslateX(0);
        content.setTranslateY(0);

        notifyGraphChanged();
        macrosChanged();
        updateHintVisibility();
    }

    public Patch buildPatch() {
        return model.buildPatch();
    }

    public void refreshWaveforms(Patch patch) {
        if (patch == null) {
            return;
        }
        for (NodeView view : nodeViews) {
            view.refreshWaveform(patch);
        }
    }
}
