package Petroleum.Sources;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class MidiDebugLog {
    private static final int MAX_ENTRIES = 300;
    private static final Deque<String> entries = new ArrayDeque<>();

    private MidiDebugLog() {
    }

    static synchronized void log(String message) {
        entries.addLast(message);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    public static synchronized List<String> snapshot() {
        return List.copyOf(entries);
    }

    public static synchronized void clear() {
        entries.clear();
    }
}
