package JSynth.Gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

final class SoundPreview extends Canvas {
    static final double HEIGHT = 150;

    private static final Color BACKGROUND = Theme.WAVE_BACKGROUND;
    private static final Color MIDLINE = Theme.WAVE_MIDLINE;
    private static final Color TRACE = Theme.WAVE_TRACE;

    private float[] data = new float[0];

    SoundPreview() {
        super(400, HEIGHT);
        widthProperty().addListener((obs, oldV, newV) -> draw());
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
        return HEIGHT;
    }

    void show(float[] samples) {
        data = samples;
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
        if (data.length < 2 || w < 2) {
            return;
        }

        float peak = 0f;
        for (float v : data) {
            if (Float.isFinite(v)) {
                peak = Math.max(peak, Math.abs(v));
            }
        }
        double scale = Math.max(0.05f, peak);
        int columns = (int) w;
        g.setStroke(TRACE);
        g.setLineWidth(1);
        for (int x = 0; x < columns; x++) {
            int start = (int) ((long) x * data.length / columns);
            int end = (int) ((long) (x + 1) * data.length / columns);
            if (end <= start) {
                end = start + 1;
            }
            float min = 0f;
            float max = 0f;
            for (int i = start; i < end && i < data.length; i++) {
                float v = data[i];
                if (!Float.isFinite(v)) {
                    continue;
                }
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double top = mid - (max / scale) * (mid - 3);
            double bottom = mid - (min / scale) * (mid - 3);
            g.strokeLine(x + 0.5, top, x + 0.5, bottom);
        }
    }
}
