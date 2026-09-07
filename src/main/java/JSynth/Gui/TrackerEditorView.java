package JSynth.Gui;

import JSynth.Tracker.MidiImporter;
import JSynth.Tracker.TrackerNode;
import JSynth.Tracker.TrackerPattern;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

final class TrackerEditorView {
    private final BorderPane root = new BorderPane();
    private final TrackerGridView gridView = new TrackerGridView(this);
    private final TrackerPianoRollView pianoRollView = new TrackerPianoRollView(this);
    private final ScrollPane gridScroll = new ScrollPane(gridView);
    private final ScrollPane pianoScroll = new ScrollPane(pianoRollView);
    private final BorderPane pianoContainer = new BorderPane();
    private final InputDebugPanel debugPanel = new InputDebugPanel();
    private final TextField inlineEditor = new TextField();
    private final StackPane viewStack = new StackPane(gridScroll, pianoContainer, debugPanel.getRoot(), inlineEditor);
    private final Deque<TrackerPattern> undoStack = new ArrayDeque<>();
    private final Deque<TrackerPattern> redoStack = new ArrayDeque<>();
    private final Label octaveLabel = new Label();
    private final Spinner<Integer> totalStepsSpinner = new Spinner<>(1, 9999, TrackerPattern.DEFAULT_TOTAL_STEPS);
    private final Spinner<Integer> stepsPerBeatSpinner = new Spinner<>(1, 64, TrackerPattern.DEFAULT_STEPS_PER_BEAT);
    private boolean suppressPatternSpinnerEvents;

    private final Label effectNoteLabel = new Label("No note selected");
    private final ComboBox<TrackerPattern.SlideMode> slideModeBox =
            new ComboBox<>(javafx.collections.FXCollections.observableArrayList(TrackerPattern.SlideMode.values()));
    private final Spinner<Integer> slideStepsSpinner = new Spinner<>(0, 64, 0);
    private final Spinner<Integer> arpSemitone1Spinner = new Spinner<>(-48, 48, 0);
    private final Spinner<Integer> arpSemitone2Spinner = new Spinner<>(-48, 48, 0);
    private final Spinner<Integer> arpRateTicksSpinner = new Spinner<>(0, 16, 0);
    private final Spinner<Double> delayFractionSpinner = new Spinner<>(0.0, 0.95, 0.0, 0.05);
    private final Spinner<Integer> retriggerTicksSpinner = new Spinner<>(0, 16, 0);
    private final Spinner<Integer> cutTicksSpinner = new Spinner<>(0, 64, 0);
    private final Button applyEffectButton = new Button("Apply effect");
    private final VBox effectPanel = new VBox(6);

    private Function<String, String> inlineEditorCommit;

    private TrackerNode node;
    private TrackerPattern working = new TrackerPattern();

    TrackerEditorView() {
        pianoContainer.setTop(buildChannelBar(TrackerPattern.DEFAULT_CHANNELS));
        pianoContainer.setCenter(pianoScroll);

        StackPane.setAlignment(debugPanel.getRoot(), Pos.BOTTOM_LEFT);
        StackPane.setMargin(debugPanel.getRoot(), new Insets(10));
        debugPanel.getRoot().setVisible(false);
        debugPanel.getRoot().setManaged(false);

        StackPane.setAlignment(inlineEditor, Pos.TOP_LEFT);
        inlineEditor.setManaged(false);
        inlineEditor.setVisible(false);
        inlineEditor.setPrefWidth(120);
        inlineEditor.getStyleClass().add("inline-editor");
        inlineEditor.setOnAction(e -> commitInlineEditor(true));
        inlineEditor.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelInlineEditor();
                e.consume();
            }
        });
        inlineEditor.focusedProperty().addListener((obs, was, is) -> {
            if (was && !is) {
                commitInlineEditor(false);
            }
        });

        ToggleGroup group = new ToggleGroup();
        ToggleButton gridButton = new ToggleButton("Grid");
        ToggleButton rollButton = new ToggleButton("Piano Roll");
        gridButton.setToggleGroup(group);
        rollButton.setToggleGroup(group);
        gridButton.setSelected(true);
        gridButton.setOnAction(e -> showView(true));
        rollButton.setOnAction(e -> showView(false));

        Button importButton = new Button("Import MIDI...");
        importButton.setOnAction(e -> {
            importMidi(root.getScene().getWindow());
            restoreFocus();
        });

        Button undoButton = new Button("Undo edit");
        undoButton.setOnAction(e -> {
            undo();
            restoreFocus();
        });

        Button redoButton = new Button("Redo edit");
        redoButton.setOnAction(e -> {
            redo();
            restoreFocus();
        });

        Button octaveDown = new Button("Octave -");
        Button octaveUp = new Button("Octave +");
        octaveDown.setOnAction(e -> {
            gridView.setBaseOctave(gridView.getBaseOctave() - 1);
            restoreFocus();
        });
        octaveUp.setOnAction(e -> {
            gridView.setBaseOctave(gridView.getBaseOctave() + 1);
            restoreFocus();
        });
        gridView.setOnOctaveChanged(octave -> octaveLabel.setText("Octave " + octave));
        octaveLabel.setText("Octave " + gridView.getBaseOctave());

        Label editStepLabel = new Label("Edit step");
        Spinner<Integer> editStepSpinner = new Spinner<>(0, 16, 1);
        editStepSpinner.setEditable(true);
        editStepSpinner.setPrefWidth(60);
        editStepSpinner.valueProperty().addListener((obs, oldValue, newValue) -> gridView.setEditStep(newValue));
        editStepSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) {
                restoreFocus();
            }
        });

        Label totalStepsLabel = new Label("Pattern length");
        totalStepsSpinner.setEditable(true);
        totalStepsSpinner.setPrefWidth(70);
        totalStepsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (suppressPatternSpinnerEvents) {
                return;
            }
            applyEdit(p -> {
                p.setTotalSteps(newValue);
                return p;
            });
        });
        totalStepsSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) {
                restoreFocus();
            }
        });

        Label stepsPerBeatLabel = new Label("Steps/beat");
        stepsPerBeatSpinner.setEditable(true);
        stepsPerBeatSpinner.setPrefWidth(60);
        stepsPerBeatSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (suppressPatternSpinnerEvents) {
                return;
            }
            applyEdit(p -> {
                p.setStepsPerBeat(newValue);
                return p;
            });
        });
        stepsPerBeatSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) {
                restoreFocus();
            }
        });

        HBox toolbar = new HBox(10, gridButton, rollButton, importButton, undoButton, redoButton,
                octaveDown, octaveLabel, octaveUp, editStepLabel, editStepSpinner,
                totalStepsLabel, totalStepsSpinner, stepsPerBeatLabel, stepsPerBeatSpinner);
        toolbar.setPadding(new Insets(10));

        root.setTop(toolbar);
        root.setCenter(viewStack);
        root.setRight(buildEffectPanel());
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
        gridView.setOnSelectionChanged(this::refreshEffectPanel);
        pianoRollView.setOnSelectionChanged(this::refreshEffectPanel);
        showView(true);
    }

    private void handleShortcut(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
            if (event.isShiftDown()) {
                redo();
            } else {
                undo();
            }
            restoreFocus();
            event.consume();
        }
    }

    private VBox buildEffectPanel() {
        slideModeBox.getSelectionModel().select(TrackerPattern.SlideMode.NONE);
        slideStepsSpinner.setEditable(true);
        arpSemitone1Spinner.setEditable(true);
        arpSemitone2Spinner.setEditable(true);
        arpRateTicksSpinner.setEditable(true);
        delayFractionSpinner.setEditable(true);
        retriggerTicksSpinner.setEditable(true);
        cutTicksSpinner.setEditable(true);
        for (Spinner<?> spinner : List.of(slideStepsSpinner, arpSemitone1Spinner, arpSemitone2Spinner,
                arpRateTicksSpinner, delayFractionSpinner, retriggerTicksSpinner, cutTicksSpinner)) {
            spinner.setPrefWidth(90);
            spinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (wasFocused && !isFocused) {
                    restoreFocus();
                }
            });
        }

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        int row = 0;
        grid.addRow(row++, new Label("Slide"), slideModeBox);
        grid.addRow(row++, new Label("Slide steps"), slideStepsSpinner);
        grid.addRow(row++, new Label("Arp +semi 1"), arpSemitone1Spinner);
        grid.addRow(row++, new Label("Arp +semi 2"), arpSemitone2Spinner);
        grid.addRow(row++, new Label("Arp rate (ticks)"), arpRateTicksSpinner);
        grid.addRow(row++, new Label("Delay fraction"), delayFractionSpinner);
        grid.addRow(row++, new Label("Retrigger (ticks)"), retriggerTicksSpinner);
        grid.addRow(row++, new Label("Cut (ticks)"), cutTicksSpinner);

        applyEffectButton.setOnAction(e -> {
            applyEffectFromPanel();
            restoreFocus();
        });

        effectPanel.setPadding(new Insets(10));
        effectPanel.setPrefWidth(220);
        effectPanel.getChildren().addAll(effectNoteLabel, grid, applyEffectButton);
        refreshEffectPanel();
        return effectPanel;
    }

    private int activeSelectedChannel() {
        return gridScroll.isVisible() ? gridView.getSelectedChannel() : pianoRollView.getSelectedChannel();
    }

    private int activeSelectedStep() {
        return gridScroll.isVisible() ? gridView.getSelectedStep() : pianoRollView.getSelectedStep();
    }

    private void refreshEffectPanel() {
        int channel = activeSelectedChannel();
        int step = activeSelectedStep();
        TrackerPattern.NoteEvent existing = channel < 0 || step < 0
                ? null : working.noteStartingAt(channel, step);
        boolean hasNote = existing != null;
        effectPanel.setDisable(!hasNote);
        if (!hasNote) {
            effectNoteLabel.setText("No note selected");
            return;
        }
        effectNoteLabel.setText("Channel " + (channel + 1) + ", step " + step);
        TrackerPattern.NoteEffect effect = existing.effect();
        slideModeBox.getSelectionModel().select(effect.slideMode());
        slideStepsSpinner.getValueFactory().setValue(effect.slideSteps());
        arpSemitone1Spinner.getValueFactory().setValue(effect.arpSemitone1());
        arpSemitone2Spinner.getValueFactory().setValue(effect.arpSemitone2());
        arpRateTicksSpinner.getValueFactory().setValue(effect.arpRateTicks());
        delayFractionSpinner.getValueFactory().setValue((double) effect.delayFraction());
        retriggerTicksSpinner.getValueFactory().setValue(effect.retriggerTicks());
        cutTicksSpinner.getValueFactory().setValue(effect.cutTicks());
    }

    private void applyEffectFromPanel() {
        int channel = activeSelectedChannel();
        int step = activeSelectedStep();
        TrackerPattern.NoteEvent existing = channel < 0 || step < 0
                ? null : working.noteStartingAt(channel, step);
        if (existing == null) {
            return;
        }
        TrackerPattern.NoteEffect newEffect = new TrackerPattern.NoteEffect(
                slideModeBox.getValue(),
                slideStepsSpinner.getValue(),
                arpSemitone1Spinner.getValue(),
                arpSemitone2Spinner.getValue(),
                arpRateTicksSpinner.getValue(),
                delayFractionSpinner.getValue().floatValue(),
                retriggerTicksSpinner.getValue(),
                cutTicksSpinner.getValue());
        int start = existing.startStep();
        int length = existing.lengthSteps();
        float pitch = existing.pitch();
        applyEdit(p -> {
            p.removeNote(channel, start);
            p.addNote(channel, new TrackerPattern.NoteEvent(start, length, pitch, newEffect));
            return p;
        });
    }

    private HBox buildChannelBar(int channelCount) {
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox(4);
        bar.setPadding(new Insets(6));
        for (int c = 0; c < channelCount; c++) {
            int channel = c;
            ToggleButton button = new ToggleButton("Ch " + (c + 1));
            button.setToggleGroup(group);
            button.setSelected(c == 0);
            button.setOnAction(e -> {
                pianoRollView.setActiveChannel(channel);
                restoreFocus();
            });
            bar.getChildren().add(button);
        }
        return bar;
    }

    BorderPane getRoot() {
        return root;
    }

    void toggleDebugPanel() {
        boolean shown = !debugPanel.getRoot().isVisible();
        debugPanel.getRoot().setVisible(shown);
        debugPanel.getRoot().setManaged(shown);
    }

    void open(TrackerNode node) {
        this.node = node;
        this.working = node.getPattern();
        undoStack.clear();
        redoStack.clear();
        pianoContainer.setTop(buildChannelBar(node.getChannelCount()));
        gridView.setPlayheadStep(-1);
        pianoRollView.setPlayheadStep(-1);
        pianoRollView.setActiveChannel(0);
        refresh();
        restoreFocus();
    }

    void close() {
        pushToNode();
        node = null;
    }

    void applyEdit(UnaryOperator<TrackerPattern> edit) {
        undoStack.push(working.copy());
        redoStack.clear();
        working = edit.apply(working);
        pushToNode();
        refresh();
    }

    void previewEdit(TrackerPattern transientPattern) {
        if (node != null) {
            node.setPattern(transientPattern);
        }
        gridView.render(transientPattern);
        pianoRollView.render(transientPattern);
    }

    void scrollGridIntoView(double x, double y, double w, double h) {
        scrollIntoView(gridScroll, gridView.getWidth(), gridView.getHeight(), x, y, w, h);
    }

    void scrollPianoRollIntoView(double x, double y, double w, double h) {
        scrollIntoView(pianoScroll, pianoRollView.getWidth(), pianoRollView.getHeight(), x, y, w, h);
    }

    private static void scrollIntoView(ScrollPane scrollPane, double contentWidth, double contentHeight,
                                        double x, double y, double w, double h) {
        Bounds viewport = scrollPane.getViewportBounds();
        double scrollableX = Math.max(0, contentWidth - viewport.getWidth());
        double scrollableY = Math.max(0, contentHeight - viewport.getHeight());

        if (scrollableX > 0) {
            double offsetX = scrollPane.getHvalue() * scrollableX;
            if (x < offsetX) {
                offsetX = x;
            } else if (x + w > offsetX + viewport.getWidth()) {
                offsetX = x + w - viewport.getWidth();
            }
            scrollPane.setHvalue(Math.max(0, Math.min(1, offsetX / scrollableX)));
        }
        if (scrollableY > 0) {
            double offsetY = scrollPane.getVvalue() * scrollableY;
            if (y < offsetY) {
                offsetY = y;
            } else if (y + h > offsetY + viewport.getHeight()) {
                offsetY = y + h - viewport.getHeight();
            }
            scrollPane.setVvalue(Math.max(0, Math.min(1, offsetY / scrollableY)));
        }
    }

    void tickPlayhead(boolean playing) {
        int step = playing && node != null ? node.getCurrentStep() : -1;
        gridView.setPlayheadStep(step);
        pianoRollView.setPlayheadStep(step);
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(working.copy());
        working = undoStack.pop();
        pushToNode();
        refresh();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(working.copy());
        working = redoStack.pop();
        pushToNode();
        refresh();
    }

    private void pushToNode() {
        if (node != null) {
            node.setPattern(working);
        }
    }

    private void refresh() {
        gridView.render(working);
        pianoRollView.render(working);
        refreshEffectPanel();
        suppressPatternSpinnerEvents = true;
        totalStepsSpinner.getValueFactory().setValue(working.totalSteps());
        stepsPerBeatSpinner.getValueFactory().setValue(working.stepsPerBeat());
        suppressPatternSpinnerEvents = false;
    }

    private void showView(boolean grid) {
        gridScroll.setVisible(grid);
        gridScroll.setManaged(grid);
        pianoContainer.setVisible(!grid);
        pianoContainer.setManaged(!grid);
        refreshEffectPanel();
        restoreFocus();
    }

    private void restoreFocus() {
        Platform.runLater(() -> {
            if (gridScroll.isVisible()) {
                gridView.requestFocus();
            } else {
                pianoRollView.requestFocus();
            }
        });
    }

    /**
     * Opens the shared inline text editor over {@code (canvasX, canvasY)} - a coordinate in
     * {@code source}'s own local space - seeded with {@code initialText}. Used for both pitch entry and
     * effect-code entry in the grid and piano roll views, since both are "type a short string at this
     * cell" in the same way. Embedded in the scene rather than a separate dialog window, since a popup
     * {@code Stage} is a second top-level window a tiling window manager will place wherever it likes,
     * not necessarily anywhere near the note it's editing.
     *
     * <p>{@code onCommit} runs once the edit is confirmed (Enter, or losing focus) with whatever text
     * was in the field, and returns {@code null} on success or an error message to reject it - on
     * Enter a rejection keeps the editor open with a red border and the message as a tooltip, the same
     * feedback {@code Gui.NodeView}'s inline note/formula fields give, so the user can fix a typo
     * without losing it; on focus-loss a rejection just discards the edit instead, since forcing focus
     * back into a field the user is actively leaving would fight the platform. Escape always discards,
     * valid or not.
     */
    void editInlineText(javafx.scene.Node source, double canvasX, double canvasY, String initialText,
                   Function<String, String> onCommit) {
        Point2D local = viewStack.sceneToLocal(source.localToScene(canvasX, canvasY));
        inlineEditor.setLayoutX(local.getX());
        inlineEditor.setLayoutY(local.getY());
        inlineEditor.setText(initialText);
        inlineEditor.getStyleClass().remove("invalid");
        inlineEditor.setTooltip(null);
        inlineEditorCommit = onCommit;
        inlineEditor.setVisible(true);
        inlineEditor.toFront();
        inlineEditor.applyCss();
        inlineEditor.autosize();
        Platform.runLater(() -> {
            inlineEditor.requestFocus();
            inlineEditor.selectAll();
        });
    }

    private void commitInlineEditor(boolean keepOpenOnFailure) {
        if (inlineEditorCommit == null) {
            return;
        }
        String error = inlineEditorCommit.apply(inlineEditor.getText());
        if (error == null) {
            inlineEditorCommit = null;
            inlineEditor.getStyleClass().remove("invalid");
            inlineEditor.setTooltip(null);
            inlineEditor.setVisible(false);
            restoreFocus();
        } else if (keepOpenOnFailure) {
            if (!inlineEditor.getStyleClass().contains("invalid")) {
                inlineEditor.getStyleClass().add("invalid");
            }
            inlineEditor.setTooltip(new Tooltip(error));
        } else {
            cancelInlineEditor();
        }
    }

    private void cancelInlineEditor() {
        if (inlineEditorCommit == null && !inlineEditor.isVisible()) {
            return;
        }
        inlineEditorCommit = null;
        inlineEditor.getStyleClass().remove("invalid");
        inlineEditor.setTooltip(null);
        inlineEditor.setVisible(false);
        restoreFocus();
    }

    private void importMidi(Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import MIDI file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MIDI files", "*.mid", "*.midi"));
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return;
        }
        MidiImporter.Result result;
        try {
            result = MidiImporter.importFile(file, working.stepsPerBeat(), working.channelCount());
        } catch (IOException | RuntimeException ex) {
            Dialogs.showError("MIDI Import", "Could not import " + file.getName() + ": " + ex.getMessage());
            return;
        }
        applyEdit(pattern -> result.pattern());
        String message = "Imported " + result.mappedChannelCount() + " channel(s) at "
                + Math.round(result.detectedBpm()) + " BPM."
                + (result.skippedChannelCount() > 0
                        ? " " + result.skippedChannelCount() + " channel(s) beyond the first "
                                + working.channelCount() + " were skipped."
                        : "")
                + " Set this node's bpm input to " + Math.round(result.detectedBpm()) + " to match.";
        Dialogs.info("MIDI Import Complete", message);
    }
}
