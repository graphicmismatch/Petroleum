package JSynth.Expression;

public class VariableExpr implements Expr {
    private final int slot;
    private final double constantValue;

    public VariableExpr(String name, int position) {
        if (name.equals("x")) {
            this.slot = 0;
            this.constantValue = 0.0;
        } else if (name.equals("t")) {
            this.slot = 1;
            this.constantValue = 0.0;
        } else {
            Double constant = Constants.get(name);
            if (constant == null) {
                throw new ExpressionException("Unknown variable '" + name + "'", position);
            }
            this.slot = -1;
            this.constantValue = constant;
        }
    }

    @Override
    public double evaluate(double[] slots) {
        return slot >= 0 ? slots[slot] : constantValue;
    }
}
