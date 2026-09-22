package Petroleum.Gui;

import Petroleum.Instruments.Instrument;
import Petroleum.Instruments.InstrumentNode;
import Petroleum.Oscillators.Implementations.WavetableLoader;
import Petroleum.Sources.NoteName;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class InstrumentEditorView {
    private static final double CANVAS_WIDTH = 700;
    private static final double CANVAS_HEIGHT = 220;
    private static final double POINT_RADIUS = 5;
    private static final double HIT_RADIUS = 9;

    private final BorderPane root = new BorderPane();
    private final TableView<Instrument.SampleZone> table = new TableView<>();
    private final Canvas envelopeCanvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
    private final Label envelopeHint = new Label(
            "Click empty space to add a point, drag a point to move it, "
                    + "double-click to set/unset the sustain point, right-click to delete.");

    private InstrumentNode node;
    private Instrument working = new Instrument();
    private int draggingPointIndex = -1;

    InstrumentEditorView() {
        table.setEditable(true);
        table.getColumns().addAll(List.of(nameColumn(), rootColumn(), minColumn(), maxColumn(), loopColumn()));
        table.setPlaceholder(new Label("No samples yet - use \"Import Sample...\" below."));
        table.setPrefHeight(220);

        Button importButton = new Button("Import Sample...");
        importButton.setOnAction(e -> importSample());
        Button removeButton = new Button("Remove Zone");
        removeButton.setOnAction(e -> removeSelectedZone());
        HBox zoneButtons = new HBox(10, importButton, removeButton);
        zoneButtons.setPadding(new Insets(10, 0, 10, 0));

        VBox zonesBox = new VBox(4, new Label("Sample zones"), table, zoneButtons);
        zonesBox.setPadding(new Insets(10));

        envelopeCanvas.setOnMousePressed(this::onEnvelopePressed);
        envelopeCanvas.setOnMouseDragged(this::onEnvelopeDragged);
        envelopeCanvas.setOnMouseReleased(e -> draggingPointIndex = -1);
        envelopeCanvas.setOnMouseClicked(this::onEnvelopeClicked);

        VBox envelopeBox = new VBox(4, new Label("Volume envelope"), envelopeCanvas, envelopeHint);
        envelopeBox.setPadding(new Insets(10));

        ScrollPane scroll = new ScrollPane(new VBox(zonesBox, envelopeBox));
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
    }

    BorderPane getRoot() {
        return root;
    }

    void open(InstrumentNode node) {
        this.node = node;
        this.working = node.getInstrument();
        refreshZoneTable();
        redrawEnvelope();
    }

    void close() {
        pushToNode();
        node = null;
    }

    private void pushToNode() {
        if (node != null) {
            node.setInstrument(working);
        }
    }

    private void refreshZoneTable() {
        table.setItems(FXCollections.observableArrayList(working.zones()));
    }

    private TableColumn<Instrument.SampleZone, String> nameColumn() {
        TableColumn<Instrument.SampleZone, String> column = new TableColumn<>("Name");
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        column.setCellFactory(TextFieldTableCell.forTableColumn());
        column.setOnEditCommit(event -> {
            event.getRowValue().name = event.getNewValue();
            pushToNode();
        });
        column.setPrefWidth(160);
        return column;
    }

    private TableColumn<Instrument.SampleZone, String> rootColumn() {
        return pitchColumn("Root", zone -> zone.rootHz, (zone, hz) -> zone.rootHz = hz);
    }

    private TableColumn<Instrument.SampleZone, String> minColumn() {
        return pitchColumn("Key range low", zone -> zone.minHz, (zone, hz) -> zone.minHz = hz);
    }

    private TableColumn<Instrument.SampleZone, String> maxColumn() {
        return pitchColumn("Key range high", zone -> zone.maxHz, (zone, hz) -> zone.maxHz = hz);
    }

    private interface PitchGetter {
        float get(Instrument.SampleZone zone);
    }

    private interface PitchSetter {
        void set(Instrument.SampleZone zone, float hz);
    }

    private TableColumn<Instrument.SampleZone, String> pitchColumn(String title, PitchGetter getter, PitchSetter setter) {
        TableColumn<Instrument.SampleZone, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(NoteName.format(hzToSemitone(getter.get(data.getValue())))));
        column.setCellFactory(TextFieldTableCell.forTableColumn());
        column.setOnEditCommit(event -> {
            try {
                float hz = semitoneToHz(NoteName.parseFlexible(event.getNewValue()));
                setter.set(event.getRowValue(), hz);
                pushToNode();
            } catch (IllegalArgumentException ex) {
                Dialogs.showError("Invalid pitch", ex.getMessage());
            } finally {
                table.refresh();
            }
        });
        column.setPrefWidth(130);
        return column;
    }

    private TableColumn<Instrument.SampleZone, Boolean> loopColumn() {
        TableColumn<Instrument.SampleZone, Boolean> column = new TableColumn<>("Loop");
        column.setCellValueFactory(data -> {
            Instrument.SampleZone zone = data.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(zone.loopEnabled);
            property.addListener((obs, was, is) -> {
                zone.loopEnabled = is;
                zone.loopStartFrame = 0;
                zone.loopEndFrame = zone.samples.length;
                pushToNode();
            });
            return property;
        });
        column.setCellFactory(CheckBoxTableCell.forTableColumn(column));
        column.setEditable(true);
        column.setPrefWidth(60);
        return column;
    }

    private void importSample() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import WAV file for this instrument");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        File file = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            float[] samples = WavetableLoader.loadMonoSamples(file);
            Instrument.SampleZone zone = new Instrument.SampleZone(
                    Dialogs.stripExtension(file.getName()), samples, 440f, 20f, 20000f);
            working.zones().add(zone);
            refreshZoneTable();
            pushToNode();
        } catch (IOException | UnsupportedAudioFileException ex) {
            Dialogs.showError("Could not import " + file.getName(), ex.getMessage());
        }
    }

    private void removeSelectedZone() {
        Instrument.SampleZone selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        working.zones().remove(selected);
        refreshZoneTable();
        pushToNode();
    }

    private static float hzToSemitone(float hz) {
        return (float) (69.0 + 12.0 * (Math.log(hz / 440.0) / Math.log(2.0)));
    }

    private static float semitoneToHz(float semitone) {
        return (float) (440.0 * Math.pow(2.0, (semitone - 69) / 12.0));
    }

    private double maxEnvelopeTime() {
        List<Instrument.EnvelopePoint> points = working.envelope();
        float lastTime = points.get(points.size() - 1).timeSeconds();
        return Math.max(0.1, lastTime * 1.3);
    }

    private double timeToX(float timeSeconds) {
        return timeSeconds / maxEnvelopeTime() * CANVAS_WIDTH;
    }

    private double levelToY(float level) {
        return CANVAS_HEIGHT - level * (CANVAS_HEIGHT - POINT_RADIUS * 2) - POINT_RADIUS;
    }

    private float xToTime(double x) {
        return (float) Math.max(0, x / CANVAS_WIDTH * maxEnvelopeTime());
    }

    private float yToLevel(double y) {
        double level = (CANVAS_HEIGHT - POINT_RADIUS - y) / (CANVAS_HEIGHT - POINT_RADIUS * 2);
        return (float) Math.max(0, Math.min(1, level));
    }

    private int hitPoint(double x, double y) {
        List<Instrument.EnvelopePoint> points = working.envelope();
        for (int i = 0; i < points.size(); i++) {
            double px = timeToX(points.get(i).timeSeconds());
            double py = levelToY(points.get(i).level());
            if (Math.hypot(px - x, py - y) <= HIT_RADIUS) {
                return i;
            }
        }
        return -1;
    }

    private void onEnvelopePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        int hit = hitPoint(event.getX(), event.getY());
        if (hit >= 0) {
            draggingPointIndex = hit;
            return;
        }
        List<Instrument.EnvelopePoint> points = new ArrayList<>(working.envelope());
        Instrument.EnvelopePoint added = new Instrument.EnvelopePoint(xToTime(event.getX()), yToLevel(event.getY()));
        points.add(added);
        working.setEnvelope(points);
        draggingPointIndex = working.envelope().indexOf(added);
        pushToNode();
        redrawEnvelope();
    }

    private void onEnvelopeDragged(MouseEvent event) {
        if (draggingPointIndex < 0) {
            return;
        }
        List<Instrument.EnvelopePoint> points = new ArrayList<>(working.envelope());
        float minTime = draggingPointIndex == 0 ? 0f : points.get(draggingPointIndex - 1).timeSeconds() + 0.001f;
        float maxTime = draggingPointIndex == points.size() - 1
                ? Float.MAX_VALUE : points.get(draggingPointIndex + 1).timeSeconds() - 0.001f;
        float time = Math.max(minTime, Math.min(Math.max(minTime, maxTime), xToTime(event.getX())));
        float level = yToLevel(event.getY());
        points.set(draggingPointIndex, new Instrument.EnvelopePoint(time, level));
        working.setEnvelope(points);
        pushToNode();
        redrawEnvelope();
    }

    private void onEnvelopeClicked(MouseEvent event) {
        int hit = hitPoint(event.getX(), event.getY());
        if (hit < 0) {
            return;
        }
        if (event.getButton() == MouseButton.SECONDARY) {
            List<Instrument.EnvelopePoint> points = new ArrayList<>(working.envelope());
            if (points.size() <= 1) {
                return;
            }
            points.remove(hit);
            working.setEnvelope(points);
            if (working.sustainPointIndex() == hit) {
                working.setSustainPointIndex(-1);
            } else if (working.sustainPointIndex() > hit) {
                working.setSustainPointIndex(working.sustainPointIndex() - 1);
            }
            pushToNode();
            redrawEnvelope();
        } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            working.setSustainPointIndex(working.sustainPointIndex() == hit ? -1 : hit);
            pushToNode();
            redrawEnvelope();
        }
    }

    private void redrawEnvelope() {
        GraphicsContext g = envelopeCanvas.getGraphicsContext2D();
        g.setFill(Theme.CANVAS);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        List<Instrument.EnvelopePoint> points = working.envelope();
        g.setStroke(Theme.WAVE_TRACE);
        g.setLineWidth(1.5);
        g.beginPath();
        for (int i = 0; i < points.size(); i++) {
            double x = timeToX(points.get(i).timeSeconds());
            double y = levelToY(points.get(i).level());
            if (i == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();

        for (int i = 0; i < points.size(); i++) {
            double x = timeToX(points.get(i).timeSeconds());
            double y = levelToY(points.get(i).level());
            g.setFill(i == working.sustainPointIndex() ? Theme.ACCENT : Theme.WAVE_TRACE);
            g.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
        }
    }
}
