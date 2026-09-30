package Petroleum.Gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

final class MapView {
    private final GraphEditor editor;
    private final ScrollPane canvasScroll;
    private final BorderPane root = new BorderPane();
    private final MinimapView map;
    private final ListView<MinimapView.Waypoint> waypoints = new ListView<>();
    private final Label emptyHint = new Label("No waypoints yet. Annotations, macros, the output and\nany node with its own editor show up here.");

    MapView(GraphEditor editor, ScrollPane canvasScroll) {
        this.editor = editor;
        this.canvasScroll = canvasScroll;
        this.map = new MinimapView(editor, canvasScroll, true);

        Label title = new Label("Map");
        title.setFont(Font.font("System", FontWeight.BOLD, Theme.FONT_TITLE));
        title.setTextFill(Theme.TEXT_STRONG);

        Button close = new Button();
        Theme.icon(close, "close", 12, "Close the map");
        close.setOnAction(e -> setShown(false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(Theme.GAP, title, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(Theme.PAD));

        waypoints.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(MinimapView.Waypoint item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label kind = new Label(item.kind());
                kind.setTextFill(item.color());
                kind.setFont(Font.font(Theme.FONT_SMALL));
                Label label = new Label(item.label());
                label.setTextFill(Theme.TEXT);
                setGraphic(new VBox(1, kind, label));
            }
        });
        waypoints.setOnMouseClicked(e -> goTo(waypoints.getSelectionModel().getSelectedItem()));
        waypoints.setPrefWidth(Theme.PANEL_WIDTH);
        waypoints.setMinWidth(Theme.PANEL_WIDTH);
        waypoints.getStyleClass().add("flat-scroll");

        emptyHint.setTextFill(Theme.TEXT_FAINT);
        emptyHint.setWrapText(true);
        emptyHint.setPadding(new Insets(Theme.PAD));

        VBox side = new VBox(Theme.GAP, emptyHint, waypoints);
        VBox.setVgrow(waypoints, Priority.ALWAYS);
        side.setPadding(new Insets(0, Theme.PAD, Theme.PAD, 0));
        side.setPrefWidth(Theme.PANEL_WIDTH);

        root.setTop(header);
        root.setCenter(map.getRoot());
        root.setRight(side);
        root.getStyleClass().add("map-overlay");
        BorderPane.setMargin(map.getRoot(), new Insets(0, Theme.PAD, Theme.PAD, Theme.PAD));
        setShown(false);
    }

    Pane getRoot() {
        return root;
    }

    boolean isShown() {
        return root.isVisible();
    }

    void setShown(boolean shown) {
        root.setVisible(shown);
        root.setManaged(shown);
        if (shown) {
            refresh();
        }
    }

    void refresh() {
        if (!root.isVisible()) {
            return;
        }
        waypoints.getItems().setAll(MinimapView.waypoints(editor));
        boolean empty = waypoints.getItems().isEmpty();
        emptyHint.setVisible(empty);
        emptyHint.setManaged(empty);
        map.redraw();
    }

    private void goTo(MinimapView.Waypoint waypoint) {
        if (waypoint == null) {
            return;
        }
        MinimapView.centerOn(canvasScroll, editor, waypoint.x(), waypoint.y());
        editor.selectNode(waypoint.view());
        setShown(false);
    }
}
