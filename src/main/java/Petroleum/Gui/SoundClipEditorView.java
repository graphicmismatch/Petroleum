package Petroleum.Gui;

import Petroleum.Audio.AudioInputDevice;
import Petroleum.Oscillators.Implementations.Wavetable;
import Petroleum.Oscillators.Implementations.WavetableLoader;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class SoundClipEditorView {
    private static final float SAMPLE_RATE = 44100f;
    private static final int HISTORY_LIMIT = 50;

    private final BorderPane root = new BorderPane();
    private final VBox lanesBox = new VBox(2);
    private final SoundClipTimeRuler timeRuler = new SoundClipTimeRuler();
    private final ComboBox<String> deviceChooser = new ComboBox<>();
    private final Button recordButton = new Button("⏺ Record");
    private final Button playButton = new Button("▶ Play");
    private final Button undoButton = new Button("↶ Undo");
    private final Button redoButton = new Button("↷ Redo");
    private final Label statusLabel = new Label();

    private final List<SoundClipTrack> tracks = new ArrayList<>();
    private final List<SoundClipTrackLane> lanes = new ArrayList<>();
    private final EditHistory<SoundClipEditorView> history = new EditHistory<>(HISTORY_LIMIT);

    private Wavetable clip;
    private SoundClipTrackLane activeLane;
    private float[] clipboard;
    private double samplesPerPixel = 32;

    private volatile TargetDataLine captureLine;
    private Thread captureThread;
    private volatile boolean recording;
    private volatile float[] liveBuffer;
    private volatile int liveCount;
    private SoundClipTrack recordingTrack;
    private int recordingTrackIndex;

    private volatile SourceDataLine playbackLine;
    private Thread playbackThread;
    private volatile boolean playing;

    private SoundClipTrackLane moveSourceLane;
    private long moveStartSample;
    private long moveEndSample;
    private float[] moveSegment;

    private final AnimationTimer liveTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (recordingTrack == null) {
                return;
            }
            SoundClipTrackLane lane = laneFor(recordingTrack);
            if (lane != null) {
                double width = Math.max(SoundClipWaveformView.VIEWPORT_WIDTH, liveCount / samplesPerPixel);
                lane.getWaveformView().setView(samplesPerPixel, width);
                lane.getWaveformView().setLiveBuffer(liveBuffer, liveCount);
            }
            updateStatus();
        }
    };

    SoundClipEditorView() {
        deviceChooser.setPromptText("Input device");
        Button refreshDevicesButton = new Button("⟳");
        refreshDevicesButton.setTooltip(new javafx.scene.control.Tooltip("Refresh input device list"));
        refreshDevicesButton.setOnAction(e -> refreshDevices());
        recordButton.getStyleClass().add("transport-button");
        recordButton.setOnAction(e -> toggleRecord());
        playButton.getStyleClass().add("transport-button");
        playButton.setOnAction(e -> togglePlay());

        Button addTrackButton = new Button("+ Track");
        addTrackButton.setOnAction(e -> addTrack());
        Button removeTrackButton = new Button("− Track");
        removeTrackButton.setOnAction(e -> removeActiveTrack());

        Button cutButton = new Button("✂ Cut");
        cutButton.setOnAction(e -> cutSelection());
        Button copyButton = new Button("⧉ Copy");
        copyButton.setOnAction(e -> copySelection());
        Button pasteButton = new Button("⎘ Paste");
        pasteButton.setOnAction(e -> paste());
        Button trimButton = new Button("Trim");
        trimButton.setOnAction(e -> trimToSelection());
        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> deleteSelection());
        Button clearButton = new Button("Clear Track");
        clearButton.setOnAction(e -> clearActiveTrack());
        undoButton.setOnAction(e -> undo());
        redoButton.setOnAction(e -> redo());

        Button importButton = new Button("Import WAV...");
        importButton.setOnAction(e -> importWav());
        Button exportButton = new Button("Export Mix WAV...");
        exportButton.setOnAction(e -> exportWav());
        Button zoomInButton = new Button("🔍+");
        zoomInButton.setOnAction(e -> zoomIn());
        Button zoomOutButton = new Button("🔍−");
        zoomOutButton.setOnAction(e -> zoomOut());
        Button zoomFitButton = new Button("Fit");
        zoomFitButton.setOnAction(e -> zoomToFit());

        HBox transportRow = new HBox(8,
                labeled("Device", new HBox(4, deviceChooser, refreshDevicesButton)),
                spacer(),
                recordButton, playButton,
                spacer(),
                addTrackButton, removeTrackButton);
        transportRow.getStyleClass().add("toolbar-row");
        transportRow.setPadding(new Insets(8, 10, 8, 10));

        HBox editRow = new HBox(8, cutButton, copyButton, pasteButton, trimButton, deleteButton, clearButton,
                spacer(), undoButton, redoButton, spacer(), zoomOutButton, zoomInButton, zoomFitButton);
        editRow.getStyleClass().add("toolbar-row");
        editRow.setPadding(new Insets(0, 10, 8, 10));

        HBox fileRow = new HBox(8, importButton, exportButton);
        fileRow.getStyleClass().add("toolbar-row");
        fileRow.setPadding(new Insets(0, 10, 8, 10));

        VBox toolbar = new VBox(transportRow, editRow, fileRow);
        toolbar.getStyleClass().add("sound-clip-toolbar");

        lanesBox.getChildren().add(timeRuler);
        ScrollPane lanesScroll = new ScrollPane(lanesBox);
        lanesScroll.setFitToWidth(true);

        statusLabel.setPadding(new Insets(6, 10, 6, 10));
        statusLabel.setTextFill(Theme.TEXT_DIM);
        statusLabel.getStyleClass().add("sound-clip-status-bar");

        root.setTop(toolbar);
        root.setCenter(lanesScroll);
        root.setBottom(statusLabel);
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);

        updateUndoRedoButtons();
    }

    private static HBox labeled(String caption, javafx.scene.Node control) {
        Label label = new Label(caption);
        label.setTextFill(Theme.TEXT_DIM);
        HBox box = new HBox(6, label, control);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }

    private static javafx.scene.layout.Region spacer() {
        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        region.setPrefWidth(16);
        region.getStyleClass().add("toolbar-divider");
        return region;
    }

    private void handleShortcut(KeyEvent event) {
        KeyCombination undoCombo = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
        KeyCombination redoCombo = new KeyCodeCombination(KeyCode.Z,
                KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
        if (redoCombo.match(event)) {
            redo();
            event.consume();
        } else if (undoCombo.match(event)) {
            undo();
            event.consume();
        }
    }

    BorderPane getRoot() {
        return root;
    }

    void open(Wavetable clip) {
        stopRecording();
        stopPlayback();
        this.clip = clip;
        clipboard = null;
        tracks.clear();
        tracks.add(new SoundClipTrack("Track 1", clip.getTable()));
        samplesPerPixel = Math.max(SoundClipWaveformView.MIN_SAMPLES_PER_PIXEL,
                clip.getTable().length / SoundClipWaveformView.VIEWPORT_WIDTH);
        rebuildLanes();
        history.clear();
        updateUndoRedoButtons();
        refreshDevices();
        remix();
    }

    void close() {
        stopRecording();
        stopPlayback();
    }

    private void rebuildLanes() {
        SoundClipTrack activeTrack = activeLane == null ? null : activeLane.getTrack();
        lanes.clear();
        lanesBox.getChildren().clear();
        lanesBox.getChildren().add(timeRuler);
        for (SoundClipTrack track : tracks) {
            SoundClipTrackLane lane = new SoundClipTrackLane(track, this::remix, () -> removeTrack(track));
            lane.getWaveformView().setOnSelectionChanged(() -> onLaneActivated(lane));
            lane.getWaveformView().setOnMoveGesture(new SoundClipWaveformView.MoveGestureHandler() {
                @Override
                public void onMoveStarted(SoundClipWaveformView source, long startSample, long endSample) {
                    beginMove(lane, startSample, endSample);
                }

                @Override
                public void onMoveDragged(double sceneX, double sceneY) {
                    dragMove(sceneX, sceneY);
                }

                @Override
                public void onMoveFinished(double sceneX, double sceneY) {
                    finishMove(sceneX, sceneY);
                }
            });
            lanes.add(lane);
            lanesBox.getChildren().add(lane.getRoot());
        }
        activeLane = laneFor(activeTrack);
        if (activeLane == null && !lanes.isEmpty()) {
            activeLane = lanes.get(0);
        }
        broadcastView();
    }

    private void onLaneActivated(SoundClipTrackLane lane) {
        activeLane = lane;
        updateStatus();
    }

    private SoundClipTrackLane laneFor(SoundClipTrack track) {
        if (track == null) {
            return null;
        }
        for (SoundClipTrackLane lane : lanes) {
            if (lane.getTrack() == track) {
                return lane;
            }
        }
        return null;
    }

    private java.util.Optional<SoundClipWaveformView> activeWaveformView() {
        return activeLane == null ? java.util.Optional.empty() : java.util.Optional.of(activeLane.getWaveformView());
    }

    void refreshLane(SoundClipTrack track) {
        SoundClipTrackLane lane = laneFor(track);
        if (lane != null) {
            lane.refresh();
        }
    }

    void insertTrackInternal(SoundClipTrack track, int index) {
        tracks.add(Math.min(index, tracks.size()), track);
        rebuildLanes();
        remix();
    }

    void removeTrackInternal(SoundClipTrack track) {
        tracks.remove(track);
        rebuildLanes();
        remix();
    }

    void remix() {
        if (clip == null || tracks.isEmpty()) {
            return;
        }
        boolean anySolo = tracks.stream().anyMatch(SoundClipTrack::isSolo);
        int length = 0;
        for (SoundClipTrack track : tracks) {
            length = Math.max(length, track.getSamples().length);
        }
        float[] mixed = new float[Math.max(1, length)];
        for (SoundClipTrack track : tracks) {
            boolean audible = anySolo ? track.isSolo() : !track.isMuted();
            if (!audible) {
                continue;
            }
            float[] samples = track.getSamples();
            for (int i = 0; i < samples.length; i++) {
                mixed[i] += samples[i];
            }
        }
        for (int i = 0; i < mixed.length; i++) {
            mixed[i] = Math.max(-1f, Math.min(1f, mixed[i]));
        }
        clip.setTable(mixed);
        broadcastView();
        updateStatus();
    }

    private void zoomIn() {
        setSamplesPerPixel(samplesPerPixel / 1.5);
    }

    private void zoomOut() {
        setSamplesPerPixel(samplesPerPixel * 1.5);
    }

    private void zoomToFit() {
        long longest = 0;
        for (SoundClipTrack track : tracks) {
            longest = Math.max(longest, track.getSamples().length);
        }
        setSamplesPerPixel(Math.max(SoundClipWaveformView.MIN_SAMPLES_PER_PIXEL,
                longest / SoundClipWaveformView.VIEWPORT_WIDTH));
    }

    private void setSamplesPerPixel(double value) {
        samplesPerPixel = Math.max(SoundClipWaveformView.MIN_SAMPLES_PER_PIXEL,
                Math.min(SoundClipWaveformView.MAX_SAMPLES_PER_PIXEL, value));
        broadcastView();
    }

    private void broadcastView() {
        long longest = 0;
        for (SoundClipTrack track : tracks) {
            longest = Math.max(longest, track.getSamples().length);
        }
        double width = Math.max(SoundClipWaveformView.VIEWPORT_WIDTH, longest / samplesPerPixel);
        timeRuler.configure(samplesPerPixel, SAMPLE_RATE, width);
        for (SoundClipTrackLane lane : lanes) {
            lane.getWaveformView().setView(samplesPerPixel, width);
        }
    }

    private void record(EditCommand<SoundClipEditorView> command) {
        history.record(command);
        updateUndoRedoButtons();
    }

    private void undo() {
        if (history.undo(this)) {
            updateUndoRedoButtons();
        }
    }

    private void redo() {
        if (history.redo(this)) {
            updateUndoRedoButtons();
        }
    }

    private void updateUndoRedoButtons() {
        undoButton.setDisable(!history.canUndo());
        redoButton.setDisable(!history.canRedo());
    }

    private void applySingleTrackEdit(String label, SoundClipTrack track, float[] newSamples) {
        List<SoundClipEditCommands.TrackSnapshot> before =
                List.of(new SoundClipEditCommands.TrackSnapshot(track, track.getSamples()));
        track.setSamples(newSamples);
        List<SoundClipEditCommands.TrackSnapshot> after =
                List.of(new SoundClipEditCommands.TrackSnapshot(track, newSamples));
        refreshLane(track);
        remix();
        record(SoundClipEditCommands.trackEdit(before, after, label));
    }

    private void applyTwoTrackEdit(String label, SoundClipTrack trackA, float[] newA,
            SoundClipTrack trackB, float[] newB) {
        List<SoundClipEditCommands.TrackSnapshot> before = List.of(
                new SoundClipEditCommands.TrackSnapshot(trackA, trackA.getSamples()),
                new SoundClipEditCommands.TrackSnapshot(trackB, trackB.getSamples()));
        trackA.setSamples(newA);
        trackB.setSamples(newB);
        List<SoundClipEditCommands.TrackSnapshot> after = List.of(
                new SoundClipEditCommands.TrackSnapshot(trackA, newA),
                new SoundClipEditCommands.TrackSnapshot(trackB, newB));
        refreshLane(trackA);
        refreshLane(trackB);
        remix();
        record(SoundClipEditCommands.trackEdit(before, after, label));
    }

    private void addTrack() {
        SoundClipTrack track = new SoundClipTrack("Track " + (tracks.size() + 1), new float[64]);
        int index = tracks.size();
        tracks.add(track);
        rebuildLanes();
        activeLane = laneFor(track);
        remix();
        record(SoundClipEditCommands.trackListEdit(track, index, true, "Add Track"));
    }

    private void removeActiveTrack() {
        if (activeLane == null) {
            return;
        }
        removeTrack(activeLane.getTrack());
    }

    private void removeTrack(SoundClipTrack track) {
        if (tracks.size() <= 1) {
            Dialogs.showError("Remove Track", "A clip needs at least one track.");
            return;
        }
        int index = tracks.indexOf(track);
        if (index < 0) {
            return;
        }
        tracks.remove(track);
        rebuildLanes();
        remix();
        record(SoundClipEditCommands.trackListEdit(track, index, false, "Remove Track"));
    }

    private void trimToSelection() {
        activeWaveformView().filter(SoundClipWaveformView::hasSelection).ifPresent(view -> {
            SoundClipTrack track = activeLane.getTrack();
            int start = (int) view.getSelectionStartSample();
            int end = (int) view.getSelectionEndSample();
            applySingleTrackEdit("Trim", track, Arrays.copyOfRange(track.getSamples(), start, end));
        });
    }

    private void deleteSelection() {
        activeWaveformView().filter(SoundClipWaveformView::hasSelection).ifPresent(view -> {
            SoundClipTrack track = activeLane.getTrack();
            int start = (int) view.getSelectionStartSample();
            int end = (int) view.getSelectionEndSample();
            applySingleTrackEdit("Delete", track, spliceOut(track.getSamples(), start, end));
        });
    }

    private void clearActiveTrack() {
        if (activeLane == null) {
            return;
        }
        applySingleTrackEdit("Clear", activeLane.getTrack(), new float[64]);
    }

    private void copySelection() {
        activeWaveformView().filter(SoundClipWaveformView::hasSelection).ifPresent(view -> {
            int start = (int) view.getSelectionStartSample();
            int end = (int) view.getSelectionEndSample();
            clipboard = Arrays.copyOfRange(activeLane.getTrack().getSamples(), start, end);
        });
    }

    private void cutSelection() {
        if (activeLane == null || !activeLane.getWaveformView().hasSelection()) {
            return;
        }
        copySelection();
        deleteSelection();
    }

    private void paste() {
        if (activeLane == null || clipboard == null || clipboard.length == 0) {
            return;
        }
        SoundClipTrack track = activeLane.getTrack();
        SoundClipWaveformView view = activeLane.getWaveformView();
        int at = view.hasSelection() ? (int) view.getSelectionStartSample() : track.getSamples().length;
        applySingleTrackEdit("Paste", track, spliceIn(track.getSamples(), at, clipboard));
    }

    private static float[] spliceOut(float[] samples, int start, int end) {
        float[] result = new float[samples.length - (end - start)];
        System.arraycopy(samples, 0, result, 0, start);
        System.arraycopy(samples, end, result, start, samples.length - end);
        return result;
    }

    private static float[] spliceIn(float[] samples, int at, float[] insert) {
        int clampedAt = Math.max(0, Math.min(samples.length, at));
        float[] result = new float[samples.length + insert.length];
        System.arraycopy(samples, 0, result, 0, clampedAt);
        System.arraycopy(insert, 0, result, clampedAt, insert.length);
        System.arraycopy(samples, clampedAt, result, clampedAt + insert.length, samples.length - clampedAt);
        return result;
    }

    private void beginMove(SoundClipTrackLane lane, long startSample, long endSample) {
        moveSourceLane = lane;
        moveStartSample = startSample;
        moveEndSample = endSample;
        moveSegment = Arrays.copyOfRange(lane.getTrack().getSamples(), (int) startSample, (int) endSample);
    }

    private void dragMove(double sceneX, double sceneY) {
        for (SoundClipTrackLane lane : lanes) {
            lane.getWaveformView().clearDropPreview();
        }
        SoundClipTrackLane target = laneAtScenePoint(sceneY);
        if (target == null || moveSegment == null) {
            return;
        }
        long length = moveEndSample - moveStartSample;
        long dropAt = target.getWaveformView().xToSampleClamped(sceneX);
        target.getWaveformView().showDropPreview(dropAt, length);
    }

    private void finishMove(double sceneX, double sceneY) {
        for (SoundClipTrackLane lane : lanes) {
            lane.getWaveformView().clearDropPreview();
        }
        SoundClipTrackLane sourceLane = moveSourceLane;
        float[] segment = moveSegment;
        long start = moveStartSample;
        long end = moveEndSample;
        moveSourceLane = null;
        moveSegment = null;
        if (sourceLane == null || segment == null || segment.length == 0) {
            return;
        }
        SoundClipTrackLane target = laneAtScenePoint(sceneY);
        if (target == null) {
            sourceLane.getWaveformView().clearSelection();
            return;
        }
        SoundClipTrack sourceTrack = sourceLane.getTrack();
        long dropAt = target.getWaveformView().xToSampleClamped(sceneX);
        float[] sourceAfterRemoval = spliceOut(sourceTrack.getSamples(), (int) start, (int) end);

        if (target == sourceLane) {
            long adjustedDropAt = dropAt > end ? dropAt - segment.length : Math.min(dropAt, start);
            float[] result = spliceIn(sourceAfterRemoval, (int) adjustedDropAt, segment);
            applySingleTrackEdit("Move", sourceTrack, result);
        } else {
            SoundClipTrack targetTrack = target.getTrack();
            float[] targetAfterInsert = spliceIn(targetTrack.getSamples(), (int) dropAt, segment);
            applyTwoTrackEdit("Move", sourceTrack, sourceAfterRemoval, targetTrack, targetAfterInsert);
        }
    }

    private SoundClipTrackLane laneAtScenePoint(double sceneY) {
        for (SoundClipTrackLane lane : lanes) {
            SoundClipWaveformView view = lane.getWaveformView();
            if (view.getScene() == null) {
                continue;
            }
            Bounds bounds = view.localToScene(view.getBoundsInLocal());
            if (sceneY >= bounds.getMinY() && sceneY <= bounds.getMaxY()) {
                return lane;
            }
        }
        return null;
    }

    private void refreshDevices() {
        String current = deviceChooser.getValue();
        deviceChooser.getItems().setAll(AudioInputDevice.listAvailableDeviceNames());
        if (current != null && deviceChooser.getItems().contains(current)) {
            deviceChooser.setValue(current);
        }
    }

    private void toggleRecord() {
        if (recording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        String device = deviceChooser.getValue();
        if (device == null) {
            Dialogs.showError("Record", "Choose an input device first.");
            return;
        }
        stopPlayback();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        TargetDataLine opened;
        try {
            opened = AudioInputDevice.open(device, format);
        } catch (LineUnavailableException ex) {
            Dialogs.showError("Record", "Could not open " + device + ": " + ex.getMessage());
            return;
        }
        opened.start();
        captureLine = opened;
        liveBuffer = new float[(int) (SAMPLE_RATE * 4)];
        liveCount = 0;
        recordingTrackIndex = tracks.size();
        recordingTrack = new SoundClipTrack("Track " + (tracks.size() + 1), new float[0]);
        tracks.add(recordingTrack);
        rebuildLanes();
        activeLane = laneFor(recordingTrack);
        recording = true;
        recordButton.setText("Stop");
        captureThread = new Thread(() -> runCapture(opened), "SoundClip-Capture");
        captureThread.setDaemon(true);
        captureThread.start();
        liveTimer.start();
    }

    private void runCapture(TargetDataLine line) {
        byte[] bytes = new byte[4096];
        while (captureLine == line) {
            int read = line.read(bytes, 0, bytes.length);
            for (int i = 0; i + 1 < read; i += 2) {
                short sample = (short) ((bytes[i] & 0xFF) | (bytes[i + 1] << 8));
                appendLiveSample(sample / 32768f);
            }
        }
    }

    private void appendLiveSample(float value) {
        float[] buf = liveBuffer;
        if (liveCount == buf.length) {
            buf = Arrays.copyOf(buf, buf.length * 2);
            liveBuffer = buf;
        }
        buf[liveCount] = value;
        liveCount++;
    }

    private void stopRecording() {
        if (!recording) {
            return;
        }
        recording = false;
        recordButton.setText("Record");
        TargetDataLine closing = captureLine;
        captureLine = null;
        if (closing != null) {
            closing.stop();
            closing.close();
        }
        liveTimer.stop();
        SoundClipTrack track = recordingTrack;
        int index = recordingTrackIndex;
        recordingTrack = null;
        if (track == null) {
            return;
        }
        track.setSamples(Arrays.copyOf(liveBuffer, liveCount));
        refreshLane(track);
        remix();
        record(SoundClipEditCommands.trackListEdit(track, index, true, "Record"));
    }

    private void togglePlay() {
        if (playing) {
            stopPlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (clip == null) {
            return;
        }
        stopRecording();
        float[] table = clip.getTable();
        int start = 0;
        int end = table.length;
        if (activeLane != null && activeLane.getWaveformView().hasSelection()) {
            start = (int) activeLane.getWaveformView().getSelectionStartSample();
            end = (int) Math.min(table.length, activeLane.getWaveformView().getSelectionEndSample());
        }
        if (start >= end) {
            return;
        }
        float[] segment = Arrays.copyOfRange(table, start, end);
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        SourceDataLine opened;
        try {
            opened = AudioSystem.getSourceDataLine(format);
            opened.open(format);
        } catch (LineUnavailableException ex) {
            Dialogs.showError("Play", "Could not open audio output: " + ex.getMessage());
            return;
        }
        opened.start();
        playbackLine = opened;
        playing = true;
        playButton.setText("Stop");
        playbackThread = new Thread(() -> runPlayback(opened, segment), "SoundClip-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void runPlayback(SourceDataLine line, float[] segment) {
        byte[] pcm = new byte[segment.length * 2];
        for (int i = 0; i < segment.length; i++) {
            int clamped = Math.max(-32768, Math.min(32767, Math.round(segment[i] * 32767f)));
            pcm[i * 2] = (byte) (clamped & 0xFF);
            pcm[i * 2 + 1] = (byte) ((clamped >> 8) & 0xFF);
        }
        line.write(pcm, 0, pcm.length);
        line.drain();
        Platform.runLater(this::stopPlayback);
    }

    private void stopPlayback() {
        if (!playing) {
            return;
        }
        playing = false;
        playButton.setText("Play");
        SourceDataLine closing = playbackLine;
        playbackLine = null;
        if (closing != null) {
            closing.stop();
            closing.close();
        }
    }

    private void importWav() {
        if (activeLane == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import WAV into this track");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        File file = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            float[] samples = WavetableLoader.loadMonoSamples(file);
            applySingleTrackEdit("Import", activeLane.getTrack(), samples);
        } catch (IOException | UnsupportedAudioFileException ex) {
            Dialogs.showError("Import", "Could not import " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void exportWav() {
        if (clip == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export mixdown to WAV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        File file = chooser.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            writeWav(clip.getTable(), file);
        } catch (IOException ex) {
            Dialogs.showError("Export", "Could not export " + file.getName() + ": " + ex.getMessage());
        }
    }

    private static void writeWav(float[] samples, File file) throws IOException {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int clamped = Math.max(-32768, Math.min(32767, Math.round(samples[i] * 32767f)));
            pcm[i * 2] = (byte) (clamped & 0xFF);
            pcm[i * 2 + 1] = (byte) ((clamped >> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, samples.length)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        }
    }

    private void updateStatus() {
        if (clip == null) {
            statusLabel.setText("");
            return;
        }
        double seconds = clip.getTable().length / (double) SAMPLE_RATE;
        StringBuilder text = new StringBuilder(
                String.format("%d track%s   Length: %s", tracks.size(), tracks.size() == 1 ? "" : "s",
                        formatTime(seconds)));
        if (activeLane != null && activeLane.getWaveformView().hasSelection()) {
            SoundClipWaveformView view = activeLane.getWaveformView();
            double startTime = view.getSelectionStartSample() / (double) SAMPLE_RATE;
            double endTime = view.getSelectionEndSample() / (double) SAMPLE_RATE;
            text.append(String.format("   Selection: %s - %s (%s) on %s",
                    formatTime(startTime), formatTime(endTime), formatTime(endTime - startTime),
                    activeLane.getTrack().getName()));
        }
        statusLabel.setText(text.toString());
    }

    private static String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        double secs = seconds - minutes * 60;
        return String.format("%d:%05.2f", minutes, secs);
    }
}
