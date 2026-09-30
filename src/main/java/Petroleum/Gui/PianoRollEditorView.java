package Petroleum.Gui;

import Petroleum.Patch.Patch;
import Petroleum.Serialization.PatchCodec;
import Petroleum.Tracker.MidiExporter;
import Petroleum.Tracker.PianoRollClip;
import Petroleum.Tracker.PianoRollNode;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

final class PianoRollEditorView {
    private record Snap(String label, int ticks) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final int BEAT = PianoRollClip.TICKS_PER_BEAT;
    private static final Snap[] SNAPS = {
            new Snap("Bar", PianoRollClip.TICKS_PER_BAR),
            new Snap("1/2", BEAT * 2),
            new Snap("1/4", BEAT),
            new Snap("1/8", BEAT / 2),
            new Snap("1/8 triplet", BEAT / 3),
            new Snap("1/16", BEAT / 4),
            new Snap("1/16 triplet", BEAT / 6),
            new Snap("1/32", BEAT / 8),
            new Snap("Off", 1)};

    private final BorderPane root = new BorderPane();
    private final PianoRollView rollView = new PianoRollView(this);
    private final Deque<PianoRollClip> undoStack = new ArrayDeque<>();
    private final Deque<PianoRollClip> redoStack = new ArrayDeque<>();
    private final Spinner<Integer> barsSpinner = new Spinner<>(1, 999, PianoRollClip.DEFAULT_BARS);
    private final Label statusLabel = new Label();
    private boolean suppressBarsEvents;
    private final ComboBox<Integer> voiceBox = new ComboBox<>();
    private boolean suppressVoiceEvents;

    private static final int AUDITION_TICKS = PianoRollClip.TICKS_PER_BEAT / 2;
    private static final Duration AUDITION_HOLD = Duration.seconds(0.9);

    private final PatchCodec codec;
    private final Button playButton = new Button();
    private final Spinner<Double> bpmSpinner = new Spinner<>(20.0, 400.0, 120.0, 1.0);
    private final CheckBox auditionCheck = new CheckBox("Audition notes");
    private final PauseTransition auditionStop = new PauseTransition(AUDITION_HOLD);
    private final AnimationTimer previewTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (preview != null) {
                rollView.setPlayheadTick(preview.roll.getPlayheadTick());
            }
        }
    };
    private Consumer<Patch> playAudio = patch -> { };
    private Runnable stopAudio = () -> { };
    private BooleanSupplier graphPlaying = () -> false;
    private PianoRollPreview preview;
    private boolean auditioning;
    private boolean warnedAboutUserVoice;

    private PianoRollNode node;
    private PianoRollClip working = new PianoRollClip();

    PianoRollEditorView(PatchCodec codec) {
        this.codec = codec;
        Theme.icon(playButton, "play", 16, playTooltip());
        playButton.setOnAction(e -> {
            togglePreview();
            rollView.focusCanvas();
        });
        bpmSpinner.setEditable(true);
        bpmSpinner.setPrefWidth(80);
        bpmSpinner.setTooltip(new Tooltip("Tempo of the preview only - the node itself follows its bpm input"));
        bpmSpinner.valueProperty().addListener((obs, was, is) -> {
            if (preview != null && is != null) {
                preview.bpm.setValue(is.floatValue());
            }
        });
        auditionCheck.setSelected(true);
        auditionCheck.setTooltip(new Tooltip("Play a short blip when a note is placed, clicked or dragged"));
        auditionStop.setOnFinished(e -> {
            if (auditioning) {
                auditioning = false;
                stopAudio.run();
            }
        });

        Button importButton = new Button("Import MIDI...");
        importButton.setOnAction(e -> {
            importMidi();
            rollView.focusCanvas();
        });

        Button exportButton = new Button("Export MIDI...");
        exportButton.setOnAction(e -> {
            exportMidi();
            rollView.focusCanvas();
        });

        Button undoButton = new Button();
        Theme.icon(undoButton, "undo", 16, "Undo edit");
        undoButton.setOnAction(e -> {
            undo();
            rollView.focusCanvas();
        });
        Button redoButton = new Button();
        Theme.icon(redoButton, "redo", 16, "Redo edit");
        redoButton.setOnAction(e -> {
            redo();
            rollView.focusCanvas();
        });

        ComboBox<Snap> snapBox = new ComboBox<>();
        snapBox.getItems().setAll(SNAPS);
        snapBox.getSelectionModel().select(5);
        snapBox.setOnAction(e -> {
            rollView.setSnapTicks(snapBox.getValue().ticks());
            rollView.focusCanvas();
        });

        barsSpinner.setEditable(true);
        barsSpinner.setPrefWidth(75);
        barsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!suppressBarsEvents && newValue != null) {
                applyEdit(c -> {
                    c.setLengthTicks(newValue * PianoRollClip.TICKS_PER_BAR);
                    return c;
                });
            }
        });

        Button zoomOut = new Button("-");
        zoomOut.setTooltip(new Tooltip("Zoom out (Ctrl+scroll)"));
        zoomOut.setOnAction(e -> {
            rollView.zoomBy(1 / 1.4);
            rollView.focusCanvas();
        });
        Button zoomIn = new Button("+");
        zoomIn.setTooltip(new Tooltip("Zoom in (Ctrl+scroll)"));
        zoomIn.setOnAction(e -> {
            rollView.zoomBy(1.4);
            rollView.focusCanvas();
        });
        Button fitButton = new Button("Fit");
        fitButton.setTooltip(new Tooltip("Zoom to show the whole clip"));
        fitButton.setOnAction(e -> {
            rollView.zoomToFit();
            rollView.focusCanvas();
        });

        ToggleGroup toolGroup = new ToggleGroup();
        ToggleButton notesTool = new ToggleButton("Notes");
        ToggleButton penTool = new ToggleButton("Pitch pen");
        notesTool.setToggleGroup(toolGroup);
        penTool.setToggleGroup(toolGroup);
        notesTool.setSelected(true);
        notesTool.setTooltip(new Tooltip("Place, move and resize notes"));
        penTool.setTooltip(new Tooltip("Draw the pitch curve over the notes; right-drag erases"));
        toolGroup.selectedToggleProperty().addListener((obs, was, is) -> {
            if (is == null) {
                was.setSelected(true);
                return;
            }
            rollView.setTool(is == penTool ? PianoRollView.Tool.PEN : PianoRollView.Tool.NOTES);
            rollView.focusCanvas();
        });

        ComboBox<String> laneBox = new ComboBox<>();
        laneBox.getItems().setAll("Velocity", "Pitch bend");
        laneBox.getSelectionModel().select(0);
        laneBox.setOnAction(e -> {
            rollView.setLaneShowsBend(laneBox.getSelectionModel().getSelectedIndex() == 1);
            rollView.focusCanvas();
        });

        voiceBox.setTooltip(new Tooltip("Which voice's pitch curve the bend lane shows and edits"));
        voiceBox.setOnAction(e -> {
            if (!suppressVoiceEvents && voiceBox.getValue() != null) {
                rollView.setLaneVoice(voiceBox.getValue() - 1);
                rollView.focusCanvas();
            }
        });
        rollView.setOnLaneVoiceChanged(voice -> {
            suppressVoiceEvents = true;
            voiceBox.setValue(voice + 1);
            suppressVoiceEvents = false;
        });

        Button clearBendButton = new Button("Clear voice curve");
        clearBendButton.setTooltip(new Tooltip("Remove the pitch curve of the voice chosen in Curve voice"));
        clearBendButton.setOnAction(e -> {
            int voice = rollView.getLaneVoice();
            applyEdit(c -> {
                c.clearBend(voice);
                return c;
            });
            rollView.focusCanvas();
        });

        CheckBox followCheck = new CheckBox("Follow playhead");
        followCheck.setSelected(true);
        followCheck.setOnAction(e -> {
            rollView.setFollow(followCheck.isSelected());
            rollView.focusCanvas();
        });

        FlowPane toolbar = new FlowPane(10, 8, playButton, new Label("Preview BPM"), bpmSpinner, auditionCheck,
                importButton, exportButton, undoButton, redoButton, notesTool, penTool,
                new Label("Lane"), laneBox, new Label("Curve voice"), voiceBox, clearBendButton,
                new Label("Snap"), snapBox, new Label("Length (bars)"), barsSpinner,
                new Label("Zoom"), zoomOut, zoomIn, fitButton, followCheck, statusLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPrefWrapLength(0);
        toolbar.setPadding(new Insets(10));

        rollView.setOnSelectionChanged(this::refreshStatus);
        root.setTop(toolbar);
        root.setCenter(rollView);
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
    }

    BorderPane getRoot() {
        return root;
    }

    private static String playTooltip() {
        return "Preview the piano roll (Space). Plays through " + PianoRollPreview.userVoiceFile()
                + " if it exists, otherwise the built-in sine voice.";
    }

    void setAudio(Consumer<Patch> play, Runnable stop, BooleanSupplier graphIsPlaying) {
        playAudio = play;
        stopAudio = stop;
        graphPlaying = graphIsPlaying;
    }

    void open(PianoRollNode node, float bpm) {
        this.node = node;
        bpmSpinner.getValueFactory().setValue((double) bpm);
        working = node.getClip();
        undoStack.clear();
        redoStack.clear();
        rollView.clearSelection();
        rollView.setVoiceCount(node.getChannelCount());
        suppressVoiceEvents = true;
        voiceBox.getItems().clear();
        for (int v = 1; v <= node.getChannelCount(); v++) {
            voiceBox.getItems().add(v);
        }
        voiceBox.setValue(rollView.getLaneVoice() + 1);
        suppressVoiceEvents = false;
        refresh();
        rollView.setPlayheadTick(-1);
        rollView.centerOnContent();
        rollView.focusCanvas();
    }

    void close() {
        stopPreview();
        pushToNode();
        node = null;
    }

    boolean isPreviewing() {
        return preview != null;
    }

    void togglePreview() {
        if (preview != null) {
            stopPreview();
        } else {
            startPreview();
        }
    }

    private void startPreview() {
        if (node == null) {
            return;
        }
        PianoRollPreview built = buildPreview(working, node.getChannelCount(), bpmSpinner.getValue().floatValue(),
                true);
        if (built == null) {
            return;
        }
        auditionStop.stop();
        auditioning = false;
        preview = built;
        playAudio.accept(built.patch);
        Theme.icon(playButton, "stop", 16, "Stop the preview (Space)");
        previewTimer.start();
    }

    void stopPreview() {
        if (preview == null) {
            return;
        }
        preview = null;
        previewTimer.stop();
        stopAudio.run();
        rollView.setPlayheadTick(-1);
        Theme.icon(playButton, "play", 16, playTooltip());
    }

    void audition(float pitch, float velocity) {
        if (!auditionCheck.isSelected() || preview != null || graphPlaying.getAsBoolean()) {
            return;
        }
        PianoRollClip blip = new PianoRollClip(PianoRollClip.TICKS_PER_BAR);
        blip.add(new PianoRollClip.Note(0, AUDITION_TICKS, pitch, velocity, 0));
        PianoRollPreview built = buildPreview(blip, 1, 120f, false);
        if (built == null) {
            return;
        }
        auditioning = true;
        playAudio.accept(built.patch);
        auditionStop.playFromStart();
    }

    private PianoRollPreview buildPreview(PianoRollClip clip, int voices, float bpm, boolean loop) {
        try {
            byte[] userVoice = PianoRollPreview.userVoice();
            if (userVoice != null) {
                return new PianoRollPreview(codec, userVoice, clip, voices, bpm, loop);
            }
        } catch (IOException | RuntimeException ex) {
            if (!warnedAboutUserVoice) {
                warnedAboutUserVoice = true;
                Dialogs.showError("Preview voice", "Could not use " + PianoRollPreview.userVoiceFile() + ": "
                        + ex.getMessage() + "\n\nUsing the built-in sine voice instead.");
            }
        }
        try {
            return new PianoRollPreview(codec, PianoRollPreview.builtInVoice(), clip, voices, bpm, loop);
        } catch (RuntimeException ex) {
            Dialogs.showError("Preview voice", "Could not build the preview: " + ex.getMessage());
            return null;
        }
    }

    void applyEdit(UnaryOperator<PianoRollClip> edit) {
        PianoRollClip updated = edit.apply(working.copy());
        if (Arrays.equals(updated.encode(), working.encode())) {
            pushToNode();
            refresh();
            return;
        }
        undoStack.push(working);
        redoStack.clear();
        working = updated;
        pushToNode();
        refresh();
    }

    void previewEdit(PianoRollClip transientClip) {
        if (node != null) {
            node.setClip(transientClip);
        }
        if (preview != null) {
            preview.roll.setClip(transientClip);
        }
        rollView.render(transientClip);
    }

    void tickPlayhead(boolean playing) {
        if (preview != null) {
            return;
        }
        rollView.setPlayheadTick(playing && node != null ? node.getPlayheadTick() : -1);
    }

    private void handleShortcut(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            return;
        }
        if (event.getCode() == KeyCode.SPACE && !event.isShortcutDown() && !event.isAltDown()) {
            togglePreview();
            rollView.focusCanvas();
            event.consume();
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
            if (event.isShiftDown()) {
                redo();
            } else {
                undo();
            }
            event.consume();
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(working);
        working = undoStack.pop();
        pushToNode();
        refresh();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(working);
        working = redoStack.pop();
        pushToNode();
        refresh();
    }

    private void pushToNode() {
        if (node != null) {
            node.setClip(working);
        }
        if (preview != null) {
            preview.roll.setClip(working);
        }
    }

    private void refresh() {
        rollView.render(working);
        suppressBarsEvents = true;
        barsSpinner.getValueFactory().setValue(
                Math.max(1, working.lengthTicks() / PianoRollClip.TICKS_PER_BAR));
        suppressBarsEvents = false;
        refreshStatus();
    }

    private void refreshStatus() {
        String text = working.notes().size() + " notes";
        if (rollView.selectionSize() > 0) {
            text += ", " + rollView.selectionSize() + " selected";
        }
        int stolen = node == null ? 0 : node.getStolenNoteCount();
        if (stolen > 0) {
            text += " - " + stolen + " cut short or silent (overlapping in one voice, or on a voice above "
                    + node.getChannelCount() + ")";
        }
        statusLabel.setText(text);
    }

    private void importMidi() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import MIDI file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MIDI files", "*.mid", "*.midi"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        PianoRollClip.ImportResult result;
        try {
            result = PianoRollClip.importMidi(file, node == null ? PianoRollNode.DEFAULT_VOICES : node.getChannelCount());
        } catch (IOException | RuntimeException ex) {
            Dialogs.showError("MIDI Import", "Could not import " + file.getName() + ": " + ex.getMessage());
            return;
        }
        rollView.clearSelection();
        applyEdit(c -> result.clip());
        rollView.centerOnContent();
        rollView.zoomToFit();
        Dialogs.info("MIDI Import Complete", "Imported " + result.noteCount() + " notes. Set this node's bpm "
                + "input to " + Math.round(result.detectedBpm()) + " to match the file's tempo.");
    }

    private void exportMidi() {
        File file = Dialogs.midiSaveDialog(root.getScene().getWindow(), "piano-roll");
        if (file == null) {
            return;
        }
        try {
            MidiExporter.export(working, bpmSpinner.getValue().floatValue(), file);
        } catch (IOException | RuntimeException ex) {
            Dialogs.showError("MIDI Export", "Could not export " + file.getName() + ": " + ex.getMessage());
            return;
        }
        Dialogs.info("MIDI Export Complete", "Wrote " + working.notes().size() + " notes to "
                + file.getName() + " at " + Math.round(bpmSpinner.getValue()) + " BPM.");
    }
}
