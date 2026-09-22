package JSynth.Gui;

import JSynth.Dsp.Fft;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import java.util.Arrays;

final class SoundClipWaveformView extends Canvas {
    static final double HEIGHT = 130;
    static final double VIEWPORT_WIDTH = 900;
    static final double MIN_SAMPLES_PER_PIXEL = 1;
    static final double MAX_SAMPLES_PER_PIXEL = 4096;
    static final double MAX_CANVAS_WIDTH = 4096;
    private static final int SPECTROGRAM_WINDOW = 1024;
    private static final int SPECTROGRAM_HOP = 256;

    interface MoveGestureHandler {
        void onMoveStarted(SoundClipWaveformView source, long startSample, long endSample);

        void onMoveDragged(double sceneX, double sceneY);

        void onMoveFinished(double sceneX, double sceneY);
    }

    private float[] buffer = new float[0];
    private double samplesPerPixel = 32;
    private long selectionStartSample = -1;
    private long selectionEndSample = -1;
    private long playheadSample = -1;
    private double dragStartX = -1;
    private boolean moving;
    private Runnable onSelectionChanged = () -> { };
    private MoveGestureHandler onMoveGesture = new MoveGestureHandler() {
        @Override
        public void onMoveStarted(SoundClipWaveformView source, long startSample, long endSample) {
        }

        @Override
        public void onMoveDragged(double sceneX, double sceneY) {
        }

        @Override
        public void onMoveFinished(double sceneX, double sceneY) {
        }
    };

    private boolean spectrogramMode;
    private float[] spectrogramSourceBuffer;
    private float[][] spectrogramFrames;

    private long dropPreviewStartSample = -1;
    private long dropPreviewLength = -1;

    SoundClipWaveformView() {
        super(VIEWPORT_WIDTH, HEIGHT);
        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
        draw();
    }

    void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback == null ? () -> { } : callback;
    }

    void setOnMoveGesture(MoveGestureHandler callback) {
        this.onMoveGesture = callback;
    }

    void setBuffer(float[] buffer) {
        this.buffer = buffer;
        spectrogramSourceBuffer = null;
        clearSelection();
        draw();
    }

    void setLiveBuffer(float[] source, int count) {
        this.buffer = Arrays.copyOf(source, count);
        spectrogramSourceBuffer = null;
        draw();
    }

    float[] getBuffer() {
        return buffer;
    }

    void setPlayheadSample(long sample) {
        this.playheadSample = sample;
        draw();
    }

    void clearSelection() {
        selectionStartSample = -1;
        selectionEndSample = -1;
        draw();
    }

    boolean hasSelection() {
        return selectionStartSample >= 0 && selectionEndSample > selectionStartSample;
    }

    long getSelectionStartSample() {
        return selectionStartSample;
    }

    long getSelectionEndSample() {
        return selectionEndSample;
    }

    void setSpectrogramMode(boolean spectrogramMode) {
        this.spectrogramMode = spectrogramMode;
        draw();
    }

    boolean isSpectrogramMode() {
        return spectrogramMode;
    }

    void showDropPreview(long startSample, long lengthSamples) {
        dropPreviewStartSample = startSample;
        dropPreviewLength = lengthSamples;
        draw();
    }

    void clearDropPreview() {
        if (dropPreviewStartSample < 0) {
            return;
        }
        dropPreviewStartSample = -1;
        dropPreviewLength = -1;
        draw();
    }

    long xToSampleClamped(double sceneX) {
        double localX = sceneToLocalX(sceneX);
        return xToSample(localX);
    }

    private double sceneToLocalX(double sceneX) {
        return sceneToLocal(sceneX, 0).getX();
    }

    void setView(double samplesPerPixel, double width) {
        this.samplesPerPixel = samplesPerPixel;
        setWidth(Math.min(width, MAX_CANVAS_WIDTH));
        draw();
    }

    private void onPressed(MouseEvent event) {
        long sample = xToSample(event.getX());
        if (hasSelection() && sample >= selectionStartSample && sample <= selectionEndSample) {
            moving = true;
            onMoveGesture.onMoveStarted(this, selectionStartSample, selectionEndSample);
            return;
        }
        moving = false;
        dragStartX = event.getX();
        selectionStartSample = sample;
        selectionEndSample = sample;
        draw();
        onSelectionChanged.run();
    }

    private void onDragged(MouseEvent event) {
        if (moving) {
            onMoveGesture.onMoveDragged(event.getSceneX(), event.getSceneY());
            return;
        }
        if (dragStartX < 0) {
            return;
        }
        long a = xToSample(dragStartX);
        long b = xToSample(event.getX());
        selectionStartSample = Math.min(a, b);
        selectionEndSample = Math.max(a, b);
        draw();
        onSelectionChanged.run();
    }

    private void onReleased(MouseEvent event) {
        if (!moving) {
            return;
        }
        moving = false;
        onMoveGesture.onMoveFinished(event.getSceneX(), event.getSceneY());
    }

    private long xToSample(double x) {
        long sample = Math.round(x * samplesPerPixel);
        return Math.max(0, Math.min(buffer.length, sample));
    }

    void draw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        g.setFill(Theme.CANVAS);
        g.fillRect(0, 0, w, h);

        if (spectrogramMode) {
            drawSpectrogram(g, w, h);
        } else {
            drawWaveform(g, w, h);
        }

        if (hasSelection()) {
            double sx = selectionStartSample / samplesPerPixel;
            double ex = selectionEndSample / samplesPerPixel;
            g.setFill(Theme.ACCENT.deriveColor(0, 1, 1, 0.25));
            g.fillRect(sx, 0, ex - sx, h);
        }

        if (dropPreviewStartSample >= 0) {
            double sx = dropPreviewStartSample / samplesPerPixel;
            double ex = (dropPreviewStartSample + dropPreviewLength) / samplesPerPixel;
            g.setStroke(Theme.WARNING);
            g.setLineWidth(2);
            g.strokeRect(sx, 1, Math.max(1, ex - sx), h - 2);
        }

        if (playheadSample >= 0) {
            double x = playheadSample / samplesPerPixel;
            g.setStroke(Theme.ACCENT);
            g.setLineWidth(1.5);
            g.strokeLine(x, 0, x, h);
        }
    }

    private void drawWaveform(GraphicsContext g, double w, double h) {
        double mid = h / 2.0;
        g.setStroke(Theme.WAVE_MIDLINE);
        g.setLineWidth(1);
        g.strokeLine(0, mid, w, mid);

        if (buffer.length == 0) {
            return;
        }
        int columns = (int) Math.ceil(w);
        double[] topY = new double[columns];
        double[] bottomY = new double[columns];
        for (int x = 0; x < columns; x++) {
            int start = (int) Math.min(buffer.length, x * samplesPerPixel);
            int end = (int) Math.min(buffer.length, (x + 1) * samplesPerPixel);
            float max = 0f;
            float min = 0f;
            for (int i = start; i < end; i++) {
                float v = buffer[i];
                if (v > max) {
                    max = v;
                }
                if (v < min) {
                    min = v;
                }
            }
            topY[x] = mid - max * (mid - 2);
            bottomY[x] = mid - min * (mid - 2);
        }

        double[] xs = new double[columns * 2];
        double[] ys = new double[columns * 2];
        for (int x = 0; x < columns; x++) {
            xs[x] = x;
            ys[x] = topY[x];
        }
        for (int x = 0; x < columns; x++) {
            xs[columns + x] = columns - 1 - x;
            ys[columns + x] = bottomY[columns - 1 - x];
        }

        g.setFill(Theme.WAVE_TRACE.deriveColor(0, 1, 0.9, 0.55));
        g.fillPolygon(xs, ys, xs.length);
        g.setStroke(Theme.WAVE_TRACE);
        g.setLineWidth(1);
        g.strokePolygon(xs, ys, xs.length);
    }

    private void drawSpectrogram(GraphicsContext g, double w, double h) {
        ensureSpectrogram();
        int width = (int) Math.max(1, w);
        int height = (int) Math.max(1, h);
        if (spectrogramFrames.length == 0) {
            return;
        }
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        int bins = spectrogramFrames[0].length;
        double hue = Theme.ACCENT.getHue();
        for (int x = 0; x < width; x++) {
            long sample = (long) (x * samplesPerPixel);
            int frame = (int) Math.min(spectrogramFrames.length - 1, sample / SPECTROGRAM_HOP);
            float[] magnitudes = spectrogramFrames[frame];
            for (int y = 0; y < height; y++) {
                int bin = (int) ((height - 1 - y) / (double) Math.max(1, height - 1) * (bins - 1));
                double db = 20 * Math.log10(magnitudes[bin] + 1e-6);
                double brightness = clamp01((db + 80) / 80);
                writer.setColor(x, y, Color.hsb(hue, 0.85, brightness));
            }
        }
        g.drawImage(image, 0, 0);
    }

    private void ensureSpectrogram() {
        if (spectrogramFrames != null && spectrogramSourceBuffer == buffer) {
            return;
        }
        spectrogramSourceBuffer = buffer;
        if (buffer.length < SPECTROGRAM_WINDOW) {
            spectrogramFrames = new float[0][];
            return;
        }
        float[] hann = new float[SPECTROGRAM_WINDOW];
        for (int i = 0; i < SPECTROGRAM_WINDOW; i++) {
            hann[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (SPECTROGRAM_WINDOW - 1));
        }
        Fft fft = new Fft(SPECTROGRAM_WINDOW);
        int frameCount = (buffer.length - SPECTROGRAM_WINDOW) / SPECTROGRAM_HOP + 1;
        spectrogramFrames = new float[frameCount][SPECTROGRAM_WINDOW / 2 + 1];
        float[] re = new float[SPECTROGRAM_WINDOW];
        float[] im = new float[SPECTROGRAM_WINDOW];
        for (int frame = 0; frame < frameCount; frame++) {
            int start = frame * SPECTROGRAM_HOP;
            for (int i = 0; i < SPECTROGRAM_WINDOW; i++) {
                re[i] = buffer[start + i] * hann[i];
                im[i] = 0f;
            }
            fft.forward(re, im);
            for (int k = 0; k <= SPECTROGRAM_WINDOW / 2; k++) {
                spectrogramFrames[frame][k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
            }
        }
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
