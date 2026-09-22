package Petroleum.Gui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.FileChooser;

import java.util.List;
import java.util.Optional;

final class Dialogs {
    private Dialogs() {
    }

    static Optional<String> chooseFromList(String header, String message, List<String> options, String current) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                current != null && options.contains(current) ? current : null, options);
        dialog.setHeaderText(header);
        dialog.setContentText(message);
        return dialog.showAndWait();
    }

    static void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(header);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    static void info(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    static FileChooser patchFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Petroleum patches", "*.ptr", "*.jsynth"),
                new FileChooser.ExtensionFilter("Diesel scripts", "*.dsl"));
        return chooser;
    }
}
