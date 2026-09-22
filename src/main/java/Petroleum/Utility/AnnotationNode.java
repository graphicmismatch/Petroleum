package Petroleum.Utility;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Stateful;

import java.util.List;
import java.util.Map;

public class AnnotationNode extends AbstractNode implements AnnotationSource, Stateful {
    private String text = "";

    public AnnotationNode() {
        super(List.of(), List.of());
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    @Override
    public void saveState(Map<String, String> state) {
        state.put("text", text);
    }

    @Override
    public void loadState(Map<String, String> state) {
        String saved = state.get("text");
        if (saved != null) {
            text = saved;
        }
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
    }

    @Override
    public String getName() {
        return "Annotation";
    }
}
