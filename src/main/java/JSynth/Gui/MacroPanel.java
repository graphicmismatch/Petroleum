package JSynth.Gui;

import JSynth.Serialization.Macro;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

final class MacroPanel {
    private final GraphEditor editor;
    private final VBox root = new VBox(Theme.GAP);
    private final VBox list = new VBox(Theme.GAP);
    private final Label emptyHint = new Label(
            "Click the + next to any knob to expose it here, and on the quick screen.");
    private final List<MacroSliderRow> rows = new ArrayList<>();

    private boolean syncing;

    MacroPanel(GraphEditor editor) {
        this.editor = editor;

        Label title = new Label("Macros");
        title.setFont(Font.font("System", FontWeight.BOLD, Theme.FONT_HEADING));
        title.setTextFill(Theme.TEXT);

        emptyHint.setTextFill(Theme.TEXT_FAINT);
        emptyHint.setWrapText(true);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("flat-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(title, emptyHint, scroll);
        root.setPadding(new Insets(Theme.PAD));
        root.setPrefWidth(Theme.PANEL_WIDTH);
        root.setMinWidth(Theme.PANEL_WIDTH);
        root.getStyleClass().add("side-panel");

        refresh();
    }

    Pane getRoot() {
        return root;
    }

    void refresh() {
        list.getChildren().clear();
        rows.clear();
        for (Macro macro : editor.getMacros()) {
            list.getChildren().add(buildRow(macro));
        }
        boolean empty = list.getChildren().isEmpty();
        emptyHint.setVisible(empty);
        emptyHint.setManaged(empty);
    }

    void syncValues() {
        syncing = true;
        try {
            for (MacroSliderRow row : rows) {
                row.syncFromModel();
            }
        } finally {
            syncing = false;
        }
    }

    private VBox buildRow(Macro macro) {
        Label name = new Label(macro.name());
        name.setTextFill(Theme.TEXT);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Button remove = new Button("×");
        remove.getStyleClass().add("icon-button");
        remove.setTooltip(new Tooltip("Remove this macro"));
        remove.setOnAction(e -> editor.removeMacro(macro));

        MacroSliderRow sliderRow = new MacroSliderRow(macro, editor::refreshKnobDisplays, () -> syncing);

        HBox header = new HBox(Theme.GAP, name, sliderRow.readout(), remove);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox row = new VBox(2, header, sliderRow.slider());
        row.setPadding(new Insets(4, 0, 6, 0));
        row.getStyleClass().add("macro-row");

        if (editor.isMacroInactive(macro)) {
            Label overridden = new Label("not driving anything");
            overridden.setTextFill(Theme.WARNING);
            overridden.setFont(Font.font(Theme.FONT_SMALL));
            row.getChildren().add(overridden);
            sliderRow.slider().setDisable(true);
        }

        rows.add(sliderRow);
        return row;
    }
}
