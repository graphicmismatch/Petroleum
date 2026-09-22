package JSynth.Gui;

import JSynth.Audio.ExportSettings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.util.Optional;

final class ExportDialog {
    private static ExportSettings last = ExportSettings.defaults();

    private ExportDialog() {
    }

    static Optional<ExportSettings> show() {
        Dialog<ExportSettings> dialog = new Dialog<>();
        dialog.setTitle("Export options");
        dialog.setHeaderText("Smaller rates and depths make smaller files.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ChoiceBox<Integer> rate = new ChoiceBox<>(
                FXCollections.observableArrayList(11025, 22050, 44100, 48000, 88200, 96000, 192000));
        rate.setValue((int) last.sampleRate());

        ChoiceBox<Integer> depth = new ChoiceBox<>(FXCollections.observableArrayList(8, 16, 24, 32));
        depth.setValue(last.bitDepth());
        depth.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer bits) {
                return bits == 32 ? "32 (float)" : String.valueOf(bits);
            }

            @Override
            public Integer fromString(String s) {
                return Integer.parseInt(s.split(" ")[0]);
            }
        });

        ChoiceBox<String> channels = new ChoiceBox<>(
                FXCollections.observableArrayList("Mono", "Stereo"));
        channels.setValue(last.channels() == 1 ? "Mono" : "Stereo");

        CheckBox normalize = new CheckBox("Normalize to peak");
        normalize.setSelected(last.normalize());

        CheckBox trim = new CheckBox("Trim leading and trailing silence");
        trim.setSelected(last.trimSilence());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.add(new Label("Sample rate (Hz)"), 0, 0);
        grid.add(rate, 1, 0);
        grid.add(new Label("Bit depth"), 0, 1);
        grid.add(depth, 1, 1);
        grid.add(new Label("Channels"), 0, 2);
        grid.add(channels, 1, 2);
        grid.add(normalize, 1, 3);
        grid.add(trim, 1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            return new ExportSettings(
                    rate.getValue(),
                    depth.getValue(),
                    channels.getValue().equals("Mono") ? 1 : 2,
                    normalize.isSelected(),
                    trim.isSelected());
        });

        Optional<ExportSettings> result = dialog.showAndWait();
        result.ifPresent(settings -> last = settings);
        return result;
    }
}
