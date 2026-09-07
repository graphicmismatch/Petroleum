package JSynth.Effects;

import JSynth.Expression.FormulaSource;
import JSynth.Expression.MathExpression;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;
import JSynth.Graph.Port;

import java.util.List;

public class WaveshaperNode extends AbstractNode implements FormulaSource {
    public static final int IN = 0;

    private volatile MathExpression expression;

    public WaveshaperNode(String source) {
        super(List.of(new Port("in", 0f)), List.of("out"));
        this.expression = new MathExpression(source);
    }

    @Override
    public String getSource() {
        return expression.getSource();
    }

    @Override
    public void setSource(String source) {
        this.expression = new MathExpression(source);
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        MathExpression current = expression;
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = (float) current.evaluate(inputs[IN][s]);
        }
    }

    @Override
    public String getName() {
        return "Waveshaper";
    }
}
