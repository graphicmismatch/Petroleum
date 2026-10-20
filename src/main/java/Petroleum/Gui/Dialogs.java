package Petroleum.Gui;

import Petroleum.Collab.CollabClient;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
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

    static Optional<String[]> session(boolean host, String server, String name) {
        TextField serverField = new TextField(server);
        serverField.setPromptText("host:port, or tls://host:port for encryption");
        TextField nameField = new TextField(name);
        TextField roomField = new TextField();
        ChoiceBox<String> joinAs = roleChoice(CollabClient.EDITOR);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Server"), serverField);
        grid.addRow(1, new Label("Your name"), nameField);
        if (host) {
            grid.addRow(2, new Label("New people join as"), joinAs);
        } else {
            grid.addRow(2, new Label("Room code"), roomField);
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(host ? "Host Session" : "Join Session");
        dialog.setHeaderText(host ? "Share this patch with collaborators" : "Join a shared patch");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dialog.showAndWait().filter(b -> b == ButtonType.OK).map(b -> new String[] {
                serverField.getText().trim(), nameField.getText().trim(), roomField.getText().trim(),
                joinAs.getValue().toLowerCase()});
    }

    static void people(CollabClient client) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        Dialog<ButtonType> dialog = new Dialog<>();
        ChoiceBox<String> joinAs = roleChoice(client.joinAs());
        joinAs.setOnAction(e -> client.setJoinAs(joinAs.getValue().toLowerCase()));
        grid.addRow(0, new Label("New people join as"), joinAs);
        int row = 1;
        for (CollabClient.Member member : client.members()) {
            String name = member.name().isEmpty() ? "(no name)" : member.name();
            String label = member.id().equals(client.memberId()) ? name + " (you)"
                    : member.online() ? name : name + " - offline";
            if (CollabClient.HOST.equals(member.role())) {
                grid.addRow(row++, new Label(label), new Label("Host"));
                continue;
            }
            ChoiceBox<String> role = roleChoice(member.role());
            role.setOnAction(e -> client.setRole(member.id(), role.getValue().toLowerCase()));
            Button makeHost = new Button("Make Host");
            makeHost.setOnAction(e -> {
                if (confirm("Make " + name + " the host?", "You become an editor, and only the new host can"
                        + " manage people or close the room.")) {
                    client.transfer(member.id());
                    dialog.close();
                }
            });
            Button remove = new Button("Remove");
            remove.setOnAction(e -> {
                if (confirm("Remove " + name + "?", "They are disconnected and lose their place in the room."
                        + " Anyone with the room code can still join as someone new; use New Room Code to"
                        + " stop that.")) {
                    client.kick(member.id());
                    dialog.close();
                }
            });
            grid.addRow(row++, new Label(label), role, makeHost, remove);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(400, 40 + row * 34));
        dialog.setTitle("People");
        dialog.setHeaderText("Room " + client.room());
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static ChoiceBox<String> roleChoice(String role) {
        ChoiceBox<String> box = new ChoiceBox<>();
        box.getItems().addAll("Editor", "Viewer");
        box.setValue(CollabClient.VIEWER.equals(role) ? "Viewer" : "Editor");
        return box;
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

    static File midiSaveDialog(Window window, String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export MIDI file");
        chooser.setInitialFileName(suggestedName + ".mid");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MIDI files", "*.mid", "*.midi"));
        File file = chooser.showSaveDialog(window);
        if (file == null || file.getName().toLowerCase().endsWith(".mid")
                || file.getName().toLowerCase().endsWith(".midi")) {
            return file;
        }
        return new File(file.getParentFile(), file.getName() + ".mid");
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
