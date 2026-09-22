package JSynth.Gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

final class SoundClipTimeRuler extends Canvas {
    private static final double HEIGHT = 22;
    private static final double MIN_TICK_SPACING_PIXELS = 60;
    private static final double[] NICE_STEPS_SECONDS = {
            0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5,
            1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600
    };

    private double samplesPerPixel = 32;
    private float sampleRate = 44100f;

    SoundClipTimeRuler() {
        super(SoundClipWaveformView.VIEWPORT_WIDTH, HEIGHT);
        draw();
    }

    void configure(double samplesPerPixel, float sampleRate, double width) {
        this.samplesPerPixel = samplesPerPixel;
        this.sampleRate = sampleRate;
        setWidth(Math.min(width, SoundClipWaveformView.MAX_CANVAS_WIDTH));
        draw();
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        g.setFill(Theme.NODE_HEADER);
        g.fillRect(0, 0, w, h);
        g.setStroke(Theme.BORDER);
        g.setLineWidth(1);
        g.strokeLine(0, h - 1, w, h - 1);

        double pixelsPerSecond = sampleRate / samplesPerPixel;
        double step = niceStep(pixelsPerSecond);
        g.setFill(Theme.TEXT_DIM);
        g.setStroke(Theme.TEXT_FAINT);
        int decimals = decimalsFor(step);
        for (double t = 0; t * pixelsPerSecond <= w; t += step) {
            double x = t * pixelsPerSecond;
            g.strokeLine(x, h - 7, x, h - 1);
            g.fillText(formatTime(t, decimals), x + 3, h - 9);
        }
    }

    private static double niceStep(double pixelsPerSecond) {
        for (double step : NICE_STEPS_SECONDS) {
            if (step * pixelsPerSecond >= MIN_TICK_SPACING_PIXELS) {
                return step;
            }
        }
        return NICE_STEPS_SECONDS[NICE_STEPS_SECONDS.length - 1];
    }

    private static int decimalsFor(double step) {
        if (step < 0.01) {
            return 3;
        }
        if (step < 1) {
            return 2;
        }
        return 0;
    }

    private static String formatTime(double seconds, int decimals) {
        int minutes = (int) (seconds / 60);
        double secs = seconds - minutes * 60;
        if (minutes == 0) {
            return String.format("%." + decimals + "f", secs) + "s";
        }
        int width = decimals > 0 ? decimals + 3 : 2;
        return String.format("%d:%0" + width + "." + decimals + "f", minutes, secs);
    }
}
