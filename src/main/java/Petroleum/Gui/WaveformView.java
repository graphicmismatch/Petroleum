package Petroleum.Gui;

import Petroleum.Patch.Patch;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

final class WaveformView extends Canvas {
    static final double HEIGHT = 36;

    private static final Color BACKGROUND = Theme.CANVAS;
    private static final Color MIDLINE = Theme.WAVE_MIDLINE;
    private static final Color TRACE = Theme.WAVE_TRACE;

    private final float[] data = new float[Patch.DEFAULT_BLOCK_CAPACITY];
    private int count;

    private boolean enabled = true;
    private boolean autoRange = true;
    private float rangeMin = -1f;
    private float rangeMax = 1f;

    WaveformView() {
        super(NodeView.MIN_WIDTH, HEIGHT);
        widthProperty().addListener((obs, oldV, newV) -> draw());
        draw();
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        draw();
    }

    void setRange(boolean autoRange, float min, float max) {
        this.autoRange = autoRange;
        this.rangeMin = min;
        this.rangeMax = max;
        draw();
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }

    void render(float[] samples, int sampleCount) {
        int n = Math.min(samples.length, Math.min(sampleCount, data.length));
        System.arraycopy(samples, 0, data, 0, n);
        count = n;
        draw();
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        g.setFill(BACKGROUND);
        g.fillRect(0, 0, w, h);
        double mid = h / 2.0;
        g.setStroke(MIDLINE);
        g.setLineWidth(1);
        g.strokeLine(0, mid, w, mid);
        if (!enabled || count < 2 || w < 2) {
            return;
        }
        float min;
        float max;
        if (autoRange) {
            float peak = 0f;
            for (int i = 0; i < count; i++) {
                float v = data[i];
                if (Float.isFinite(v)) {
                    peak = Math.max(peak, Math.abs(v));
                }
            }
            float scale = Math.max(1f, peak);
            min = -scale;
            max = scale;
        } else {
            min = rangeMin;
            max = rangeMax;
        }
        float span = Math.max(1e-6f, max - min);
        int columns = (int) w;
        g.setStroke(TRACE);
        g.setLineWidth(1.2);
        g.beginPath();
        for (int x = 0; x < columns; x++) {
            int idx = (int) ((long) x * count / columns);
            float v = data[idx];
            if (!Float.isFinite(v)) {
                v = 0f;
            }
            double t = Math.max(0.0, Math.min(1.0, (v - min) / span));
            double y = (h - 2) - t * (h - 4);
            if (x == 0) {
                g.moveTo(0, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();
    }
}
