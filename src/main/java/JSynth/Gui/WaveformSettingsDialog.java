package JSynth.Gui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

final class WaveformSettingsDialog {
    record Result(boolean enabled, int sourceIndex, boolean autoRange, float min, float max) {
    }

    private WaveformSettingsDialog() {
    }

    static Optional<Result> show(String nodeName, List<String> sources, Result current) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle("Graph Settings");
        dialog.setHeaderText(nodeName);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        CheckBox enabled = new CheckBox("Show graph");
        enabled.setSelected(current.enabled());

        ChoiceBox<String> source = new ChoiceBox<>(FXCollections.observableArrayList(sources));
        int currentIndex = Math.max(0, Math.min(sources.size() - 1, current.sourceIndex()));
        source.setValue(sources.get(currentIndex));
        source.setDisable(sources.size() <= 1);

        CheckBox autoRange = new CheckBox("Auto range");
        autoRange.setSelected(current.autoRange());

        TextField minField = new TextField(Theme.formatValue(current.min()));
        TextField maxField = new TextField(Theme.formatValue(current.max()));
        minField.disableProperty().bind(autoRange.selectedProperty());
        maxField.disableProperty().bind(autoRange.selectedProperty());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.add(enabled, 0, 0, 2, 1);
        grid.add(new Label("Source"), 0, 1);
        grid.add(source, 1, 1);
        grid.add(autoRange, 0, 2, 2, 1);
        grid.add(new Label("Min"), 0, 3);
        grid.add(minField, 1, 3);
        grid.add(new Label("Max"), 0, 4);
        grid.add(maxField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            float min = parse(minField.getText(), current.min());
            float max = parse(maxField.getText(), current.max());
            if (max <= min) {
                max = min + 1f;
            }
            return new Result(enabled.isSelected(), sources.indexOf(source.getValue()), autoRange.isSelected(), min, max);
        });

        return dialog.showAndWait();
    }

    private static float parse(String text, float fallback) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
