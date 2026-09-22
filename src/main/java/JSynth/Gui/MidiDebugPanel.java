package JSynth.Gui;

import JSynth.Sources.MidiDebugLog;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

final class MidiDebugPanel {
    private final VBox root = new VBox(4);
    private final ObservableList<String> items = FXCollections.observableArrayList();
    private final ListView<String> list = new ListView<>(items);
    private int lastKnownSize = -1;
    private final Timeline poll = new Timeline(new KeyFrame(Duration.millis(150), e -> refresh()));

    MidiDebugPanel() {
        list.getStyleClass().add("debug-log");
        list.setFocusTraversable(false);
        list.setPrefSize(480, 200);
        VBox.setVgrow(list, Priority.ALWAYS);
        poll.setCycleCount(Timeline.INDEFINITE);

        Label title = new Label("MIDI Debug");
        title.getStyleClass().add("section-title");

        Button clear = new Button("Clear");
        clear.getStyleClass().add("icon-button");
        clear.setOnAction(e -> {
            MidiDebugLog.clear();
            lastKnownSize = -1;
            refresh();
        });

        root.getChildren().addAll(title, list, clear);
        root.setPadding(new Insets(8));
        root.getStyleClass().add("card");
        root.setMaxWidth(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);
        root.setMouseTransparent(false);
    }

    void setShown(boolean shown) {
        root.setVisible(shown);
        root.setManaged(shown);
        if (shown) {
            refresh();
            poll.play();
        } else {
            poll.stop();
        }
    }

    boolean isShown() {
        return root.isVisible();
    }

    private void refresh() {
        List<String> snapshot = MidiDebugLog.snapshot();
        if (snapshot.size() == lastKnownSize) {
            return;
        }
        lastKnownSize = snapshot.size();
        items.setAll(snapshot);
        if (!items.isEmpty()) {
            list.scrollTo(items.size() - 1);
        }
    }

    VBox getRoot() {
        return root;
    }
}
