package JSynth.Expression;

public class MathExpression {
    private final String source;
    private final Expr root;
    private final double[] slots = new double[2];

    public MathExpression(String source) {
        this.source = source;
        this.root = Parser.parse(source);
    }

    public double evaluate(double x) {
        slots[0] = x;
        slots[1] = x / (2 * Math.PI);
        return root.evaluate(slots);
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return source;
    }
}
