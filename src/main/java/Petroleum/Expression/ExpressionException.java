package Petroleum.Expression;

public class ExpressionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExpressionException(String message, int position) {
        super(message + " (at position " + position + ")");
    }
}
