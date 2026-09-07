package JSynth.Gui;

import JSynth.Sources.NoteName;
import JSynth.Tracker.NoteEntryText;
import JSynth.Tracker.TrackerPattern;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

final class TrackerPianoRollView extends Canvas {
    private static final double PIXELS_PER_STEP = 14;
    private static final double PIXELS_PER_SEMITONE = 10;
    private static final double RESIZE_HANDLE_PIXELS = 6;
    private static final int MIN_ROW = 0;
    private static final int MAX_ROW = 127;
    private static final float MIN_NOTE = MIN_ROW;
    private static final float MAX_NOTE = MAX_ROW;

    private final TrackerEditorView editor;
    private TrackerPattern pattern = new TrackerPattern();
    private int playheadStep = -1;
    private int activeChannel;

    private int selectedChannel = -1;
    private int selectedStep = -1;
    private boolean noteHighlightVisible = true;
    private Runnable onSelectionChanged = () -> { };

    private int dragChannel = -1;
    private int hitChannel = -1;
    private int dragStartStep;
    private float dragPitch;
    private TrackerPattern.NoteEffect dragEffect = TrackerPattern.NoteEffect.NONE;
    private boolean placingNew;
    private boolean movingNote;
    private int moveStepOffset;
    private int moveLength;

    TrackerPianoRollView(TrackerEditorView editor) {
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

    void setActiveChannel(int channel) {
        this.activeChannel = channel;
    }

    void render(TrackerPattern pattern) {
        this.pattern = pattern;
        double width = Math.max(400, pattern.totalSteps() * PIXELS_PER_STEP);
        double height = (MAX_ROW - MIN_ROW + 1) * PIXELS_PER_SEMITONE;
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

    void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback == null ? () -> { } : callback;
    }

    int getSelectedChannel() {
        return selectedChannel;
    }

    int getSelectedStep() {
        return selectedStep;
    }

    private double pitchY(float pitch) {
        return (MAX_NOTE - pitch) * PIXELS_PER_SEMITONE;
    }

    /** Snaps to the semitone row under the cursor - the original, discrete placement behavior. */
    private float noteAtY(double y) {
        float note = MAX_NOTE - (float) Math.floor(y / PIXELS_PER_SEMITONE);
        return Math.max(MIN_NOTE, Math.min(MAX_NOTE, note));
    }

    /** Reads the exact fractional pitch under the cursor, no row-snapping - true microtonal placement. */
    private float continuousNoteAtY(double y) {
        float note = (float) (MAX_NOTE - y / PIXELS_PER_SEMITONE);
        return Math.max(MIN_NOTE, Math.min(MAX_NOTE, note));
    }

    private int stepAtX(double x) {
        return (int) Math.floor(x / PIXELS_PER_STEP);
    }

    private int clampStep(int step) {
        return Math.max(0, Math.min(pattern.totalSteps() - 1, step));
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        double width = getWidth();
        double height = getHeight();
        g.setFill(Theme.CANVAS);
        g.fillRect(0, 0, width, height);

        int stepsPerBeat = Math.max(1, pattern.stepsPerBeat());
        int totalSteps = pattern.totalSteps();
        for (int step = 0; step <= totalSteps; step++) {
            double x = step * PIXELS_PER_STEP;
            boolean barStart = step % (stepsPerBeat * 4) == 0;
            g.setStroke(barStart ? Theme.BORDER : Theme.NODE_HEADER);
            g.setLineWidth(barStart ? 1.2 : 0.5);
            g.strokeLine(x, 0, x, height);
        }
        for (int note = MIN_ROW; note <= MAX_ROW; note++) {
            if (note % 12 == 0) {
                double y = pitchY(note);
                g.setStroke(Theme.BORDER);
                g.setLineWidth(0.5);
                g.strokeLine(0, y, width, y);
            }
        }

        if (playheadStep >= 0) {
            g.setFill(Theme.ACCENT.deriveColor(0, 1, 1, 0.2));
            g.fillRect(playheadStep * PIXELS_PER_STEP, 0, PIXELS_PER_STEP, height);
        }

        for (int c = 0; c < pattern.channelCount(); c++) {
            for (TrackerPattern.NoteEvent note : pattern.notes(c)) {
                double x = note.startStep() * PIXELS_PER_STEP;
                double y = pitchY(note.pitch());
                double w = note.lengthSteps() * PIXELS_PER_STEP;
                boolean selected = noteHighlightVisible && selectedChannel == c && selectedStep == note.startStep();
                g.setFill(selected ? Theme.ACCENT : ChannelColors.fill(c));
                g.fillRoundRect(x + 1, y + 1, Math.max(2, w - 2), PIXELS_PER_SEMITONE - 2, 3, 3);
                g.setStroke(Theme.BORDER);
                g.strokeRoundRect(x + 1, y + 1, Math.max(2, w - 2), PIXELS_PER_SEMITONE - 2, 3, 3);
                if (!note.effect().equals(TrackerPattern.NoteEffect.NONE)) {
                    g.setFill(Theme.WARNING);
                    g.fillOval(x + Math.max(2, w - 2) - 3, y + 1, 3, 3);
                }
            }
        }
    }

    private boolean isResizeHandle(TrackerPattern.NoteEvent note, double x) {
        double width = note.lengthSteps() * PIXELS_PER_STEP;
        double handle = Math.min(RESIZE_HANDLE_PIXELS, width / 3.0);
        double noteRight = note.endStep() * PIXELS_PER_STEP;
        return x >= noteRight - handle;
    }

    private int clampMoveStart(int start, int length) {
        return Math.max(0, Math.min(pattern.totalSteps() - length, start));
    }

    private TrackerPattern.NoteEvent hitTest(double x, double y) {
        int step = stepAtX(x);
        for (int c = 0; c < pattern.channelCount(); c++) {
            for (TrackerPattern.NoteEvent note : pattern.notes(c)) {
                double noteY = pitchY(note.pitch());
                if (step >= note.startStep() && step < note.endStep()
                        && y >= noteY && y < noteY + PIXELS_PER_SEMITONE) {
                    hitChannel = c;
                    return note;
                }
            }
        }
        hitChannel = -1;
        return null;
    }

    private void onPressed(MouseEvent event) {
        requestFocus();
        event.consume();
        int step = clampStep(stepAtX(event.getX()));
        float note = event.isAltDown() ? continuousNoteAtY(event.getY()) : noteAtY(event.getY());
        TrackerPattern.NoteEvent hit = hitTest(event.getX(), event.getY());
        if (event.getButton() == MouseButton.SECONDARY) {
            if (hit != null) {
                int deleteChannel = hitChannel;
                int deleteStep = hit.startStep();
                editor.applyEdit(p -> {
                    p.removeNote(deleteChannel, deleteStep);
                    return p;
                });
            }
            return;
        }
        noteHighlightVisible = true;
        if (hit != null) {
            selectedChannel = hitChannel;
            selectedStep = hit.startStep();
            dragChannel = hitChannel;
            dragStartStep = hit.startStep();
            dragPitch = hit.pitch();
            dragEffect = hit.effect();
            placingNew = false;
            movingNote = !isResizeHandle(hit, event.getX());
            moveStepOffset = step - hit.startStep();
            moveLength = hit.lengthSteps();
            if (event.getClickCount() == 2) {
                dragChannel = -1;
                movingNote = false;
                editNote(hitChannel, hit);
                onSelectionChanged.run();
                draw();
                return;
            }
        } else {
            selectedChannel = activeChannel;
            selectedStep = step;
            dragChannel = activeChannel;
            dragStartStep = step;
            dragPitch = note;
            dragEffect = TrackerPattern.NoteEffect.NONE;
            placingNew = true;
            movingNote = false;
            if (event.getClickCount() == 2) {
                dragChannel = -1;
                int channel = activeChannel;
                TrackerPattern.NoteEvent placed = new TrackerPattern.NoteEvent(step, 1, note);
                editor.applyEdit(p -> {
                    p.addNote(channel, placed);
                    return p;
                });
                editNote(channel, placed);
                onSelectionChanged.run();
                draw();
                return;
            }
            preview(step, note);
        }
        onSelectionChanged.run();
        draw();
    }

    private void onDragged(MouseEvent event) {
        if (dragChannel < 0) {
            return;
        }
        int step = clampStep(stepAtX(event.getX()));
        if (movingNote) {
            float pitch = event.isAltDown() ? continuousNoteAtY(event.getY()) : noteAtY(event.getY());
            previewMove(clampMoveStart(step - moveStepOffset, moveLength), pitch);
            return;
        }
        float pitch = placingNew
                ? (event.isAltDown() ? continuousNoteAtY(event.getY()) : noteAtY(event.getY()))
                : dragPitch;
        preview(step, pitch);
    }

    private void preview(int step, float pitch) {
        dragPitch = pitch;
        int endStep = Math.max(dragStartStep, step);
        int length = Math.max(1, endStep - dragStartStep + 1);
        TrackerPattern candidate = pattern.copy();
        candidate.addNote(dragChannel, new TrackerPattern.NoteEvent(dragStartStep, length, pitch, dragEffect));
        editor.previewEdit(candidate);
    }

    private void previewMove(int newStart, float pitch) {
        dragPitch = pitch;
        TrackerPattern candidate = pattern.copy();
        candidate.removeNote(dragChannel, dragStartStep);
        candidate.addNote(dragChannel, new TrackerPattern.NoteEvent(newStart, moveLength, pitch, dragEffect));
        editor.previewEdit(candidate);
    }

    private void onReleased(MouseEvent event) {
        if (dragChannel < 0) {
            return;
        }
        int step = clampStep(stepAtX(event.getX()));
        int channel = dragChannel;
        float pitch = dragPitch;
        TrackerPattern.NoteEffect effect = dragEffect;
        if (movingNote) {
            int newStart = clampMoveStart(step - moveStepOffset, moveLength);
            int length = moveLength;
            int originalStart = dragStartStep;
            movingNote = false;
            dragChannel = -1;
            editor.applyEdit(p -> {
                p.removeNote(channel, originalStart);
                p.addNote(channel, new TrackerPattern.NoteEvent(newStart, length, pitch, effect));
                return p;
            });
            return;
        }
        int endStep = Math.max(dragStartStep, step);
        int length = Math.max(1, endStep - dragStartStep + 1);
        int start = dragStartStep;
        dragChannel = -1;
        editor.applyEdit(p -> {
            p.addNote(channel, new TrackerPattern.NoteEvent(start, length, pitch, effect));
            return p;
        });
    }

    private void onScroll(ScrollEvent event) {
        if (selectedChannel < 0 || selectedStep < 0) {
            return;
        }
        TrackerPattern.NoteEvent existing = pattern.noteStartingAt(selectedChannel, selectedStep);
        if (existing == null) {
            return;
        }
        if (Math.abs(event.getDeltaY()) < 1e-6) {
            return;
        }
        float delta = event.getDeltaY() > 0 ? 1f : -1f;
        int channel = selectedChannel;
        int start = existing.startStep();
        int length = existing.lengthSteps();
        TrackerPattern.NoteEffect effect = existing.effect();
        float newPitch = Math.max(MIN_NOTE, Math.min(MAX_NOTE, existing.pitch() + delta));
        editor.applyEdit(p -> {
            p.removeNote(channel, start);
            p.addNote(channel, new TrackerPattern.NoteEvent(start, length, newPitch, effect));
            return p;
        });
        editor.scrollPianoRollIntoView(start * PIXELS_PER_STEP, pitchY(newPitch),
                length * PIXELS_PER_STEP, PIXELS_PER_SEMITONE);
        InputDebugLog.log(String.format("pitch roll  deltaY=%+7.2f  note %s -> %s",
                event.getDeltaY(), NoteName.format(existing.pitch()), NoteName.format(newPitch)));
        event.consume();
    }

    private void editNote(int channel, TrackerPattern.NoteEvent existing) {
        int start = existing.startStep();
        int length = existing.lengthSteps();
        TrackerPattern.NoteEffect currentEffect = existing.effect();
        double x = start * PIXELS_PER_STEP;
        double y = pitchY(existing.pitch());
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

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            if (selectedChannel >= 0 && selectedStep >= 0) {
                int channel = selectedChannel;
                int step = selectedStep;
                editor.applyEdit(p -> {
                    p.removeNote(channel, step);
                    return p;
                });
            }
        } else if (event.getCode() == KeyCode.ESCAPE) {
            noteHighlightVisible = false;
            draw();
        } else if (event.getCode() == KeyCode.TAB) {
            if (selectedChannel >= 0 && selectedStep >= 0) {
                TrackerPattern.NoteEvent existing = pattern.noteStartingAt(selectedChannel, selectedStep);
                if (existing != null) {
                    editNote(selectedChannel, existing);
                }
            }
            event.consume();
        }
    }
}
