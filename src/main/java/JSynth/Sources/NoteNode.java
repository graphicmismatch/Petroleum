package JSynth.Sources;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Stateful;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class NoteNode extends AbstractNode implements NoteSource, Stateful {
    private static final String DEFAULT_NOTE = "A4";

    private volatile String noteText;
    private volatile float frequency;

    public NoteNode() {
        this(DEFAULT_NOTE);
    }

    public NoteNode(String noteText) {
        super(List.of(), List.of("out"));
        setNoteText(noteText);
    }

    @Override
    public String getNoteText() {
        return noteText;
    }

    @Override
    public void setNoteText(String text) {
        float pitch = NoteName.parse(text);
        this.noteText = text;
        this.frequency = (float) (440.0 * Math.pow(2.0, (pitch - 69) / 12.0));
    }

    public float getFrequency() {
        return frequency;
    }

    @Override
    public void saveState(Map<String, String> state) {
        state.put("note", noteText);
    }

    @Override
    public void loadState(Map<String, String> state) {
        String saved = state.get("note");
        if (saved != null) {
            setNoteText(saved);
        }
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        Arrays.fill(outputs[OUT], 0, frameCount, frequency);
    }

    @Override
    public String getName() {
        return "Note (" + noteText + ")";
    }
}
