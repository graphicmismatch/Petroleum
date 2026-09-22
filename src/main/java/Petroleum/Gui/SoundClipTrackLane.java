package Petroleum.Gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class SoundClipTrackLane {
    private static final double CONTROL_PANEL_WIDTH = 150;

    private final SoundClipTrack track;
    private final BorderPane root = new BorderPane();
    private final SoundClipWaveformView waveformView = new SoundClipWaveformView();
    private final Button viewToggleButton = new Button("Spectrogram");

    SoundClipTrackLane(SoundClipTrack track, Runnable onStateChanged, Runnable onRemove) {
        this.track = track;

        TextField nameField = new TextField(track.getName());
        nameField.getStyleClass().add("track-name-field");
        nameField.textProperty().addListener((obs, oldValue, newValue) -> track.setName(newValue));

        ToggleButton muteButton = new ToggleButton("Mute");
        muteButton.setSelected(track.isMuted());
        muteButton.setOnAction(e -> {
            track.setMuted(muteButton.isSelected());
            onStateChanged.run();
        });

        ToggleButton soloButton = new ToggleButton("Solo");
        soloButton.setSelected(track.isSolo());
        soloButton.setOnAction(e -> {
            track.setSolo(soloButton.isSelected());
            onStateChanged.run();
        });

        HBox muteSoloRow = new HBox(4, muteButton, soloButton);

        viewToggleButton.setMaxWidth(Double.MAX_VALUE);
        viewToggleButton.setOnAction(e -> {
            boolean spectrogram = !waveformView.isSpectrogramMode();
            waveformView.setSpectrogramMode(spectrogram);
            viewToggleButton.setText(spectrogram ? "Waveform" : "Spectrogram");
        });

        Button removeButton = new Button("Remove");
        removeButton.setMaxWidth(Double.MAX_VALUE);
        removeButton.setOnAction(e -> onRemove.run());

        VBox controlPanel = new VBox(6, nameField, muteSoloRow, viewToggleButton, removeButton);
        controlPanel.setPadding(new Insets(8));
        controlPanel.setAlignment(Pos.TOP_LEFT);
        controlPanel.setPrefWidth(CONTROL_PANEL_WIDTH);
        controlPanel.setMinWidth(CONTROL_PANEL_WIDTH);
        controlPanel.setMaxWidth(CONTROL_PANEL_WIDTH);
        controlPanel.getStyleClass().add("track-control-panel");

        root.setLeft(controlPanel);
        root.setCenter(waveformView);
        root.getStyleClass().add("track-lane");
        waveformView.setBuffer(track.getSamples());
    }

    BorderPane getRoot() {
        return root;
    }

    SoundClipTrack getTrack() {
        return track;
    }

    SoundClipWaveformView getWaveformView() {
        return waveformView;
    }

    void refresh() {
        waveformView.setBuffer(track.getSamples());
    }
}
