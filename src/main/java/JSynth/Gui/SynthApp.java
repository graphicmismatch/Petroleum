package JSynth.Gui;

import JSynth.Audio.AudioEngine;
import JSynth.Audio.ExportSettings;
import JSynth.Graph.Node;
import JSynth.Instruments.InstrumentNode;
import JSynth.Oscillators.OscillatorNode;
import JSynth.Oscillators.Implementations.DrawnWaveform;
import JSynth.Patch.Patch;
import JSynth.Tracker.TrackerNode;
import JSynth.Serialization.PatchCodec;
import JSynth.Serialization.PatchDocument;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class SynthApp extends Application {
    private static final Preferences PREFS = Preferences.userNodeForPackage(SynthApp.class);
    private static final double DEFAULT_WIDTH = 1200;
    private static final double DEFAULT_HEIGHT = 800;
    private static final double MIN_WIDTH = 900;
    private static final double MIN_HEIGHT = 600;
    private static final Duration SCREEN_FADE = Duration.millis(160);

    private final AudioEngine audioEngine = new AudioEngine();
    private final GraphEditor editor = new GraphEditor();
    private final PatchCodec patchCodec = editor.getPatchCodec();
    private final QuickView quickView = new QuickView(editor.getOscillatorFactory());
    private final MacroPanel macroPanel = new MacroPanel(editor);
    private final InputDebugPanel debugPanel = new InputDebugPanel();
    private final StackPane screens = new StackPane();
    private final BorderPane quickScreen = new BorderPane();
    private final BorderPane editorScreen = new BorderPane();
    private final BorderPane docsScreen = new BorderPane();
    private final DocumentationView docsView = new DocumentationView();
    private final BorderPane trackerScreen = new BorderPane();
    private final TrackerEditorView trackerEditorView = new TrackerEditorView();
    private final BorderPane waveformScreen = new BorderPane();
    private final WaveformDrawView waveformDrawView = new WaveformDrawView();
    private final BorderPane instrumentScreen = new BorderPane();
    private final InstrumentEditorView instrumentEditorView = new InstrumentEditorView();
    private final ProgressIndicator renderProgress = new ProgressIndicator();
    private ScrollPane canvasScroll;
    private double savedEditorHvalue;
    private double savedEditorVvalue;
    private final Button undoButton = new Button("Undo");
    private final Button redoButton = new Button("Redo");
    private final MenuItem undoMenuItem = new MenuItem("Undo");
    private final MenuItem redoMenuItem = new MenuItem("Redo");
    private final Label zoomLabel = new Label();
    private boolean playing = false;
    private boolean rendering = false;

    private enum Screen { QUICK, EDITOR, DOCS, TRACKER, WAVEFORM, INSTRUMENT }

    private Screen activeScreen = Screen.EDITOR;
    private Screen previousScreen = Screen.EDITOR;
    private Patch livePatch;
    private final AnimationTimer waveformTimer = new AnimationTimer() {
        private long lastFrame;

        @Override
        public void handle(long now) {
            if (now - lastFrame < 33_000_000L) {
                return;
            }
            lastFrame = now;
            editor.refreshWaveforms(livePatch);
            if (activeScreen == Screen.TRACKER) {
                trackerEditorView.tickPlayhead(playing);
            }
        }
    };

    @Override
    public void start(Stage stage) {
        canvasScroll = new ScrollPane(editor.getCanvas());
        canvasScroll.setPannable(false);
        canvasScroll.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        canvasScroll.setOnContextMenuRequested(event -> {
            Window window = canvasScroll.getScene() == null ? null : canvasScroll.getScene().getWindow();
            editor.showAddNodeMenu(event.getSceneX(), event.getSceneY(), event.getScreenX(), event.getScreenY(),
                    window);
            event.consume();
        });
        StackPane canvasStack = new StackPane(canvasScroll, debugPanel.getRoot());
        StackPane.setAlignment(debugPanel.getRoot(), Pos.BOTTOM_LEFT);
        StackPane.setMargin(debugPanel.getRoot(), new Insets(10));
        debugPanel.getRoot().setVisible(false);
        debugPanel.getRoot().setManaged(false);
        editorScreen.setCenter(canvasStack);
        editorScreen.setTop(buildEditorHeader());
        editorScreen.setBottom(buildTransportBar(stage));
        editorScreen.setRight(macroPanel.getRoot());

        editor.installShortcuts(editorScreen);
        editor.setOnGraphChanged(this::onGraphChanged);
        editor.setOnMacrosChanged(macroPanel::refresh);
        editor.setOnKnobChanged(macroPanel::syncValues);
        editor.setOnOpenDocumentation(this::openNodeDocumentation);
        editor.setOnOpenTrackerEditor(this::openTrackerEditor);
        editor.setOnOpenWaveformEditor(this::openWaveformEditor);
        editor.setOnOpenInstrumentEditor(this::openInstrumentEditor);

        ScrollPane quickScroll = new ScrollPane(quickView.getRoot());
        quickScroll.setFitToWidth(true);
        quickScreen.setTop(buildQuickHeader());
        quickScreen.setCenter(quickScroll);

        quickView.setOnBeforeDocumentReplaced(this::stopPlayback);
        quickView.setOnPlay(this::playDocument);
        quickView.setOnExport(document -> onExportDocument(stage, document));
        quickView.setOnOpenInEditor(this::openInEditor);
        quickView.setOnExportAll(sounds -> onExportAll(stage, sounds));

        docsScreen.setTop(buildDocsHeader());
        docsScreen.setCenter(docsView.getRoot());

        trackerScreen.setTop(buildTrackerHeader());
        trackerScreen.setCenter(trackerEditorView.getRoot());

        waveformScreen.setTop(buildWaveformHeader());
        waveformScreen.setCenter(waveformDrawView.getRoot());

        instrumentScreen.setTop(buildInstrumentHeader());
        instrumentScreen.setCenter(instrumentEditorView.getRoot());

        screens.getChildren().addAll(quickScreen, editorScreen, docsScreen, trackerScreen, waveformScreen,
                instrumentScreen);
        showScreen(Screen.EDITOR);
        Platform.runLater(this::scrollToGraph);

        quickScreen.getStyleClass().add("screen");
        editorScreen.getStyleClass().add("screen");
        docsScreen.getStyleClass().add("screen");
        trackerScreen.getStyleClass().add("screen");
        waveformScreen.getStyleClass().add("screen");
        instrumentScreen.getStyleClass().add("screen");

        renderProgress.setMaxSize(28, 28);
        renderProgress.setVisible(false);
        renderProgress.setManaged(false);
        StackPane.setAlignment(renderProgress, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(renderProgress, new Insets(16));

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));
        StackPane screensWithOverlay = new StackPane(screens, renderProgress);
        root.setCenter(screensWithOverlay);

        double width = PREFS.getDouble("windowWidth", DEFAULT_WIDTH);
        double height = PREFS.getDouble("windowHeight", DEFAULT_HEIGHT);
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/jsynth.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("JSynth");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        Image icon = new Image(getClass().getResource("/icons/jsynth-256.png").toExternalForm());
        stage.getIcons().add(icon);

        double x = PREFS.getDouble("windowX", -1);
        double y = PREFS.getDouble("windowY", -1);
        if (x >= 0 && y >= 0) {
            stage.setX(x);
            stage.setY(y);
        }
        stage.setMaximized(PREFS.getBoolean("windowMaximized", false));

        stage.setOnCloseRequest(event -> {
            if (!confirmDiscardIfDirty("Close JSynth and discard them?")) {
                event.consume();
                return;
            }
            saveWindowState(stage);
            stopPlayback();
        });

        stage.show();
    }

    private void saveWindowState(Stage stage) {
        PREFS.putBoolean("windowMaximized", stage.isMaximized());
        if (!stage.isMaximized()) {
            PREFS.putDouble("windowX", stage.getX());
            PREFS.putDouble("windowY", stage.getY());
            PREFS.putDouble("windowWidth", stage.getWidth());
            PREFS.putDouble("windowHeight", stage.getHeight());
        }
    }

    private MenuBar buildMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar();

        MenuItem openItem = new MenuItem("Open Patch...");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        openItem.setOnAction(e -> onLoadPatch(stage));
        MenuItem saveItem = new MenuItem("Save Patch...");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveItem.setOnAction(e -> onSavePatch(stage));
        MenuItem exportItem = new MenuItem("Export to WAV...");
        exportItem.setOnAction(e -> onRenderToWav(stage));
        MenuItem quickItem = new MenuItem("Quick Sounds");
        quickItem.setOnAction(e -> openQuickView());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> stage.fireEvent(
                new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(openItem, saveItem, new SeparatorMenuItem(), exportItem,
                new SeparatorMenuItem(), quickItem, new SeparatorMenuItem(), exitItem);

        undoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        undoMenuItem.setOnAction(e -> editor.undo());
        redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z,
                KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        redoMenuItem.setOnAction(e -> editor.redo());
        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(undoMenuItem, redoMenuItem);

        MenuItem centerItem = new MenuItem("Center View");
        centerItem.setOnAction(e -> centerView());
        MenuItem zoomInItem = new MenuItem("Zoom In");
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN));
        zoomInItem.setOnAction(e -> zoomAroundCenter(1.2));
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN));
        zoomOutItem.setOnAction(e -> zoomAroundCenter(1.0 / 1.2));
        MenuItem resetZoomItem = new MenuItem("Reset Zoom");
        resetZoomItem.setOnAction(e -> {
            editor.setZoom(1.0);
            refreshZoomLabel();
        });
        MenuItem debugItem = new MenuItem("Toggle Debug Panel");
        debugItem.setOnAction(e -> toggleDebugPanel());
        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(centerItem, zoomInItem, zoomOutItem, resetZoomItem,
                new SeparatorMenuItem(), debugItem);

        MenuItem docsItem = new MenuItem("Documentation");
        docsItem.setOnAction(e -> showDocsScreen());
        MenuItem shortcutsItem = new MenuItem("Keyboard Shortcuts");
        shortcutsItem.setOnAction(e -> showShortcutsDialog());
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().addAll(docsItem, shortcutsItem, aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, helpMenu);
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            menuBar.setUseSystemMenuBar(true);
        }
        return menuBar;
    }

    private void toggleDebugPanel() {
        if (activeScreen == Screen.TRACKER) {
            trackerEditorView.toggleDebugPanel();
            return;
        }
        boolean shown = !debugPanel.getRoot().isVisible();
        debugPanel.getRoot().setVisible(shown);
        debugPanel.getRoot().setManaged(shown);
    }

    private void showShortcutsDialog() {
        String text = """
                File
                  Ctrl/Cmd+S              Save Patch
                  Ctrl/Cmd+O              Open Patch

                Edit
                  Ctrl/Cmd+Z              Undo
                  Ctrl/Cmd+Shift+Z        Redo
                  Delete / Backspace      Delete selection

                View
                  Ctrl/Cmd+=              Zoom in
                  Ctrl/Cmd+-              Zoom out

                Tracker piano-roll keys
                  Z S X D C V G B H N J M          One octave of white/black keys
                  Q 2 W 3 E R 5 T 6 Y 7 U I 9 O 0 P One octave up from those
                  Arrow keys                       Move the cursor
                  Home / End                       Jump to first / last step
                """;
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        alert.setHeaderText("Keyboard Shortcuts");
        alert.getDialogPane().setPrefWidth(460);
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "A node-graph synthesis engine and visual editor.", ButtonType.OK);
        alert.setHeaderText("About JSynth");
        alert.showAndWait();
    }

    private HBox buildEditorHeader() {
        undoButton.setOnAction(e -> editor.undo());
        redoButton.setOnAction(e -> editor.redo());
        editor.setOnHistoryChanged(this::refreshHistoryButtons);
        refreshHistoryButtons();

        Button quickButton = new Button("Quick sounds...");
        quickButton.setOnAction(e -> openQuickView());
        Tooltip.install(quickButton, new Tooltip("Switch to the Quick Sounds screen to roll a random patch"));
        Button docsButton = new Button("Docs");
        docsButton.setOnAction(e -> showDocsScreen());
        Tooltip.install(docsButton, new Tooltip("Open the documentation screen"));

        Button zoomOutButton = new Button("−");
        zoomOutButton.setOnAction(e -> zoomAroundCenter(1.0 / 1.2));
        Tooltip.install(zoomOutButton, new Tooltip("Zoom out (Ctrl/Cmd+-)"));
        Button zoomInButton = new Button("+");
        zoomInButton.setOnAction(e -> zoomAroundCenter(1.2));
        Tooltip.install(zoomInButton, new Tooltip("Zoom in (Ctrl/Cmd+=)"));
        zoomLabel.setMinWidth(46);
        zoomLabel.setAlignment(Pos.CENTER);
        refreshZoomLabel();
        Button centerViewButton = new Button("Center View");
        centerViewButton.setOnAction(e -> centerView());
        Tooltip.install(centerViewButton, new Tooltip("Scroll the canvas to center the placed nodes"));

        HBox box = new HBox(10, quickButton, undoButton, redoButton, docsButton,
                zoomOutButton, zoomLabel, zoomInButton, centerViewButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private static final double SCROLL_DELTA_EPSILON = 1e-6;

    private void onScroll(ScrollEvent event) {
        if (!event.isControlDown()) {
            return;
        }
        event.consume();
        if (Math.abs(event.getDeltaY()) < SCROLL_DELTA_EPSILON) {
            return;
        }
        double factor = event.getDeltaY() > 0 ? 1.1 : 1.0 / 1.1;
        zoomAt(factor, event.getX(), event.getY(), String.format("scroll deltaY=%+7.2f", event.getDeltaY()));
    }

    private void zoomAroundCenter(double factor) {
        double viewportWidth = canvasScroll.getViewportBounds().getWidth();
        double viewportHeight = canvasScroll.getViewportBounds().getHeight();
        zoomAt(factor, viewportWidth / 2.0, viewportHeight / 2.0, "button      ");
    }

    private void zoomAt(double factor, double viewportX, double viewportY, String source) {
        double oldZoom = editor.getZoom();
        Bounds contentBefore = editor.getCanvas().getBoundsInParent();
        double viewportWidth = canvasScroll.getViewportBounds().getWidth();
        double viewportHeight = canvasScroll.getViewportBounds().getHeight();

        double scrollableXBefore = Math.max(0, contentBefore.getWidth() - viewportWidth);
        double scrollableYBefore = Math.max(0, contentBefore.getHeight() - viewportHeight);
        double offsetXBefore = canvasScroll.getHvalue() * scrollableXBefore;
        double offsetYBefore = canvasScroll.getVvalue() * scrollableYBefore;

        double localX = (offsetXBefore + viewportX) / oldZoom;
        double localY = (offsetYBefore + viewportY) / oldZoom;

        editor.setZoom(oldZoom * factor);
        double newZoom = editor.getZoom();
        refreshZoomLabel();

        Bounds contentAfter = editor.getCanvas().getBoundsInParent();
        double scrollableXAfter = Math.max(0, contentAfter.getWidth() - viewportWidth);
        double scrollableYAfter = Math.max(0, contentAfter.getHeight() - viewportHeight);
        double offsetXAfter = localX * newZoom - viewportX;
        double offsetYAfter = localY * newZoom - viewportY;

        canvasScroll.setHvalue(scrollableXAfter <= 0 ? 0 : clamp01(offsetXAfter / scrollableXAfter));
        canvasScroll.setVvalue(scrollableYAfter <= 0 ? 0 : clamp01(offsetYAfter / scrollableYAfter));

        InputDebugLog.log(String.format("zoom  %s  factor=%.4f  %6.1f%% -> %6.1f%%",
                source, factor, oldZoom * 100, newZoom * 100));
    }

    private void refreshZoomLabel() {
        zoomLabel.setText(Math.round(editor.getZoom() * 100) + "%");
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private void centerView() {
        if (canvasScroll == null || editor.isEmpty()) {
            return;
        }
        double zoom = editor.getZoom();
        double centerX = (editor.getContentMinX() + editor.getContentMaxX()) / 2.0 * zoom;
        double centerY = (editor.getContentMinY() + editor.getContentMaxY()) / 2.0 * zoom;

        double viewportWidth = canvasScroll.getViewportBounds().getWidth();
        double viewportHeight = canvasScroll.getViewportBounds().getHeight();
        Bounds content = editor.getCanvas().getBoundsInParent();
        double scrollableX = Math.max(0, content.getWidth() - viewportWidth);
        double scrollableY = Math.max(0, content.getHeight() - viewportHeight);

        double targetX = centerX - viewportWidth / 2.0;
        double targetY = centerY - viewportHeight / 2.0;

        canvasScroll.setHvalue(scrollableX <= 0 ? 0 : clamp01(targetX / scrollableX));
        canvasScroll.setVvalue(scrollableY <= 0 ? 0 : clamp01(targetY / scrollableY));
    }

    private HBox buildQuickHeader() {
        Button docsButton = new Button("Docs");
        docsButton.setOnAction(e -> showDocsScreen());
        Tooltip.install(docsButton, new Tooltip("Open the documentation screen"));
        HBox box = new HBox(10, docsButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private HBox buildDocsHeader() {
        Button backButton = new Button("< Back");
        backButton.setOnAction(e -> showScreen(previousScreen));
        HBox box = new HBox(10, backButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private HBox buildTrackerHeader() {
        Button backButton = new Button("< Back");
        backButton.setOnAction(e -> {
            trackerEditorView.close();
            showScreen(previousScreen);
        });
        HBox box = new HBox(10, backButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private void openTrackerEditor(Node node) {
        if (!(node instanceof TrackerNode trackerNode)) {
            return;
        }
        trackerEditorView.open(trackerNode);
        showScreen(Screen.TRACKER);
    }

    private HBox buildWaveformHeader() {
        Button backButton = new Button("< Back");
        backButton.setOnAction(e -> showScreen(previousScreen));
        HBox box = new HBox(10, backButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private void openWaveformEditor(Node node) {
        if (!(node instanceof OscillatorNode oscillatorNode)
                || !(oscillatorNode.getOscillator() instanceof DrawnWaveform drawnWaveform)) {
            return;
        }
        waveformDrawView.open(drawnWaveform);
        showScreen(Screen.WAVEFORM);
    }

    private HBox buildInstrumentHeader() {
        Button backButton = new Button("< Back");
        backButton.setOnAction(e -> {
            instrumentEditorView.close();
            showScreen(previousScreen);
        });
        HBox box = new HBox(10, backButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private void openInstrumentEditor(Node node) {
        if (!(node instanceof InstrumentNode instrumentNode)) {
            return;
        }
        instrumentEditorView.open(instrumentNode);
        showScreen(Screen.INSTRUMENT);
    }

    private void openQuickView() {
        if (!confirmDiscardIfDirty("Quick Sounds clears the canvas and replaces it with a generated sound."
                + " The current graph's unsaved changes will be lost. Continue?")) {
            return;
        }
        stopPlayback();
        editor.loadDocument(new PatchDocument());
        quickView.ensureSound();
        showScreen(Screen.QUICK);
    }

    private boolean confirmDiscardIfDirty(String message) {
        if (!editor.isDirty()) {
            return true;
        }
        return Dialogs.confirm("Unsaved Changes", message);
    }

    private void refreshHistoryButtons() {
        undoButton.setDisable(!editor.canUndo());
        redoButton.setDisable(!editor.canRedo());
        undoButton.setText(editor.canUndo() ? "Undo " + editor.undoLabel() : "Undo");
        redoButton.setText(editor.canRedo() ? "Redo " + editor.redoLabel() : "Redo");
        undoMenuItem.setDisable(!editor.canUndo());
        redoMenuItem.setDisable(!editor.canRedo());
        undoMenuItem.setText(editor.canUndo() ? "Undo " + editor.undoLabel() : "Undo");
        redoMenuItem.setText(editor.canRedo() ? "Redo " + editor.redoLabel() : "Redo");
    }

    private void showScreen(Screen target) {
        if (activeScreen != Screen.DOCS && activeScreen != Screen.TRACKER && activeScreen != Screen.WAVEFORM
                && activeScreen != Screen.INSTRUMENT) {
            previousScreen = activeScreen;
        }
        boolean changingScreen = activeScreen != target;
        if (changingScreen && activeScreen == Screen.EDITOR && canvasScroll != null) {
            savedEditorHvalue = canvasScroll.getHvalue();
            savedEditorVvalue = canvasScroll.getVvalue();
        }
        activeScreen = target;
        quickScreen.setVisible(target == Screen.QUICK);
        quickScreen.setManaged(target == Screen.QUICK);
        editorScreen.setVisible(target == Screen.EDITOR);
        editorScreen.setManaged(target == Screen.EDITOR);
        docsScreen.setVisible(target == Screen.DOCS);
        docsScreen.setManaged(target == Screen.DOCS);
        trackerScreen.setVisible(target == Screen.TRACKER);
        trackerScreen.setManaged(target == Screen.TRACKER);
        waveformScreen.setVisible(target == Screen.WAVEFORM);
        waveformScreen.setManaged(target == Screen.WAVEFORM);
        instrumentScreen.setVisible(target == Screen.INSTRUMENT);
        instrumentScreen.setManaged(target == Screen.INSTRUMENT);
        if (changingScreen) {
            BorderPane incoming = paneFor(target);
            FadeTransition fadeIn = new FadeTransition(SCREEN_FADE, incoming);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        }
        if (changingScreen && target == Screen.EDITOR) {
            Platform.runLater(() -> {
                canvasScroll.setHvalue(savedEditorHvalue);
                canvasScroll.setVvalue(savedEditorVvalue);
            });
        }
    }

    private BorderPane paneFor(Screen screen) {
        return switch (screen) {
            case QUICK -> quickScreen;
            case EDITOR -> editorScreen;
            case DOCS -> docsScreen;
            case TRACKER -> trackerScreen;
            case WAVEFORM -> waveformScreen;
            case INSTRUMENT -> instrumentScreen;
        };
    }

    private void showDocsScreen() {
        showScreen(Screen.DOCS);
        docsView.showWelcome();
    }

    private void openNodeDocumentation(Node node) {
        showScreen(Screen.DOCS);
        docsView.showNode(Docs.keyFor(editor.getNodeFactory(), node));
    }

    private void scrollToGraph() {
        if (canvasScroll == null) {
            return;
        }
        double margin = 40;
        double viewportWidth = canvasScroll.getViewportBounds().getWidth();
        double viewportHeight = canvasScroll.getViewportBounds().getHeight();
        double scrollableX = editor.getCanvasWidth() - viewportWidth;
        double scrollableY = editor.getCanvasHeight() - viewportHeight;
        double targetX = Math.max(0, editor.getContentMinX() - margin);
        double targetY = Math.max(0, editor.getContentMinY() - margin);
        canvasScroll.setHvalue(scrollableX <= 0 ? 0 : Math.min(1, targetX / scrollableX));
        canvasScroll.setVvalue(scrollableY <= 0 ? 0 : Math.min(1, targetY / scrollableY));
    }

    private void playDocument(PatchDocument document) {
        try {
            restartPlayback(document.buildPatch());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            stopPlayback();
            showError(ex.getMessage());
        }
    }

    private void openInEditor(PatchDocument document) {
        stopPlayback();
        try {
            editor.loadDocument(document);
        } catch (RuntimeException ex) {
            showError("Could not open the sound in the editor: " + ex.getMessage());
            return;
        }
        showScreen(Screen.EDITOR);
        Platform.runLater(this::scrollToGraph);
    }

    private void onExportDocument(Stage stage, PatchDocument document) {
        Optional<ExportSettings> settings = ExportDialog.show();
        if (settings.isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export WAV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        chooser.setInitialFileName("sound.wav");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        stopPlayback();
        Patch patch;
        try {
            patch = document.buildPatch();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }
        double duration = quickView.suggestedDuration();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                audioEngine.renderToWav(patch, duration, file, settings.get());
                return null;
            }
        };
        runRenderTask(task, null, ex -> showError("Could not export: " + ex.getMessage()));
    }

    private void onExportAll(Stage stage, List<QuickView.NamedSound> sounds) {
        if (sounds.isEmpty()) {
            showError("There are no rolls to export yet.");
            return;
        }
        Optional<ExportSettings> settings = ExportDialog.show();
        if (settings.isEmpty()) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Export " + sounds.size() + " sounds to a folder");
        File directory = chooser.showDialog(stage);
        if (directory == null) {
            return;
        }
        stopPlayback();
        AtomicInteger written = new AtomicInteger();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = sounds.size();
                for (int i = 0; i < total; i++) {
                    QuickView.NamedSound sound = sounds.get(i);
                    File file = new File(directory, sound.fileName());
                    audioEngine.renderToWav(sound.document().buildPatch(), sound.duration(), file, settings.get());
                    written.incrementAndGet();
                    updateProgress(i + 1, total);
                }
                return null;
            }
        };
        runRenderTask(task,
                () -> Dialogs.info("Batch Export Complete",
                        "Exported " + sounds.size() + " sounds to " + directory.getName()),
                ex -> showError("Exported " + written.get() + " of " + sounds.size()
                        + " sounds, then failed: " + ex.getMessage()));
    }

    private void runRenderTask(Task<Void> task, Runnable onSuccess, Consumer<Throwable> onFailure) {
        if (rendering) {
            return;
        }
        rendering = true;
        renderProgress.progressProperty().bind(task.progressProperty());
        renderProgress.setVisible(true);
        renderProgress.setManaged(true);
        task.setOnSucceeded(e -> {
            rendering = false;
            renderProgress.setVisible(false);
            renderProgress.setManaged(false);
            renderProgress.progressProperty().unbind();
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
        task.setOnFailed(e -> {
            rendering = false;
            renderProgress.setVisible(false);
            renderProgress.setManaged(false);
            renderProgress.progressProperty().unbind();
            Throwable ex = task.getException();
            if (onFailure != null) {
                onFailure.accept(ex);
            } else {
                showError(ex != null ? ex.getMessage() : "Unknown error");
            }
        });
        Thread thread = new Thread(task, "jsynth-render");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        stopPlayback();
    }

    private void startPlayback(Patch patch) {
        audioEngine.play(patch);
        trackPlayback(patch);
    }

    private void restartPlayback(Patch patch) {
        audioEngine.restart(patch);
        trackPlayback(patch);
    }

    private void trackPlayback(Patch patch) {
        livePatch = patch;
        playing = true;
        waveformTimer.start();
    }

    private void stopPlayback() {
        waveformTimer.stop();
        audioEngine.stop();
        livePatch = null;
        playing = false;
        trackerEditorView.tickPlayhead(false);
    }

    private HBox buildTransportBar(Stage stage) {
        Button playButton = new Button("Play");
        Button stopButton = new Button("Stop");
        Button renderButton = new Button("Export to WAV...");

        playButton.setOnAction(e -> tryBuildPatch().ifPresent(this::restartPlayback));

        stopButton.setOnAction(e -> stopPlayback());

        renderButton.setOnAction(e -> onRenderToWav(stage));

        Button saveButton = new Button("Save Patch...");
        Button loadButton = new Button("Open Patch...");
        saveButton.setOnAction(e -> onSavePatch(stage));
        loadButton.setOnAction(e -> onLoadPatch(stage));

        HBox box = new HBox(10, playButton, stopButton, renderButton, saveButton, loadButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private void onSavePatch(Stage stage) {
        File file = choosePatchFile(stage, true);
        if (file == null) {
            return;
        }
        try {
            PatchDocument document = editor.toDocument();
            document.setName(Dialogs.stripExtension(file.getName()));
            Files.writeString(file.toPath(), patchCodec.save(document));
            editor.clearDirty();
        } catch (IOException | RuntimeException ex) {
            showError("Could not save patch: " + ex.getMessage());
        }
    }

    private void onLoadPatch(Stage stage) {
        if (!confirmDiscardIfDirty("Opening a different patch will discard the current graph's"
                + " unsaved changes. Continue?")) {
            return;
        }
        File file = choosePatchFile(stage, false);
        if (file == null) {
            return;
        }
        boolean wasPlaying = playing;
        if (wasPlaying) {
            stopPlayback();
        }
        try {
            editor.loadDocument(patchCodec.load(Files.readString(file.toPath())));
        } catch (IOException | RuntimeException ex) {
            showError("Could not open patch: " + ex.getMessage());
            return;
        }
        if (wasPlaying) {
            tryBuildPatch().ifPresent(this::startPlayback);
        }
        Platform.runLater(this::scrollToGraph);
    }

    private static File choosePatchFile(Stage stage, boolean save) {
        FileChooser chooser = Dialogs.jsynthFileChooser(save ? "Save Patch" : "Open Patch");
        if (save) {
            chooser.setInitialFileName("patch.jsynth");
            return chooser.showSaveDialog(stage);
        }
        return chooser.showOpenDialog(stage);
    }

    private void onGraphChanged() {
        macroPanel.refresh();
        if (!playing) {
            return;
        }
        tryBuildPatch().ifPresent(this::startPlayback);
    }

    private void onRenderToWav(Stage stage) {
        TextInputDialog durationDialog = new TextInputDialog("5");
        durationDialog.setTitle("Export to WAV");
        durationDialog.setHeaderText(null);
        durationDialog.setContentText("Duration (seconds):");
        Optional<String> durationText = durationDialog.showAndWait();
        if (durationText.isEmpty()) {
            return;
        }

        double duration;
        try {
            duration = Double.parseDouble(durationText.get().trim());
        } catch (NumberFormatException ex) {
            showError("Invalid duration: " + durationText.get());
            return;
        }

        Optional<ExportSettings> settings = ExportDialog.show();
        if (settings.isEmpty()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export WAV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        chooser.setInitialFileName("patch.wav");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        Optional<Patch> patchOpt = tryBuildPatch();
        if (patchOpt.isEmpty()) {
            return;
        }
        Patch patch = patchOpt.get();

        boolean wasPlaying = playing;
        if (wasPlaying) {
            stopPlayback();
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                audioEngine.renderToWav(patch, duration, file, settings.get());
                return null;
            }
        };
        runRenderTask(task,
                () -> {
                    if (wasPlaying) {
                        tryBuildPatch().ifPresent(this::startPlayback);
                    }
                },
                ex -> {
                    showError("Failed to render WAV: " + ex.getMessage());
                    if (wasPlaying) {
                        tryBuildPatch().ifPresent(this::startPlayback);
                    }
                });
    }

    private Optional<Patch> tryBuildPatch() {
        try {
            return Optional.of(editor.buildPatch());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            showError(ex.getMessage());
            return Optional.empty();
        }
    }

    private void showError(String message) {
        Dialogs.showError("Patch Error", message);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
