package JSynth.Gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

final class InputDebugLog {
    private static final int MAX_ENTRIES = 300;
    private static final ObservableList<String> entries = FXCollections.observableArrayList();

    private InputDebugLog() {
    }

    static ObservableList<String> entries() {
        return entries;
    }

    static void log(String message) {
        entries.add(message);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }
}
