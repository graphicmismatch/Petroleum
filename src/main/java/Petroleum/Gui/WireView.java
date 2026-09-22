package Petroleum.Gui;

import Petroleum.Patch.Connection;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;

public final class WireView extends CubicCurve {
    private static final Color WIRE_COLOR = Theme.WIRE;
    private static final Color SELECTED_WIRE_COLOR = Theme.WIRE_SELECTED;

    private final Connection connection;
    private final PortHandle sourceHandle;
    private final PortHandle targetHandle;
    private final javafx.scene.Node canvas;

    public WireView(Connection connection, PortHandle sourceHandle, PortHandle targetHandle,
                     javafx.scene.Node canvas, GraphEditor editor) {
        this.connection = connection;
        this.sourceHandle = sourceHandle;
        this.targetHandle = targetHandle;
        this.canvas = canvas;

        setFill(null);
        setStroke(WIRE_COLOR);
        setStrokeWidth(2.5);

        updateGeometry();

        ContextMenu menu = new ContextMenu();
        MenuItem delete = new MenuItem("Delete Wire");
        delete.setOnAction(e -> editor.removeWire(this));
        menu.getItems().add(delete);
        setOnContextMenuRequested(e -> {
            menu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        setOnMousePressed(e -> {
            editor.selectWire(this);
            e.consume();
        });
    }

    public void setSelected(boolean selected) {
        setStroke(selected ? SELECTED_WIRE_COLOR : WIRE_COLOR);
        setStrokeWidth(selected ? 3.5 : 2.5);
    }

    public Connection getConnection() {
        return connection;
    }

    public PortHandle getSourceHandle() {
        return sourceHandle;
    }

    public PortHandle getTargetHandle() {
        return targetHandle;
    }

    public void updateGeometry() {
        Point2D start = centerInCanvas(sourceHandle, canvas);
        Point2D end = centerInCanvas(targetHandle, canvas);
        layoutCurve(this, start.getX(), start.getY(), end.getX(), end.getY());
    }

    public static Point2D centerInCanvas(PortHandle handle, javafx.scene.Node canvas) {
        Point2D scenePoint = handle.localToScene(handle.getCenterX(), handle.getCenterY());
        return canvas.sceneToLocal(scenePoint);
    }

    static void layoutCurve(CubicCurve curve, double startX, double startY, double endX, double endY) {
        curve.setStartX(startX);
        curve.setStartY(startY);
        curve.setEndX(endX);
        curve.setEndY(endY);
        double dx = Math.max(40, Math.abs(endX - startX) / 2);
        curve.setControlX1(startX + dx);
        curve.setControlY1(startY);
        curve.setControlX2(endX - dx);
        curve.setControlY2(endY);
    }
}
