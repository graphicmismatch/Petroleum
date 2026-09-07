package JSynth.Expression;

public class UnaryExpr implements Expr {
    private final TokenType operator;
    private final Expr operand;

    public UnaryExpr(TokenType operator, Expr operand) {
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public double evaluate(double[] slots) {
        double value = operand.evaluate(slots);
        return operator == TokenType.MINUS ? -value : value;
    }
}
