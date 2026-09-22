package Petroleum.Gui;

import Petroleum.Oscillators.Implementations.DrawnWaveform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.Arrays;

final class WaveformDrawView {
    private static final double CANVAS_WIDTH = 900;
    private static final double CANVAS_HEIGHT = 300;

    private final BorderPane root = new BorderPane();
    private final Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);

    private DrawnWaveform waveform;
    private int lastIndex = -1;

    WaveformDrawView() {
        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clear());

        HBox toolbar = new HBox(10, clearButton);
        toolbar.setPadding(new Insets(10));

        canvas.setOnMousePressed(this::onDrag);
        canvas.setOnMouseDragged(this::onDrag);
        canvas.setOnMouseReleased(e -> lastIndex = -1);

        root.setTop(toolbar);
        root.setCenter(canvas);
        draw();
    }

    BorderPane getRoot() {
        return root;
    }

    void open(DrawnWaveform waveform) {
        this.waveform = waveform;
        lastIndex = -1;
        draw();
    }

    private void clear() {
        if (waveform == null) {
            return;
        }
        Arrays.fill(waveform.getTable(), 0f);
        draw();
    }

    private void onDrag(MouseEvent event) {
        if (waveform == null) {
            return;
        }
        float[] table = waveform.getTable();
        int index = indexFor(event.getX(), table.length);
        float value = valueFor(event.getY());

        if (lastIndex >= 0 && lastIndex != index) {
            float lastValue = table[lastIndex];
            int from = Math.min(lastIndex, index);
            int to = Math.max(lastIndex, index);
            float fromValue = lastIndex < index ? lastValue : value;
            float toValue = lastIndex < index ? value : lastValue;
            for (int i = from; i <= to; i++) {
                float t = to == from ? 0f : (float) (i - from) / (to - from);
                table[i] = fromValue + (toValue - fromValue) * t;
            }
        } else {
            table[index] = value;
        }

        lastIndex = index;
        draw();
    }

    private static int indexFor(double x, int tableLength) {
        int index = (int) Math.round(x / CANVAS_WIDTH * (tableLength - 1));
        return Math.max(0, Math.min(tableLength - 1, index));
    }

    private static float valueFor(double y) {
        float value = (float) (1 - 2 * (y / CANVAS_HEIGHT));
        return Math.max(-1f, Math.min(1f, value));
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.setFill(Theme.CANVAS);
        g.fillRect(0, 0, w, h);

        double mid = h / 2.0;
        g.setStroke(Theme.WAVE_MIDLINE);
        g.setLineWidth(1);
        g.strokeLine(0, mid, w, mid);

        if (waveform == null) {
            return;
        }
        float[] table = waveform.getTable();
        g.setStroke(Theme.WAVE_TRACE);
        g.setLineWidth(1.5);
        g.beginPath();
        for (int i = 0; i < table.length; i++) {
            double x = (double) i / (table.length - 1) * w;
            double y = mid - table[i] * (mid - 2);
            if (i == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();
    }
}
