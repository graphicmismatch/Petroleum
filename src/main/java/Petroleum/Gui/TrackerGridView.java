package Petroleum.Gui;

import Petroleum.Sources.NoteName;
import Petroleum.Tracker.NoteEffectText;
import Petroleum.Tracker.NoteEntryText;
import Petroleum.Tracker.TrackerPattern;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

final class TrackerGridView extends Canvas {
    private static final double GUTTER_WIDTH = 44;
    private static final double COLUMN_WIDTH = 132;
    private static final double ROW_HEIGHT = 18;
    private static final int DEFAULT_NOTE = 60;
    private static final float MIN_NOTE = 0f;
    private static final float MAX_NOTE = 127f;
    private static final int MIN_OCTAVE = -1;
    private static final int MAX_OCTAVE = 8;

    private static final Map<KeyCode, Integer> PIANO_KEYS = Map.ofEntries(
            Map.entry(KeyCode.Z, 0), Map.entry(KeyCode.S, 1), Map.entry(KeyCode.X, 2),
            Map.entry(KeyCode.D, 3), Map.entry(KeyCode.C, 4), Map.entry(KeyCode.V, 5),
            Map.entry(KeyCode.G, 6), Map.entry(KeyCode.B, 7), Map.entry(KeyCode.H, 8),
            Map.entry(KeyCode.N, 9), Map.entry(KeyCode.J, 10), Map.entry(KeyCode.M, 11),
            Map.entry(KeyCode.Q, 12), Map.entry(KeyCode.DIGIT2, 13), Map.entry(KeyCode.W, 14),
            Map.entry(KeyCode.DIGIT3, 15), Map.entry(KeyCode.E, 16), Map.entry(KeyCode.R, 17),
            Map.entry(KeyCode.DIGIT5, 18), Map.entry(KeyCode.T, 19), Map.entry(KeyCode.DIGIT6, 20),
            Map.entry(KeyCode.Y, 21), Map.entry(KeyCode.DIGIT7, 22), Map.entry(KeyCode.U, 23),
            Map.entry(KeyCode.I, 24), Map.entry(KeyCode.DIGIT9, 25), Map.entry(KeyCode.O, 26),
            Map.entry(KeyCode.DIGIT0, 27), Map.entry(KeyCode.P, 28));

    private final TrackerEditorView editor;
    private TrackerPattern pattern = new TrackerPattern();
    private int playheadStep = -1;

    private int selectedChannel = -1;
    private int selectedStep = -1;
    private int selectionAnchorStep = -1;
    private int selectionAnchorChannel = -1;
    private boolean noteHighlightVisible = true;
    private Runnable onSelectionChanged = () -> { };

    private int dragChannel = -1;
    private int dragStartStep;
    private float dragPitch;

    private int baseOctave = 4;
    private int editStep = 1;
    private IntConsumer onOctaveChange = octave -> { };

    TrackerGridView(TrackerEditorView editor) {
        this.editor = editor;
        setFocusTraversable(true);
        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
        setOnScroll(this::onScroll);
        setOnKeyPressed(this::onKeyPressed);
        draw();
    }

    @Override
    public boolean isResizable() {
        return false;
    }

    void render(TrackerPattern pattern) {
        this.pattern = pattern;
        double width = GUTTER_WIDTH + pattern.channelCount() * COLUMN_WIDTH;
        double height = pattern.totalSteps() * ROW_HEIGHT;
        if (getWidth() != width || getHeight() != height) {
            setWidth(width);
            setHeight(height);
        }
        draw();
    }

    void setPlayheadStep(int step) {
        if (playheadStep != step) {
            playheadStep = step;
            draw();
        }
    }

    int getBaseOctave() {
        return baseOctave;
    }

    void setBaseOctave(int octave) {
        baseOctave = Math.max(MIN_OCTAVE, Math.min(MAX_OCTAVE, octave));
        onOctaveChange.accept(baseOctave);
    }

    void setEditStep(int steps) {
        editStep = Math.max(0, steps);
    }

    void setOnOctaveChanged(IntConsumer listener) {
        this.onOctaveChange = listener == null ? octave -> { } : listener;
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        double width = getWidth();
        double height = getHeight();
        g.setFill(Theme.CANVAS);
        g.fillRect(0, 0, width, height);

        int totalSteps = pattern.totalSteps();
        int stepsPerBeat = Math.max(1, pattern.stepsPerBeat());

        g.setFont(Font.font(Theme.FONT_SMALL));
        for (int step = 0; step < totalSteps; step++) {
            double y = step * ROW_HEIGHT;
            boolean barStart = step % (stepsPerBeat * 4) == 0;
            boolean beatStart = step % stepsPerBeat == 0;
            g.setFill(barStart ? Theme.NODE_HEADER : (beatStart ? Theme.NODE_BODY : Theme.CANVAS));
            g.fillRect(0, y, width, ROW_HEIGHT);
            if (playheadStep == step) {
                g.setFill(Theme.ACCENT.deriveColor(0, 1, 1, 0.25));
                g.fillRect(0, y, width, ROW_HEIGHT);
            }
            g.setStroke(Theme.BORDER);
            g.setLineWidth(0.5);
            g.strokeLine(0, y, width, y);
            g.setFill(Theme.TEXT_FAINT);
            g.fillText(Integer.toString(step), 4, y + ROW_HEIGHT - 5);
        }
        g.setStroke(Theme.BORDER);
        g.setLineWidth(1);
        g.strokeLine(GUTTER_WIDTH, 0, GUTTER_WIDTH, height);
        for (int c = 1; c < pattern.channelCount(); c++) {
            double x = GUTTER_WIDTH + c * COLUMN_WIDTH;
            g.strokeLine(x, 0, x, height);
        }

        for (int c = 0; c < pattern.channelCount(); c++) {
            double x = GUTTER_WIDTH + c * COLUMN_WIDTH;
            for (TrackerPattern.NoteEvent note : pattern.notes(c)) {
                double noteY = note.startStep() * ROW_HEIGHT;
                double noteHeight = note.lengthSteps() * ROW_HEIGHT;
                boolean selected = noteHighlightVisible && selectedChannel == c && selectedStep == note.startStep();
                g.setFill(selected ? Theme.ACCENT : ChannelColors.fill(c));
                g.fillRoundRect(x + 3, noteY + 1, COLUMN_WIDTH - 6, noteHeight - 2, 4, 4);
                g.setStroke(Theme.BORDER);
                g.strokeRoundRect(x + 3, noteY + 1, COLUMN_WIDTH - 6, noteHeight - 2, 4, 4);

                g.save();
                g.beginPath();
                g.rect(x + 3, noteY + 1, COLUMN_WIDTH - 6, noteHeight - 2);
                g.clip();
                g.setFill(Theme.TEXT_STRONG);
                g.fillText(NoteName.format(note.pitch()), x + 8, noteY + ROW_HEIGHT - 5);
                String effectText = NoteEffectText.format(note.effect());
                if (!effectText.isEmpty()) {
                    g.setFill(Theme.TEXT_DIM);
                    g.fillText(effectText, x + 42, noteY + ROW_HEIGHT - 5);
                }
                g.restore();
            }
        }

        if (selectionAnchorStep >= 0 && selectionAnchorChannel >= 0) {
            int rangeStart = Math.min(selectionAnchorStep, selectedStep);
            int rangeEnd = Math.max(selectionAnchorStep, selectedStep);
            int channelStart = Math.min(selectionAnchorChannel, selectedChannel);
            int channelEnd = Math.max(selectionAnchorChannel, selectedChannel);
            double sx = GUTTER_WIDTH + channelStart * COLUMN_WIDTH;
            double sy = rangeStart * ROW_HEIGHT;
            double sw = (channelEnd - channelStart + 1) * COLUMN_WIDTH;
            double sh = (rangeEnd - rangeStart + 1) * ROW_HEIGHT;
            g.setFill(Theme.ACCENT.deriveColor(0, 1, 1, 0.15));
            g.fillRect(sx, sy, sw, sh);
            g.setStroke(Theme.ACCENT);
            g.setLineWidth(1.5);
            g.strokeRect(sx + 1, sy + 1, sw - 2, sh - 2);
        }

        if (selectedChannel >= 0 && selectedStep >= 0 && selectedStep < totalSteps) {
            double cx = GUTTER_WIDTH + selectedChannel * COLUMN_WIDTH;
            double cy = selectedStep * ROW_HEIGHT;
            g.setStroke(Theme.ACCENT);
            g.setLineWidth(2);
            g.strokeRect(cx + 1, cy + 1, COLUMN_WIDTH - 2, ROW_HEIGHT - 2);
        }
    }

    private int stepAt(double y) {
        return (int) Math.floor(y / ROW_HEIGHT);
    }

    private int channelAt(double x) {
        if (x < GUTTER_WIDTH) {
            return -1;
        }
        int c = (int) ((x - GUTTER_WIDTH) / COLUMN_WIDTH);
        return c >= 0 && c < pattern.channelCount() ? c : -1;
    }

    private int clampStep(int step) {
        return Math.max(0, Math.min(pattern.totalSteps() - 1, step));
    }

    private int barSteps() {
        return Math.max(1, pattern.stepsPerBeat()) * 4;
    }

    private void onPressed(MouseEvent event) {
        requestFocus();
        event.consume();
        int channel = channelAt(event.getX());
        int step = stepAt(event.getY());
        if (channel < 0 || step < 0 || step >= pattern.totalSteps()) {
            selectedChannel = -1;
            selectedStep = -1;
            selectionAnchorStep = -1;
            selectionAnchorChannel = -1;
            dragChannel = -1;
            draw();
            return;
        }
        TrackerPattern.NoteEvent existing = pattern.noteAt(channel, step);
        if (event.getButton() == MouseButton.SECONDARY) {
            if (existing != null) {
                int deleteChannel = channel;
                int deleteStep = existing.startStep();
                editor.applyEdit(p -> {
                    p.removeNote(deleteChannel, deleteStep);
                    return p;
                });
            }
            return;
        }
        noteHighlightVisible = true;
        selectionAnchorStep = -1;
        selectionAnchorChannel = -1;
        selectedChannel = channel;
        dragChannel = channel;
        if (existing != null) {
            selectedStep = existing.startStep();
            dragStartStep = existing.startStep();
            dragPitch = existing.pitch();
            if (event.getClickCount() == 2) {
                dragChannel = -1;
                editNote(channel, existing);
                draw();
                return;
            }
        } else {
            selectedStep = step;
            dragStartStep = step;
            dragPitch = DEFAULT_NOTE;
            if (event.getClickCount() == 2) {
                dragChannel = -1;
                TrackerPattern.NoteEvent placed = new TrackerPattern.NoteEvent(step, 1, DEFAULT_NOTE);
                editor.applyEdit(p -> {
                    p.addNote(channel, placed);
                    return p;
                });
                editNote(channel, placed);
                draw();
                return;
            }
            preview(step);
        }
        draw();
    }

    private void onDragged(MouseEvent event) {
        if (dragChannel < 0) {
            return;
        }
        preview(clampStep(stepAt(event.getY())));
    }

    private void preview(int step) {
        int endStep = Math.max(dragStartStep, step);
        int length = Math.max(1, endStep - dragStartStep + 1);
        TrackerPattern candidate = pattern.copy();
        candidate.addNote(dragChannel, new TrackerPattern.NoteEvent(dragStartStep, length, dragPitch));
        editor.previewEdit(candidate);
    }

    private void onReleased(MouseEvent event) {
        if (dragChannel < 0) {
            return;
        }
        int endStep = Math.max(dragStartStep, clampStep(stepAt(event.getY())));
        int length = Math.max(1, endStep - dragStartStep + 1);
        int channel = dragChannel;
        int start = dragStartStep;
        float pitch = dragPitch;
        dragChannel = -1;
        editor.applyEdit(p -> {
            p.addNote(channel, new TrackerPattern.NoteEvent(start, length, pitch));
            return p;
        });
    }

    private void onScroll(ScrollEvent event) {
        if (selectedChannel < 0 || selectedStep < 0) {
            return;
        }
        if (Math.abs(event.getDeltaY()) < 1e-6) {
            return;
        }
        float delta = (event.getDeltaY() > 0 ? 1f : -1f) * (event.isShiftDown() ? 12f : 1f);
        if (selectionAnchorStep >= 0 && selectionAnchorChannel >= 0) {
            transposeSelection(delta);
        } else {
            transposeNoteAtCursor(delta);
        }
        event.consume();
    }

    private void transposeNoteAtCursor(float delta) {
        TrackerPattern.NoteEvent existing = pattern.noteStartingAt(selectedChannel, selectedStep);
        if (existing == null) {
            return;
        }
        int channel = selectedChannel;
        int start = existing.startStep();
        int length = existing.lengthSteps();
        float newPitch = Math.max(MIN_NOTE, Math.min(MAX_NOTE, existing.pitch() + delta));
        TrackerPattern.NoteEffect effect = existing.effect();
        editor.applyEdit(p -> {
            p.removeNote(channel, start);
            p.addNote(channel, new TrackerPattern.NoteEvent(start, length, newPitch, effect));
            return p;
        });
        InputDebugLog.log(String.format("pitch grid  delta=%+5.1f  note %s -> %s",
                delta, NoteName.format(existing.pitch()), NoteName.format(newPitch)));
    }

    private void transposeSelection(float delta) {
        int rangeStart = Math.min(selectionAnchorStep, selectedStep);
        int rangeEnd = Math.max(selectionAnchorStep, selectedStep);
        int channelStart = Math.min(selectionAnchorChannel, selectedChannel);
        int channelEnd = Math.max(selectionAnchorChannel, selectedChannel);
        editor.applyEdit(p -> {
            for (int c = channelStart; c <= channelEnd; c++) {
                for (TrackerPattern.NoteEvent note : List.copyOf(p.notes(c))) {
                    if (note.startStep() >= rangeStart && note.startStep() <= rangeEnd) {
                        float newPitch = Math.max(MIN_NOTE, Math.min(MAX_NOTE, note.pitch() + delta));
                        p.removeNote(c, note.startStep());
                        p.addNote(c, new TrackerPattern.NoteEvent(
                                note.startStep(), note.lengthSteps(), newPitch, note.effect()));
                    }
                }
            }
            return p;
        });
        InputDebugLog.log(String.format("pitch grid block  delta=%+5.1f", delta));
    }

    private void editNote(int channel, TrackerPattern.NoteEvent existing) {
        int start = existing.startStep();
        int length = existing.lengthSteps();
        TrackerPattern.NoteEffect currentEffect = existing.effect();
        double x = GUTTER_WIDTH + channel * COLUMN_WIDTH;
        double y = start * ROW_HEIGHT;
        editor.editInlineText(this, x, y, NoteEntryText.format(existing.pitch(), currentEffect), text -> {
            NoteEntryText.Parsed parsed;
            try {
                parsed = NoteEntryText.parse(text, currentEffect);
            } catch (IllegalArgumentException ex) {
                return ex.getMessage();
            }
            editor.applyEdit(p -> {
                p.removeNote(channel, start);
                p.addNote(channel, new TrackerPattern.NoteEvent(start, length, parsed.pitch(), parsed.effect()));
                return p;
            });
            return null;
        });
    }

    private void ensureCursor() {
        if (selectedChannel < 0 || selectedStep < 0) {
            selectedChannel = 0;
            selectedStep = 0;
        }
    }

    private void setCursor(int channel, int step) {
        selectedChannel = Math.max(0, Math.min(pattern.channelCount() - 1, channel));
        selectedStep = Math.max(0, Math.min(pattern.totalSteps() - 1, step));
        selectionAnchorStep = -1;
        selectionAnchorChannel = -1;
        noteHighlightVisible = true;
        editor.scrollGridIntoView(GUTTER_WIDTH + selectedChannel * COLUMN_WIDTH, selectedStep * ROW_HEIGHT,
                COLUMN_WIDTH, ROW_HEIGHT);
        draw();
        onSelectionChanged.run();
    }

    void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback == null ? () -> { } : callback;
    }

    int getSelectedChannel() {
        return selectedChannel;
    }

    int getSelectedStep() {
        return selectedStep;
    }

    private void enterNoteAtCursor(int semitoneOffset) {
        float pitch = Math.max(MIN_NOTE, Math.min(MAX_NOTE, (baseOctave + 1) * 12 + semitoneOffset));
        int channel = selectedChannel;
        int step = selectedStep;
        TrackerPattern.NoteEvent existing = pattern.noteStartingAt(channel, step);
        int length = existing != null ? existing.lengthSteps() : 1;
        editor.applyEdit(p -> {
            p.addNote(channel, new TrackerPattern.NoteEvent(step, length, pitch));
            return p;
        });
        if (editStep > 0) {
            setCursor(channel, step + editStep);
        }
    }

    private void resizeNoteAtCursor(int delta) {
        TrackerPattern.NoteEvent existing = pattern.noteStartingAt(selectedChannel, selectedStep);
        if (existing == null) {
            return;
        }
        int channel = selectedChannel;
        int start = existing.startStep();
        float pitch = existing.pitch();
        TrackerPattern.NoteEffect effect = existing.effect();
        int newLength = Math.max(1, Math.min(pattern.totalSteps() - start, existing.lengthSteps() + delta));
        editor.applyEdit(p -> {
            p.removeNote(channel, start);
            p.addNote(channel, new TrackerPattern.NoteEvent(start, newLength, pitch, effect));
            return p;
        });
    }

    private void deleteAtCursor() {
        if (selectionAnchorStep >= 0 && selectionAnchorChannel >= 0) {
            int rangeStart = Math.min(selectionAnchorStep, selectedStep);
            int rangeEnd = Math.max(selectionAnchorStep, selectedStep);
            int channelStart = Math.min(selectionAnchorChannel, selectedChannel);
            int channelEnd = Math.max(selectionAnchorChannel, selectedChannel);
            editor.applyEdit(p -> {
                for (int c = channelStart; c <= channelEnd; c++) {
                    for (TrackerPattern.NoteEvent note : List.copyOf(p.notes(c))) {
                        if (note.startStep() >= rangeStart && note.startStep() <= rangeEnd) {
                            p.removeNote(c, note.startStep());
                        }
                    }
                }
                return p;
            });
            return;
        }
        int channel = selectedChannel;
        int step = selectedStep;
        editor.applyEdit(p -> {
            p.removeNote(channel, step);
            return p;
        });
    }

    private void ensureSelectionAnchor() {
        if (selectionAnchorStep < 0) {
            selectionAnchorStep = selectedStep;
            selectionAnchorChannel = selectedChannel;
        }
    }

    private void extendSelection(int deltaStep) {
        ensureSelectionAnchor();
        int newStep = Math.max(0, Math.min(pattern.totalSteps() - 1, selectedStep + deltaStep));
        if (newStep == selectedStep) {
            return;
        }
        selectedStep = newStep;
        noteHighlightVisible = true;
        editor.scrollGridIntoView(GUTTER_WIDTH + selectedChannel * COLUMN_WIDTH, selectedStep * ROW_HEIGHT,
                COLUMN_WIDTH, ROW_HEIGHT);
        draw();
        onSelectionChanged.run();
    }

    private void extendSelectionChannel(int deltaChannel) {
        ensureSelectionAnchor();
        int newChannel = Math.max(0, Math.min(pattern.channelCount() - 1, selectedChannel + deltaChannel));
        if (newChannel == selectedChannel) {
            return;
        }
        selectedChannel = newChannel;
        noteHighlightVisible = true;
        editor.scrollGridIntoView(GUTTER_WIDTH + selectedChannel * COLUMN_WIDTH, selectedStep * ROW_HEIGHT,
                COLUMN_WIDTH, ROW_HEIGHT);
        draw();
        onSelectionChanged.run();
    }

    private void copySelection() {
        if (selectionAnchorStep < 0 || selectionAnchorChannel < 0) {
            return;
        }
        int rangeStart = Math.min(selectionAnchorStep, selectedStep);
        int rangeEnd = Math.max(selectionAnchorStep, selectedStep);
        int channelStart = Math.min(selectionAnchorChannel, selectedChannel);
        int channelEnd = Math.max(selectionAnchorChannel, selectedChannel);
        List<TrackerEditorView.ClipboardNote> copied = new ArrayList<>();
        for (int c = channelStart; c <= channelEnd; c++) {
            for (TrackerPattern.NoteEvent note : pattern.notes(c)) {
                if (note.startStep() >= rangeStart && note.startStep() <= rangeEnd) {
                    copied.add(new TrackerEditorView.ClipboardNote(
                            c - channelStart, note.startStep() - rangeStart, note));
                }
            }
        }
        editor.setClipboard(copied);
    }

    private void cutSelection() {
        if (selectionAnchorStep < 0 || selectionAnchorChannel < 0) {
            return;
        }
        copySelection();
        deleteAtCursor();
    }

    private void pasteAtCursor() {
        List<TrackerEditorView.ClipboardNote> toPaste = editor.getClipboard();
        if (toPaste.isEmpty()) {
            return;
        }
        int baseChannel = selectedChannel;
        int baseStep = selectedStep;
        editor.applyEdit(p -> {
            for (TrackerEditorView.ClipboardNote entry : toPaste) {
                int channel = baseChannel + entry.channelOffset();
                int start = baseStep + entry.stepOffset();
                if (channel >= 0 && channel < p.channelCount() && start >= 0 && start < p.totalSteps()) {
                    TrackerPattern.NoteEvent note = entry.note();
                    p.addNote(channel, new TrackerPattern.NoteEvent(
                            start, note.lengthSteps(), note.pitch(), note.effect()));
                }
            }
            return p;
        });
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        ensureCursor();

        if (event.isControlDown() && code == KeyCode.UP) {
            setBaseOctave(baseOctave + 1);
            event.consume();
            return;
        }
        if (event.isControlDown() && code == KeyCode.DOWN) {
            setBaseOctave(baseOctave - 1);
            event.consume();
            return;
        }
        if (event.isAltDown() && code == KeyCode.UP) {
            extendSelection(-1);
            event.consume();
            return;
        }
        if (event.isAltDown() && code == KeyCode.DOWN) {
            extendSelection(1);
            event.consume();
            return;
        }
        if (event.isAltDown() && code == KeyCode.LEFT) {
            extendSelectionChannel(-1);
            event.consume();
            return;
        }
        if (event.isAltDown() && code == KeyCode.RIGHT) {
            extendSelectionChannel(1);
            event.consume();
            return;
        }
        if (event.isShortcutDown() && !event.isShiftDown() && !event.isAltDown()) {
            if (code == KeyCode.C) {
                copySelection();
                event.consume();
                return;
            }
            if (code == KeyCode.X) {
                cutSelection();
                event.consume();
                return;
            }
            if (code == KeyCode.V) {
                pasteAtCursor();
                event.consume();
                return;
            }
        }
        if (code == KeyCode.ESCAPE) {
            noteHighlightVisible = false;
            selectionAnchorStep = -1;
            selectionAnchorChannel = -1;
            draw();
            event.consume();
            return;
        }
        if (code == KeyCode.TAB) {
            TrackerPattern.NoteEvent existing = pattern.noteStartingAt(selectedChannel, selectedStep);
            if (existing != null) {
                editNote(selectedChannel, existing);
            }
            event.consume();
            return;
        }

        if (event.isShiftDown() && code == KeyCode.UP) {
            resizeNoteAtCursor(-1);
            event.consume();
            return;
        }
        if (event.isShiftDown() && code == KeyCode.DOWN) {
            resizeNoteAtCursor(1);
            event.consume();
            return;
        }

        if (code == KeyCode.UP) {
            setCursor(selectedChannel, selectedStep - 1);
        } else if (code == KeyCode.DOWN) {
            setCursor(selectedChannel, selectedStep + 1);
        } else if (code == KeyCode.LEFT) {
            setCursor(selectedChannel - 1, selectedStep);
        } else if (code == KeyCode.RIGHT) {
            setCursor(selectedChannel + 1, selectedStep);
        } else if (code == KeyCode.HOME) {
            setCursor(selectedChannel, 0);
        } else if (code == KeyCode.END) {
            setCursor(selectedChannel, pattern.totalSteps() - 1);
        } else if (code == KeyCode.PAGE_UP) {
            setCursor(selectedChannel, selectedStep - barSteps());
        } else if (code == KeyCode.PAGE_DOWN) {
            setCursor(selectedChannel, selectedStep + barSteps());
        } else if (code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) {
            deleteAtCursor();
        } else {
            Integer offset = PIANO_KEYS.get(code);
            if (offset != null) {
                enterNoteAtCursor(offset);
            } else {
                return;
            }
        }
        event.consume();
    }
}
