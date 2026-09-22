package Petroleum.Gui;

import java.util.ArrayDeque;
import java.util.Deque;

final class EditHistory<T> {
    private final int limit;
    private final Deque<EditCommand<T>> undoStack = new ArrayDeque<>();
    private final Deque<EditCommand<T>> redoStack = new ArrayDeque<>();

    private boolean applying;

    EditHistory(int limit) {
        this.limit = limit;
    }

    boolean isApplying() {
        return applying;
    }

    boolean canUndo() {
        return !undoStack.isEmpty();
    }

    boolean canRedo() {
        return !redoStack.isEmpty();
    }

    String undoLabel() {
        return undoStack.isEmpty() ? "" : undoStack.peek().label();
    }

    String redoLabel() {
        return redoStack.isEmpty() ? "" : redoStack.peek().label();
    }

    void record(EditCommand<T> command) {
        if (applying) {
            return;
        }
        undoStack.push(command);
        while (undoStack.size() > limit) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    boolean undo(T target) {
        if (undoStack.isEmpty()) {
            return false;
        }
        EditCommand<T> command = undoStack.pop();
        boolean previous = applying;
        applying = true;
        try {
            command.undo(target);
        } catch (RuntimeException ex) {
            undoStack.push(command);
            throw ex;
        } finally {
            applying = previous;
        }
        redoStack.push(command);
        return true;
    }

    boolean redo(T target) {
        if (redoStack.isEmpty()) {
            return false;
        }
        EditCommand<T> command = redoStack.pop();
        boolean previous = applying;
        applying = true;
        try {
            command.redo(target);
        } catch (RuntimeException ex) {
            redoStack.push(command);
            throw ex;
        } finally {
            applying = previous;
        }
        undoStack.push(command);
        return true;
    }

    void runSuppressed(Runnable action) {
        boolean previous = applying;
        applying = true;
        try {
            action.run();
        } finally {
            applying = previous;
        }
    }

    void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
