package Petroleum.Gui;

interface EditCommand<T> {
    void undo(T target);

    void redo(T target);

    String label();
}
