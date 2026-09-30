package Petroleum.Gui;

import Petroleum.Audio.AudioEngine;
import Petroleum.Generators.SoundCategory;
import Petroleum.Generators.SoundGenerator;
import Petroleum.Oscillators.OscillatorFactory;
import Petroleum.Serialization.Macro;
import Petroleum.Serialization.PatchDocument;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

public final class QuickView {
    private static final int HISTORY_LIMIT = 20;
    private static final double PREVIEW_SAMPLE_RATE = 44100;

    private record Roll(SoundCategory category, long seed, Map<String, Float> values) {
    }

    public record NamedSound(String fileName, PatchDocument document, double duration) {
    }

    private final SoundGenerator generator;
    private final Random seedSource = new Random();

    private final VBox root = new VBox(12);
    private final SoundPreview preview = new SoundPreview();
    private final VBox macroBox = new VBox(4);
    private final HBox historyStrip = new HBox(6);
    private final Label seedLabel = new Label();
    private final TextField seedField = new TextField();
    private final Label statusLabel = new Label();

    private final Deque<Roll> history = new ArrayDeque<>();
    private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(120));
    private final List<MacroSliderRow> macroRows = new ArrayList<>();
    private boolean syncingMacros;

    private PatchDocument document;
    private SoundCategory currentCategory = SoundCategory.PICKUP;
    private long currentSeed;

    private Consumer<PatchDocument> onPlay = d -> { };
    private Consumer<PatchDocument> onExport = d -> { };
    private Consumer<PatchDocument> onOpenInEditor = d -> { };
    private Consumer<List<NamedSound>> onExportAll = s -> { };
    private Runnable onBeforeDocumentReplaced = () -> { };

    public QuickView(OscillatorFactory oscillatorFactory) {
        this.generator = new SoundGenerator(oscillatorFactory);
        previewDebounce.setOnFinished(e -> refreshPreview());

        root.setPadding(new Insets(Theme.SECTION_GAP));
        root.getStyleClass().add("screen");

        Label title = new Label("Quick Sounds");
        title.setFont(Font.font("System", FontWeight.BOLD, Theme.FONT_TITLE));
        title.setTextFill(Theme.TEXT_STRONG);

        Label subtitle = new Label("Pick a category to roll a sound. Everything here is a real patch you can open.");
        subtitle.setTextFill(Theme.TEXT_DIM);

        TilePane categories = new TilePane(Theme.GAP, Theme.GAP);
        categories.setPrefColumns(4);
        for (SoundCategory category : SoundCategory.values()) {
            Button button = new Button(category.label());
            button.getStyleClass().add("category-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(e -> roll(category));
            categories.getChildren().add(button);
        }

        preview.widthProperty().bind(root.widthProperty().subtract(Theme.SECTION_GAP * 2 + Theme.PAD * 2));

        Button playButton = new Button();
        Theme.icon(playButton, "play", 16, "Play");
        playButton.getStyleClass().add("primary-button");
        Button mutateButton = new Button("Mutate");
        Button exportButton = new Button("Export WAV...");
        playButton.setOnAction(e -> play());
        mutateButton.setOnAction(e -> mutate());
        exportButton.setOnAction(e -> {
            if (document != null) {
                onExport.accept(document);
            }
        });

        Button openButton = new Button("Open in graph editor →");
        openButton.getStyleClass().add("category-button");
        openButton.setMaxWidth(Double.MAX_VALUE);
        openButton.setOnAction(e -> {
            if (document != null) {
                onOpenInEditor.accept(document);
            }
        });

        seedLabel.setTextFill(Theme.TEXT_DIM);
        seedField.setPromptText("sound code");
        seedField.setPrefWidth(120);
        seedField.textProperty().addListener((obs, oldText, newText) -> clearSeedFieldError());
        Button loadCode = new Button("Load code");
        loadCode.setOnAction(e -> loadSeedCode());

        HBox seedRow = new HBox(Theme.GAP, seedLabel, seedField, loadCode);
        seedRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setTextFill(Theme.TEXT_DIM);

        ScrollPane historyScroll = new ScrollPane(historyStrip);
        historyScroll.setFitToHeight(true);
        historyScroll.setPrefHeight(46);
        historyScroll.getStyleClass().add("flat-scroll");

        Label historyLabel = new Label("RECENT ROLLS");
        historyLabel.getStyleClass().add("section-title");

        Button exportAllButton = new Button("Export all rolls...");
        exportAllButton.setOnAction(e -> onExportAll.accept(collectHistory()));

        HBox transport = new HBox(Theme.GAP, playButton, mutateButton, exportButton, exportAllButton);

        Label soundHeading = new Label("SOUND");
        soundHeading.getStyleClass().add("section-title");
        VBox soundCard = new VBox(Theme.GAP, soundHeading, preview, macroBox, transport, openButton);
        soundCard.getStyleClass().add("card");

        Label categoryHeading = new Label("CATEGORY");
        categoryHeading.getStyleClass().add("section-title");
        VBox categoryCard = new VBox(Theme.GAP, categoryHeading, categories, seedRow);
        categoryCard.getStyleClass().add("card");

        root.getChildren().addAll(title, subtitle, categoryCard, soundCard,
                statusLabel, historyLabel, historyScroll);
        VBox.setVgrow(macroBox, Priority.ALWAYS);
    }

    public void ensureSound() {
        if (document == null) {
            adopt(currentCategory, seedSource.nextLong(), null, false);
            recordHistory();
        }
    }

    public Pane getRoot() {
        return root;
    }

    public void setOnPlay(Consumer<PatchDocument> callback) {
        this.onPlay = callback == null ? d -> { } : callback;
    }

    public void setOnExport(Consumer<PatchDocument> callback) {
        this.onExport = callback == null ? d -> { } : callback;
    }

    public void setOnOpenInEditor(Consumer<PatchDocument> callback) {
        this.onOpenInEditor = callback == null ? d -> { } : callback;
    }

    public void setOnExportAll(Consumer<List<NamedSound>> callback) {
        this.onExportAll = callback == null ? s -> { } : callback;
    }

    public void setOnBeforeDocumentReplaced(Runnable callback) {
        this.onBeforeDocumentReplaced = callback == null ? () -> { } : callback;
    }

    public double suggestedDuration() {
        return document == null ? 1.0 : generator.suggestedDuration(document);
    }

    private void roll(SoundCategory category) {
        long seed = seedSource.nextLong();
        adopt(category, seed, null, true);
        recordHistory();
    }

    private void mutate() {
        if (document == null) {
            return;
        }
        generator.mutate(document, seedSource.nextLong(), 0.15f);
        syncMacroControls();
        refreshPreview();
        recordHistory();
        status("Mutated. The graph is unchanged - only the macro values moved.");
        play();
    }

    private void adopt(SoundCategory category, long seed, Map<String, Float> values, boolean autoPlay) {
        onBeforeDocumentReplaced.run();
        currentCategory = category;
        currentSeed = seed;
        document = generator.generate(category, seed);
        if (values != null) {
            for (Macro macro : document.getMacros()) {
                Float saved = values.get(macro.name());
                if (saved != null) {
                    macro.target().setValue(saved);
                }
            }
        }
        seedLabel.setText(category.label() + "  ·  code " + SoundGenerator.encodeSeed(seed));
        buildMacroControls();
        refreshPreview();
        status(document.getNodes().size() + " nodes, " + document.getMacros().size() + " macros.");
        if (autoPlay) {
            play();
        }
    }

    private void buildMacroControls() {
        macroBox.getChildren().clear();
        macroRows.clear();
        for (Macro macro : document.getMacros()) {
            Label name = new Label(macro.name());
            name.setTextFill(Theme.TEXT);
            name.setPrefWidth(90);

            MacroSliderRow sliderRow = new MacroSliderRow(macro, previewDebounce::playFromStart, () -> syncingMacros);
            sliderRow.slider().setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(sliderRow.slider(), Priority.ALWAYS);
            sliderRow.readout().setPrefWidth(70);

            HBox row = new HBox(Theme.GAP, name, sliderRow.slider(), sliderRow.readout());
            row.setAlignment(Pos.CENTER_LEFT);
            macroBox.getChildren().add(row);
            macroRows.add(sliderRow);
        }
    }

    private void syncMacroControls() {
        syncingMacros = true;
        try {
            for (MacroSliderRow row : macroRows) {
                row.syncFromModel();
            }
        } finally {
            syncingMacros = false;
        }
    }

    private void play() {
        if (document != null) {
            onPlay.accept(document);
        }
    }

    private void refreshPreview() {
        if (document == null) {
            return;
        }
        preview.show(renderOffline());
    }

    private float[] renderOffline() {
        return AudioEngine.renderMono(document.buildPatch(), generator.suggestedDuration(document),
                (float) PREVIEW_SAMPLE_RATE);
    }

    private void loadSeedCode() {
        String text = seedField.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            adopt(currentCategory, SoundGenerator.decodeSeed(text), null, true);
            recordHistory();
            clearSeedFieldError();
        } catch (IllegalArgumentException ex) {
            if (!seedField.getStyleClass().contains("invalid")) {
                seedField.getStyleClass().add("invalid");
            }
            seedField.setTooltip(new Tooltip(ex.getMessage()));
        }
    }

    private void clearSeedFieldError() {
        seedField.getStyleClass().remove("invalid");
        seedField.setTooltip(null);
    }

    private void status(String message) {
        statusLabel.setText(message);
    }

    private List<NamedSound> collectHistory() {
        List<NamedSound> sounds = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Roll roll : history) {
            PatchDocument doc = generator.generate(roll.category(), roll.seed());
            for (Macro macro : doc.getMacros()) {
                Float saved = roll.values().get(macro.name());
                if (saved != null) {
                    macro.target().setValue(saved);
                }
            }
            String base = roll.category().label().toLowerCase() + "-"
                    + SoundGenerator.encodeSeed(roll.seed());
            String name = base;
            int suffix = 2;
            while (!used.add(name)) {
                name = base + "-" + suffix++;
            }
            sounds.add(new NamedSound(name + ".wav", doc, generator.suggestedDuration(doc)));
        }
        return sounds;
    }

    private void recordHistory() {
        Map<String, Float> values = new LinkedHashMap<>();
        for (Macro macro : document.getMacros()) {
            values.put(macro.name(), macro.target().getValue());
        }
        history.addFirst(new Roll(currentCategory, currentSeed, values));
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
        rebuildHistoryStrip();
    }

    private void rebuildHistoryStrip() {
        historyStrip.getChildren().clear();
        for (Roll roll : history) {
            Button button = new Button(roll.category().label() + " " + SoundGenerator.encodeSeed(roll.seed()));
            button.getStyleClass().add("chip");
            button.setOnAction(e -> adopt(roll.category(), roll.seed(), roll.values(), true));
            historyStrip.getChildren().add(button);
        }
    }
}
