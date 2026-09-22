package Petroleum.Expression;

public class BinaryExpr implements Expr {
    private final TokenType operator;
    private final Expr left;
    private final Expr right;
    private final int position;

    public BinaryExpr(TokenType operator, Expr left, Expr right, int position) {
        this.operator = operator;
        this.left = left;
        this.right = right;
        this.position = position;
    }

    @Override
    public double evaluate(double[] slots) {
        double a = left.evaluate(slots);
        double b = right.evaluate(slots);
        switch (operator) {
            case PLUS:
                return a + b;
            case MINUS:
                return a - b;
            case STAR:
                return a * b;
            case SLASH:
                if (b == 0.0) {
                    throw new ExpressionException("Division by zero", position);
                }
                return a / b;
            case PERCENT:
                if (b == 0.0) {
                    throw new ExpressionException("Division by zero", position);
                }
                return a % b;
            case CARET:
                return Math.pow(a, b);
            default:
                throw new ExpressionException("Unsupported operator '" + operator + "'", position);
        }
    }
}
