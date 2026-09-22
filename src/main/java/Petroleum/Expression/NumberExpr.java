package Petroleum.Expression;

public class NumberExpr implements Expr {
    private final double value;

    public NumberExpr(double value) {
        this.value = value;
    }

    @Override
    public double evaluate(double[] slots) {
        return value;
    }
}
