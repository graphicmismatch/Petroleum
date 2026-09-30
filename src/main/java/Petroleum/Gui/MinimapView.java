package Petroleum.Gui;

import Petroleum.Instruments.InstrumentNode;
import Petroleum.Modules.PatchNode;
import Petroleum.Serialization.Macro;
import Petroleum.Sinks.AudioOutNode;
import Petroleum.Tracker.PianoRollNode;
import Petroleum.Tracker.TrackerNode;
import Petroleum.Utility.AnnotationSource;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

final class MinimapView {
    private static final double NODE_MIN_SIZE = 2;
    private static final double WAYPOINT_RADIUS = 3.5;
    private static final double LABEL_MAX_CHARS = 28;

    record Waypoint(String kind, String label, double x, double y, NodeView view, Color color) {
    }

    private final GraphEditor editor;
    private final ScrollPane canvasScroll;
    private final boolean detailed;
    private final Pane root = new Pane();
    private final Canvas canvas = new Canvas();

    private double scale = 1;
    private double offsetX;
    private double offsetY;

    MinimapView(GraphEditor editor, ScrollPane canvasScroll, boolean detailed) {
        this.editor = editor;
        this.canvasScroll = canvasScroll;
        this.detailed = detailed;

        root.getChildren().add(canvas);
        root.getStyleClass().add("minimap");
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        canvas.widthProperty().addListener(o -> redraw());
        canvas.heightProperty().addListener(o -> redraw());
        canvas.setCursor(Cursor.HAND);

        canvas.setOnMousePressed(this::navigateTo);
        canvas.setOnMouseDragged(this::navigateTo);

        canvasScroll.hvalueProperty().addListener(o -> redraw());
        canvasScroll.vvalueProperty().addListener(o -> redraw());
        canvasScroll.viewportBoundsProperty().addListener(o -> redraw());
        editor.getCanvas().boundsInParentProperty().addListener(o -> redraw());
    }

    Pane getRoot() {
        return root;
    }

    private void navigateTo(javafx.scene.input.MouseEvent event) {
        if (scale <= 0) {
            return;
        }
        centerOn(canvasScroll, editor,
                toContent(event.getX(), offsetX, scale),
                toContent(event.getY(), offsetY, scale));
    }

    static double scaleFor(double viewWidth, double viewHeight, double canvasWidth, double canvasHeight) {
        if (viewWidth <= 0 || viewHeight <= 0 || canvasWidth <= 0 || canvasHeight <= 0) {
            return 0;
        }
        return Math.min(viewWidth / canvasWidth, viewHeight / canvasHeight);
    }

    static double offsetFor(double viewSize, double canvasSize, double scale) {
        return (viewSize - canvasSize * scale) / 2.0;
    }

    static double toMinimap(double contentPos, double offset, double scale) {
        return offset + contentPos * scale;
    }

    static double toContent(double minimapPos, double offset, double scale) {
        return (minimapPos - offset) / scale;
    }

    static void centerOn(ScrollPane scroll, GraphEditor editor, double contentX, double contentY) {
        double zoom = editor.getZoom();
        Bounds viewport = scroll.getViewportBounds();
        double scrollableX = Math.max(0, editor.getCanvasWidth() * zoom - viewport.getWidth());
        double scrollableY = Math.max(0, editor.getCanvasHeight() * zoom - viewport.getHeight());
        double targetX = contentX * zoom - viewport.getWidth() / 2.0;
        double targetY = contentY * zoom - viewport.getHeight() / 2.0;
        scroll.setHvalue(scrollableX <= 0 ? 0 : clamp01(targetX / scrollableX));
        scroll.setVvalue(scrollableY <= 0 ? 0 : clamp01(targetY / scrollableY));
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    static List<Waypoint> waypoints(GraphEditor editor) {
        List<Waypoint> found = new ArrayList<>();
        for (NodeView view : editor.getNodeViews()) {
            Petroleum.Graph.Node node = view.getNode();
            double x = view.getLayoutX() + editor.getOriginX() + GraphEditor.nodeWidth(view) / 2.0;
            double y = view.getLayoutY() + editor.getOriginY() + GraphEditor.nodeHeight(view) / 2.0;
            if (node instanceof AnnotationSource annotation) {
                found.add(new Waypoint("Annotation", firstLine(annotation.getText(), "Empty note"),
                        x, y, view, Theme.WARNING));
            } else if (node instanceof AudioOutNode) {
                found.add(new Waypoint("Output", node.getName(), x, y, view, Theme.PORT_OUTPUT));
            } else if (node instanceof TrackerNode || node instanceof PianoRollNode
                    || node instanceof InstrumentNode || node instanceof PatchNode) {
                found.add(new Waypoint("Editor", node.getName(), x, y, view, Theme.WIRE_SELECTED));
            }
        }
        for (Macro macro : editor.getMacros()) {
            NodeView view = editor.viewOf(macro.target());
            if (view == null) {
                continue;
            }
            found.add(new Waypoint("Macro", macro.name(),
                    view.getLayoutX() + editor.getOriginX() + GraphEditor.nodeWidth(view) / 2.0,
                    view.getLayoutY() + editor.getOriginY() + GraphEditor.nodeHeight(view) / 2.0,
                    view, Theme.ACCENT));
        }
        return found;
    }

    private static String firstLine(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String line = text.strip().lines().findFirst().orElse(fallback).strip();
        return line.length() > LABEL_MAX_CHARS ? line.substring(0, (int) LABEL_MAX_CHARS - 1) + "…" : line;
    }

    void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        if (width <= 0 || height <= 0) {
            return;
        }

        gc.setFill(Theme.WAVE_BACKGROUND);
        gc.fillRect(0, 0, width, height);

        double canvasWidth = editor.getCanvasWidth();
        double canvasHeight = editor.getCanvasHeight();
        scale = scaleFor(width, height, canvasWidth, canvasHeight);
        offsetX = offsetFor(width, canvasWidth, scale);
        offsetY = offsetFor(height, canvasHeight, scale);

        gc.setStroke(Theme.BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(offsetX, offsetY, canvasWidth * scale, canvasHeight * scale);

        gc.setFill(Theme.NODE_HEADER);
        for (NodeView view : editor.getNodeViews()) {
            gc.fillRect(toMinimap(view.getLayoutX() + editor.getOriginX(), offsetX, scale),
                    toMinimap(view.getLayoutY() + editor.getOriginY(), offsetY, scale),
                    Math.max(NODE_MIN_SIZE, GraphEditor.nodeWidth(view) * scale),
                    Math.max(NODE_MIN_SIZE, GraphEditor.nodeHeight(view) * scale));
        }

        if (detailed) {
            gc.setFont(Font.font(Theme.FONT_SMALL));
        }
        for (Waypoint waypoint : waypoints(editor)) {
            double x = toMinimap(waypoint.x(), offsetX, scale);
            double y = toMinimap(waypoint.y(), offsetY, scale);
            gc.setFill(waypoint.color());
            gc.fillOval(x - WAYPOINT_RADIUS, y - WAYPOINT_RADIUS, WAYPOINT_RADIUS * 2, WAYPOINT_RADIUS * 2);
            gc.setStroke(Theme.PORT_STROKE);
            gc.strokeOval(x - WAYPOINT_RADIUS, y - WAYPOINT_RADIUS, WAYPOINT_RADIUS * 2, WAYPOINT_RADIUS * 2);
            if (detailed) {
                gc.setFill(Theme.TEXT);
                gc.fillText(waypoint.label(), x + WAYPOINT_RADIUS + 3, y + 4);
            }
        }

        drawViewportRect(gc);
    }

    private void drawViewportRect(GraphicsContext gc) {
        Bounds viewport = canvasScroll.getViewportBounds();
        if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) {
            return;
        }
        double zoom = editor.getZoom();
        double scrollableX = Math.max(0, editor.getCanvasWidth() * zoom - viewport.getWidth());
        double scrollableY = Math.max(0, editor.getCanvasHeight() * zoom - viewport.getHeight());
        double visibleX = canvasScroll.getHvalue() * scrollableX / zoom;
        double visibleY = canvasScroll.getVvalue() * scrollableY / zoom;
        double visibleWidth = Math.min(editor.getCanvasWidth(), viewport.getWidth() / zoom);
        double visibleHeight = Math.min(editor.getCanvasHeight(), viewport.getHeight() / zoom);

        gc.setStroke(Theme.ACCENT);
        gc.setLineWidth(1.5);
        gc.strokeRect(toMinimap(visibleX, offsetX, scale), toMinimap(visibleY, offsetY, scale),
                visibleWidth * scale, visibleHeight * scale);
    }
}
