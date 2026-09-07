package JSynth.Oscillators.Implementations;

import JSynth.Expression.FormulaSource;
import JSynth.Expression.MathExpression;
import JSynth.Oscillators.IOscillator;

public class ExpressionOscillator implements IOscillator, FormulaSource {
    private volatile MathExpression expression;

    public ExpressionOscillator(String source) {
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
    public float getSample(double phase) {
        return (float) expression.evaluate(phase);
    }

    @Override
    public String getName() {
        return "Expression (" + expression.getSource() + ")";
    }
}
