package JSynth.Sources;

public interface NoteSource {
    String getNoteText();

    /**
     * @throws IllegalArgumentException if text is not a parseable note name
     */
    void setNoteText(String text);
}
