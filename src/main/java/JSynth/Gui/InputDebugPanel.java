package JSynth.Gui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class InputDebugPanel {
    private final VBox root = new VBox(4);
    private final ListView<String> list = new ListView<>(InputDebugLog.entries());
    private boolean scrollPending;

    InputDebugPanel() {
        list.getStyleClass().add("debug-log");
        list.setFocusTraversable(false);
        list.setPrefSize(420, 160);
        VBox.setVgrow(list, Priority.ALWAYS);
        InputDebugLog.entries().addListener((ListChangeListener<String>) change -> {
            if (!root.isVisible() || scrollPending) {
                return;
            }
            scrollPending = true;
            Platform.runLater(() -> {
                scrollPending = false;
                if (root.isVisible() && !list.getItems().isEmpty()) {
                    list.scrollTo(list.getItems().size() - 1);
                }
            });
        });

        Label title = new Label("Input Debug");
        title.getStyleClass().add("section-title");

        Button clear = new Button("Clear");
        clear.getStyleClass().add("icon-button");
        clear.setOnAction(e -> InputDebugLog.entries().clear());

        root.getChildren().addAll(title, list, clear);
        root.setPadding(new Insets(8));
        root.getStyleClass().add("card");
        root.setMaxWidth(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);
        root.setMouseTransparent(false);
    }

    VBox getRoot() {
        return root;
    }
}
