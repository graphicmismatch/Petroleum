package Petroleum.Gui;

import Petroleum.Sources.NoteName;
import Petroleum.Tracker.PianoRollClip;
import Petroleum.Tracker.PianoRollClip.Note;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PianoRollView extends BorderPane {
    private static final double KEYS_WIDTH = 56;
    private static final double RULER_HEIGHT = 22;
    private static final double VELOCITY_HEIGHT = 80;
    private static final double LANE_GAP = 4;
    private static final double RESIZE_HANDLE_PIXELS = 6;
    private static final double MIN_PIXELS_PER_TICK = 0.05;
    private static final double MAX_PIXELS_PER_TICK = 6;
    private static final double MIN_ROW_HEIGHT = 5;
    private static final double MAX_ROW_HEIGHT = 32;
    private static final int ROWS = 128;
    private static final boolean[] BLACK_KEY = {false, true, false, true, false, false, true, false, true, false,
            true, false};

    enum Tool { NOTES, PEN }

    private enum Mode { NONE, CREATE, MOVE, RESIZE, SELECT, VELOCITY, LENGTH, PEN }

    private final PianoRollEditorView editor;
    private final Canvas canvas = new Canvas();
    private final Pane host = new Pane(canvas);
    private final ScrollBar hbar = new ScrollBar();
    private final ScrollBar vbar = new ScrollBar();
    private final TextField pitchField = new TextField();

    private PianoRollClip clip = new PianoRollClip();
    private Set<Note> selection = new LinkedHashSet<>();
    private List<Note> clipboard = List.of();
    private double playheadTick = -1;
    private boolean follow = true;
    private int snapTicks = PianoRollClip.TICKS_PER_BEAT / 4;
    private int lastLength = PianoRollClip.TICKS_PER_BEAT / 4;
    private float lastVelocity = PianoRollClip.DEFAULT_VELOCITY;
    private double pixelsPerTick = 0.6;
    private double rowHeight = 12;
    private double hoverTick;
    private Runnable onSelectionChanged = () -> { };

    private Mode mode = Mode.NONE;
    private PianoRollClip dragBase;
    private PianoRollClip candidate;
    private List<Note> dragNotes = List.of();
    private Set<Note> selectionBase = Set.of();
    private Note anchor;
    private double pressX;
    private double pressY;
    private double lastDragX;
    private double bandY;
    private double pressTick;
    private float pressPitch;
    private Note editingNote;
    private boolean centerPending;
    private float auditionedPitch = Float.NaN;
    private Tool tool = Tool.NOTES;
    private boolean laneShowsBend;
    private boolean penInLane;
    private boolean erasing;
    private double penLastTick = Double.NaN;
    private float penLastCents;
    private double penMinTick;
    private double penMaxTick;
    private int penVoice = -1;
    private int voiceCount = 1;
    private int laneVoice;
    private java.util.function.IntConsumer onLaneVoiceChanged = voice -> { };

    PianoRollView(PianoRollEditorView editor) {
        this.editor = editor;
        host.setMinSize(0, 0);
        host.setPrefSize(600, 400);
        canvas.widthProperty().bind(host.widthProperty());
        canvas.heightProperty().bind(host.heightProperty());
        canvas.widthProperty().addListener((obs, was, is) -> refreshScrollBars());
        canvas.heightProperty().addListener((obs, was, is) -> {
            refreshScrollBars();
            if (centerPending) {
                centerOnContent();
            }
        });
        canvas.setFocusTraversable(true);
        canvas.setOnMousePressed(this::onPressed);
        canvas.setOnMouseDragged(this::onDragged);
        canvas.setOnMouseReleased(this::onReleased);
        canvas.setOnMouseMoved(e -> hoverTick = xToTick(e.getX()));
        canvas.setOnScroll(this::onScroll);
        canvas.setOnKeyPressed(this::onKeyPressed);

        hbar.setOrientation(Orientation.HORIZONTAL);
        vbar.setOrientation(Orientation.VERTICAL);
        hbar.valueProperty().addListener((obs, was, is) -> draw());
        vbar.valueProperty().addListener((obs, was, is) -> draw());

        pitchField.setManaged(false);
        pitchField.setVisible(false);
        pitchField.setPrefWidth(90);
        pitchField.getStyleClass().add("inline-editor");
        pitchField.setOnAction(e -> commitPitchField(true));
        pitchField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                closePitchField();
                e.consume();
            }
        });
        pitchField.focusedProperty().addListener((obs, was, is) -> {
            if (was && !is && editingNote != null) {
                commitPitchField(false);
            }
        });
        host.getChildren().add(pitchField);

        setCenter(host);
        setRight(vbar);
        setBottom(hbar);
    }

    void setOnSelectionChanged(Runnable callback) {
        onSelectionChanged = callback == null ? () -> { } : callback;
    }

    void setSnapTicks(int ticks) {
        snapTicks = Math.max(1, ticks);
        draw();
    }

    void setTool(Tool tool) {
        this.tool = tool;
        draw();
    }

    void setLaneShowsBend(boolean showsBend) {
        laneShowsBend = showsBend;
        draw();
    }

    void setVoiceCount(int voiceCount) {
        this.voiceCount = Math.max(1, voiceCount);
        laneVoice = Math.min(laneVoice, this.voiceCount - 1);
        draw();
    }

    void setLaneVoice(int voice) {
        laneVoice = Math.max(0, Math.min(voiceCount - 1, voice));
        draw();
    }

    int getLaneVoice() {
        return laneVoice;
    }

    void setOnLaneVoiceChanged(java.util.function.IntConsumer callback) {
        onLaneVoiceChanged = callback == null ? voice -> { } : callback;
    }

    void setFollow(boolean follow) {
        this.follow = follow;
    }

    int selectionSize() {
        return selection.size();
    }

    void focusCanvas() {
        Platform.runLater(canvas::requestFocus);
    }

    void render(PianoRollClip clip) {
        this.clip = clip;
        Set<Note> kept = new LinkedHashSet<>();
        for (Note note : selection) {
            if (clip.notes().contains(note)) {
                kept.add(note);
            }
        }
        selection = kept;
        refreshScrollBars();
        draw();
    }

    void clearSelection() {
        selection = new LinkedHashSet<>();
        onSelectionChanged.run();
    }

    void setPlayheadTick(double tick) {
        if (tick == playheadTick) {
            return;
        }
        playheadTick = tick;
        if (follow && tick >= 0 && mode == Mode.NONE) {
            double x = tick * pixelsPerTick;
            double left = hbar.getValue();
            double width = gridWidth();
            if (x < left || x > left + width) {
                hbar.setValue(Math.min(hbar.getMax(), Math.max(0, x - width * 0.1)));
            }
        }
        draw();
    }

    void centerOnContent() {
        centerPending = canvas.getHeight() <= RULER_HEIGHT + VELOCITY_HEIGHT;
        refreshScrollBars();
        float center = 60;
        if (!clip.notes().isEmpty()) {
            float low = Float.MAX_VALUE;
            float high = -Float.MAX_VALUE;
            for (Note note : clip.notes()) {
                low = Math.min(low, note.pitch());
                high = Math.max(high, note.pitch());
            }
            center = (low + high) / 2f;
        }
        double target = (PianoRollClip.MAX_PITCH - center) * rowHeight - gridHeight() / 2;
        vbar.setValue(Math.max(0, Math.min(vbar.getMax(), target)));
        hbar.setValue(0);
    }

    void zoomToFit() {
        double width = gridWidth();
        if (width <= 0) {
            return;
        }
        int ticks = Math.max(clip.lengthTicks(), clip.lastNoteEnd());
        pixelsPerTick = clampZoom(width / ticks);
        refreshScrollBars();
        hbar.setValue(0);
        draw();
    }

    void zoomBy(double factor) {
        zoomHorizontal(factor, KEYS_WIDTH + gridWidth() / 2);
    }

    private double gridWidth() {
        return Math.max(1, canvas.getWidth() - KEYS_WIDTH);
    }

    private double gridHeight() {
        return Math.max(1, canvas.getHeight() - RULER_HEIGHT - VELOCITY_HEIGHT - LANE_GAP);
    }

    private double gridBottom() {
        return RULER_HEIGHT + gridHeight();
    }

    private double velocityTop() {
        return canvas.getHeight() - VELOCITY_HEIGHT;
    }

    private double worldWidth() {
        return (Math.max(clip.lengthTicks(), clip.lastNoteEnd()) + PianoRollClip.TICKS_PER_BAR) * pixelsPerTick;
    }

    private void refreshScrollBars() {
        double hMax = Math.max(0, worldWidth() - gridWidth());
        hbar.setMax(hMax);
        hbar.setVisibleAmount(gridWidth());
        hbar.setUnitIncrement(PianoRollClip.TICKS_PER_BEAT * pixelsPerTick);
        hbar.setBlockIncrement(gridWidth() * 0.8);
        hbar.setValue(Math.min(hbar.getValue(), hMax));
        double vMax = Math.max(0, ROWS * rowHeight - gridHeight());
        vbar.setMax(vMax);
        vbar.setVisibleAmount(gridHeight());
        vbar.setUnitIncrement(rowHeight);
        vbar.setBlockIncrement(gridHeight() * 0.8);
        vbar.setValue(Math.min(vbar.getValue(), vMax));
        draw();
    }

    private double tickToX(double tick) {
        return KEYS_WIDTH + tick * pixelsPerTick - hbar.getValue();
    }

    private double xToTick(double x) {
        return Math.max(0, (x - KEYS_WIDTH + hbar.getValue()) / pixelsPerTick);
    }

    private double pitchToY(float pitch) {
        return RULER_HEIGHT + (PianoRollClip.MAX_PITCH - pitch) * rowHeight - vbar.getValue();
    }

    private float rowAtY(double y) {
        float pitch = PianoRollClip.MAX_PITCH - (float) Math.floor((y - RULER_HEIGHT + vbar.getValue()) / rowHeight);
        return clampPitch(pitch);
    }

    private float continuousPitchAtY(double y) {
        return clampPitch((float) (PianoRollClip.MAX_PITCH - (y - RULER_HEIGHT + vbar.getValue()) / rowHeight));
    }

    private float pitchAt(MouseEvent event) {
        return event.isAltDown() ? continuousPitchAtY(event.getY()) : rowAtY(event.getY());
    }

    private static float clampPitch(float pitch) {
        return Math.max(PianoRollClip.MIN_PITCH, Math.min(PianoRollClip.MAX_PITCH, pitch));
    }

    private int snapFloor(double tick) {
        return (int) (Math.floor(tick / snapTicks) * snapTicks);
    }

    private int snapRound(double tick) {
        return (int) (Math.round(tick / snapTicks) * snapTicks);
    }

    private static double clampZoom(double pixelsPerTick) {
        return Math.max(MIN_PIXELS_PER_TICK, Math.min(MAX_PIXELS_PER_TICK, pixelsPerTick));
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        g.setFill(Theme.CANVAS);
        g.fillRect(0, 0, width, height);

        g.save();
        g.beginPath();
        g.rect(KEYS_WIDTH, RULER_HEIGHT, width - KEYS_WIDTH, gridHeight());
        g.clip();
        drawRows(g, width);
        drawBeatLines(g, RULER_HEIGHT, gridBottom());
        double endX = tickToX(clip.lengthTicks());
        if (endX < width) {
            g.setFill(Theme.WAVE_BACKGROUND.deriveColor(0, 1, 1, 0.6));
            g.fillRect(Math.max(KEYS_WIDTH, endX), RULER_HEIGHT, width, gridHeight());
        }
        drawNotes(g);
        drawPitchLines(g);
        drawPlayhead(g, RULER_HEIGHT, gridBottom());
        if (mode == Mode.SELECT) {
            double x = Math.min(pressX, lastDragX);
            double y = Math.min(pressY, bandY);
            g.setFill(Theme.ACCENT.deriveColor(0, 1, 1, 0.15));
            g.fillRect(x, y, Math.abs(lastDragX - pressX), Math.abs(bandY - pressY));
            g.setStroke(Theme.ACCENT);
            g.setLineWidth(1);
            g.strokeRect(x, y, Math.abs(lastDragX - pressX), Math.abs(bandY - pressY));
        }
        g.restore();

        drawKeys(g);
        drawRuler(g, width);
        drawVelocityLane(g, width);
    }

    private void drawRows(GraphicsContext g, double width) {
        int first = (int) Math.max(0, Math.floor(vbar.getValue() / rowHeight));
        int last = (int) Math.min(ROWS - 1, Math.ceil((vbar.getValue() + gridHeight()) / rowHeight));
        for (int row = first; row <= last; row++) {
            int pitch = ROWS - 1 - row;
            double y = pitchToY(pitch);
            if (BLACK_KEY[pitch % 12]) {
                g.setFill(Theme.WAVE_BACKGROUND);
                g.fillRect(KEYS_WIDTH, y, width, rowHeight);
            }
            if (pitch % 12 == 0) {
                g.setStroke(Theme.BORDER);
                g.setLineWidth(1);
                g.strokeLine(KEYS_WIDTH, y + rowHeight, width, y + rowHeight);
            }
        }
    }

    private void drawBeatLines(GraphicsContext g, double top, double bottom) {
        int step = snapTicks;
        while (step * pixelsPerTick < 6) {
            step *= 2;
        }
        int firstTick = (int) (Math.floor(xToTick(KEYS_WIDTH) / step) * step);
        int lastTick = (int) Math.ceil(xToTick(canvas.getWidth()));
        for (int tick = firstTick; tick <= lastTick; tick += step) {
            double x = tickToX(tick);
            if (tick % PianoRollClip.TICKS_PER_BAR == 0) {
                g.setStroke(Theme.BORDER);
                g.setLineWidth(1.2);
            } else if (tick % PianoRollClip.TICKS_PER_BEAT == 0) {
                g.setStroke(Theme.NODE_HEADER);
                g.setLineWidth(0.8);
            } else {
                g.setStroke(Theme.NODE_BODY);
                g.setLineWidth(0.5);
            }
            g.strokeLine(x, top, x, bottom);
        }
    }

    private void drawNotes(GraphicsContext g) {
        double left = xToTick(KEYS_WIDTH);
        double right = xToTick(canvas.getWidth());
        g.setFont(Font.font(Theme.FONT_SMALL - 1));
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.LEFT);
        for (Note note : clip.notes()) {
            if (note.end() < left || note.start() > right) {
                continue;
            }
            double x = tickToX(note.start());
            double y = pitchToY(note.pitch());
            double w = Math.max(3, note.length() * pixelsPerTick - 1);
            boolean selected = selection.contains(note);
            boolean pastEnd = note.start() >= clip.lengthTicks();
            boolean silent = pastEnd || note.voice() >= voiceCount;
            double alpha = (silent ? 0.3 : 1.0) * (0.4 + 0.6 * note.velocity());
            Color fill = silent ? Theme.TEXT_FAINT : ChannelColors.fill(note.voice());
            g.setFill((selected ? Theme.WIRE_SELECTED : fill).deriveColor(0, 1, 1, alpha));
            g.fillRoundRect(x + 0.5, y + 0.5, w, rowHeight - 1, 4, 4);
            g.setStroke(selected ? Theme.WIRE_SELECTED : Theme.PORT_STROKE);
            g.setLineWidth(1);
            g.strokeRoundRect(x + 0.5, y + 0.5, w, rowHeight - 1, 4, 4);
            if (w > 30 && rowHeight >= 10) {
                g.setFill(Theme.PORT_STROKE);
                String label = NoteName.format(note.pitch()) + (w > 60 ? "  v" + (note.voice() + 1) : "");
                g.fillText(label, x + 4, y + rowHeight / 2, w - 6);
            }
        }
    }

    private void drawPlayhead(GraphicsContext g, double top, double bottom) {
        if (playheadTick < 0) {
            return;
        }
        double x = tickToX(playheadTick);
        g.setStroke(Theme.WARNING);
        g.setLineWidth(1.5);
        g.strokeLine(x, top, x, bottom);
    }

    private void drawKeys(GraphicsContext g) {
        g.setFill(Theme.CANVAS);
        g.fillRect(0, RULER_HEIGHT, KEYS_WIDTH, gridHeight());
        g.save();
        g.beginPath();
        g.rect(0, RULER_HEIGHT, KEYS_WIDTH, gridHeight());
        g.clip();
        g.setFont(Font.font(Theme.FONT_SMALL - 1));
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.RIGHT);
        int first = (int) Math.max(0, Math.floor(vbar.getValue() / rowHeight));
        int last = (int) Math.min(ROWS - 1, Math.ceil((vbar.getValue() + gridHeight()) / rowHeight));
        for (int row = first; row <= last; row++) {
            int pitch = ROWS - 1 - row;
            double y = pitchToY(pitch);
            boolean black = BLACK_KEY[pitch % 12];
            g.setFill(black ? Theme.PORT_STROKE : Theme.TEXT);
            g.fillRect(0, y, black ? KEYS_WIDTH * 0.62 : KEYS_WIDTH - 1, rowHeight);
            g.setStroke(Theme.TEXT_FAINT);
            g.setLineWidth(0.5);
            g.strokeLine(0, y + rowHeight, KEYS_WIDTH - 1, y + rowHeight);
            if (pitch % 12 == 0 && rowHeight >= 8) {
                g.setFill(Theme.CANVAS);
                g.fillText(NoteName.format(pitch), KEYS_WIDTH - 4, y + rowHeight / 2);
            }
        }
        g.restore();
    }

    private void drawRuler(GraphicsContext g, double width) {
        g.setFill(Theme.NODE_HEADER);
        g.fillRect(0, 0, width, RULER_HEIGHT);
        g.save();
        g.beginPath();
        g.rect(KEYS_WIDTH, 0, width - KEYS_WIDTH, RULER_HEIGHT);
        g.clip();
        g.setFont(Font.font(Theme.FONT_SMALL));
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.LEFT);
        int barStep = 1;
        while (barStep * PianoRollClip.TICKS_PER_BAR * pixelsPerTick < 40) {
            barStep *= 2;
        }
        int firstBar = (int) Math.floor(xToTick(KEYS_WIDTH) / PianoRollClip.TICKS_PER_BAR);
        firstBar -= firstBar % barStep;
        int lastBar = (int) Math.ceil(xToTick(width) / PianoRollClip.TICKS_PER_BAR);
        for (int bar = firstBar; bar <= lastBar; bar += barStep) {
            double x = tickToX((double) bar * PianoRollClip.TICKS_PER_BAR);
            g.setStroke(Theme.BORDER);
            g.setLineWidth(1);
            g.strokeLine(x, RULER_HEIGHT * 0.4, x, RULER_HEIGHT);
            g.setFill(Theme.TEXT_DIM);
            g.fillText(Integer.toString(bar + 1), x + 3, RULER_HEIGHT / 2);
        }
        double endX = tickToX(clip.lengthTicks());
        g.setFill(Theme.ACCENT);
        g.fillPolygon(new double[] {endX - 7, endX, endX}, new double[] {0, 0, RULER_HEIGHT * 0.7}, 3);
        g.setStroke(Theme.ACCENT);
        g.strokeLine(endX, 0, endX, RULER_HEIGHT);
        drawPlayhead(g, 0, RULER_HEIGHT);
        g.restore();
        g.setFill(Theme.NODE_BODY);
        g.fillRect(0, 0, KEYS_WIDTH, RULER_HEIGHT);
    }

    private void drawVelocityLane(GraphicsContext g, double width) {
        double top = velocityTop();
        g.setFill(Theme.NODE_BODY);
        g.fillRect(0, top - LANE_GAP, width, LANE_GAP);
        g.setFill(Theme.WAVE_BACKGROUND);
        g.fillRect(0, top, width, VELOCITY_HEIGHT);
        g.setFill(Theme.TEXT_FAINT);
        g.setFont(Font.font(Theme.FONT_SMALL - 1));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        g.fillText(laneShowsBend ? "Bend v" + (laneVoice + 1) : "Velocity", 4, top + 4);
        if (laneShowsBend) {
            g.save();
            g.beginPath();
            g.rect(0, top, width, VELOCITY_HEIGHT);
            g.clip();
            drawBeatLines(g, top, top + VELOCITY_HEIGHT);
            drawBendLane(g, width, top);
            drawPlayhead(g, top, top + VELOCITY_HEIGHT);
            g.restore();
            return;
        }
        g.save();
        g.beginPath();
        g.rect(KEYS_WIDTH, top, width - KEYS_WIDTH, VELOCITY_HEIGHT);
        g.clip();
        drawBeatLines(g, top, top + VELOCITY_HEIGHT);
        double usable = VELOCITY_HEIGHT - 8;
        for (Note note : clip.notes()) {
            double x = tickToX(note.start());
            if (x < KEYS_WIDTH - 4 || x > width + 4) {
                continue;
            }
            double h = note.velocity() * usable;
            double base = top + VELOCITY_HEIGHT - 2;
            boolean selected = selection.contains(note);
            g.setStroke(selected ? Theme.WIRE_SELECTED : Theme.ACCENT);
            g.setLineWidth(2);
            g.strokeLine(x + 1, base, x + 1, base - h);
            g.setFill(selected ? Theme.WIRE_SELECTED : Theme.ACCENT);
            g.fillOval(x - 2, base - h - 3, 6, 6);
        }
        drawPlayhead(g, top, top + VELOCITY_HEIGHT);
        g.restore();
    }

    private Note hitTest(double x, double y) {
        double tick = xToTick(x);
        List<Note> notes = clip.notes();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note note = notes.get(i);
            double noteY = pitchToY(note.pitch());
            double right = note.start() + Math.max(3 / pixelsPerTick, note.length());
            if (tick >= note.start() && tick < right && y >= noteY && y < noteY + rowHeight) {
                return note;
            }
        }
        return null;
    }

    private boolean isResizeHandle(Note note, double x) {
        double noteWidth = note.length() * pixelsPerTick;
        double handle = Math.min(RESIZE_HANDLE_PIXELS, noteWidth / 3.0);
        return x >= tickToX(note.end()) - handle;
    }

    private void onPressed(MouseEvent event) {
        centerPending = false;
        canvas.requestFocus();
        event.consume();
        double x = event.getX();
        double y = event.getY();
        pressX = x;
        pressY = y;
        lastDragX = x;
        bandY = y;
        pressTick = xToTick(x);
        dragBase = clip;
        candidate = null;
        mode = Mode.NONE;
        if (x < KEYS_WIDTH) {
            return;
        }
        if (y < RULER_HEIGHT) {
            mode = Mode.LENGTH;
            dragLength(x);
            return;
        }
        if (y >= velocityTop() && laneShowsBend) {
            startPen(x, y, true, event.getButton() == MouseButton.SECONDARY);
            return;
        }
        if (y >= velocityTop()) {
            mode = Mode.VELOCITY;
            candidate = clip.copy();
            paintVelocity(x, x, y);
            return;
        }
        if (y >= gridBottom()) {
            return;
        }

        if (tool == Tool.PEN) {
            startPen(x, y, false, event.getButton() == MouseButton.SECONDARY);
            return;
        }
        Note hit = hitTest(x, y);
        if (event.getButton() == MouseButton.SECONDARY) {
            if (hit != null) {
                selection.remove(hit);
                editor.applyEdit(c -> {
                    c.remove(hit);
                    return c;
                });
            }
            return;
        }
        if (hit != null && event.getClickCount() == 2) {
            openPitchField(hit);
            return;
        }
        if (hit != null) {
            if (event.isShiftDown()) {
                if (!selection.remove(hit)) {
                    selection.add(hit);
                }
                onSelectionChanged.run();
                draw();
                return;
            }
            if (!selection.contains(hit)) {
                selection = new LinkedHashSet<>(List.of(hit));
            }
            anchor = hit;
            pressPitch = event.isAltDown() ? continuousPitchAtY(y) : rowAtY(y);
            dragNotes = new ArrayList<>(selection);
            mode = isResizeHandle(hit, x) ? Mode.RESIZE : Mode.MOVE;
            lastLength = hit.length();
            lastVelocity = hit.velocity();
            auditionedPitch = hit.pitch();
            editor.audition(hit.pitch(), hit.velocity());
            onSelectionChanged.run();
            draw();
            return;
        }
        if (event.isShiftDown() || event.isShortcutDown()) {
            mode = Mode.SELECT;
            selectionBase = event.isShiftDown() ? new LinkedHashSet<>(selection) : Set.of();
            selection = new LinkedHashSet<>(selectionBase);
            onSelectionChanged.run();
            draw();
            return;
        }
        mode = Mode.CREATE;
        anchor = new Note(snapFloor(pressTick), lastLength, pitchAt(event), lastVelocity);
        previewCreate(anchor);
        auditionedPitch = anchor.pitch();
        editor.audition(anchor.pitch(), anchor.velocity());
    }

    private void onDragged(MouseEvent event) {
        double x = event.getX();
        double y = event.getY();
        switch (mode) {
            case LENGTH -> dragLength(x);
            case VELOCITY -> paintVelocity(lastDragX, x, y);
            case PEN -> penTo(x, y);
            case CREATE -> {
                int end = event.isAltDown() ? (int) Math.round(xToTick(x)) : snapRound(xToTick(x));
                int length = Math.max(event.isAltDown() ? 1 : snapTicks, end - anchor.start());
                previewCreate(anchor.withLength(length));
            }
            case MOVE -> dragMove(event);
            case RESIZE -> dragResize(event);
            case SELECT -> {
                lastDragX = x;
                bandY = y;
                Set<Note> picked = new LinkedHashSet<>(selectionBase);
                double t0 = xToTick(Math.min(pressX, x));
                double t1 = xToTick(Math.max(pressX, x));
                double y0 = Math.min(pressY, y);
                double y1 = Math.max(pressY, y);
                for (Note note : clip.notes()) {
                    double noteY = pitchToY(note.pitch());
                    if (note.end() > t0 && note.start() < t1 && noteY + rowHeight > y0 && noteY < y1) {
                        picked.add(note);
                    }
                }
                selection = picked;
                onSelectionChanged.run();
                draw();
            }
            default -> {
            }
        }
        lastDragX = x;
    }

    private void onReleased(MouseEvent event) {
        Mode finished = mode;
        mode = Mode.NONE;
        if (finished == Mode.PEN && !erasing && penMinTick <= penMaxTick) {
            candidate.anchorBend(penVoice, penMinTick, penMaxTick);
        }
        if (finished == Mode.CREATE && candidate != null) {
            lastLength = selection.isEmpty() ? lastLength : selection.iterator().next().length();
        }
        if (candidate != null && finished != Mode.SELECT) {
            PianoRollClip result = candidate;
            candidate = null;
            editor.applyEdit(c -> result);
        } else {
            draw();
        }
        onSelectionChanged.run();
    }

    private void startPen(double x, double y, boolean inLane, boolean erase) {
        mode = Mode.PEN;
        penInLane = inLane;
        erasing = erase;
        penLastTick = Double.NaN;
        penMinTick = Double.MAX_VALUE;
        penMaxTick = -Double.MAX_VALUE;
        candidate = clip.copy();
        penVoice = inLane ? laneVoice : -1;
        if (!inLane && erase) {
            Note nearest = nearestSounding(xToTick(x), continuousPitchAtY(y));
            penVoice = nearest == null ? laneVoice : nearest.voice();
        }
        if (penVoice >= 0 && penVoice != laneVoice) {
            laneVoice = penVoice;
            onLaneVoiceChanged.accept(laneVoice);
        }
        penTo(x, y);
    }

    private void penTo(double x, double y) {
        double tick = xToTick(x);
        if (erasing) {
            candidate.eraseBend(penVoice, Double.isNaN(penLastTick) ? tick : penLastTick, tick);
            penLastTick = tick;
            editor.previewEdit(candidate);
            return;
        }
        float cents = penInLane ? laneCentsAtY(y) : noteCentsAt(tick, continuousPitchAtY(y));
        if (Float.isNaN(cents)) {
            penLastTick = Double.NaN;
            return;
        }
        if (Double.isNaN(penLastTick)) {
            candidate.drawBend(penVoice, tick, cents, tick, cents);
        } else {
            candidate.drawBend(penVoice, penLastTick, penLastCents, tick, cents);
        }
        penLastTick = tick;
        penLastCents = cents;
        penMinTick = Math.min(penMinTick, tick);
        penMaxTick = Math.max(penMaxTick, tick);
        editor.previewEdit(candidate);
    }

    private Note nearestSounding(double tick, float cursorPitch) {
        Note closest = null;
        for (Note note : candidate.notes()) {
            boolean eligible = penVoice < 0 || note.voice() == penVoice;
            if (eligible && tick >= note.start() && tick < note.end() && (closest == null
                    || Math.abs(note.pitch() - cursorPitch) < Math.abs(closest.pitch() - cursorPitch))) {
                closest = note;
            }
        }
        return closest;
    }

    private float noteCentsAt(double tick, float cursorPitch) {
        Note closest = nearestSounding(tick, cursorPitch);
        if (closest == null) {
            return Float.NaN;
        }
        if (penVoice < 0) {
            penVoice = closest.voice();
            laneVoice = penVoice;
            onLaneVoiceChanged.accept(laneVoice);
        }
        return (cursorPitch - (closest.pitch() - 0.5f)) * 100f;
    }

    private double laneMid() {
        return velocityTop() + VELOCITY_HEIGHT / 2;
    }

    private double laneHalf() {
        return VELOCITY_HEIGHT / 2 - 4;
    }

    private float laneCentsAtY(double y) {
        double value = (laneMid() - y) / laneHalf() * PianoRollClip.MAX_BEND_CENTS;
        return (float) Math.max(-PianoRollClip.MAX_BEND_CENTS, Math.min(PianoRollClip.MAX_BEND_CENTS, value));
    }

    private void drawPitchLines(GraphicsContext g) {
        if (tool != Tool.PEN && !clip.hasBend()) {
            return;
        }
        g.setStroke(Theme.WARNING);
        g.setLineWidth(1.5);
        double left = KEYS_WIDTH;
        double right = canvas.getWidth();
        for (Note note : clip.notes()) {
            double x0 = Math.max(left, tickToX(note.start()));
            double x1 = Math.min(right, tickToX(note.end()));
            if (x1 <= x0) {
                continue;
            }
            int count = (int) Math.ceil((x1 - x0) / 2) + 1;
            double[] xs = new double[count];
            double[] ys = new double[count];
            for (int i = 0; i < count; i++) {
                double x = Math.min(x1, x0 + i * 2);
                xs[i] = x;
                ys[i] = pitchToY(note.pitch() - 0.5f + clip.bendAt(note.voice(), xToTick(x)) / 100f);
            }
            g.strokePolyline(xs, ys, count);
        }
    }

    private void drawBendLane(GraphicsContext g, double width, double top) {
        double mid = laneMid();
        double half = laneHalf();
        g.setStroke(Theme.BORDER);
        g.setLineWidth(1);
        g.strokeLine(KEYS_WIDTH, mid, width, mid);
        g.setStroke(Theme.NODE_HEADER);
        g.setLineWidth(0.5);
        g.strokeLine(KEYS_WIDTH, mid - half, width, mid - half);
        g.strokeLine(KEYS_WIDTH, mid + half, width, mid + half);
        g.setFill(Theme.TEXT_FAINT);
        g.fillText("+12 st", 4, top + 18);
        g.fillText("-12 st", 4, top + VELOCITY_HEIGHT - 16);
        int count = (int) Math.ceil((width - KEYS_WIDTH) / 2) + 1;
        double[] xs = new double[count];
        double[] ys = new double[count];
        for (int i = 0; i < count; i++) {
            double x = KEYS_WIDTH + i * 2;
            xs[i] = x;
            ys[i] = mid - clip.bendAt(laneVoice, xToTick(x)) / PianoRollClip.MAX_BEND_CENTS * half;
        }
        g.setStroke(ChannelColors.fill(laneVoice));
        g.setLineWidth(1.5);
        g.strokePolyline(xs, ys, count);
    }

    private void previewCreate(Note note) {
        candidate = dragBase.copy();
        Note placed = note.withVoice(candidate.freeVoice(note.start(), note.end(), voiceCount, List.of()));
        candidate.add(placed);
        selection = new LinkedHashSet<>(List.of(placed));
        editor.previewEdit(candidate);
    }

    private Note place(PianoRollClip target, Note note) {
        Note placed = target.voiceIsFree(note, List.of()) && note.voice() < voiceCount
                ? note
                : note.withVoice(target.freeVoice(note.start(), note.end(), voiceCount, List.of()));
        target.add(placed);
        return placed;
    }

    private void dragMove(MouseEvent event) {
        double rawDelta = xToTick(event.getX()) - pressTick;
        int delta = event.isAltDown()
                ? (int) Math.round(rawDelta)
                : snapRound(anchor.start() + rawDelta) - anchor.start();
        int earliest = Integer.MAX_VALUE;
        for (Note note : dragNotes) {
            earliest = Math.min(earliest, note.start());
        }
        delta = Math.max(-earliest, delta);
        float pitchDelta = (event.isAltDown() ? continuousPitchAtY(event.getY()) : rowAtY(event.getY())) - pressPitch;
        candidate = dragBase.copy();
        Set<Note> moved = new LinkedHashSet<>();
        for (Note note : dragNotes) {
            candidate.remove(note);
        }
        for (Note note : dragNotes) {
            moved.add(place(candidate, new Note(note.start() + delta, note.length(), note.pitch() + pitchDelta,
                    note.velocity(), note.voice())));
        }
        selection = moved;
        editor.previewEdit(candidate);
        float anchorPitch = anchor.pitch() + pitchDelta;
        if (anchorPitch != auditionedPitch) {
            auditionedPitch = anchorPitch;
            editor.audition(anchorPitch, anchor.velocity());
        }
    }

    private void dragResize(MouseEvent event) {
        double tick = xToTick(event.getX());
        int newEnd = event.isAltDown() ? (int) Math.round(tick) : snapRound(tick);
        int delta = newEnd - anchor.end();
        int minimum = event.isAltDown() ? 1 : Math.min(snapTicks, anchor.length());
        candidate = dragBase.copy();
        Set<Note> resized = new LinkedHashSet<>();
        for (Note note : dragNotes) {
            candidate.remove(note);
        }
        for (Note note : dragNotes) {
            resized.add(place(candidate, note.withLength(Math.max(minimum, note.length() + delta))));
        }
        selection = resized;
        editor.previewEdit(candidate);
    }

    private void dragLength(double x) {
        int bars = (int) Math.max(1, Math.round(xToTick(x) / PianoRollClip.TICKS_PER_BAR));
        candidate = dragBase.copy();
        candidate.setLengthTicks(bars * PianoRollClip.TICKS_PER_BAR);
        editor.previewEdit(candidate);
    }

    private void paintVelocity(double fromX, double toX, double y) {
        double usable = VELOCITY_HEIGHT - 8;
        float value = (float) Math.max(0, Math.min(1, (velocityTop() + VELOCITY_HEIGHT - 2 - y) / usable));
        double t0 = xToTick(Math.min(fromX, toX) - 4);
        double t1 = xToTick(Math.max(fromX, toX) + 4);
        boolean restrict = !selection.isEmpty();
        Set<Note> newSelection = new LinkedHashSet<>();
        for (Note note : new ArrayList<>(candidate.notes())) {
            boolean inRange = note.start() >= t0 && note.start() <= t1;
            if (inRange && (!restrict || selection.contains(note))) {
                candidate.remove(note);
                Note changed = note.withVelocity(value);
                candidate.add(changed);
                if (selection.contains(note)) {
                    newSelection.add(changed);
                }
                lastVelocity = value;
            } else if (selection.contains(note)) {
                newSelection.add(note);
            }
        }
        selection = newSelection;
        editor.previewEdit(candidate);
    }

    private void onScroll(ScrollEvent event) {
        event.consume();
        double dy = event.getDeltaY();
        double dx = event.getDeltaX();
        if (event.isShortcutDown()) {
            double amount = dy != 0 ? dy : dx;
            if (amount != 0) {
                zoomHorizontal(amount > 0 ? 1.15 : 1 / 1.15, event.getX());
            }
            return;
        }
        if (event.isAltDown()) {
            double amount = dy != 0 ? dy : dx;
            if (amount != 0) {
                zoomVertical(amount > 0 ? 1.15 : 1 / 1.15, event.getY());
            }
            return;
        }
        if (event.isShiftDown()) {
            double amount = dy != 0 ? dy : dx;
            hbar.setValue(clamp(hbar.getValue() - amount, hbar.getMax()));
            return;
        }
        hbar.setValue(clamp(hbar.getValue() - dx, hbar.getMax()));
        vbar.setValue(clamp(vbar.getValue() - dy, vbar.getMax()));
    }

    private static double clamp(double value, double max) {
        return Math.max(0, Math.min(max, value));
    }

    private void zoomHorizontal(double factor, double anchorX) {
        double tick = xToTick(anchorX);
        pixelsPerTick = clampZoom(pixelsPerTick * factor);
        refreshScrollBars();
        hbar.setValue(clamp(tick * pixelsPerTick - (anchorX - KEYS_WIDTH), hbar.getMax()));
        draw();
    }

    private void zoomVertical(double factor, double anchorY) {
        float pitch = continuousPitchAtY(anchorY);
        rowHeight = Math.max(MIN_ROW_HEIGHT, Math.min(MAX_ROW_HEIGHT, rowHeight * factor));
        refreshScrollBars();
        vbar.setValue(clamp((PianoRollClip.MAX_PITCH - pitch) * rowHeight - (anchorY - RULER_HEIGHT), vbar.getMax()));
        draw();
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (event.isShortcutDown()) {
            switch (code) {
                case A -> {
                    selection = new LinkedHashSet<>(clip.notes());
                    onSelectionChanged.run();
                    draw();
                }
                case C -> copySelection();
                case X -> {
                    copySelection();
                    deleteSelection();
                }
                case V -> paste();
                case D -> duplicateSelection();
                default -> {
                    return;
                }
            }
            event.consume();
            return;
        }
        switch (code) {
            case DELETE, BACK_SPACE -> deleteSelection();
            case ESCAPE -> {
                clearSelection();
                draw();
            }
            case UP -> transform(n -> n.withPitch(n.pitch() + (event.isShiftDown() ? 12 : 1)));
            case DOWN -> transform(n -> n.withPitch(n.pitch() - (event.isShiftDown() ? 12 : 1)));
            case LEFT -> nudge(-(event.isShiftDown() ? PianoRollClip.TICKS_PER_BAR : snapTicks));
            case RIGHT -> nudge(event.isShiftDown() ? PianoRollClip.TICKS_PER_BAR : snapTicks);
            case Q -> quantize();
            case OPEN_BRACKET -> moveToVoice(-1);
            case CLOSE_BRACKET -> moveToVoice(1);
            case ENTER, F2 -> {
                if (selection.size() == 1) {
                    openPitchField(selection.iterator().next());
                }
            }
            default -> {
                return;
            }
        }
        event.consume();
    }

    private void moveToVoice(int direction) {
        if (selection.isEmpty()) {
            return;
        }
        List<Note> originals = new ArrayList<>(selection);
        Set<Note> changed = new LinkedHashSet<>();
        editor.applyEdit(c -> {
            originals.forEach(c::remove);
            for (Note note : originals) {
                Note moved = note.withVoice(Math.max(0, Math.min(voiceCount - 1, note.voice() + direction)));
                c.add(moved);
                changed.add(moved);
            }
            selection = changed;
            return c;
        });
        if (!changed.isEmpty()) {
            laneVoice = changed.iterator().next().voice();
            onLaneVoiceChanged.accept(laneVoice);
        }
        onSelectionChanged.run();
    }

    private void nudge(int ticks) {
        int earliest = Integer.MAX_VALUE;
        for (Note note : selection) {
            earliest = Math.min(earliest, note.start());
        }
        int delta = Math.max(-earliest, ticks);
        transform(n -> n.withStart(n.start() + delta));
    }

    private void quantize() {
        if (selection.isEmpty()) {
            selection = new LinkedHashSet<>(clip.notes());
        }
        transform(n -> n.withStart(snapRound(n.start())));
    }

    private void transform(java.util.function.UnaryOperator<Note> change) {
        if (selection.isEmpty()) {
            return;
        }
        List<Note> originals = new ArrayList<>(selection);
        Set<Note> changed = new LinkedHashSet<>();
        editor.applyEdit(c -> {
            for (Note note : originals) {
                c.remove(note);
            }
            for (Note note : originals) {
                changed.add(place(c, change.apply(note)));
            }
            selection = changed;
            return c;
        });
        onSelectionChanged.run();
    }

    private void deleteSelection() {
        if (selection.isEmpty()) {
            return;
        }
        List<Note> doomed = new ArrayList<>(selection);
        selection = new LinkedHashSet<>();
        editor.applyEdit(c -> {
            doomed.forEach(c::remove);
            return c;
        });
        onSelectionChanged.run();
    }

    private void copySelection() {
        List<Note> copied = new ArrayList<>(selection);
        copied.sort(Comparator.comparingInt(Note::start));
        clipboard = List.copyOf(copied);
    }

    private void paste() {
        if (clipboard.isEmpty()) {
            return;
        }
        int offset = snapFloor(hoverTick) - clipboard.get(0).start();
        placeCopies(clipboard, offset);
    }

    private void duplicateSelection() {
        if (selection.isEmpty()) {
            return;
        }
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (Note note : selection) {
            start = Math.min(start, note.start());
            end = Math.max(end, note.end());
        }
        int span = Math.max(snapTicks, (int) Math.ceil((end - start) / (double) snapTicks) * snapTicks);
        placeCopies(new ArrayList<>(selection), span);
    }

    private void placeCopies(List<Note> notes, int offset) {
        Set<Note> placed = new LinkedHashSet<>();
        editor.applyEdit(c -> {
            for (Note note : notes) {
                placed.add(place(c, note.withStart(note.start() + offset)));
            }
            selection = placed;
            return c;
        });
        onSelectionChanged.run();
    }

    private void openPitchField(Note note) {
        editingNote = note;
        pitchField.setText(NoteName.format(note.pitch()));
        pitchField.getStyleClass().remove("invalid");
        pitchField.setTooltip(null);
        pitchField.setLayoutX(Math.max(KEYS_WIDTH, tickToX(note.start())));
        pitchField.setLayoutY(Math.max(RULER_HEIGHT, pitchToY(note.pitch()) - 4));
        pitchField.setVisible(true);
        pitchField.autosize();
        Platform.runLater(() -> {
            pitchField.requestFocus();
            pitchField.selectAll();
        });
    }

    private void commitPitchField(boolean keepOpenOnFailure) {
        Note note = editingNote;
        if (note == null) {
            return;
        }
        float pitch;
        try {
            pitch = clampPitch(NoteName.parseFlexible(pitchField.getText()));
        } catch (IllegalArgumentException ex) {
            if (keepOpenOnFailure) {
                if (!pitchField.getStyleClass().contains("invalid")) {
                    pitchField.getStyleClass().add("invalid");
                }
                pitchField.setTooltip(new Tooltip(ex.getMessage()));
            } else {
                closePitchField();
            }
            return;
        }
        closePitchField();
        selection = new LinkedHashSet<>(List.of(note));
        transform(n -> n.withPitch(pitch));
    }

    private void closePitchField() {
        editingNote = null;
        pitchField.setVisible(false);
        canvas.requestFocus();
    }
}
