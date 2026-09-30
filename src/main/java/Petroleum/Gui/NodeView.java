package Petroleum.Gui;

import Petroleum.Audio.AudioInputDevice;
import Petroleum.Effects.WaveshaperNode;
import Petroleum.Expression.ExpressionException;
import Petroleum.Expression.FormulaSource;
import Petroleum.Graph.Node;
import Petroleum.Graph.Port;
import Petroleum.Instruments.InstrumentNode;
import Petroleum.Modules.PatchNode;
import Petroleum.Oscillators.IOscillator;
import Petroleum.Oscillators.OscillatorFactory;
import Petroleum.Oscillators.OscillatorNode;
import Petroleum.Oscillators.WrapperOscillator;
import Petroleum.Oscillators.Implementations.DrawnWaveform;
import Petroleum.Oscillators.Implementations.Wavetable;
import Petroleum.Patch.Connection;
import Petroleum.Patch.Patch;
import Petroleum.Serialization.Macro;
import Petroleum.Sinks.VirtualLineOut;
import Petroleum.Sources.ConstantNode;
import Petroleum.Sources.MicInputNode;
import Petroleum.Sources.MidiInputNode;
import Petroleum.Sources.NoteSource;
import Petroleum.Tracker.MidiPlayerNode;
import Petroleum.Tracker.PianoRollNode;
import Petroleum.Tracker.TrackerNode;
import Petroleum.Utility.AnnotationSource;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.sampled.LineUnavailableException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

public final class NodeView extends Pane {
    public static final double MIN_WIDTH = 170;
    private static final double HEADER_HEIGHT = 24;
    private static final double ROW_HEIGHT = 20;
    private static final double PADDING = 8;
    private static final double SLIDER_HEIGHT = 50;
    private static final double CONTROL_ROW_HEIGHT = 26;
    private static final double COMBO_ROW_HEIGHT = 26;
    private static final double NOTE_ROW_HEIGHT = 26;
    private static final double EXPRESSION_EDITOR_MIN_HEIGHT = 70;
    private static final double ANNOTATION_EDITOR_MIN_HEIGHT = 100;
    private static final double HANDLE_SIZE = 12;
    private static final double DOCS_BUTTON_SIZE = 18;
    private static final double GRAPH_BUTTON_SIZE = 14;
    private static final double EDIT_BUTTON_WIDTH = 40;
    private static final String CONSTANT_KEY = "";

    private static final Color BORDER_COLOR = Theme.BORDER;
    private static final Color SELECTED_BORDER_COLOR = Theme.ACCENT;

    private final Node node;
    private final List<PortHandle> inputHandles = new ArrayList<>();
    private final List<PortHandle> outputHandles = new ArrayList<>();
    private final Map<String, KnobBinding> bindings = new LinkedHashMap<>();
    private boolean syncing;
    private final Rectangle fullBackground;
    private final WaveformView waveform = new WaveformView();
    private final float[] waveformScratch = new float[Patch.DEFAULT_BLOCK_CAPACITY];
    private final double minHeight;

    private boolean waveformEnabled = true;
    private int waveformSourceIndex = 0;
    private boolean waveformAutoRange = true;
    private float waveformRangeMin = -1f;
    private float waveformRangeMax = 1f;

    private final Label title;
    private final Tooltip docsTooltip;

    private double dragAnchorX;
    private double dragAnchorY;
    private double dragOriginX;
    private double dragOriginY;

    public NodeView(Node node, GraphEditor editor) {
        this.node = node;

        List<Port> inputs = node.getInputPorts();
        List<String> outputs = node.getOutputPorts();
        int rows = Math.max(Math.max(inputs.size(), outputs.size()), 1);
        boolean isConstant = node instanceof ConstantNode;
        boolean isTracker = node instanceof TrackerNode;
        boolean isPianoRoll = node instanceof PianoRollNode;
        boolean isInstrument = node instanceof InstrumentNode;
        boolean isMidiInput = node instanceof MidiInputNode;
        boolean isMidiPlayer = node instanceof MidiPlayerNode;
        boolean isMicInput = node instanceof MicInputNode;
        boolean isVirtualLineOut = node instanceof VirtualLineOut;
        boolean isPatchNode = node instanceof PatchNode;
        boolean isDrawnWaveform = drawnWaveformOf(node) != null;
        boolean isSoundClip = soundClipOf(node) != null;
        WrapperOscillator wrapperOscillator = wrapperOscillatorOf(node);
        FormulaSource formulaSource = formulaSourceOf(node);
        NoteSource noteSource = node instanceof NoteSource ns ? ns : null;
        AnnotationSource annotationSource = node instanceof AnnotationSource a ? a : null;

        double footerHeight = isConstant ? SLIDER_HEIGHT : inputs.size() * CONTROL_ROW_HEIGHT;
        if (wrapperOscillator != null) {
            footerHeight += COMBO_ROW_HEIGHT;
        }
        if (noteSource != null) {
            footerHeight += NOTE_ROW_HEIGHT;
        }
        if (formulaSource != null) {
            footerHeight += EXPRESSION_EDITOR_MIN_HEIGHT;
        }
        if (annotationSource != null) {
            footerHeight += ANNOTATION_EDITOR_MIN_HEIGHT;
        }

        this.minHeight = HEADER_HEIGHT + rows * ROW_HEIGHT + WaveformView.HEIGHT + 4 + PADDING + footerHeight;

        setPrefSize(MIN_WIDTH, minHeight);
        setMinSize(MIN_WIDTH, minHeight);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        fullBackground = new Rectangle(MIN_WIDTH, minHeight);
        fullBackground.setArcWidth(10);
        fullBackground.setArcHeight(10);
        fullBackground.setFill(Theme.NODE_BODY);
        fullBackground.setStroke(BORDER_COLOR);
        fullBackground.setMouseTransparent(true);
        fullBackground.widthProperty().bind(widthProperty());
        fullBackground.heightProperty().bind(heightProperty());
        getChildren().add(fullBackground);

        Rectangle headerBackground = new Rectangle(MIN_WIDTH, HEADER_HEIGHT);
        headerBackground.setArcWidth(10);
        headerBackground.setArcHeight(10);
        headerBackground.setFill(Theme.NODE_HEADER);
        headerBackground.setCursor(Cursor.OPEN_HAND);
        headerBackground.widthProperty().bind(widthProperty());
        getChildren().add(headerBackground);

        title = new Label(node.getName());
        title.setTextFill(Theme.TEXT_STRONG);
        title.setFont(Font.font(null, FontWeight.BOLD, Theme.FONT_BODY));
        title.setLayoutX(PADDING);
        title.setLayoutY(4);
        boolean showEditButton = isTracker || isPianoRoll || isDrawnWaveform || isSoundClip || isInstrument || isMidiInput
                || isMidiPlayer || isMicInput || isVirtualLineOut || isPatchNode;
        double headerButtonsWidth = DOCS_BUTTON_SIZE + (showEditButton ? EDIT_BUTTON_WIDTH + 4 : 0);
        title.prefWidthProperty().bind(widthProperty().subtract(PADDING * 2 + headerButtonsWidth + 4));
        title.setMouseTransparent(true);
        getChildren().add(title);

        String docsKey = Docs.keyFor(editor.getNodeFactory(), node);
        Button docsButton = new Button();
        docsButton.getStyleClass().add("icon-button");
        docsButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE));
        docsButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
        docsButton.setPrefSize(DOCS_BUTTON_SIZE, DOCS_BUTTON_SIZE);
        docsTooltip = new Tooltip("Open documentation for " + node.getName());
        docsButton.setTooltip(docsTooltip);
        Theme.icon(docsButton, "help", 12, docsTooltip.getText());
        docsButton.setOnAction(e -> editor.openDocumentation(this));
        getChildren().add(docsButton);

        Tooltip.install(headerBackground, new Tooltip(Docs.shortDescription(docsKey)));

        if (isTracker) {
            Button editButton = new Button("Edit");
            editButton.getStyleClass().add("icon-button");
            editButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            editButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            editButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            editButton.setTooltip(new Tooltip("Open the pattern editor for " + node.getName()));
            editButton.setOnAction(e -> editor.openTrackerEditor(this));
            getChildren().add(editButton);
        }

        if (isPianoRoll) {
            Button editButton = new Button("Edit");
            editButton.getStyleClass().add("icon-button");
            editButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            editButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            editButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            editButton.setTooltip(new Tooltip("Open the piano roll for " + node.getName()));
            editButton.setOnAction(e -> editor.openPianoRollEditor(this));
            getChildren().add(editButton);
        }

        if (isDrawnWaveform) {
            Button drawButton = new Button("Draw");
            drawButton.getStyleClass().add("icon-button");
            drawButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            drawButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            drawButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            drawButton.setTooltip(new Tooltip("Open the waveform drawing screen for " + node.getName()));
            drawButton.setOnAction(e -> editor.openWaveformEditor(this));
            getChildren().add(drawButton);
        }

        if (isSoundClip) {
            Button recordButton = new Button("Record");
            recordButton.getStyleClass().add("icon-button");
            recordButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            recordButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            recordButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            recordButton.setTooltip(new Tooltip("Open the record/edit screen for " + node.getName()));
            recordButton.setOnAction(e -> editor.openSoundClipEditor(this));
            getChildren().add(recordButton);
        }

        if (isInstrument) {
            Button editButton = new Button("Edit");
            editButton.getStyleClass().add("icon-button");
            editButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            editButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            editButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            editButton.setTooltip(new Tooltip("Open the instrument editor for " + node.getName()));
            editButton.setOnAction(e -> editor.openInstrumentEditor(this));
            getChildren().add(editButton);
        }

        if (isPatchNode) {
            Button editButton = new Button("Edit");
            editButton.getStyleClass().add("icon-button");
            editButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            editButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            editButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            editButton.setTooltip(new Tooltip("Open the subpatch editor for " + node.getName()));
            editButton.setOnAction(e -> editor.openSubpatchEditor(this));
            getChildren().add(editButton);
        }

        if (isMidiInput) {
            MidiInputNode midiInputNode = (MidiInputNode) node;
            Button midiButton = new Button("MIDI...");
            midiButton.getStyleClass().add("icon-button");
            midiButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            midiButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            midiButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            midiButton.setTooltip(new Tooltip("Choose the MIDI input device and channel for " + node.getName()));
            midiButton.setOnAction(e -> {
                List<String> devices = MidiInputNode.listAvailableDeviceNames();
                if (devices.isEmpty()) {
                    Dialogs.showError("MIDI Input", "No MIDI input devices were found.");
                    return;
                }
                Dialogs.chooseFromList("MIDI Input", "Choose a MIDI input device:",
                        devices, midiInputNode.getDeviceName()).ifPresent(name -> {
                    try {
                        midiInputNode.connect(name);
                    } catch (MidiUnavailableException ex) {
                        Dialogs.showError("MIDI Input", "Could not open " + name + ": " + ex.getMessage());
                    }
                    Dialogs.chooseFromList("MIDI Input", "Choose a MIDI channel:",
                            MidiInputNode.channelNames(), midiInputNode.getChannelName())
                            .ifPresent(midiInputNode::setChannelName);
                });
            });
            getChildren().add(midiButton);
        }

        if (isMidiPlayer) {
            MidiPlayerNode midiPlayerNode = (MidiPlayerNode) node;
            Button loadButton = new Button("Load");
            loadButton.getStyleClass().add("icon-button");
            loadButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            loadButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            loadButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            Tooltip loadTooltip = new Tooltip(midiPlayerTooltip(midiPlayerNode));
            loadButton.setTooltip(loadTooltip);
            loadButton.setOnAction(e -> {
                javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
                chooser.setTitle("Load MIDI file");
                chooser.getExtensionFilters().add(
                        new javafx.stage.FileChooser.ExtensionFilter("MIDI files", "*.mid", "*.midi"));
                java.io.File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
                if (file == null) {
                    return;
                }
                MidiPlayerNode.LoadResult result;
                try {
                    result = midiPlayerNode.loadMidi(java.nio.file.Files.readAllBytes(file.toPath()), file.getName());
                } catch (java.io.IOException | IllegalArgumentException ex) {
                    Dialogs.showError("MIDI Player", "Could not load " + file.getName() + ": " + ex.getMessage());
                    return;
                }
                editor.markDirty();
                loadTooltip.setText(midiPlayerTooltip(midiPlayerNode));
                String summary = String.format("Loaded %s: %d notes, %.1f seconds.", file.getName(),
                        result.noteCount(), result.lengthSeconds());
                if (result.stolenNotes() > 0) {
                    summary += "\n\n" + result.stolenNotes() + " notes were cut short because more than "
                            + midiPlayerNode.getChannelCount() + " notes sounded at once. "
                            + "Use a MIDI Player with more voices to hear every note in full.";
                }
                Dialogs.info("MIDI Player", summary);
            });
            getChildren().add(loadButton);
        }

        if (isMicInput) {
            MicInputNode micInputNode = (MicInputNode) node;
            Button micButton = new Button("Mic...");
            micButton.getStyleClass().add("icon-button");
            micButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            micButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            micButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            micButton.setTooltip(new Tooltip("Choose the microphone input device for " + node.getName()));
            micButton.setOnAction(e -> {
                List<String> devices = MicInputNode.listAvailableDeviceNames();
                if (devices.isEmpty()) {
                    Dialogs.showError("Mic Input", "No audio input devices were found.");
                    return;
                }
                String current = micInputNode.getDeviceName() == null
                        ? AudioInputDevice.getDefaultDeviceName()
                        : micInputNode.getDeviceName();
                Dialogs.chooseFromList("Mic Input", "Choose an audio input device:",
                        devices, current).ifPresent(name -> {
                    try {
                        micInputNode.connect(name);
                    } catch (LineUnavailableException ex) {
                        Dialogs.showError("Mic Input", "Could not open " + name + ": " + ex.getMessage());
                    }
                });
            });
            getChildren().add(micButton);
        }

        if (isVirtualLineOut) {
            VirtualLineOut virtualLineOut = (VirtualLineOut) node;
            Button lineOutButton = new Button("Line...");
            lineOutButton.getStyleClass().add("icon-button");
            lineOutButton.layoutXProperty().bind(widthProperty().subtract(PADDING + DOCS_BUTTON_SIZE + 4 + EDIT_BUTTON_WIDTH));
            lineOutButton.setLayoutY((HEADER_HEIGHT - DOCS_BUTTON_SIZE) / 2);
            lineOutButton.setPrefSize(EDIT_BUTTON_WIDTH, DOCS_BUTTON_SIZE);
            lineOutButton.setTooltip(new Tooltip("Choose the virtual audio device for " + node.getName()));
            lineOutButton.setOnAction(e -> {
                List<String> mixers = VirtualLineOut.listAvailableMixerNames();
                if (mixers.isEmpty()) {
                    Dialogs.showError("Virtual Line Out", "No virtual audio devices were found.");
                    return;
                }
                Dialogs.chooseFromList("Virtual Line Out", "Choose a virtual audio device:",
                        mixers, virtualLineOut.getMixerName()).ifPresent(virtualLineOut::setMixerName);
            });
            getChildren().add(lineOutButton);
        }

        for (int i = 0; i < inputs.size(); i++) {
            double y = HEADER_HEIGHT + i * ROW_HEIGHT + ROW_HEIGHT / 2.0;
            String portName = inputs.get(i).name();
            PortHandle handle = new PortHandle(this, portName, false);
            handle.setCenterX(0);
            handle.setCenterY(y);

            Label label = new Label(portName);
            label.setTextFill(Theme.TEXT);
            label.setFont(Font.font(Theme.FONT_SMALL));
            label.setLayoutX(9);
            label.setLayoutY(y - 8);
            label.setMouseTransparent(true);

            installWireTarget(handle, editor);
            getChildren().addAll(label, handle);
            inputHandles.add(handle);
        }

        for (int j = 0; j < outputs.size(); j++) {
            double y = HEADER_HEIGHT + j * ROW_HEIGHT + ROW_HEIGHT / 2.0;
            String portName = outputs.get(j);
            PortHandle handle = new PortHandle(this, portName, true);
            handle.centerXProperty().bind(widthProperty());
            handle.setCenterY(y);
            handle.setCursor(Cursor.CROSSHAIR);

            Label label = new Label(portName);
            label.setTextFill(Theme.TEXT);
            label.setFont(Font.font(Theme.FONT_SMALL));
            label.layoutXProperty().bind(widthProperty().subtract(60));
            label.setLayoutY(y - 8);
            label.setMouseTransparent(true);

            getChildren().addAll(label, handle);
            outputHandles.add(handle);
            installWireSource(handle, editor);
        }

        double footerY = HEADER_HEIGHT + rows * ROW_HEIGHT + 4;

        waveform.setLayoutX(PADDING);
        waveform.setLayoutY(footerY);
        waveform.widthProperty().bind(widthProperty().subtract(PADDING * 2));
        waveform.setMouseTransparent(true);
        getChildren().add(waveform);

        double waveformTopY = footerY;
        Button graphSettingsButton = new Button();
        graphSettingsButton.getStyleClass().add("icon-button");
        graphSettingsButton.layoutXProperty().bind(widthProperty().subtract(PADDING + GRAPH_BUTTON_SIZE));
        graphSettingsButton.setLayoutY(waveformTopY + 2);
        graphSettingsButton.setPrefSize(GRAPH_BUTTON_SIZE, GRAPH_BUTTON_SIZE);
        Theme.icon(graphSettingsButton, "settings", 10, "Graph settings");
        graphSettingsButton.setOnAction(e -> openWaveformSettings());
        getChildren().add(graphSettingsButton);

        footerY += WaveformView.HEIGHT + 4;

        if (wrapperOscillator != null) {
            footerY = buildBaseCombo(footerY, wrapperOscillator, editor.getOscillatorFactory());
        }

        if (isConstant) {
            buildConstantControl(footerY, editor, (ConstantNode) node);
        } else {
            for (Port port : inputs) {
                footerY = buildPortControl(footerY, editor, port);
            }
        }

        if (noteSource != null) {
            footerY = buildNoteControl(footerY, noteSource);
        }

        if (formulaSource != null) {
            buildFormulaControl(footerY, formulaSource);
        }

        if (annotationSource != null) {
            buildAnnotationControl(footerY, annotationSource);
        }

        Rectangle resizeHandle = new Rectangle(HANDLE_SIZE, HANDLE_SIZE);
        resizeHandle.setFill(Theme.TEXT_FAINT);
        resizeHandle.setCursor(Cursor.SE_RESIZE);
        resizeHandle.layoutXProperty().bind(widthProperty().subtract(HANDLE_SIZE));
        resizeHandle.layoutYProperty().bind(heightProperty().subtract(HANDLE_SIZE));
        getChildren().add(resizeHandle);
        installResize(resizeHandle, editor);

        installDrag(headerBackground, editor);
        installContextMenu(headerBackground, editor);

        setOnMousePressed(event -> editor.selectNode(this));
    }

    public Node getNode() {
        return node;
    }

    private void refreshTitle() {
        title.setText(node.getName());
        docsTooltip.setText("Open documentation for " + node.getName());
    }

    public List<PortHandle> getInputHandles() {
        return inputHandles;
    }

    public void setSelected(boolean selected) {
        fullBackground.setStroke(selected ? SELECTED_BORDER_COLOR : BORDER_COLOR);
        fullBackground.setStrokeWidth(selected ? 2 : 1);
    }

    public void refreshPortControlStates(Map<InputKey, Connection> connectionsByPort) {
        for (KnobBinding binding : bindings.values()) {
            if (binding.portName == null || binding.externallyDriven) {
                continue;
            }
            Connection current = connectionsByPort.get(new InputKey(node, binding.portName));
            if (current == null || current.source() == binding.shadow) {
                continue;
            }
            binding.externallyDriven = true;
            binding.row.setDisable(true);
            if (current.source() instanceof ConstantNode driver) {
                binding.knob = driver;
                binding.materialized = true;
            }
        }
    }

    Map<String, Float> getShadowValues() {
        Map<String, Float> values = new LinkedHashMap<>();
        for (Map.Entry<String, KnobBinding> entry : bindings.entrySet()) {
            KnobBinding binding = entry.getValue();
            if (binding.portName == null || binding.materialized) {
                continue;
            }
            values.put(entry.getKey(), binding.shadow.getValue());
        }
        return values;
    }

    void applyShadowValues(Map<String, Float> values) {
        for (Map.Entry<String, Float> entry : values.entrySet()) {
            KnobBinding binding = bindings.get(entry.getKey());
            if (binding == null || binding.portName == null) {
                continue;
            }
            float value = entry.getValue();
            binding.shadow.setValue(value);
            Slider slider = binding.field.slider();
            if (value < slider.getMin()) {
                slider.setMin(value);
            }
            if (value > slider.getMax()) {
                slider.setMax(value);
            }
            slider.setValue(value);
        }
    }

    double portRowOffset(String portName) {
        List<Port> ports = node.getInputPorts();
        for (int i = 0; i < ports.size(); i++) {
            if (ports.get(i).name().equals(portName)) {
                return HEADER_HEIGHT + i * ROW_HEIGHT;
            }
        }
        return 0;
    }

    PortHandle findInputHandle(String portName) {
        for (PortHandle handle : inputHandles) {
            if (handle.getPortName().equals(portName)) {
                return handle;
            }
        }
        return null;
    }

    PortHandle findOutputHandle(String portName) {
        for (PortHandle handle : outputHandles) {
            if (handle.getPortName().equals(portName)) {
                return handle;
            }
        }
        return null;
    }

    void restoreShadowConnection(GraphModel model, String portName) {
        KnobBinding binding = bindings.get(portName);
        if (binding != null) {
            model.addConnection(new Connection(binding.shadow, "out", node, portName));
            binding.knob = binding.shadow;
            binding.materialized = false;
            binding.externallyDriven = false;
            binding.row.setDisable(false);
        }
    }

    void disposeShadowNodes(GraphModel model) {
        for (KnobBinding binding : bindings.values()) {
            if (binding.portName == null) {
                continue;
            }
            if (!(binding.materialized && binding.shadow == binding.knob)) {
                model.removeNode(binding.shadow);
            }
        }
    }

    List<ConstantNode> ownedKnobs() {
        List<ConstantNode> owned = new ArrayList<>();
        for (KnobBinding binding : bindings.values()) {
            owned.add(binding.knob);
        }
        return owned;
    }

    void refreshWaveform(Patch patch) {
        if (!waveformEnabled) {
            return;
        }
        int copied = node.getOutputPorts().isEmpty()
                ? patch.copyInputBlock(node, 0, waveformScratch)
                : patch.copyOutputBlock(node, waveformSourceIndex, waveformScratch);
        if (copied > 0) {
            waveform.render(waveformScratch, copied);
        }
    }

    private List<String> waveformSources() {
        List<String> outputs = node.getOutputPorts();
        return outputs.isEmpty() ? List.of("in0") : outputs;
    }

    private void openWaveformSettings() {
        WaveformSettingsDialog.Result current = new WaveformSettingsDialog.Result(
                waveformEnabled, waveformSourceIndex, waveformAutoRange, waveformRangeMin, waveformRangeMax);
        WaveformSettingsDialog.show(node.getName(), waveformSources(), current).ifPresent(result -> {
            waveformEnabled = result.enabled();
            waveformSourceIndex = Math.max(0, Math.min(waveformSources().size() - 1, result.sourceIndex()));
            waveformAutoRange = result.autoRange();
            waveformRangeMin = result.min();
            waveformRangeMax = result.max();
            waveform.setEnabled(waveformEnabled);
            waveform.setRange(waveformAutoRange, waveformRangeMin, waveformRangeMax);
        });
    }

    private double buildBaseCombo(double y, WrapperOscillator wrapperOscillator, OscillatorFactory oscillatorFactory) {
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(oscillatorFactory.names()));
        combo.setLayoutX(PADDING);
        combo.setLayoutY(y);
        combo.prefWidthProperty().bind(widthProperty().subtract(PADDING * 2));
        combo.setValue(currentBaseName(wrapperOscillator));
        combo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                return;
            }
            IOscillator newBase = oscillatorFactory.create(newV);
            wrapperOscillator.setBase(newBase);
            refreshTitle();
        });

        getChildren().add(combo);
        return y + COMBO_ROW_HEIGHT;
    }

    private record SliderField(TextField field, Slider slider) {
    }

    private static final class KnobBinding {
        final String portName;
        final ConstantNode shadow;
        final SliderField field;
        final HBox row;
        final Button macroButton;
        ConstantNode knob;
        boolean materialized;
        boolean externallyDriven;

        KnobBinding(String portName, ConstantNode shadow, SliderField field, HBox row, Button macroButton,
                    boolean materialized) {
            this.portName = portName;
            this.shadow = shadow;
            this.field = field;
            this.row = row;
            this.macroButton = macroButton;
            this.knob = shadow;
            this.materialized = materialized;
        }
    }

    private SliderField buildSliderField(GraphEditor editor, float initial, double min, double max,
                                                  DoubleConsumer setter, DoubleSupplier currentValue) {
        TextField field = new TextField(String.format("%.3f", initial));
        Slider slider = new Slider(min, max, initial);

        slider.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncing) {
                return;
            }
            float value = newV.floatValue();
            setter.accept(value);
            field.setText(String.format("%.3f", value));
            editor.notifyKnobChanged();
        });

        field.setOnAction(e -> {
            try {
                float value = Float.parseFloat(field.getText().trim());
                setter.accept(value);
                if (value < slider.getMin()) {
                    slider.setMin(value);
                }
                if (value > slider.getMax()) {
                    slider.setMax(value);
                }
                slider.setValue(value);
                editor.notifyKnobChanged();
            } catch (NumberFormatException ex) {
                field.setText(String.format("%.3f", currentValue.getAsDouble()));
            }
        });

        return new SliderField(field, slider);
    }

    private void buildConstantControl(double y, GraphEditor editor, ConstantNode constant) {
        SliderField sf = buildSliderField(editor, constant.getValue(), -2000, 2000,
                value -> constant.setValue((float) value), constant::getValue);

        Button macroButton = buildMacroButton(editor, CONSTANT_KEY, "Constant");
        bindings.put(CONSTANT_KEY, new KnobBinding(null, constant, sf, null, macroButton, true));

        HBox top = new HBox(4, sf.field(), macroButton);
        HBox.setHgrow(sf.field(), Priority.ALWAYS);
        sf.field().setMaxWidth(Double.MAX_VALUE);
        top.setLayoutX(PADDING);
        top.setLayoutY(y);
        top.prefWidthProperty().bind(widthProperty().subtract(PADDING * 2));

        sf.slider().setLayoutX(PADDING);
        sf.slider().setLayoutY(y + 26);
        sf.slider().prefWidthProperty().bind(widthProperty().subtract(PADDING * 2));

        getChildren().addAll(top, sf.slider());
    }

    private Button buildMacroButton(GraphEditor editor, String key, String suggestedName) {
        Button button = new Button("+");
        button.setFont(Font.font(Theme.FONT_SMALL));
        button.setMinWidth(30);
        button.setPrefWidth(30);
        button.setOnAction(e -> onMacroButton(editor, key, suggestedName));
        return button;
    }

    private void onMacroButton(GraphEditor editor, String key, String suggestedName) {
        KnobBinding binding = bindings.get(key);
        Macro macro = editor.findMacro(binding.knob);
        if (macro != null) {
            showMacroMenu(editor, macro, binding);
            return;
        }
        TextInputDialog dialog = new TextInputDialog(suggestedName);
        dialog.setTitle("Expose as a macro");
        dialog.setHeaderText("This knob becomes a named slider on the quick screen.");
        dialog.setContentText("Macro name:");
        dialog.showAndWait().ifPresent(name -> {
            ConstantNode knob = binding.materialized ? binding.knob
                    : editor.materializeKnob(this, binding.portName, binding.shadow);
            if (knob == null) {
                return;
            }
            try {
                editor.promoteToMacro(knob, name, (float) binding.field.slider().getMin(),
                        (float) binding.field.slider().getMax());
                binding.knob = knob;
                binding.materialized = true;
                if (binding.row != null) {
                    binding.row.setDisable(true);
                }
            } catch (IllegalArgumentException ex) {
                Dialogs.showError("Could not create the macro", ex.getMessage());
            }
        });
        refreshMacroButtons(editor);
    }

    private void showMacroMenu(GraphEditor editor, Macro macro, KnobBinding binding) {
        Button button = binding.macroButton;
        ContextMenu menu = new ContextMenu();

        MenuItem rename = new MenuItem("Rename...");
        rename.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog(macro.name());
            dialog.setTitle("Rename macro");
            dialog.setContentText("Macro name:");
            dialog.showAndWait().ifPresent(name -> {
                try {
                    editor.renameMacro(macro, name);
                } catch (IllegalArgumentException ex) {
                    Dialogs.showError("Could not rename the macro", ex.getMessage());
                }
            });
        });

        MenuItem range = new MenuItem("Set range...");
        range.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog(macro.min() + ", " + macro.max());
            dialog.setTitle("Macro range");
            dialog.setHeaderText("Lowest and highest value, separated by a comma.");
            dialog.setContentText("Range:");
            dialog.showAndWait().ifPresent(text -> {
                String[] parts = text.split(",");
                try {
                    editor.setMacroRange(macro, Float.parseFloat(parts[0].trim()),
                            Float.parseFloat(parts[1].trim()));
                } catch (RuntimeException ex) {
                    Dialogs.showError("Could not set the range", "Enter two numbers, for example: 0, 1");
                }
            });
        });

        MenuItem remove = new MenuItem("Remove macro");
        remove.setOnAction(e -> editor.removeMacro(macro));

        menu.getItems().addAll(rename, range, remove);
        if (button != null) {
            menu.show(button, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    void refreshMacroButtons(GraphEditor editor) {
        for (KnobBinding binding : bindings.values()) {
            Macro macro = editor.findMacro(binding.knob);
            Button button = binding.macroButton;
            if (macro == null) {
                button.setText("+");
                button.setTooltip(new Tooltip("Expose as a macro"));
                button.getStyleClass().remove("macro-active");
            } else {
                button.setText(macro.name());
                button.setTooltip(new Tooltip("Macro \"" + macro.name() + "\" - click to rename, re-range or remove"));
                if (!button.getStyleClass().contains("macro-active")) {
                    button.getStyleClass().add("macro-active");
                }
            }
        }
    }

    void syncControlsFromNodes() {
        syncing = true;
        try {
            for (KnobBinding binding : bindings.values()) {
                applyValue(binding.field, binding.knob.getValue());
            }
        } finally {
            syncing = false;
        }
    }

    private static void applyValue(SliderField control, float value) {
        if (value < control.slider().getMin()) {
            control.slider().setMin(value);
        }
        if (value > control.slider().getMax()) {
            control.slider().setMax(value);
        }
        control.slider().setValue(value);
        control.field().setText(String.format("%.3f", value));
    }

    private double buildPortControl(double y, GraphEditor editor, Port port) {
        ConstantNode shadow = new ConstantNode(port.defaultValue());
        editor.getModel().addNode(shadow);
        editor.getModel().addConnection(new Connection(shadow, "out", node, port.name()));

        float def = port.defaultValue();
        float magnitude = Math.max(Math.abs(def), 1f) * 4f;

        SliderField sf = buildSliderField(editor, def, -magnitude, magnitude,
                value -> shadow.setValue((float) value), shadow::getValue);

        sf.field().setPrefWidth(50);
        sf.slider().setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sf.slider(), Priority.ALWAYS);

        HBox row = new HBox(4, sf.slider(), sf.field());
        row.setLayoutX(PADDING);
        row.setLayoutY(y);
        row.prefWidthProperty().bind(widthProperty().subtract(PADDING * 2));

        getChildren().add(row);
        Button macroButton = buildMacroButton(editor, port.name(), node.getName() + " " + port.name());
        row.getChildren().add(macroButton);
        bindings.put(port.name(), new KnobBinding(port.name(), shadow, sf, row, macroButton, false));

        return y + CONTROL_ROW_HEIGHT;
    }

    private double buildNoteControl(double y, NoteSource noteSource) {
        TextField field = new TextField(noteSource.getNoteText());
        field.setLayoutX(PADDING);
        field.setLayoutY(y);
        field.prefWidthProperty().bind(widthProperty().subtract(PADDING * 2));
        field.setPromptText("A4, C#3, Bb5+25c...");

        field.textProperty().addListener((obs, oldText, newText) -> {
            try {
                noteSource.setNoteText(newText);
                field.getStyleClass().remove("invalid");
                field.setTooltip(null);
                refreshTitle();
            } catch (IllegalArgumentException ex) {
                if (!field.getStyleClass().contains("invalid")) {
                    field.getStyleClass().add("invalid");
                }
                field.setTooltip(new Tooltip(ex.getMessage()));
            }
        });

        getChildren().add(field);
        return y + NOTE_ROW_HEIGHT;
    }

    private void buildFormulaControl(double y, FormulaSource formula) {
        TextArea area = new TextArea(formula.getSource());
        area.setWrapText(true);
        area.setLayoutX(PADDING);
        area.setLayoutY(y);
        area.prefWidthProperty().bind(widthProperty().subtract(PADDING * 2));
        area.prefHeightProperty().bind(heightProperty().subtract(y).subtract(PADDING));

        area.textProperty().addListener((obs, oldText, newText) -> {
            try {
                formula.setSource(newText);
                area.getStyleClass().remove("invalid");
                area.setTooltip(null);
                refreshTitle();
            } catch (ExpressionException ex) {
                if (!area.getStyleClass().contains("invalid")) {
                    area.getStyleClass().add("invalid");
                }
                area.setTooltip(new Tooltip(ex.getMessage()));
            }
        });

        getChildren().add(area);
    }

    private void buildAnnotationControl(double y, AnnotationSource annotation) {
        TextArea area = new TextArea(annotation.getText());
        area.setWrapText(true);
        area.setPromptText("Write documentation for this patch here...");
        area.setLayoutX(PADDING);
        area.setLayoutY(y);
        area.prefWidthProperty().bind(widthProperty().subtract(PADDING * 2));
        area.prefHeightProperty().bind(heightProperty().subtract(y).subtract(PADDING));

        area.textProperty().addListener((obs, oldText, newText) -> annotation.setText(newText));

        getChildren().add(area);
    }

    private static WrapperOscillator wrapperOscillatorOf(Node node) {
        if (node instanceof OscillatorNode oscNode
                && oscNode.getOscillator() instanceof WrapperOscillator wrapper) {
            return wrapper;
        }
        return null;
    }

    private static DrawnWaveform drawnWaveformOf(Node node) {
        if (node instanceof OscillatorNode oscNode
                && oscNode.getOscillator() instanceof DrawnWaveform drawn) {
            return drawn;
        }
        return null;
    }

    private static String midiPlayerTooltip(MidiPlayerNode node) {
        String file = node.getFileName();
        return file == null ? "Load a MIDI file into " + node.getName()
                : "Playing " + file + " - click to load a different MIDI file";
    }

    private static Wavetable soundClipOf(Node node) {
        if (node instanceof OscillatorNode oscNode
                && oscNode.getOscillator() instanceof Wavetable wavetable) {
            return wavetable;
        }
        return null;
    }

    private static FormulaSource formulaSourceOf(Node node) {
        if (node instanceof OscillatorNode oscNode
                && oscNode.getOscillator() instanceof FormulaSource formula) {
            return formula;
        }
        if (node instanceof FormulaSource formula) {
            return formula;
        }
        return null;
    }

    private static String currentBaseName(WrapperOscillator wrapperOscillator) {
        return wrapperOscillator.getBase().getName();
    }

    private void installDrag(Rectangle handle, GraphEditor editor) {
        handle.setOnMousePressed(event -> {
            editor.selectNode(this);
            toFront();
            Point2D canvasPoint = getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
            dragAnchorX = canvasPoint.getX() - getLayoutX();
            dragAnchorY = canvasPoint.getY() - getLayoutY();
            dragOriginX = getLayoutX();
            dragOriginY = getLayoutY();
            event.consume();
        });
        handle.setOnMouseDragged(event -> {
            Point2D canvasPoint = getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
            setLayoutX(canvasPoint.getX() - dragAnchorX);
            setLayoutY(canvasPoint.getY() - dragAnchorY);
            editor.onNodeMoved(this);
            event.consume();
        });
        handle.setOnMouseReleased(event -> {
            if (getLayoutX() != dragOriginX || getLayoutY() != dragOriginY) {
                editor.recordMove(this, dragOriginX, dragOriginY, getLayoutX(), getLayoutY());
            }
            event.consume();
        });
    }

    private void installResize(Rectangle handle, GraphEditor editor) {
        double[] start = new double[4];
        handle.setOnMousePressed(event -> {
            editor.selectNode(this);
            toFront();
            start[0] = getWidth();
            start[1] = getHeight();
            Point2D startPoint = getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
            start[2] = startPoint.getX();
            start[3] = startPoint.getY();
            event.consume();
        });
        handle.setOnMouseDragged(event -> {
            Point2D point = getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
            double newWidth = Math.max(MIN_WIDTH, start[0] + (point.getX() - start[2]));
            double newHeight = Math.max(minHeight, start[1] + (point.getY() - start[3]));
            resize(newWidth, newHeight);
            setPrefSize(newWidth, newHeight);
            editor.onNodeMoved(this);
            event.consume();
        });
    }

    private void installContextMenu(Rectangle handle, GraphEditor editor) {
        ContextMenu menu = new ContextMenu();
        if (node instanceof TrackerNode trackerNode) {
            menu.getItems().add(convertChannelsMenu(trackerNode.getChannelCount(),
                    count -> editor.convertTrackerChannels(this, count)));
        } else if (node instanceof PianoRollNode pianoRollNode) {
            menu.getItems().add(convertChannelsMenu(pianoRollNode.getChannelCount(),
                    count -> editor.convertPianoRollVoices(this, count)));
        } else if (node instanceof InstrumentNode instrumentNode) {
            menu.getItems().add(convertChannelsMenu(instrumentNode.getChannelCount(),
                    count -> editor.convertInstrumentChannels(this, count)));
        }
        MenuItem delete = new MenuItem("Delete Node");
        delete.setOnAction(e -> editor.removeNode(this));
        menu.getItems().add(delete);
        handle.setOnContextMenuRequested(e -> {
            menu.show(handle, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private static Menu convertChannelsMenu(int currentCount, IntConsumer onConvert) {
        Menu convert = new Menu("Convert channels");
        for (int count : new int[]{2, 4, 8, 16}) {
            MenuItem item = new MenuItem(count + " channels");
            item.setDisable(count == currentCount);
            item.setOnAction(e -> onConvert.accept(count));
            convert.getItems().add(item);
        }
        return convert;
    }

    private void installWireTarget(PortHandle handle, GraphEditor editor) {
        Tooltip.install(handle, new Tooltip("Right-click to delete the connection into " + handle.getPortName()));
        handle.setOnContextMenuRequested(event -> {
            if (editor.removeWireInto(handle)) {
                event.consume();
            }
        });
    }

    private void installWireSource(PortHandle handle, GraphEditor editor) {
        handle.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            editor.beginWiring(handle);
            event.consume();
        });
        handle.setOnMouseDragged(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            editor.updateWiring(event.getSceneX(), event.getSceneY());
            event.consume();
        });
        handle.setOnMouseReleased(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            editor.finishWiring(event);
            event.consume();
        });
    }
}
